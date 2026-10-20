# 阶段 5：运营数据、售后状态与模拟资金流水核验

## 本阶段真实交付

管理员（角色 1 / 2）进入左侧“资金对账”，读取真实 MySQL 数据的售后待办数、已驳回数、已模拟退款数、已成功记入模拟钱包的售后退款流水总额；按任务 ID 可以单独检查预付/退款/结算金额，或列出最近任务中的前 200 条异常记录。服务端不提供“自动修正余额”的接口，不允许运维端跳过资金流水直接更改用户钱包。

```
GET /admin/operations/summary
GET /admin/operations/ledger/{taskId}
GET /admin/operations/anomalies?limit=100
```

### 钱包核对依据（仅模拟资金）

| 任务状态 | 预付/流水应该满足的条件 |
|---|---|
| 1 待接单、2 已接、3 配送中、4 待确认 | 委托者扣款 1 笔、总额等于 task.reward；没有结算或退款 |
| 5 已完成 | 委托者扣款一笔，总额与任务相等；服务费收入(type=2)+商品预算报销(type=6)=task.reward，且不得重复退款 |
| 6 已取消 | 委托者扣款一笔且模拟全额退款恰好一笔、等于task.reward；不得有已结算报酬 |

任务资金总额由已有 `tip + delivery_fee + product_cost = reward` 约束决定。未完成任务使用“预付而未结算”状态，不能把它当成财务异常。采购预算不是服务收入，不能把全部预付统计成平台营收。

实现方式：对 `transaction_record` 按 `task_id` 做 GROUP BY，分类聚合账本，然后关联 `task` 计算状态差异。查询有数量上限，后端对限额做校验，管理页仅显示异常任务。售后总额从真实 type=5 模拟退款流水获取，而不是直接汇总任务报价。性能索引 `idx_tr_task_type(task_id,type)` 专门服务于按任务查流水。

### 已知边界

“资金对账”只检查 **交易记录是否符合任务金额与状态约束**，不等于核对每笔用户历史余额链的完整审计；未核验支付机构/银行流水（本项目没有真钱网关）；不验证实际商品凭证和差额；对异常只报警/展示，不能自动补账。对状态不一致、临时恢复订单等历史数据需要财务人工确认，不可静默修改。

### 真实测试

`e2e/operations_audit_smoke.py` 在先前售后仲裁回归之后运行，检查两笔真实订单：全额退款、驳回后正常结算。然后在**CI 临时数据库**内将一笔扣款流水金额暂时加大 0.01 元；确认任务详情和异常列表显示它；最后恢复原值并检查恢复正常。该实验**绝不执行在生产数据库**。相关测试结果以该 commit 对应的 GitHub Actions 记录为准。

### 原有库升级

先确保已完成阶段 2/3/4 数据迁移、备份并暂停业务，且当前目录位于 `docker`：

```powershell
Get-Content -Raw ..\backend\migrations\20261020_operations_indexes.sql |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot runningerrands'
docker compose up -d --build
```

只需要执行一次索引创建。新 MySQL 数据卷从整套 `backend/runningerrands.sql` 初次初始化包含索引，不需要二次迁移。旧库不能重跑内含删库语句的全量初始化脚本。
