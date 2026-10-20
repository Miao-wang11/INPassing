# 顺手（ShunShou）校园互助交易平台

> **已有分阶段核验的交易核心能力，但尚未达到完整 V1 验收**：基于 [iRun](https://github.com/Ikeu-1030/iRun) 的 MIT 许可源码进行正式二次开发。尚未完成顺手 V1.0 全部需求，不应当作已上线产品或校招简历中已经完成的全部功能。

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

**范围边界**：本项目还没有实现完整的多校区体系、服务橱窗、实际采购费用核对、纠纷退款和校园二手交易。模拟支付绝非真实资金托管；移动端只验证了相关 Vue SFC 编译语法，没有完成小程序真机验收。

## 阶段 2：模拟交易款项分项结算（已核验）

- 发布时验证金额：`预付总额 = 小费 + 配送费 + 预估商品预算`，不允许负数、三位及以上的有效小数、总额不一致。
- **劳动服务收入**单独记为流水 `type=2`（小费 + 配送费）；**代购预算报销**记为 `type=6`，不再与跑腿服务收入混合统计。
- 手动确认完成、定时自动完成以及管理员强制完成，均走同一套模拟结算函数与原有数据库唯一幂等键。
- 账单页、任务广场和后台分类显示服务报酬与采购预算，账户汇总的 `total_income` 现在只统计服务收入。
- 修复上游 `CreditScoreAspect` 在事务同步已结束后重复注册 `afterCommit` 导致“送达已完成、接口却返回失败”的问题；按真实事务上下文执行信用计算并添加单元测试。
- [金额与流水业务规则](docs/PHASE2_SETTLEMENT_LEDGER.md) · [PR #3 变更](https://github.com/Miao-wang11/INPassing/pull/3) · [已通过的真实资金交易 CI](https://github.com/Miao-wang11/INPassing/actions/runs/39392513693)

**重要限制**：`productCost` 目前是**预估预算的模拟报销**，不是实际购物支出的核验金额。尚未实现小票审查、差额退回/补款、售后冻结及真钱交易；请勿当成真实资金托管平台部署使用。

## 阶段 2：学校校区归属与任务隔离（已核验）

- 新增学校、校区基础资料表；后台左侧 **学校校区** 菜单可以新增学校与所属校区。
- 学生认证需要选择具体校区：`pending_campus_id` 只是申请，管理员在**审核管理**中核验后才写入正式 `campus_id`；任何人不能通过个人资料直接改变归属。
- 发布任务时校区取自服务端认证信息，写入不可由用户请求指定的 `task.campus_id`。广场、搜索、筛选、附近任务、详情和抢单均由后端按**批准的校区**限制；Redis 广场缓存按校区分离。
- [PR #4 代码](https://github.com/Miao-wang11/INPassing/pull/4) · [真实 Docker/MySQL 与跨校区交易测试（全部成功）](https://github.com/Miao-wang11/INPassing/actions/runs/39396275002) · [校区设计、升级与权限说明](docs/PHASE2_CAMPUS_SCOPE.md)。
- 阶段验证含：两校区双向隔离、管理员新增学校校区、提交认证与管理员批准、阻止未批准或未绑定历史账号使用任务交易，以及前两批抢单和资金回归。

**升级特别注意：如果已经用旧版本运行过，MySQL 卷内缺少新表与字段，必须先备份并执行 [增量迁移 SQL](backend/migrations/20261020_campus_scope.sql)，再启动新后端。绝不能在旧库上重跑 `backend/runningerrands.sql`（包含删库语句）。具体 PowerShell 步骤见 [运行手册](docs/RUNBOOK.md)。**

未完成：真实校区信息审核验真、历史用户/任务人工归属迁移、校区停用和跨校区特殊授权、校园地点目录以及服务时段排班。现有订单和余额仍是模拟交易，不是生产资金系统。

## 阶段 3：学生服务橱窗与定向预约（已验证的模拟交易）

- 不区分固定商家/买家账号：认证学生可以**提供服务**、发布服务报价，也可以**主动委托**其他学生服务。
- 手机端首页新增“同学服务橱窗”，个人页新增“我的服务橱窗”。可查看同校区报价、发布、编辑、上下架和预约服务，按当前校区访问。
- 预约成功在**同一数据库事务**中创建任务、模拟余额预付、订单以及指定服务者接单；被指定的任务不进入公开抢单广场，任何其他学生不能抢走。
- `service_booking(requester_id,request_key)` 唯一键防重复点击/并发重复扣款，重复请求返回原订单，服务时段使用 UTC+8。
- 已通过真实 MySQL + Redis + HTTP 测试：6 个并发重复预约只形成一笔订单、服务者离线事务回滚、订单取货/送达/确认及分账、取消定向订单后的隐藏及全额退还，以及先前的校区/抢单/资金回归。
- [PR #6 隐私与越权修复](https://github.com/Miao-wang11/INPassing/pull/6)：指定服务任务/订单详情仅交易双方可访问；非参与者不能通过猜测 ID 取得取件码、私密备注、证明照片或精确配送地址。
- [实现和逐步升级说明](docs/PHASE3_SERVICE_STOREFRONT.md) · [PR #5](https://github.com/Miao-wang11/INPassing/pull/5) · [专项 CI（成功）](https://github.com/Miao-wang11/INPassing/actions/runs/39399416104)。

**升级已有 MySQL 数据库时必须先备份并且仅执行一次** `backend/migrations/20261020_service_offer_booking.sql`，再启动新版；不能重跑具有 DROP DATABASE 的 `backend/runningerrands.sql`。详见 [运行与升级手册](docs/RUNBOOK.md)。

**验收边界**：资金仍是纯数据库模拟余额；未实现真实支付、购物小票差额核验、争议售后、服务排班/改价协商、全功能手机真机测试。正式运营前还需完善这些场景。

## 阶段 4：售后争议与模拟退款（已实现，CI 验收见 PR #7）

学生端订单详情新增“申请售后”，只在已送达待确认时受理，双方可查看售后日志。后台新增**售后纠纷**，审核员查看凭证，超级管理员可进行“驳回争议/恢复正常确认”或“模拟全额退款/终止订单”仲裁。

- 待处理争议冻结订单的手动确认、超时结算及管理员强制改状态。
- 模拟退款和任务/订单终态、服务者容量、资金流水与仲裁事件一次提交，同一售后单不可重复裁决或重复退款。
- [源码 PR #7](https://github.com/Miao-wang11/INPassing/pull/7)、[售后接口、权限、升级与测试说明](docs/PHASE4_ORDER_DISPUTES.md)。
- **升级旧库要先备份**，校区和服务橱窗两份迁移完成之后，额外执行 `backend/migrations/20261020_order_dispute.sql`，严禁直接重跑 `backend/runningerrands.sql`。

**业务边界**：本批只支持尚未结算订单的模拟全额退款，不支持真实资金、已结算后追回、部分赔偿或凭证文件安全托管。

## 阶段 5：售后运营与模拟资金流水对账

管理后台“资金对账”展示真实售后待办、退款处理数量和已记入模拟钱包的退款金额，并支持按任务核对预付/退款/服务结算/采购报销是否符合状态规则。查出异常只报告，不直接修改用户余额。

新索引文件：backend/migrations/20261020_operations_indexes.sql。已有库备份后执行一次；新库已经包括，不重复操作。

[源码 PR #8](https://github.com/Miao-wang11/INPassing/pull/8) · [对账说明](docs/PHASE5_OPERATIONS_RECONCILIATION.md) · [简历可验证事实](docs/RESUME_VERIFIED_FACTS.md) · [验收矩阵](docs/RELEASE_ACCEPTANCE_MATRIX.md)

## Windows 11 + Docker Desktop 快速运行（推荐）

1. 安装 Git、Docker Desktop（Linux 容器模式，确保 `docker compose version` 正常），可选 IntelliJ IDEA 与 JDK 21。
2. 从默认 main 分支克隆已通过校园权限与交易 CI 的版本：

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
   - 开启演示种子时：`testuser / 123456`、`testuser2 / 123456` 属于虚拟东校区（ID 10001）；`testuser3 / 123456` 属于虚拟西校区（ID 10002）。演示模拟支付密码为 `123456`

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

`mobile/` 保留了上游 uni-app 代码。当前 Docker Compose 仅部署管理后台与后端；服务橱窗 UI 位于 `mobile/pages-service/storefront/storefront.vue`。移动端可在 HBuilderX 导入 `mobile`，按目标平台修改 `manifest.json` 的 AppID/合法域名和接口基地址后单独构建。本阶段未将微信小程序真机运行标记为已验证。

## 文档与开发规范

- [阶段 0：选型与代码风险](docs/PHASE0_BASE_SELECTION.md)
- [详细运行与故障排查](docs/RUNBOOK.md)
- [源项目作者 README（遵守署名）](docs/UPSTREAM_iRun_README.md)
- 固定上游源码提交见 `docs/UPSTREAM_IRUN_COMMIT.txt`，后续更改均在本仓库保留 Git 记录。

**分支策略**：`main` 提供 CI 已核验的阶段 1 底座及阶段 2 分批交付能力；`integration/irun-bootstrap` 保留迁入历史。服务橱窗基础交易已核验；实际商品采购核对、售后争议、完整服务排班与真机联调等正式 V1 能力仍待后续开发与独立验收.
