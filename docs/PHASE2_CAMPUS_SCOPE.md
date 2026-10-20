# 顺手阶段 2 · 校园归属与跨校区隔离

## 1. 本批实现范围

源自 iRun 的 `user.campus` 保存的是**学院字符串**，继续保留供个人资料显示，**绝不能作为校区安全凭证**。新增正规 `school`、`school_campus` 基础资料，学生账号的 `pending_campus_id`（申请）和 `campus_id`（审核通过）分别存储，任务 `task.campus_id` 为发布时快照。

用户端 `GET /campus/list` 返回启用的学校校区选项。管理员 `GET /admin/campuses/schools`、`GET /admin/campuses`、`POST /admin/campuses/schools`、`POST /admin/campuses` 管理参考信息，新增操作要求超级管理员角色。

学生提交 `POST /user/certify` 时必需提供 `campusId`，进入审核态仍不可交易；管理员通过 `PUT /admin/users/{userId}/certify?isCertify=2` 后才把申请校区绑定为正式 `user.campus_id`。普通 `PUT /user/profile` 更新的是学院文本，不能修改审核校区。

后端使用当前 JWT 用户 ID 查询已核准校区；发布时写入任务校区，不采信前端任务请求里的 `campusId`。任务大厅、关键词搜索、条件过滤、附近任务均在 SQL 增加校区约束，缓存/空标记/互斥锁也按校区分 key。详情与接单还在服务端进行校区比较（接单锁内重检），防止猜测其他校区的任务 ID 越权读取或抢单。

## 2. 首次部署

全新 MySQL 数据卷：`backend/runningerrands.sql` 已包含参考表及校区字段，正常 `docker compose up -d --build` 初始化。**不要在已有数据库手动重跑这个文件，它包含 DROP DATABASE。**

生产/非演示环境不会自动创建虚构校园。部署后请先用管理员登录，通过 POST 校区管理接口创建学校和校区，再让新注册学生在身份认证页面选择学校与校区并经管理员审核通过。

如果 `SHUNSHOU_DEMO_SEED=true`，Docker 仅创建用于演示的虚拟学校“顺手演示大学”、东/西校区（校区ID 10001、10002），并为三个测试账号赋予审核后的校区。测试账号密码较弱，绝对不能在公网启用。

## 3. **升级已有数据库（必需，一次性执行）**

若旧 MySQL Docker 数据卷已有运行中数据，先**备份**数据库；然后在仓库根目录对应 PowerShell 执行：

```powershell
cd docker
# 建议先备份 MySQL 后再执行，以下迁移不可无损自动撤销
Get-Content -Raw ..\backend\migrations\20261020_campus_scope.sql |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot runningerrands'
docker compose up -d --build
```

迁移脚本**不删除原有用户、任务、订单或余额**。旧用户的 `campus_id`、旧任务的 `campus_id` 默认空，系统采用**默认拒绝跨校区业务**，不会凭学院名称猜测校区。旧用户需先由管理员核验归属后，进行**明确、审计过的数据绑定**；旧任务必须结合发布者、实际地点、相关订单独立评估后再补填 `task.campus_id`，不能全部批量指定某个校区。未分配的旧任务不会出现在校区任务广场，避免暴露给错误校区。需要数据审计和历史任务迁移方案后才能整体切换运营。

此 SQL 是一次性增量迁移：已应用不能重复执行 ALTER TABLE；不要用 `down -v` 试图解决数据库升级。

## 4. 验证

纯逻辑单测检查认证中、跨校区、历史未绑定账号是否拒绝；真实 CI 使用两校区演示账户，测试大厅、搜索、条件过滤、附近任务、详情、接单的双向隔离，和“申请中不可使用→管理员批准才获得校区→普通个人资料无法更改校区”全流程。

注意：本批未实现复杂多校区范围任务、跨校区配送授权、校区停用编辑/删除与完整运营配置页面、校园地点字典，也没有把所有订单历史视图改为租户粒度。当前限于**校园任务招募场景的同校区隔离**，勿将其误写成完整 SaaS 多租户平台。
