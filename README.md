# 顺手 ShunShou — 校园互助交易平台

Spring Boot 3 / Java 21 + MyBatis-Plus + MySQL 8 + Redis + Vue 3 管理后台 + uni-app 学生端。同一学生账号可以发单与接单。

**重要：现有钱包、支付、退款、结算属于本地模拟交易，不是真实收付款系统。** 本仓库基于 [iRun](https://github.com/Ikeu-1030/iRun)（MIT）二次开发，原作者版权和许可见 [LICENSE](LICENSE)。

## 1. Windows 本地环境

推荐使用 Docker Desktop 一键运行后端、管理后台、MySQL、Redis。

必需：Git、Docker Desktop（WSL2 / Linux 容器模式）。
按需安装：JDK 21、IntelliJ IDEA、Maven 3.9+、Node.js 22、HBuilderX、微信开发者工具。

用 PowerShell 确认 Docker Desktop 已启动：

    git --version
    docker --version
    docker compose version

## 2. 首次运行：完整 Docker 环境（推荐）

在 PowerShell：

    git clone https://github.com/Miao-wang11/INPassing.git
    cd INPassing\docker
    Copy-Item .env.example .env
    notepad .env

编辑生成的 docker/.env，**替换下面所有示意密码和 JWT 密钥**：

    MYSQL_ROOT_PASSWORD=自行设置强数据库密码
    JWT_ADMIN_SECRET=自行设置至少32位随机管理员密钥
    JWT_USER_SECRET=自行设置另一段至少32位随机学生密钥
    ADMIN_USERNAME=admin
    ADMIN_PASSWORD=自行设置强后台密码
    MYSQL_PORT=3308
    REDIS_PORT=6381
    BACKEND_PORT=8080
    NGINX_PORT=80
    SHUNSHOU_DEMO_SEED=false
    SHUNSHOU_AI_INTERNAL_TOKEN=自行生成64位随机十六进制字符串

若需要演示账号，在**隔离的一次性数据库**中额外启用 SHUNSHOU_DEMO_SEED=true，
并在不上传 Git 的 docker/.env 中配置 SHUNSHOU_DEMO_PASSWORD=自行生成6位随机数字。
SHUNSHOU_DEMO_SEED=true **仅适合隔离的本地演示**，会生成演示学校与学生。无演示需求设为 false。阿里云、微信、腾讯地图密钥在本地演示可以先留空，但短信、OSS 上传、微信授权与地图相关功能将受到限制。

仍在 docker 目录运行：

    docker compose config
    docker compose up -d --build
    docker compose ps
    docker compose logs -f backend

退出日志跟踪按 Ctrl+C，不会停掉容器。完整镜像构建会重新编译 Vue 管理端和 Java 后端，并启动内部 Python Agent。
Python 8000 端口不对外映射。MySQL 全新数据卷以 `backend/runningerrands.sql`
初始化（该脚本已包含校区、服务橱窗与售后），只额外运行两份
`20261021_agent_*.sql`。已有数据卷不要再次执行建库脚本或重复迁移。

### 服务访问

| 用途 | 地址 |
| --- | --- |
| 管理后台 | http://localhost/api/ |
| 后端健康检查 | http://localhost:8080/api/actuator/health |
| MySQL | 127.0.0.1:3308，库 runningerrands，账号 root |
| Redis | 127.0.0.1:6381，database 1，无密码 |

若本机 80 端口被占用，在 .env 改为 NGINX_PORT=8088，管理后台改访问 http://localhost:8088/api/ 。同理可以改变宿主机的 MYSQL_PORT、REDIS_PORT、BACKEND_PORT。

**演示账号**（仅 SHUNSHOU_DEMO_SEED=true）：testuser、testuser2（东校区），testuser3（西校区），登录和模拟支付密码均使用本地 `.env` 中的 `SHUNSHOU_DEMO_PASSWORD`，不再使用仓库公开的默认密码。管理员账号为 ADMIN_USERNAME 和你设置的 ADMIN_PASSWORD。

注意：管理员由后端在数据库首次创建；数据库已存在账号时，修改 .env 不会重置其密码。同样，改变 .env 的 MYSQL_ROOT_PASSWORD 不会修改已存在数据卷中的 MySQL 密码。

### 常用命令

在 INPassing/docker 目录：

    docker compose ps
    docker compose logs --tail=100 backend
    docker compose restart backend
    docker compose up -d --build
    docker compose down

docker compose down 默认保留数据。**不要执行 docker compose down -v**，除非你确认要永久删除容器中的 MySQL 和 Redis 数据卷。

## 3. 在 IDEA 单独调试 Spring Boot（可选）

先启动 MySQL、Redis（进入 INPassing/docker）：

    docker compose up -d mysql redis

不要再同时运行占用 8080 的 Docker 后端：

    docker compose stop backend nginx

在 IDEA 以 Maven 项目打开 INPassing/backend/pom.xml，项目 JDK 设置为 21。主启动类：

    com.ikeu.server.RunningerrandsServerApplication

从 backend/runningerrands-server/src/main/resources/application-template.yml 复制出同目录的 application-dev.yml（该文件被 gitignore 忽略），修改为你自己的本机参数：

- MySQL URL：jdbc:mysql://localhost:3308/runningerrands?useUnicode=true&characterEncoding=UTF-8&serverTimezone=GMT%2B8
- MySQL username：root；password：docker/.env 的 MYSQL_ROOT_PASSWORD
- Redis host：localhost；port：6381；database：1；无密码
- IDEA Run Configuration 环境变量：RUNNING_ERRANDS_JWT_ADMIN_SECRET、RUNNING_ERRANDS_JWT_USER_SECRET、RUNNING_ERRANDS_ADMIN_USERNAME、RUNNING_ERRANDS_ADMIN_PASSWORD，填入你的安全配置

Spring Boot 默认激活 dev。注意 application-template.yml 原本默认使用数据库端口 3306、Redis 端口 6379，**配合 Docker 暴露端口必须改成 3308、6381**。不要提交私密的 application-dev.yml 或 AccessKey。

Maven 构建（在 backend 目录）：

    mvn clean package -DskipTests

## 4. 管理后台独立开发（可选）

先让 Java 后端在 localhost:8080 正常运行，再打开 PowerShell：

    cd INPassing\admin
    npm ci
    npm run dev

访问 http://localhost:3001/api/ ，Vite 会将 API 请求代理到 localhost:8080。要运行生产构建可以使用 npm run build。

## 5. 运行 uni-app 学生端（可选）

1. 用 HBuilderX 导入 mobile 文件夹，运行到 H5 浏览器或微信开发者工具；**Docker Compose 不包含学生端 H5/小程序的自动部署**。
2. 开发环境请求配置在 mobile/utils/config.js，默认 API 为 http://localhost:8080/api，WebSocket 为 ws://localhost:8080/api/ws。本机浏览器可以使用 localhost。
3. **真机不能用 localhost 访问电脑后端**：将 mobile/utils/config.js 中 develop 环境的地址改为运行后端电脑的局域网 IP，并确认同 Wi-Fi、Windows 防火墙及端口访问。
4. 微信小程序要在 mobile/manifest.json 设置你自己的 mp-weixin AppID，正式发布需配置合法 HTTPS/WSS 域名并在微信平台登记合法服务器域名。仓库中残留的原项目 AppID 和线上域名不可直接作为你的正式配置。
5. 若未配置短信、OSS、地图或微信开放平台凭据，相关第三方能力不可视为已打通。优先用普通演示账号核对本地业务流程。

## 6. 数据库初始化与旧库升级

**全新空 MySQL 卷**：Docker 首次启动自动执行 backend/runningerrands.sql 建库建表。

**已有数据的旧库**：backend/runningerrands.sql 包含 DROP DATABASE；绝对不要手动对旧库重跑，否则会丢失数据。备份后确认旧库已做过哪些迁移，再按顺序对缺失项各执行一次：

    backend/migrations/20261020_campus_scope.sql
    backend/migrations/20261020_service_offer_booking.sql
    backend/migrations/20261020_order_dispute.sql
    backend/migrations/20261020_operations_indexes.sql

迁移文件不是允许重复盲目运行的通用初始化脚本。可用 DataGrip 导入、核对 SQL；旧数据库账号、已有任务和校区关联需要按实际数据检查。

## 7. 常见问题

- 连接不到 MySQL：检查 docker compose ps、docker/.env 密码，确认容器内是 mysql:3306，本机是 localhost:3308；旧卷密码不会随 .env 自动变更。
- 连接不到 Redis：容器内是 redis:6379，本机是 localhost:6381；所用数据库为 1。
- 后端启动不了：运行 docker compose logs -f backend；先检查 MySQL/Redis 健康状态、管理员初始化和 JWT 密钥。
- 管理端空白或 404：确认路径有 /api/；访问 http://localhost:8080/api/actuator/health；核查 nginx 和 backend 日志。
- 代码修改后容器没变化：回到 docker 目录执行 docker compose up -d --build。
- 学生端网络失败：检查 mobile/utils/config.js 地址、设备 localhost、微信域名限制及 WebSocket 协议。
- 遇到端口占用：调整 .env 映射，单独 IDEA 运行 Java 时不要同时保留 Docker backend。

只保留实际源码、运行依赖、构建配置、SQL 与增量迁移。该项目尚不支持真实支付资金托管，运行前请区分模拟业务和生产服务。
