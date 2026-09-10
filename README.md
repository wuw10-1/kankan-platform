# Kankan · 跨设备同步观影平台

基于 Spring Boot 与 WebSocket 的远程同步观影平台：房主控制播放、暂停与进度拖动，房间内成员实时同步观看，并支持房间内文字聊天与弹幕。

个人独立完成的本科毕业设计项目。

---

## 功能

- **用户认证**：注册、登录、JWT 令牌鉴权、基于角色的权限控制（`ROLE_ADMIN` / `ROLE_USER`）
- **房间管理**：创建房间、加入房间、成员列表、房主权限控制
- **播放状态同步**：播放、暂停、拖动进度条、倍速切换，房间内广播同步
- **基于时间戳的延迟补偿**：定期探测 RTT 并用 EWMA 平滑，SEEK 目标点按单向延迟回退，含防抖、限频、边界保护与关键帧对齐
- **实时聊天**：房间内消息收发，支持文本、图片与系统消息（`TEXT` / `IMAGE` / `SYSTEM`）
- **弹幕**：可开关，将聊天文本浮现在视频画面上
- **视频管理**：视频上传与元数据管理（支持本地磁盘与阿里云 OSS 两种存储后端）

## 技术栈

| 层级 | 实际使用 |
| --- | --- |
| 后端 | Spring Boot 3.1.0、Java 17、Spring MVC |
| 实时通信 | Spring WebSocket（STOMP）+ SockJS，内建 simple broker（`/topic`、`/queue`） |
| 安全 | Spring Security + JWT（jjwt） |
| 持久化 | Spring Data JPA + Hibernate；开发环境 H2 内存库 / 生产环境 MySQL 8 |
| 缓存 | Spring Data Redis（生产环境）；开发环境不依赖 Redis |
| 前端 | Thymeleaf 模板 + Vue 3（CDN 全局构建）+ Element Plus + Axios + STOMP.js |
| 文件存储 | 本地磁盘 `uploads/`，或阿里云 OSS SDK |
| 视频处理 | FFmpeg（可选，用于转码与缩略图） |
| API 文档 | springdoc-openapi（Swagger UI） |
| 部署 | Docker + Docker Compose（MySQL + Redis + 应用） |

> 前端通过 CDN 引入 Vue 3 全局构建并在 Thymeleaf 页面中挂载，属于轻量集成方式：**未使用** Vite/Webpack 构建流程，也未使用 Redux/Pinia 状态管理。

## 快速开始

### 环境要求

- JDK 17 或更高
- Maven（或直接使用仓库自带的 `mvnw` 包装器）

开发环境使用 H2 内存数据库，**无需**安装 MySQL 或 Redis。

### 运行

```bash
# Linux / macOS
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Windows
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

也可以先构建再运行：

```bash
./mvnw clean package -DskipTests
java -jar target/kankan-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Windows 下另提供便捷脚本：`run-dev.bat`、`start-dev.bat`。

### 访问地址

服务端口为 **8090**，上下文路径为 **`/api`**：

| 用途 | 地址 |
| --- | --- |
| 应用首页 | <http://localhost:8090/api/> |
| H2 控制台（仅 dev） | <http://localhost:8090/api/h2-console> |
| Swagger UI | <http://localhost:8090/api/swagger-ui.html> |

### 默认账号

`DataInitializer` 在启动时创建以下账号：

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `password` | `ROLE_ADMIN` |
| `user` | `password` | `ROLE_USER` |

**这两个口令是硬编码的开发默认值，请勿用于任何公开部署。** 生产环境应从环境变量读取，并在首次登录后立即修改。

## 配置

生产环境配置通过环境变量注入，仓库内不含任何真实凭据：

