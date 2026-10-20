# 顺手（ShunShou）校园互助交易平台

> **阶段 1 基础底座 + 阶段 2 第一批学生双向身份功能**：基于 [iRun](https://github.com/Ikeu-1030/iRun) 的 MIT 许可源码进行正式二次开发。尚未完成顺手 V1.0 全部需求，不应当作已上线产品或校招简历中已经完成的全部功能。

## 当前仓库状态

- 原始底座源码已迁入：Spring Boot 3 / Java 21 后端、Vue 3 管理端、uni-app 移动端、MySQL SQL、Redis 与 Docker 配置。
- GitHub Actions 已证明 Maven 打包、隐私回归测试、管理后台 npm 构建、完整 Docker 四容器启动、后端健康接口、Nginx 管理页面，以及管理员/演示学生登录检查通过。其他顺手功能仍需分阶段验收。
- 任务详情权限相关的跨用户缓存风险已修补，回归测试与 Docker 部署改造已经经过 CI 验证。
- 系统目前仍带有上游 iRun 的业务规则及外观，**不是顺手最终版本**。
- 支付是本地数据库记录和模拟余额，不接真实微信或支付宝，也不可当真实货币系统使用。

## 阶段 2：已实现并核验的学生双向交易

- **同一个 user.id** 可发布需求、也可承接他人需求。首次抢单的已认证学生自动创建接单信用/容量档案，**不是另一套商家登录账号**。
- 原有封禁、低信用、离线、未认证以及自接单检查继续有效；已存在的档案不会被自动重置。
- 增加 MySQL 条件更新，保证同一学生并发抢不同任务时，进行中订单数不会突破接单容量。
- uni-app 任务广场允许已认证学生直接点击接单，原独立配送员申请页改为接单资格说明。
- CI **实际执行成功**：Maven/单元测试、Vue 后台构建、移动端相关 Vue 单文件组件语法检查、Docker 四服务启动、真实用户登录、双向交换交易角色、20 次并发同任务抢单（恰好 1 个成功）、两任务同时抢单不得超过容量，以及既有封禁保留。
- [阶段 2 详细业务设计](docs/PHASE2_DOMAIN_DESIGN.md) · [阶段 2 源码合并记录 PR #2](https://github.com/Miao-wang11/INPassing/pull/2) · [实际成功的业务与并发验证](https://github.com/Miao-wang11/INPassing/actions/runs/39390130088)

**范围边界**：本项目还没有实现完整的多校区体系、服务橱窗、统一资金模型、纠纷退款和校园二手交易。模拟支付绝非真实资金托管；移动端只验证了相关 Vue SFC 编译语法，没有完成小程序真机验收。

## Windows 11 + Docker Desktop 快速运行（推荐）

1. 安装 Git、Docker Desktop（Linux 容器模式，确保 `docker compose version` 正常），可选 IntelliJ IDEA 与 JDK 21。
2. 从默认 main 分支克隆当前已验证的基础系统及阶段 2 第一批功能：

```powershell
git clone https://github.com/Miao-wang11/INPassing.git
cd INPassing\docker
Copy-Item .env.example .env
notepad .env
```

3. 在 `docker/.env` 里**自行修改** `MYSQL_ROOT_PASSWORD`、`JWT_ADMIN_SECRET`、`JWT_USER_SECRET`、`ADMIN_PASSWORD`，不可沿用 `replace-with` 示例值。
4. 可选本地演示账号：设置 `SHUNSHOU_DEMO_SEED=true`，只供本机测试，绝不可公开部署；生产或联网环境为 `false`。
5. 启动（从 `docker` 目录执行）：

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

6. 访问：
   - 管理后台：<http://localhost/api/>（如果 80 被占用，改 `NGINX_PORT=8088`，则访问 <http://localhost:8088/api/>）
   - 后端健康检查：<http://localhost:8080/api/actuator/health>
   - 本机 MySQL：`127.0.0.1:3308`；Redis：`127.0.0.1:6381`
   - 管理员：`ADMIN_USERNAME`（默认 admin）+ 你设置的 `ADMIN_PASSWORD`
   - 开启演示种子时：`testuser / 123456` 与 `testuser2 / 123456`；模拟支付密码上游也是 `123456`

请严格区分：**以上为已提交的启动说明，是否成功必须以你本地或 CI 的真实测试为准。**

## 本地 Java 后端编译（非 Docker）

```powershell
cd backend
mvn clean package -DskipTests
```

后端连接实际 MySQL/Redis 需要依据 `backend/runningerrands-server/src/main/resources/application-template.yml` 建立本地 dev 配置（原始 `application.yml` 默认激活 dev）。不得把本地数据库密码提交到仓库。

管理端：

```powershell
cd admin
npm ci
npm run build
npm run dev
```

## Mobile 用户端

`mobile/` 保留了上游 uni-app 代码。当前 Docker Compose 仅部署管理后台与后端；移动端可在 HBuilderX 导入 `mobile`，按目标平台修改 `manifest.json` 的 AppID/合法域名和接口基地址后单独构建。本阶段未将微信小程序真机运行标记为已验证。

## 文档与开发规范

- [阶段 0：选型与代码风险](docs/PHASE0_BASE_SELECTION.md)
- [详细运行与故障排查](docs/RUNBOOK.md)
- [源项目作者 README（遵守署名）](docs/UPSTREAM_iRun_README.md)
- 固定上游源码提交见 `docs/UPSTREAM_IRUN_COMMIT.txt`，后续更改均在本仓库保留 Git 记录。

**分支策略**：`main` 提供已通过基础构建、容器启动与登录检查的阶段 1 底座；`integration/irun-bootstrap` 保留迁入历史。校园双向身份、资金结算、服务橱窗和争议处理等正式 V1 能力将在后续阶段独立验收。
