# 顺手第三批开发：学生服务橱窗与定向委托（Phase 3）

## 实际设计与代码路径

提供服务的学生在移动端“服务橱窗”登记服务标题、类型、介绍、报价、起止时间，可编辑、暂停或重新上线。任何通过学生校区认证的用户都可以发布服务与下单委托，不需要商家账号。服务列表、详情按**管理员审核通过的校区**过滤，隐藏提供者手机号等个人敏感信息。

- `GET /service-offers` 同校区上架目录；可选 `category,page,size`
- `GET /service-offers/mine` 本人的服务（包括已下架）
- `GET /service-offers/{id}` 查看上架服务
- `POST /service-offers` 发布服务；请求 `category,subType?,title,description,servicePrice,availableFrom,availableUntil`
- `PUT /service-offers/{id}` 更新本人后续报价和描述，不修改已成交历史任务
- `PUT /service-offers/{id}/status?status=0|1` 上下架
- `POST /service-offers/{id}/book` 发起预约：`requestKey,deliveryAddressId,pickupAddress,shoppingBudget?,privateNote?,payPassword`。**不接受前端指定 price/provider/campus；所有金额、服务者来自 MySQL 服务列表。**

移动端导航：首页“同学服务橱窗”、我的“我的服务橱窗”。UI 代码 `mobile/pages-service/storefront/storefront.vue`，API `mobile/api/service-offers.js`。Docker CI 编译服务端和管理员前端，同时静态解析 Uni-app Vue 页面；移动端微信/Android 真机还需要单独验证，不算 CI 真机通过。

### 预约原子事务

```
委托者已认证校区 → 检查 requestKey 历史结果
 → SELECT service_offer ... FOR UPDATE
 → 校验同校区、非自购、已上架、预约时段、服务者已认证
 → 根据当前服务报价确定服务费 + 预估商品预算
 → INSERT IGNORE service_booking (requester_id, request_key) 唯一占位
 → taskService.publishTask（校区快照 + MySQL 预付扣款）
 → 设置 task.service_offer_id、task.reserved_runner_id
 → taskOrderService.acceptOrder（真实接单资格 / 容量 / 数据库 CAS）
 → 保存 service_booking.task_id/order_id，一次提交
```

只要任何步骤抛出业务异常，前面的记录和模拟余额扣款都会一起回滚。失败时不能留下已扣费但无执行订单的服务预约。重复的同一 `requestKey` 和同一委托参数返回原订单号；相同 `requestKey` 如果内容变化直接拒绝。通过 MySQL `UNIQUE(requester_id,request_key)` 保护并发重复提交，不能仅靠前端按钮或 Redis 锁。

此处使用旧 `TaskService.publishTask` 的原有支付幂等键、旧 `TaskOrderService.acceptOrder` 的接单资格与容量 CAS，及现有 pickup/deliver/confirm 的履约/结算流程。服务者委托时自动接受，不再进入公开抢单广场；任意其他学生即使知道 taskId 也不能抢。若指定服务者自己取消了订单，原任务重新变为待接单但仍保留 `reserved_runner_id`，不可被陌生人抢单或公开展示；后续业务需要实现委托取消/重新协商的更完善流程。

### 金额与资金边界

本批服务价格作为服务费 `tip`，配送费为 0；预估商品预算写 `productCost`，两者加起来是模拟预付款 `reward`。确认完成后旧阶段 2 结算逻辑把服务劳动收入与商品预算报销分成两条流水。当前商品费用为**预估预算**，并没有真正检查采购小票，也不支持差额补款/退款。支付宝、微信支付、提现、代收托管均未接入。**严禁以真钱服务上线。**

## 数据库升级

新数据库直接使用 `backend/runningerrands.sql` 初始化，新 schema 中包含 `service_offer`、`service_booking` 和 `task.service_offer_id / reserved_runner_id` 两个字段。

已经运行过的数据库**严禁重跑** `backend/runningerrands.sql`（内含 DROP DATABASE），必须先备份并且仅执行一次 `backend/migrations/20261020_service_offer_booking.sql`：

```powershell
git pull origin main
cd docker
# 先用 DataGrip / mysqldump 对 runningerrands 做可恢复备份。
Get-Content -Raw ..\backend\migrations\20261020_service_offer_booking.sql |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot runningerrands'
docker compose up -d --build
```

如从阶段 1 或阶段 2 早期版本升级，请先按 `docs/PHASE2_CAMPUS_SCOPE.md` 执行校区迁移并处理身份归属，再执行本轮迁移。不能跳过上轮迁移。

## 测试与验收

GitHub Actions `backend-compile-and-privacy-test` 运行金额校验、安全授权等单元测试；`admin-build` 构建 Vue 管理端并静态解析新增 Uni-app Vue 页面；`docker-smoke` 使用真实 MySQL/Redis 和 HTTP 接口继续执行所有旧版校区隔离、抢单和资金测试，再运行 `e2e/service_offer_smoke.py`。

专项覆盖：同校区上架可见、异校区不可读/预约、自购拒绝、模拟预付款金额和指定接单人、6 次相同请求不重复扣款、改变请求号内容拒绝、服务者取货送达结算、服务者离线时全链路回滚、服务者上下架权限及历史请求幂等返回。

**不能因为源码提交就声明测试通过：只有对应提交的 GitHub Actions 全部成功后才算完成。** CI 不能代替真实手机端的手工体验、灰度数据迁移和安全审计。

## 下一轮待开发

服务委托售后及变更：报价调整、商品差价核验、指定服务者主动拒单、超时通知和取消协商、赔付申诉、自动退款；完善服务可预约时段/排班、上下架内容审核、举报、本人服务详情页；真实 Uni-app H5 / App / 小程序构建与真机联调。
