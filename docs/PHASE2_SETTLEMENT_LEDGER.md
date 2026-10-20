# 顺手阶段 2（第二批）：模拟资金分项结算

完成时间：以 GitHub 提交与 CI 记录为准。目标是消除 iRun 将“商品预算”统计为服务收入的错误。

## 一、当前执行的业务口径

旧 task.reward 字段代表发单方预付的**总金额**。对新的任务，必须满足：

- 预付总额 = 小费 + 配送费 + 预估采购预算
- 服务收入 = 小费 + 配送费（transaction_record.type=2）
- 预算报销 = 预估采购预算（transaction_record.type=6），是采购预付费用的转付，不计入服务劳动收入
- 取消未接单任务全额退款（原 type=5，仍保留幂等键）
- 订单手动确认、定时自动完成都调用统一 settleTask 方法
- 仍使用模拟余额、原交易流水及 payment_idempotent 的唯一键；无真实第三方支付接口
- 这批未上线采购小票验真、实际成本调整、超额补差、差价返还、提现资金通道、纠纷冻结

**注意**：type=6 现在是“基于预算的模拟报销”，**不是“已经核验真实购物花费”**。真正采购业务投产前必须补充采购凭证、差价核算、用户确认与售后争议，不能把本阶段当成真钱可用托管系统。

## 二、交易一致性

`TaskFeeBreakdown` 校验金额非负、小数不超过两位、总额一致且不超过 DECIMAL(10,2)。非法数据直接拒绝结算并回滚整笔订单的确认事务。已付历史数据不进行静默修改。

`PaymentServiceImpl.settleTask` 使用原始 `INCOME:task:{taskId}:runner:{runnerId}` 唯一键防止重复结算；以 `SELECT FOR UPDATE` 锁定余额；在同一个数据库事务中更新账户余额、插入服务收入和预算报销两条分类流水，不允许部分完成。

例如用户发布代购物品：小费 2.50、配送费 1.00、商品预算 6.40，发单方余额先减少 9.90；完成后接单者余额增加 9.90，但业绩/服务收入只有 3.50，采购预算 6.40 作为另一类型明细显示。服务报酬为 0 时不生成 0 金额的服务收入流水。

后端 `sumIncomeByUserId` 仍仅统计 type=2；钱包的 `total_income` 改为只统计劳动服务收入，避免把充值、退款、采购报销合计成收入。新增 `shopping_reimbursement`、`total_recharge`、`total_refund` 统计键。账单端与管理员端新增 type=6 标签。

## 三、升级与兼容

- 目前只复用了 `transaction_record.type` 整数字段，不要求新增表或迁移脚本；`backend/runningerrands.sql` 只修改初始化注释，**不要在已有数据库重新执行该文件**，因为上游脚本会 DROP DATABASE。
- 已存在完成订单的历史流水类型（原整笔 type=2）不会自动重分类：由于无法仅凭一条历史收入记录证明真实采购成本，本轮拒绝编造历史分账记录。生产升级前应另做历史数据校准策略。
- 重用原 INCOME 幂等键与 payForTask / refundForTask 的语义，保证接口升级不将已经结算的历史订单重新打款。
- 前端复制页面 `mobile/pages-user/bills` 与 `mobile/pages/bills` 均更新 type=6 显示。

## 四、实际验收

- 纯金额单测：正常分项、零服务费、零采购费、非法负数、第三位小数、总额不符、全零金额。
- GitHub Actions 执行 Maven、npm、Docker、登录和既有双向身份/抢单回归。
- 新增 `e2e/fee_settlement_smoke.py`：用真实 Docker MySQL + REST 进行发布 → 模拟预付 → 接单 → 上传履约 URL → 双方确认 → 余额和流水对账、重复确认、取消未接单任务退款、无效金额拒绝。

**已完成真实核验**：[GitHub Actions run 39392513693](https://github.com/Miao-wang11/INPassing/actions/runs/39392513693) 全部通过，包含 Maven 单元测试、Vue 编译、Docker MySQL/Redis/Spring Boot/Nginx、学生登录与双向抢单回归，以及模拟支付、服务费/商品预算分账、重复确认拒绝、取消退款、非法金额拒绝。

另通过真实 E2E 检查发现上游 `CreditScoreAspect` 在事务提交后注册事务同步导致 `IllegalStateException: Transaction synchronization is not active`。现已修复：同步仍有效时注册 `afterCommit`，已无同步上下文时进行独立事务信用处理；信用异常只记录错误，不将已提交送达错误显示给客户端。对应回归单测见 `CreditScoreAspectTest`。未完成的持久化信用异常重试机制已标记 TODO，仍属后续任务。
