# 顺手阶段 1 — 本地启动、配置与验证手册

## A. 最少依赖

- Windows 11 + Docker Desktop、Git，Docker Compose V2。
- 项目根目录包含 `Dockerfile`（统一前后端构建）；`docker/docker-compose.yml`、`docker/nginx/nginx.conf`。
- 首次运行需要访问 Docker Hub、Maven Central 和 npm Registry；没有网络时不能完成首次构建。
- 宿主机端口：Nginx 80、后端 8080、MySQL 3308、Redis 6381。修改 `docker/.env` 可避让。
- 本地 IDEA 开发：JDK 21、Maven 3.9+、Node.js 22+、MySQL 8、Redis 7；优先 Docker 统一启动免去 Windows Redis 安装冲突。

## B. 从零克隆后运行

```powershell
git clone -b integration/irun-bootstrap https://github.com/Miao-wang11/INPassing.git
cd INPassing\docker
Copy-Item .env.example .env
notepad .env
docker compose config
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

环境变量必须修改：`MYSQL_ROOT_PASSWORD`（MySQL 根密码）、`JWT_ADMIN_SECRET`、`JWT_USER_SECRET`（建议独立随机长字符串）、`ADMIN_PASSWORD`。不要把 `.env` 提交 Git。

默认 `SHUNSHOU_DEMO_SEED=false`，仅需演示时在隔离本机设置为 `true`。该开关只对首次没有演示用户的库进行初始化，不删除数据库已有用户。

## C. 启动验证

在新的 PowerShell 窗口：

```powershell
docker compose ps
curl.exe http://localhost:8080/api/actuator/health
curl.exe -I http://localhost/api/
docker compose logs --tail=100 backend
```

期望：`health` 返回 UP、Nginx 访问管理后台可收到 HTML/跳转到登录页。**实际结果要按控制台返回核对**。通过管理员在管理端检查用户、任务和订单页面能实际请求 API。

本地 MySQL 可通过 DataGrip 连接 `127.0.0.1:3308`，用户名 root，密码为 `MYSQL_ROOT_PASSWORD`；库名为 `runningerrands`。Redis 通过 `127.0.0.1:6381` 访问，仅绑定本机，默认 DB 1。

## D. 模拟交易（只供隔离测试）

启用 `SHUNSHOU_DEMO_SEED=true` 后，底座提供：
- 发布用户：`testuser2 / 123456`
- 可申请跑腿员的用户：`testuser / 123456`（上游跑腿员档案可能需要手动切换在线）
- 模拟余额和支付密码：演示数据中支付密码 `123456`

上游仅模拟账户余额和数据库流水，绝非真钱充值；请不要使用个人真实信息或真实资金。在未来顺手版本中，学生双向发接单不需要独立商家账号。

## E. 常见故障

**1. 80、8080、3308 或 6381 端口被占用**
- 修改 `docker/.env` 中 `NGINX_PORT=8088`、`BACKEND_PORT`、`MYSQL_PORT`、`REDIS_PORT`；重新 `docker compose up -d`。
- 改宿主端口不改容器内 MySQL/Redis 后端地址。

**2. MySQL 用户名密码错误**
- 使用首次创建 MySQL 数据卷时的密码；修改环境变量并不会修改已有卷中的 root 密码。
- 确实无需保留任何数据时才使用 `docker compose down -v`，它会**删除数据库和 Redis 卷**。

**3. 后端启动失败**
- `docker compose logs --tail=200 backend` 查看具体异常；再检查 `docker compose ps` 是否 MySQL/Redis healthy。
- 不要将 `application-template.yml` 中的示例数据库密码直接用在正式环境。

**4. 管理端为空白、资源 404**
- 根目录多阶段 Dockerfile 会执行 `npm ci && npm run build` 并把 dist 打进 Spring Boot 的静态资源路径；需要用 `docker compose build --no-cache backend` 排除旧镜像缓存。
- Nginx 只做反向代理，`/api/ws/` 同时支持 WebSocket 升级。

**5. 镜像拉取、Maven/npm 无法下载**
- 检查网络/Docker Hub、Maven Central 与 npm Registry 可达性，并保存完整日志；这类失败不等同于源码不能编译。

**6. Admin 登录后数据异常**
- 首先确认 SQL 初始化脚本是否完成、模拟账户是否生成、接口 Token 请求头是否正确；不要通过直接修改数据库来绕过授权。

## F. 验收边界与开发任务

构建通过 != API 可用 != 前后端交易闭环通过。目前还需要：正式多校区与学生认证、双向身份、品类字段/广场权限、抢单数据库 CAS/唯一约束、订单资金分类、争议售后、服务橱窗及整套并发集成测试。上述功能完成前不能按完整顺手平台对外宣称已交付。
