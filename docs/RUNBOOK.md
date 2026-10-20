# 顺手 ShunShou — Windows 11 / Docker 部署与数据升级手册

## A. 最少依赖

- Windows 11 + Docker Desktop、Git，Docker Compose V2。
- 项目根目录包含 `Dockerfile`（统一前后端构建）；`docker/docker-compose.yml`、`docker/nginx/nginx.conf`。
- 首次运行需要访问 Docker Hub、Maven Central 和 npm Registry；没有网络时不能完成首次构建。
- 宿主机端口：Nginx 80、后端 8080、MySQL 3308、Redis 6381。修改 `docker/.env` 可避让。
- 本地 IDEA 开发：JDK 21、Maven 3.9+、Node.js 22+、MySQL 8、Redis 7；优先 Docker 统一启动免去 Windows Redis 安装冲突。

## B. 从零克隆后运行

```powershell
git clone https://github.com/Miao-wang11/INPassing.git
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

## B2. 已有 MySQL 数据卷：升级校区数据结构（不可跳过）

**首次克隆全新项目可直接进入 C；本节仅适用于以前已运行过 iRun/顺手，并需要保留原用户、订单、模拟余额的情况。**

1. 先停止旧后端写入业务，使用 DataGrip 或 `mysqldump` 备份 `runningerrands` 数据库；并确认备份能够读取。
2. 在项目根目录 `git pull origin main` 后，进入 `docker` 目录，**保留数据库卷**，不要执行 `docker compose down -v`。
3. 用旧数据库容器运行一次且只运行一次增量 SQL：

```powershell
cd docker
Get-Content -Raw ..\backend\migrations\20261020_campus_scope.sql |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot runningerrands'
docker compose up -d --build
docker compose ps
```

如果尚未启动 MySQL 容器，可先 `docker compose up -d mysql`，等健康检查通过再执行 SQL。SQL 仅新增参考学校、校区表以及 user/task 校区字段和索引，**不会清空原表**；已执行过不能重复运行 `ALTER TABLE`。

**数据治理边界**：旧账号和旧任务的校区字段默认是 `NULL`，系统不会因为学院文本相同就强行合并校区身份。未核验历史账号无法进入新校区广场及接单，未归属历史任务也不向任何校区广场展示。请按 [校园归属迁移说明](PHASE2_CAMPUS_SCOPE.md) 手动核实身份和历史单据，尤其有未完成交易的任务，不得简单批量赋值。

## B2.1. 阶段 3 服务橱窗数据库迁移（已有库必需）

此章节只针对从已运行的阶段 2 版本升级，不适用于全新首次初始化的数据库。升级前确保已通过前一节的校区身份迁移。

1. 在 DataGrip / `mysqldump` **备份并验证能恢复** `runningerrands`，暂停旧版业务写入。
2. 已使用 `git pull origin main` 获取新版代码且位于 `docker` 目录时，执行**一次**：

```powershell
Get-Content -Raw ..\backend\migrations\20261020_service_offer_booking.sql |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot runningerrands'
docker compose up -d --build
docker compose ps
```

会新增 `service_offer` 服务表、`service_booking` 幂等预约表以及 `task.service_offer_id`、`task.reserved_runner_id`。已存在的任务保持普通公开任务属性，不清空旧数据；新预约才带保留服务者限制。不要重复运行 `ALTER TABLE` 或直接运行上游删库建表脚本。

首次全新安装没有历史数据时，Docker 自动执行 `backend/runningerrands.sql` 初始化（包含本批新表），**不要**再另外运行增量脚本。

## B2.2. 阶段 4 售后争议表迁移（已有数据库升级）

执行之前必须确认已完成阶段 2 的学校校区和阶段 3 的服务橱窗迁移；此脚本**只执行一次**，首次新库不需要单独执行。请先暂停业务写入、备份 `runningerrands` 且验证备份可恢复。

```powershell
# 仓库根目录：git pull origin main
cd docker
Get-Content -Raw ..\backend\migrations\20261020_order_dispute.sql |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot runningerrands'
docker compose up -d --build
docker compose ps
```

升级只新增 `order_dispute` 和 `dispute_event`，不清除历史订单、资金流水、旧用户。管理员进入“售后纠纷”，只有超级管理员能进行真实仲裁按钮操作；对于以前已结算或已取消的订单，必须按历史账务单独核查，不能在这套未结算模拟退款流程里直接追回钱。

## B3. 首次运营配置

非演示环境 `SHUNSHOU_DEMO_SEED=false` 不会生成虚构学校。请以超级管理员登录后台，在左侧 **学校校区** 页面先添加学校，再添加对应校区。随后学生在认证页面选择学校/校区，审核人员在 **审核管理** 页面确认申请校区后通过；获批校区才可发布或接取本校区任务。已有学号/院系文本不能直接证明校区归属。

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
- 东校区两位可同时发布与接单的学生：`testuser / 123456`、`testuser2 / 123456`。
- 西校区学生：`testuser3 / 123456`（只能查看、接取西校区任务）。
- 上游历史跑腿员档案仍保留离线/封禁等限制，不能靠换角色绕过。
- 模拟余额和支付密码：演示数据中支付密码 `123456`

上游仅模拟账户余额和数据库流水，绝非真钱充值；请不要使用个人真实信息或真实资金。已经支持一个经过学生认证的账号同时发接单，不需要独立商家账号。

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

## E2. 服务橱窗手机端联调

Docker 部署的是后端 API + Vue 管理后台，并没有自动编译部署 Uni-app 用户端。用 HBuilderX 打开仓库 `mobile` 目录，配置应用 AppID、合规请求域名或本机调试接口基址，再运行 H5 / App / 小程序平台。

登录已审核并绑定校区的学生，点击首页“同学服务橱窗”，即可查看校区服务；用另一同校区账号发布有效时间段与报酬，第一账号选择本人地址并输入模拟支付密码完成委托，再在订单列表中执行取货、送达、确认。

服务者需处于有效认证、未封禁、接单容量内且在线；不满足时整笔模拟预约回滚，不会只扣费不下单。服务价格由后端服务表计算，前端只输入可选的预估购物预算。**UI 静态语法检查和 HTTP 真实链路 CI 已通过，但手机端真机流转尚未做端到端自动测试。**

[详细业务边界与接口](PHASE3_SERVICE_STOREFRONT.md)

## F. 验收边界与开发任务

构建通过 != API 可用 != 前后端交易闭环通过。已通过真实 CI：学生双向身份、校园任务校区隔离、数据库抢单 CAS 和容量限制、模拟资金分项与退款。现已具备服务橱窗发布、上下架与定向预约的真实模拟交易链路。仍需要：历史账号和任务数据迁移审核、校区运营、正式品类模板、实际商品费用核对、售后争议、服务时段排班、复杂并发验收与真机测试。上述功能完成前不能按完整顺手平台对外宣称已交付。
