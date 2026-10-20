# 顺手（ShunShou）阶段 0：开源底座审计与选型

> 审查日期：2026-10-20。结论：**暂定 iRun 作为主底座，须先完成隔离环境启动、安全修复和回归测试；尚未认定其完整可运行。**
>
> 本报告基于 GitHub 仓库树、许可证、真实 Java/POM/SQL/配置文件和用户提供的两份需求文档。未完成 Maven 构建、MySQL/Redis 启动、前端联调和端到端测试，不可将“源码存在”写作“功能已通过验证”。

## 1. 四个主候选

| 项目 | 后端/前端 | 可核实的代码与数据库 | 许可证（以 LICENSE 为准） | 判断 |
| --- | --- | --- | --- | --- |
| [iRun](https://github.com/Ikeu-1030/iRun) | Spring Boot 3.2.0 / Java 21 / MyBatis-Plus 3.5.5 / Redis、Redisson、STOMP WebSocket；admin 为 Vue 3，mobile 为 uni-app | backend 三个 Maven 模块；15 张 SQL 建表；Task/TaskOrder/Payment/Chat/Review/Notification/Admin 实现类；Docker Compose | **MIT** | **首选条件性底座**，业务覆盖最接近目标，但存在高优先级安全和事务核查项 |
| [CampusGo](https://github.com/huangcancan-xbc/CampusGo) | Spring Boot 3.2.5 / Java 21 / MyBatis-Plus 3.5.7 / Vue 3 | backend、frontend、demo_database.sql；4 张表（user/task/task_type/campus_location） | **MIT** | 可学习轻量结构，不宜直接承载完整交易闭环 |
| [福U跑腿](https://github.com/Karrecy/fuu-run) | Spring Boot 2.7.6 / Java 8 / Sa-Token / MyBatis-Plus / Redis / Redisson / Netty / uni-app / 管理端 | 多模块后端、22 张表、订单/支付/申诉/财务/多校区后台；真实微信支付下单及回调代码 | **AGPL-3.0** | 业务流程参考，不直接拷贝进入拟采用 MIT 底座的项目，除非事先解决许可证兼容与履约 |
| [Campus Errand](https://github.com/liulasty/campus-errand) | Spring Boot 2.7.3 / Java 8 目标 / MyBatis-Plus / Spring Security / Redis / RabbitMQ / Vue 2 | 单体后端、Vue 2 前端、15 张业务表、任务审批/接单申请/信用/通知/后台和测试文件 | **GPL-2.0** | 可参考产品与业务流程；README 的 MIT 徽章和 LICENSE 实际 GPL-2.0 不一致，禁止当 MIT 代码复制 |

四库并未在本轮完成可执行构建验证；表数来自各自主 SQL 文件的 CREATE TABLE 语句，不等于模型设计质量或已测试覆盖范围。

## 2. 源码核验：存在不等于可用

### iRun：已找到的真实实现

- [抢单实现 TaskOrderServiceImpl](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskOrderServiceImpl.java#L142-L283)：认证、状态检查、自接单限制、Redisson 锁、二次检查、写入 task_order、更新 task；也有取消、取件、送达、完成、自动完成等方法。
- [任务发布 TaskServiceImpl](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskServiceImpl.java#L189-L308)：发布时校验支付密码、创建 task、调用模拟余额扣款、事务提交后清理相关缓存。
- [支付 PaymentServiceImpl](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/PaymentServiceImpl.java#L50-L164)：payment_idempotent 唯一键、SELECT FOR UPDATE、模拟用户余额、交易流水、退款、给跑腿员结算。**非真实第三方支付，适合本项目模拟交易**。
- [聊天 ChatServiceImpl](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/ChatServiceImpl.java)、[ChatController](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/controller/user/ChatController.java)、[WebSocketConfiguration](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/config/WebSocketConfiguration.java)：STOMP 消息、持久化、历史分页、已读、撤回、JWT 握手与通道拦截器。
- [后台 Controller](https://github.com/Ikeu-1030/iRun/tree/main/backend/runningerrands-server/src/main/java/com/ikeu/server/controller/admin)：认证、用户、订单、任务、交易流水、配置、统计、操作日志等有实际路由类；仍须做真实交互验证。
- [Docker Compose](https://github.com/Ikeu-1030/iRun/blob/main/docker/docker-compose.yml)、[SQL](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands.sql)、[Docker 部署说明](https://github.com/Ikeu-1030/iRun/blob/main/docker/README.md)：提供数据库、缓存、后端与 Nginx 的部署路径。

### iRun 必修风险（不能照搬）

1. **高优先级：跨用户缓存敏感详情风险。** [TaskServiceImpl.getTaskDetail](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskServiceImpl.java#L461-L485) 用 taskId 作缓存键，加载器根据 currentUserId 构建详情、设置 isOwner；掩码逻辑又信任缓存中 isOwner，发布者预热后可能向其他访问者暴露其详情。应只缓存公共 DTO，每次请求根据真实调用者授权并查询必要隐私字段；补充跨用户回归测试。
2. **高优先级：抢单事务与 Redis 锁边界。** [acceptOrder](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskOrderServiceImpl.java#L190-L283) 在 @Transactional 方法内 unlock，需确认事务代理的提交顺序；仅依赖互斥锁和普通 updateById 不足以作为数据库最终唯一性保障。改为事务内条件更新/CAS + 唯一约束，并做 50/100 并发测试。
3. **高优先级：金额语义混淆。** 发布时 [reward=tip+deliveryFee+productCost](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskServiceImpl.java#L203-L252)，完成时 [payToRunner(task.getReward())](https://github.com/Ikeu-1030/iRun/blob/main/backend/runningerrands-server/src/main/java/com/ikeu/server/service/impl/TaskOrderServiceImpl.java#L538-L555)。顺手必须分开服务报酬、代购预付/实付、补差/退款、冻结/结算，不能把总款直接标为跑腿服务收入。
4. **高优先级：身份结构不同。** iRun 有独立 runner_profile 认证和上线/下线要求。顺手采用一个学生账号，订单上区分发布者、接单者，不能要求“发布者”和“接单者”作为互斥账号类型。
5. **中优先级：全量缓存失效和 Redis KEYS。** 多处 cache.clear() 和 stringRedisTemplate.keys(pattern)；需要评估生产数据量，并改为细粒度失效/SCAN 或版本化 key。
6. **中优先级：默认配置与测试账号。** Docker 文档包含默认管理员/测试账号，Compose 给 JWT/数据库提供开发默认值；正式配置要用环境变量强制覆盖，避免测试数据和密码进入正式环境。
7. **中优先级：状态并行设计。** 订单状态机文件存在，但取消、赔付、部分退款、补差、争议冻结仍需新增独立状态，不可把支付状态压缩到任务状态。
8. 现有测试文件主要针对信用、安全等个别单元行为；**不能据此推断抢单/支付/聊天全流程已回归通过**。

### 其他三个项目：实际可借鉴的内容

- CampusGo 的 [TaskServiceImpl.acceptTask](https://github.com/huangcancan-xbc/CampusGo/blob/main/backend/src/main/java/com/campus/errand/service/impl/TaskServiceImpl.java#L166-L185) 采用先查、后普通 updateById，未见条件状态更新；并发抢单不能直接复用。其 [demo_database.sql](https://github.com/huangcancan-xbc/CampusGo/blob/main/demo_database.sql) 的余额字段不代表真实支付交易实现。
- 福U的 [PayServiceImpl](https://github.com/Karrecy/fuu-run/blob/master/fuu-run/fuu-payment/src/main/java/com/karrecy/payment/service/impl/PayServiceImpl.java) 明确调用微信支付 V3 下单、签名验证回调和退款 SDK；**实现代码存在，但未经真实支付环境验证**。顺手只参考支付状态/回调设计思路，不引入真实微信支付。
- 福U的 OrderChatServiceImpl 仅继承 MyBatis-Plus ServiceImpl、未写业务方法；其 [OrderChatController](https://github.com/Karrecy/fuu-run/blob/master/fuu-run/fuu-admin/src/main/java/com/karrecy/admin/controller/order/OrderChatController.java) 有聊天查询。另有 Netty/WebSocket 类，不能把这个空服务类误认作完整聊天逻辑。
- Campus Errand 采用任务申请记录与审核选择思路；[TaskAcceptRecordsServiceImpl](https://github.com/liulasty/campus-errand/blob/master/src/main/java/com/lz/service/impl/TaskAcceptRecordsServiceImpl.java) 的 create 创建 PENDING 接单记录，与顺手先到先得的一单一人抢单不同，**需要另设计**。README 自称“不碰资金”；不符合顺手模拟支付闭环。

## 3. 功能差距与取舍

| 功能 | iRun 已有基础 | 顺手处理意见 |
| --- | --- | --- |
| 登录/JWT/后台权限 | 有 | 保留框架、改校园认证与分权、审安全 |
| 同一账号双向发接单 | 发单/跑腿员角色分层 | **重构** runner_profile 规则，不区分固定买家/商家 |
| 校园/校区/地点与学校认证 | 简化业务结构 | **新增** school/campus/location/verification |
| 分类发布、任务广场、搜索 | 有任务表和大厅查询 | 扩展专属字段、草稿、公开/私有 DTO、校区筛选 |
| 抢单/履约/订单状态 | 有完整方法雏形 | 事务内条件更新、唯一性、幂等、分阶段凭证与争议阻断 |
| 模拟余额、流水 | 有 | 拆分费用、模拟支付单、退款/补差/结算流水，禁止真金钱概念混淆 |
| 聊天/通知 | STOMP 与持久化已有 | 添加订单绑定会话、黑名单、授权和离线消息回放 |
| 双向评价/信用 | 有基础 | 区分评价主体，规则可解释，处理评价申诉 |
| 争议、部分退款、管理员裁决 | 明显不足 | **新增** aftersale/dispute/admin_decision |
| 服务橱窗/主动邀约 | 没有可确认的完整实现 | **新增** service_offer/invitation，并复用正式订单流 |
| 管理后台 | 现有管理路由及 Vue 项目 | 保留可用页，新增校区、认证、售后、财务核对、报表 |
| 二手闲置 | 非首版 | P2 独立商品与交易线，不混用跑腿订单 |

优先级根据《ShunShou_Requirements(1).docx》：P0 交易闭环、隐私、售后、后台；P1 服务卡/主动邀约；P2 二手交易。因“服务橱窗”是顺手产品识别度高的差异化能力，建议在 P0 稳定后作为**第一批 P1** 实现。

旧《基于 Spring Boot 的“顺手”项目设计(1).docx》出现 JPA、MyBatis 两套口径；示例明文密码比对、订单状态先读后写、不从鉴权上下文取接单人 ID。它属于早期构思文档，不作为可复用工程代码。

## 4. 架构建议

**模块化单体**，先保留 iRun Maven 三模块组织和统一后端进程；在 server 下按业务域分包，完成后才考虑再拆 Maven 子模块：

- common：通用异常、Result、ID、数据权限、幂等、审计
- auth-user-campus：登录、学生认证、校区/地点、受限状态
- task-market：分类字段、发布、草稿、广场、搜索、隐私视图
- trade-order：接单、状态机、履约凭证、超时处理
- payment-ledger：模拟支付、支付单、记账、退款、补差、冻结、结算
- im-notify：订单会话、聊天持久化、STOMP、系统通知
- aftersale-credit：纠纷、举证、仲裁、双向评价、信用与举报
- service-offer：服务橱窗、邀约、双方确认后建订单
- admin：审核、学校管理、工单、财务核对、权限/日志/报表
- admin（Vue 3）和 mobile（uni-app，优先 H5 联调，微信小程序上线视账号资质）

推荐实体主线：user、school、campus、place、student_verification、task、task_spec、task_private_info、order、order_event、delivery_proof、payment_order、ledger_entry、refund_order、settlement、dispute、dispute_evidence、conversation、message、review、credit_event、service_offer、service_invitation、admin_action_log。

**不能直接把原 task 与 task_order 当作两份相互独立的成交记录。** 推荐任务为发布/招募载体，订单为一次履约交易：任务状态与订单履约状态分别管理；支付、退款和结算再分别管理。取消已经接单的任务后默认关闭该招募，重新发布创建新 taskId（保留 original_task_id 追溯），这样可以合理为 order.task_id 建立唯一约束。

抢单权威保障：在数据库事务中通过条件 UPDATE 抢占 task（受 status/expire_time/school/self 等约束）、插入 order，检查更新行数=1、唯一约束，提交后通知与更新缓存；Redis 只能作辅助限流/热点保护。支付采用 request_no/order_no 唯一键、明确支付/退款/结算状态、金额不变量及不可变流水。

## 5. 运行条件与可复现核验路线

**当前未完成运行验证，以下仅是上游提供的启动路线，不是本仓库已经成功运行的保证。**

iRun 官方 docker 路线：安装 Docker 24+ 和 Compose V2 → 克隆官方 iRun → cd docker → 复制 .env.example 到 .env → 更换开发默认密码/JWT secret → docker compose up -d --build → docker compose ps 与 logs → GET http://localhost:8080/api/actuator/health → 测试登录；MySQL 外部端口默认 3308、Redis 6381、后端 8080、Nginx 80。详情：[官方部署说明](https://github.com/Ikeu-1030/iRun/blob/main/docker/README.md)。

顺手最终交付前必须实测：
1. 从**INPassing** 全新 clone，不依赖开发者电脑历史环境。
2. 环境变量样例与 .env.gitignore、数据库建表/迁移与初始化种子、密码/JWT/OSS 外置。
3. 后端 mvn clean verify、前端 npm ci && npm run build、Docker Compose 构建与健康检查。
4. 端到端：注册/校园认证/发布/模拟付款/广场/50+ 同抢/履约/确认/结算/双评/售后退款/消息。
5. 反向权限：自己抢单、未认证接单、陌生人查询取件码、修改他人订单、越权聊天、重复回调、同一交易双退款、异常金额。
6. 留存 CI 日志/测试报告、截图与数据库账实一致性证据。**实际达标后**方可标为“可运行版本”并写入简历。

### 当前环境阻碍

本轮容器具备 Java 21 和 git，但没有可用的 Maven、Docker，且终端无法解析 github.com；通过已连接的 GitHub 接口能够查看和提交文本文件，但无法在当前容器完整克隆 iRun 并搭建 MySQL/Redis/前端集群。因此**阶段 0 为源码审查级，不是启动/压测验收级**。

## 6. 交付阶段与门禁

- **阶段 0（本次）**：读取两份上传文档、审仓库/POM/源码/SQL/许可证、形成底座比较和风险清单；输出此报告。
- **阶段 1**：合规引入 iRun（保留 MIT 原许可证和版权），修复跨用户缓存泄露、并发抢单、测试默认配置；在可用环境启动原系统，跑通实际端到端基础链路。未通过不能声称底座稳定。
- **阶段 2**：定稿需求与设计，冻结状态机/资金模型/数据迁移/公开隐私字段策略。
- **阶段 3**：学生双向身份、校园/校区、发布与广场、抢单、履约；要求并发抢单及越权集成测试通过。
- **阶段 4**：模拟支付拆账、退款/补差、聊天消息权限、离线历史及通知；要求幂等和账实一致测试通过。
- **阶段 5**：售后、管理后台、双评信用、服务橱窗 P1；要求真实前端操作闭环通过。
- **阶段 6**：自动测试、联调、Docker 部署与全新 clone 验收；出 Windows/IDEA 与 Docker 两版运行说明。
- **阶段 7**：根据实际提交和测试证据形成简历描述、面试可复盘技术故事。

## 7. 版权边界

- iRun、CampusGo：MIT，可按许可证规定保留版权与许可声明后二次开发。
- 福U跑腿：AGPL-3.0，修改并作为网络服务提供时触发相应源代码提供义务；和计划基于 MIT 的顺手项目混合需先做兼容性合规判断。
- Campus Errand：仓库 LICENSE 为 GPL-2.0（README 的 MIT 徽章不具法律优先性），衍生作品在分发时有相应 copyleft 义务。
- CampusHub、trade_cos 等未核对到明确根许可证的参考项目，不直接复制代码；仅参考业务模式。引用第三方组件按各自许可证单独核查。

## 8. INPassing 当前状态

审计时仓库仅有 README.md，无顺手正式代码。GitHub 账号具有写入权限。本报告作为文档提交不代表完整代码已提交；**绝不宣称仓库已可本地启动**。

核验索引：请从上述链接进入真实源码相应行号；后续每个改动记录目标文件、改动原因、测试命令、真实结果和提交 SHA。
