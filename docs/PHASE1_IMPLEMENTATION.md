# 顺手阶段 1 代码迁入、修复与验收记录

日期：2026-10-20。此文档列举的是**实际提交及已执行的检查**，不把未来阶段需求当成已完成的系统功能。

## 1. 代码来源与落地

- 来源：`Ikeu-1030/iRun`，固定原始提交 `14d19341a5ae6e5a3c751328fcc273395da5bd21`。
- 经 GitHub Actions 成功导入原始 `backend/`、`admin/`、`mobile/`、`docker/`，仓库根目录保留原始 MIT `LICENSE`，历史参考保存在 `docs/UPSTREAM_iRun_README.md` 等文件。
- 不从 GPL-2.0 的 Campus Errand 或 AGPL-3.0 的福U跑腿直接复制源码。
- 迁入集成分支 `integration/irun-bootstrap`，未编造人工本地测试结果。

## 2. 已产生实际代码改动

| 文件 | 变更内容 | 为什么需要 |
| --- | --- | --- |
| `backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskServiceImpl.java` | 查询任务详情每次基于真实访问者生成并脱敏，禁用用户特定 DTO 的 taskId 共享缓存 | 防止发布者预热缓存后，其他用户读到含 isOwner=true 的私密详情 |
| `backend/runningerrands-server/src/test/java/com/ikeu/server/service/impl/TaskDetailPrivacyTest.java` | 验证发布者→陌生人、陌生人→发布者两种顺序 | 回归检查跨用户缓存及脱敏 |
| `backend/runningerrands-server/src/main/java/com/ikeu/server/mapper/TaskMapper.java` | 添加 `claimTaskIfWaiting` InnoDB 条件 UPDATE（status+expire_time） | Redis lease 过期不再成为唯一接单成功的数据库保证 |
| `backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskOrderServiceImpl.java` | 抢单时检查原子状态更新影响行数=1，否则回滚 | 防止多线程在不同事务中都确认成功 |
| `admin/src/utils/request.ts` | Axios 响应拦截器返回规范的 AxiosResponse；请求统一解包 | 修正 Vue TypeScript 对 API 响应类型的判断 |
| `admin/src/main.ts`、订单/任务/用户页面 | 修复 Element Plus locale、模板循环 index 类型 | 管理端可正常 TypeScript 构建 |
| 根目录 `Dockerfile` | 先 npm 构建最新 Vue 前端，再 Maven 编译后端，并把 dist 放入静态资源 | 避免依赖上游过期预编译静态文件 |
| `docker/nginx/nginx.conf` | 用合法的 conf.d server 块和容器内部 DNS 代理 | 原上游配置含 Windows F:/ 路径及不适用 conf.d 的全局指令 |
| `docker/docker-compose.yml`、`docker/.env.example`、根目录 ignore 文件 | 强制显式密码/JWT、宿主仅 127.0.0.1 暴露数据库/Redis | 防止使用可公开猜到的默认密码和不必要的网络暴露 |
| `DockerTestDataInitializer.java` | 显式 `SHUNSHOU_DEMO_SEED=true` 才创建弱密码样例账号 | 防止在非本地演示配置意外植入测试用户 |
| `README.md`、`docs/RUNBOOK.md` | Docker/Windows 11 安装、启动、排错、凭据、范围说明 | 支持全新 clone 后按步骤配置运行 |
| `.github/workflows/verify.yml` | Maven 编译+隐私测试、npm 构建、MySQL/Redis/后端/Nginx 启动、健康及页面检查 | 不靠“看起来能用”的口头声明 |

## 3. 测试证据（只声明已实际执行成功的部分）

- [构建通过：Maven + Vue TypeScript + Compose 语法](https://github.com/Miao-wang11/INPassing/actions/runs/39386926995)
- [容器通过：隐私回归、Docker MySQL/Redis/Spring Boot/Nginx、健康接口和管理端 HTML](https://github.com/Miao-wang11/INPassing/actions/runs/39387282568)
- [增加 MySQL 抢单 CAS 后，同样完成构建和 Docker 容器检查](https://github.com/Miao-wang11/INPassing/actions/runs/39387351035)

管理员/演示学生真实登录、业务抢单/付款/交付/退款完整 API 链路、50/100 并发测试、移动端真机、第三方消息/外部存储 **不在以上成功证据范围内**。增强版登录测试见 `.github/workflows/verify.yml` 后续实际执行状态。

## 4. 阶段 2 开发前约束

- 准确区分“任务招募”和“已成立的履约订单”；实际支付、退款、结算必须独立状态，不允许把预估采购费全部当作服务费结算。
- 学生身份与订单发布/承接角色不得混为“普通用户 vs 永久跑腿员”两个互斥账号。
- 抢单需补充 SQL 数据库并发真实验证、完整取消重发约束和幂等请求键；目前“有 CAS”不能代替全流程压测。
- 服务橱窗、邀约、校园认证、售后仲裁、价格补差、双向评价、校区管理均未因上游导入而自动完成。
- 该仓库当前是可运行底座，不是顺手正式完整 V1.0；简历成果必须基于未来独立完成及已验证功能描述。
