# Kankan 项目技术总结

本文说明 Kankan 的实际架构、消息流程与当前实现边界。功能清单与运行方式见 [README.md](README.md)。

## 一、系统组成

```
浏览器（Thymeleaf 页面 + Vue 3 CDN 全局构建）
   │  REST（/api/**）            STOMP over SockJS（/ws）
   ▼                                   ▼
Spring MVC 控制器层  ────────►  WebSocket 消息层
   │                                   │
   ▼                                   ▼
Service 业务层 ──► JPA 仓储 ──► MySQL / H2
   │
   └──► 文件存储：本地磁盘 或 阿里云 OSS
```

- **开发环境**：H2 内存库 + 无 Redis，零外部依赖即可启动
- **生产环境**：MySQL 8 + Redis 6，经 Docker Compose 编排

## 二、数据模型

| 实体 | 关键字段 | 说明 |
| --- | --- | --- |
| `User` | username、email、phone、password、roles | 密码经 BCrypt 加盐哈希存储 |
| `Role` | `ROLE_ADMIN`、`ROLE_USER` | 角色枚举，用于访问控制 |
| `Room` | roomCode、name、password、isPrivate、maxMembers、owner、currentVideo、status | 支持公开/私密房间与人数上限 |
| `Video` | url、duration、status | 视频元数据 |
| `ChatMessage` | room、sender、content、type、imageUrl | 消息类型为 `TEXT` / `IMAGE` / `SYSTEM` |

## 三、鉴权流程

1. 客户端调用 `/api/auth/signin`，服务端经 `AuthenticationManager` 校验后由 `JwtTokenProvider` 签发 JWT；
2. 后续请求在 `Authorization` 头携带令牌；
3. `JwtAuthenticationFilter` 解析令牌并把认证信息注入 `SecurityContext`；
4. 令牌无效或缺失时由 `JwtAuthenticationEntryPoint` 返回统一的未认证响应。

令牌使用 HMAC 签名，密钥与有效期均由配置注入（`jwt.secret`、`jwt.expiration`）。

## 四、实时同步消息流程

**连接**：客户端经 SockJS 连接 `/ws`，订阅房间主题。

**房主操作**（播放/暂停/拖动/倍速）：

```
客户端 ──/app/video.sync──► VideoSyncController
                               │ 校验房间是否存在
                               │ 更新房间视频状态（currentTime、playing）
                               ▼
        /topic/room.{roomId}.video ──► 房间内全部成员
```

**新成员加入**：

```
客户端 ──/app/video.join──► VideoSyncController
                               │ 读取房间当前状态
                               ├──/queue/video.sync──► 仅该用户（私有队列）
                               └──/topic/room.{roomId}.video──► 通知其他成员
```

**状态存储**：房间当前播放状态保存在 `Room` 实体中并由 `RoomService` 维护；生产环境可经 Redis 缓存，开发环境不依赖 Redis。

**消息代理**：使用 Spring 内建 simple broker（`enableSimpleBroker("/topic", "/queue")`），应用前缀 `/app`，用户前缀 `/user`。

## 五、弹幕实现

聊天组件提供开关（`danmakuEnabled`）。开启时，房间内的文本消息除进入聊天列表外，同时投递到视频播放器组件的弹幕层叠加显示。弹幕不单独存储，复用 `ChatMessage`（`TEXT` 类型）。

## 六、视频处理的实现边界（重要）

视频处理相关代码目前为**占位实现**，不具备实际转码能力：

| 方法 | 当前行为 |
| --- | --- |
| `VideoServiceImpl.generateHlsStream` | 直接返回原始视频 URL，**未调用 FFmpeg 切片** |
| `VideoServiceImpl.processVideo` | 仅把状态置为 `READY`，`duration` 硬编码为 `120L`；**未提取时长、未生成缩略图、未转码多清晰度** |

`Dockerfile` 中确实安装了 FFmpeg、`application.yml` 中也提供了 `ffmpeg.path` 配置项，但上述方法尚未接入。因此：

- **不具备** HLS 切片能力
- **不具备** 自适应码率（多清晰度）能力
- **不具备** 缩略图生成能力
- **不具备** 客户端时间校准或播放速率自适应微调

上述内容属于后续计划，不应作为已实现能力表述。

## 七、延迟补偿算法

论文 5.2 节的基于时间戳延迟补偿已实现，分为服务端算法与客户端算法两部分：

| 组件 | 职责 |
| --- | --- |
| `sync/LatencyCompensator` | EWMA 平滑 RTT、抖动状态机、`T_sync = t_target - RTT/2`、边界保护、防抖与限频判定、关键帧对齐 |
| `sync/LatencyCompensationService` | 按客户端维护补偿器状态；按房间执行服务端 SEEK 限频 |
| `websocket/controller/TimeSyncController` | `/app/time.ping` → `/topic/time.sync` 回声，供客户端测量 RTT |
| `static/js/latency-compensation.js` | 客户端对应实现，参数与判定规则和后端一致 |

关键参数（α=0.25、正常采样 10s / 抖动 2s、抖动阈值 50ms、补偿上限 1s、防抖 50ms、限频 200ms）均以后端常量定义，并有 33 个单元测试覆盖。

**边界说明**：算法机制可运行、可测试，但同步延迟的**实测数值尚未产出**。论文中的 18ms / 78ms / 145ms 等数据需按第 7 章方案重新测量，在此之前不得作为已测结果引用。

## 八、已知限制

- 房间播放状态在单实例 JVM 内存中，多实例部署需引入共享状态
- 未使用外部消息代理，simple broker 不支持跨实例广播
- 未实现边缘节点与云边协同：延迟补偿在客户端完成，信令仍经中心服务器转发
- 未实现语音聊天与屏幕共享
- 延迟补偿算法有单元测试覆盖（33 个用例），但认证、房间与消息通路尚无集成测试
- 延迟补偿的实测数据尚未产出，论文性能指标需重新测量
- 关键帧对齐使用 `seekable` 区间兜底，未解析真实关键帧索引
- 预测性微调已实现判定逻辑但默认未启用
- 无接口限流、连接心跳与断线重连的专项处理
- 默认账号口令硬编码于 `DataInitializer`，不适用于公开部署

## 九、后续改进方向

1. **边缘计算**：实现边缘节点与云边协同，补齐节点间转发与信令就近广播
2. **性能验证**：按论文第 7 章方案测量同步延迟，产出可复现的实验数据与脚本
3. **视频处理**：接入 FFmpeg 完成 HLS 切片与缩略图生成，补充多清晰度转码
4. **可扩展性**：以外部消息代理（RabbitMQ / Kafka）替换 simple broker，房间状态外置到 Redis，支持水平扩展
5. **工程质量**：补充认证、房间与消息通路的集成测试，加入接口限流与监控指标
6. **前端工程化**：由 CDN 全局构建迁移到 Vite 构建
7. **安全**：默认口令改为首次启动生成或强制修改，完善越权校验
