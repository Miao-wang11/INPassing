# 顺手阶段 4：订单售后、结算冻结与模拟全额退款

## 实际交付范围

新增 `order_dispute` 售后单 + `dispute_event` 不可覆盖操作流水，两者均有真实 MySQL 表及增量迁移。学生从已送达待确认的订单详情点击“申请售后 / 查看争议”，可以填写理由、上传 HTTPS 凭证地址，查询自己的售后单与审计历史。管理端左侧“售后纠纷”可筛选所有售后单，查看凭证、过程记录，**仅超级管理员**能做裁定并留下依据。

**这批只处理“待确认但尚未结算”的模拟资金争议**。尚未取件、配送中、已完成/已取消的订单不能绕过原订单状态机发起全额退款；不能把本批当作真实支付、已结算后追回款项或复杂赔偿系统。售后申请一次后不能反复覆写证据或重复发起新案。

### 状态与裁定

| 售后状态 | 条件与效果 |
|---|---|
| 1 OPEN 待仲裁 | 仅订单两方可提交；不能手动确认、自动确认或管理员强制改状态 |
| 2 REJECT 驳回 | 管理员写明理由，原订单仍保持已送达待确认；委托者可正常确认或按原超时规则自动结算 |
| 3 REFUND 全额模拟退款 | 管理员写明理由；订单待确认 → 已取消，任务待确认 → 已取消；一次性返还全部预付模拟余额给委托者，释放接单人容量 |

采用相同 `RedisConstant.ORDER_LOCK_KEY + taskId` 锁保护申请、确认、自动完成和仲裁；在 MySQL 事务内使用 `SELECT ... FOR UPDATE` 锁定当前状态以及订单状态条件更新，同时防重入裁定。退款复用已经存在的 `PaymentService.refundForTask()`、`payment_idempotent` 唯一键和不可变 `transaction_record` 流水。管理员连续提交相同结果第二次会失败，不再记账。

现有订单状态机禁止 WAIT_CONFIRM → CANCELLED；本方案**只在售后管理员模拟全额退款时**使用显式状态条件更新及审计日志作为唯一特例；普通用户/管理员旧状态修改接口不能跳过售后流程。首次受理时必须通过服务端校验 `task.publisher_id` 或 `task_order.runner_id` 是申请者；服务端不采信请求方自行提供交易角色、金额、余额、客服人员身份。

### 主要 API

```
POST /disputes/orders/{orderId}    {"reason":"...","evidenceUrls":["https://..."]}
GET  /disputes/mine
GET  /disputes/{id}
GET  /disputes/{id}/events
GET  /admin/disputes?status=1&page=1&size=10
GET  /admin/disputes/{id}
GET  /admin/disputes/{id}/events
POST /admin/disputes/{id}/decision {"decision":"REJECT"|"REFUND","note":"必须填写仲裁依据"}
```

所有学生端接口都受用户 JWT 保护；售后详情与日志只开放给交易双方。管理员列表/详情仅角色 1/2；管理员仲裁仅角色 1；仲裁另写已有管理员操作日志和新增的 dispute_event 双层审计。

### 数据库升级：已有环境与新环境

**已有库不要执行 `backend/runningerrands.sql`，因为该文件包含 DROP DATABASE。**

先备份并确认可恢复，在确保阶段 2 的校区迁移与阶段 3 的服务预约迁移已经成功应用后，一次性执行：

```powershell
cd docker
Get-Content -Raw ..\backend\migrations\20261020_order_dispute.sql |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot runningerrands'
docker compose up -d --build
docker compose ps
```

全新 Docker 数据卷正常初始化时，完整 `backend/runningerrands.sql` 已包含售后表，**不要额外执行迁移**。迁移脚本可单独用于旧库，但不是支持无限次重复运行的 schema 版本框架。

### 验证

GitHub Actions 三项检查：Maven 编译+单元测试、Vue 管理端+Uni-app SFC 静态语法检查、真实 Docker MySQL+Redis+Nginx+Spring Boot 启动和 HTTP 端到端。

`e2e/order_dispute_smoke.py` 实测用户/服务者申请、非参与者越权禁止、重复申请去重、争议冻结两类确认入口、真实 `REFUND` 订单/任务/资金/容量/事件完整变化且不重复退款、`REJECT` 后恢复旧完成结算、管理员后台过滤查询。详细运行步骤仍以 `docs/RUNBOOK.md` 为准。

### 局限与后续研发

本阶段不做已结算订单资金追回、部分退款、用户双方协商改价、购买实物小票核验、逾期客服自动介入、证据文件托管（目前仅存已提供的 HTTPS URL）、数据库版本迁移管理、支付机构退款网关或司法争议。以上若不能满足，不能以真实人民币面向学生开放交易。
