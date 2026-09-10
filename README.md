# Kankan · 跨设备同步观影平台

基于 Spring Boot 与 WebSocket 的远程同步观影平台：房主控制播放、暂停与进度拖动，房间内成员实时同步观看，并支持房间内文字聊天与弹幕。

个人独立完成的本科毕业设计项目。

---

## 功能

- **用户认证**：注册、登录、JWT 令牌鉴权、基于角色的权限控制（`ROLE_ADMIN` / `ROLE_USER`）
- **房间管理**：创建房间、加入房间、成员列表、房主权限控制
- **播放状态同步**：播放、暂停、拖动进度条、倍速切换，房间内广播同步
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

## 已知限制

以下内容**尚未实现**，在此列出以保证文档与代码一致：

- **未实现**基于时间戳的延迟补偿与 EWMA RTT 估计：当前仅做状态广播，不做时延补偿
- **未实现**边缘节点与云边协同架构
- **未实现**语音聊天（仅文字、图片与系统消息）
- 未使用外部 STOMP broker 或消息中间件：当前为 Spring 内建 simple broker，状态在单实例内存中
- 房间播放状态保存在 JVM 内存，多实例部署需引入共享状态
- 缺少自动化测试：`spring-boot-starter-test` 已引入但尚未编写用例
- 无接口限流、连接心跳与断线重连的专项处理
- 仓库内未提供 Nginx 配置（README 早期版本曾提及，实际未包含）

## 后续计划

- 补齐基于时间戳的延迟补偿与 EWMA RTT 估计
- 引入外部消息代理与共享状态，支持多实例水平扩展
- 补充单元测试与集成测试
- 前端迁移到构建式工程（Vite），替换 CDN 全局构建
- 默认账号改为首次启动随机生成或强制修改

## 许可

个人学习与毕业设计项目。