| 变量 | 用途 | 默认值（仅占位） |
| --- | --- | --- |
| `DB_URL` | MySQL 连接串 | `jdbc:mysql://localhost:3306/kankan...` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `kankan_user` / `secure_password` |
| `JWT_SECRET` | JWT 签名密钥 | 占位值，必须替换 |
| `JWT_EXPIRATION` | 令牌有效期（毫秒） | `86400000` |
| `ALIYUN_OSS_ENDPOINT` / `ACCESS_KEY_ID` / `ACCESS_KEY_SECRET` / `BUCKET_NAME` | 阿里云 OSS | 占位值 |
| `FFMPEG_PATH` / `FFPROBE_PATH` | FFmpeg 可执行文件路径 | `C:\ffmpeg\bin\...` |

开发环境配置见 `src/main/resources/application-dev.yml`，其中 JWT 密钥为显式的开发用假值。

## 容器化部署

```bash
docker compose up -d
```

`docker-compose.yml` 启动三个服务：MySQL 8、Redis 6 与应用本身。应用镜像基于 `openjdk:17-jdk-slim` 并安装 FFmpeg，对外映射端口 `8090`。

## 项目结构

```
kankan-platform/
├── src/main/java/com/kankan/
│   ├── KankanApplication.java        # 应用入口
│   ├── config/                       # 安全、WebSocket、Redis、数据初始化配置
│   ├── controller/                   # REST 接口层
│   ├── model/                        # 领域实体
│   ├── payload/                      # 请求与响应体
│   ├── repository/                   # JPA 仓储
│   ├── security/                     # JWT 签发、过滤器与认证入口
│   ├── service/                      # 业务逻辑与实现
│   └── websocket/                    # STOMP 控制器与消息结构
├── src/main/resources/
│   ├── application.yml               # 主配置（环境变量注入）
│   ├── application-dev.yml           # 开发配置（H2、无 Redis）
│   ├── db/init.sql                   # 数据库建库建表脚本
│   ├── static/                       # 前端 JS / CSS
│   └── templates/                    # Thymeleaf 页面
├── Dockerfile
└── docker-compose.yml
```

## 实时同步协议

客户端经 SockJS 连接 STOMP 端点 `/ws`。

**发送（客户端 → 服务端）**

| 目的地 | 说明 |
| --- | --- |
| `/app/video.sync` | 播放、暂停、拖动进度、倍速切换 |
| `/app/video.join` | 加入房间；服务端将当前播放状态回发给该用户 |

**订阅（服务端 → 客户端）**

| 目的地 | 说明 |
| --- | --- |
| `/topic/room.{roomId}.video` | 房间内播放状态广播 |
| `/topic/room.{roomId}.chat` | 房间内聊天消息 |
| `/queue/video.sync` | 用户私有队列：加入房间时的当前播放状态 |

同步消息体（`VideoSyncMessage`）：

```json
{
  "roomId": "123456",
  "action": "SEEK",
  "currentTime": 120.5,
  "playbackRate": 1.0,
  "userId": "42",
  "username": "alice",
  "timestamp": 1712345678901
}
```

`action` 取值：`PLAY`、`PAUSE`、`SEEK`、`RATE_CHANGE`、`JOIN`、`LEAVE`、`SYNC_REQUEST`、`SYNC_RESPONSE`。

服务端在收到 `PLAY` / `PAUSE` / `SEEK` 时更新房间的视频状态，校验房间存在后广播给房间内所有成员。

## 延迟补偿算法

对应论文 5.2 节。核心问题是：房主在本地时刻 `tsend` 执行 SEEK，成员在 `trecv` 才收到，这段时间里房主已继续播放，若直接把成员播放器跳到房主发来的目标位置，成员会落到「过去」。

**补偿公式**

```
单向延迟  d ≈ RTT / 2
补偿目标  T_sync = t_target - d
```

单向延迟无法直接测量，因此由客户端定期探测往返时延 RTT，并按上下行对称假设取一半。为抑制单次测量误差，RTT 用指数加权移动平均（EWMA）平滑：

