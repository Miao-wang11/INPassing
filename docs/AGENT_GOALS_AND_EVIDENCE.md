## 本轮 PR：目标理解与证据解释

**基线**：2026-10-22 main（检查时 HEAD `64d6e9f4cde4fa99232e9ada5dd41ad0f42d9df9`），仓库中不存在 AGENTS.md。

### 故障复现
原 `plan()` 仅执行 `_valid_slots(_rule_extract(human))`。如
“我在图书馆，准备去南门，绕路别超过十分钟” 中，正则不能得到路线和绕路上限，
但模型虽能读懂仍无法修正启动 MCP 子进程前固定的 `SHUNSHOU_MCP_ORIGIN` 和 `SHUNSHOU_MCP_DESTINATION`。
原 `present_candidates` 丢弃模型解释，改为固定 `reason_tags` 模板，模型比较没有展示。

### 目标链路
- 新增 `route_goal.GoalInterpreter`：使用现有 DeepSeek LangChain 模型的 `with_structured_output(GoalPatch, method="function_calling")`，只基于**本轮用户原文**提出字段更新，并给每个字段提供原文证据片段。
- `apply_goal_patch` 对原文片段匹配、模糊地点、额外绕路分钟语义、出发时间（当前 `Asia/Shanghai`）、硬/软类型等进行确定性校验；无法确认则明确追问，**不执行 MCP 查询**。未提及字段保持前值；明确放宽才可更新。旧会话的候选/路线证据不参与新一轮校验。
- `plan()` 等待目标字段（起点、终点、未来出发时间、绕路上限）完整可校验后，才启动 **新** MCP stdio 子进程，传入 **相同已校验** 目标起终点。HMAC 用户/校区继续由 Java 签名注入，模型无法传任何身份参数。
- 硬性类型、绕路上限、已查看/预检/路线验证、任务接单截止时间均在 `validate_recommendation` 执行层检查。严格重量/等待时间但后端无结构化可信数据时只能追问或人工核实，不会被当成已经满足。
- 保持 LangGraph、MCP 工具列表、模型自主 tool_calls/ToolMessage 循环、预算与 Java 用户点击最终接单不变。

### 解释链路
- 模型通过 `present_candidates` 提交自己选择和排序的 `task_ids` 及 `recommendations`、`excluded`、`summary`。
- 每条只允许引用 `get_task_detail` 的类型/地点/费用/截止时间与 `estimate_task_detour` 的估算分钟。费用由 `tip + deliveryFee` 而非任务总预付 `reward` 计算。
- 程序验证候选 ID、本轮工具状态、来源字段、时间和金额；拒绝任务不存在、排除理由缺少核验、虚构数值、保证准时/重量/排队等不实说法。被拒绝的比较进入 ToolMessage 错误，模型可在预算内重写；旧式调用降级成明确标记 `explanation_degraded` 的真实工具摘要，绝不伪装模型比较。
- 返回 `comparison` 及 `summary`，移动端展示每条选择/排除理由、`tool/field/value` 来源、未知条件、本轮执行限制，原确认接单保持不变。

### 测试
受控结构化模型解析桩和工具调用桩执行完整 LangGraph 入口；MCP 测试仍通过真实 stdio 子进程、签名 Java HTTP 业务桩。覆盖两种中文说法、分钟后出发、缺失/不明确地点、多轮更新、硬/软任务类型、上限、两候选真实证据差异与伪造数值/ID。Windows 和 Ubuntu 在 GitHub Actions 上跑 Python 3.13；前端 Node 合同测试检测 Vue 绑定和接单调用。**受控桩不等于真实 DeepSeek 联调**。

### 配置 / 依赖
继续使用原 `SHUNSHOU_AI_MODEL_PROVIDER=openai_compatible`、DeepSeek Key、现有 Java HMAC、Python 3.13 及 MCP 依赖，**未新增依赖和数据库迁移**。本次不接腾讯地图、不动智能委托，也不改 RAG/内存/支付/SSE。

### 未验证
真实 DeepSeek 对多轮复杂语句的稳定性、真实校园步行路网完备性、实际数据库和 HBuilderX 真机交互仍需使用各自真实凭证和数据验证。本次 CI 使用可控模型与 Java 测试替身，绝不声称线上验证成功。