```
RTT_smooth = α × RTT_sample + (1 - α) × RTT_smooth_old     (α = 0.25)
```

**RTT 探测协议**

| 方向 | 目的地 | 说明 |
| --- | --- | --- |
| 客户端 → 服务端 | `/app/time.ping` | 携带 `clientId`、`clientSendTime`、`sequence` |
| 服务端 → 客户端 | `/topic/time.sync` | 原样回带同一 `clientSendTime`，附 `serverTime`；客户端按 `clientId` 过滤 |

客户端收到响应后用 `Date.now() - clientSendTime` 得到 RTT 并回填平滑器。

**四项优化措施**

| 措施 | 实现 |
| --- | --- |
| 动态 RTT 采样频率 | 正常 10s 一次；连续 2 个样本与平滑值偏差超过 50ms 时进入抖动模式，缩短为 2s；连续 2 个正常样本后退出 |
| SEEK 防抖与限频 | 房主端 200ms 窗口内最多发送一次（5Hz）；成员端补偿后位置与本地差异小于 50ms 则忽略；服务端另有权威限频兜底 |
| 边界保护 | 补偿后位置不小于 0、不超过视频总时长，且单向延迟补偿量上限 1 秒 |
| 关键帧对齐 | 吸附到不超过目标值的最近可寻址位置，避免跳到 P/B 帧导致画面异常 |

**实现位置**

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/kankan/sync/LatencyCompensator.java` | 算法本体：EWMA、抖动状态机、补偿与边界保护、防抖与限频判定、关键帧对齐（纯计算，可单测） |
| `src/main/java/com/kankan/sync/LatencyCompensationService.java` | 按客户端维护补偿器状态，按房间执行服务端 SEEK 限频 |
| `src/main/java/com/kankan/websocket/controller/TimeSyncController.java` | RTT 探测回声端点 |
| `src/main/resources/static/js/latency-compensation.js` | 客户端对应实现：同参数、同判定规则 |
| `src/test/java/com/kankan/sync/` | 33 个用例，覆盖上述全部公式与优化措施 |

播放器控制栏会显示当前 EWMA 平滑后的 RTT，便于观察网络状况与补偿是否生效。

> 说明：算法机制已实现并有单元测试覆盖，但**同步延迟的实际数值尚未按论文第 7 章方案实测**，因此文档中不引用 18ms / 145ms 一类指标。

## 已知限制

以下内容**尚未实现**，在此列出以保证文档与代码一致：

- **未实现**边缘节点与云边协同架构：延迟补偿在客户端完成，信令仍经中心服务器转发
- **未实现**语音聊天（仅文字、图片与系统消息）
- 未使用外部 STOMP broker 或消息中间件：当前为 Spring 内建 simple broker，状态在单实例内存中
- 房间播放状态保存在 JVM 内存，多实例部署需引入共享状态
- 延迟补偿的**性能数据尚未实测**：论文中的 18ms / 78ms / 145ms 等指标需要按测试方案重新测量后才能引用
- 关键帧对齐使用浏览器 `seekable` 区间作为候选兜底，未解析视频关键帧索引
- 预测性微调（播放速率 1.01 倍）已实现判定逻辑，默认未启用
- 缺少认证与房间模块的自动化测试（延迟补偿算法已有 33 个用例）
- 无接口限流、连接心跳与断线重连的专项处理
- 仓库内未提供 Nginx 配置（早期文档曾提及，实际未包含）

## 后续计划

- 补齐边缘节点与云边协同，并实现节点间转发
- 按论文第 7 章方案测量同步延迟，产出可复现的性能数据
- 引入外部消息代理与共享状态，支持多实例水平扩展
- 补充认证、房间与消息通路的集成测试
- 前端迁移到构建式工程（Vite），替换 CDN 全局构建
- 默认账号改为首次启动随机生成或强制修改

## 许可

个人学习与毕业设计项目。
