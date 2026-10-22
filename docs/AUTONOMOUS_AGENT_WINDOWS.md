# 顺手顺路接单：自主 Agent（Windows / Conda / 非 Docker）

> 仅改造顺路接单。智能委托依旧走原有 LangGraph 流程。本分支未修改默认分支，也不会替用户抢单。

## 实际调用路径

```text
uni-app → Java JWT /agent/route-match → HMAC → FastAPI
 → LangGraph plan (per-user+per-campus+per-session SQLite checkpoint)
 → DeepSeek bind_tools (MCP discovery schema) → tool_calls
 → Python MCP ClientSession (stdio, per request) → FastMCP Server
 → signed Java GET /internal/agent/v1/... → scoped SQL/verified walk network
 → MCP tool result → ToolMessage → DeepSeek next action
 → present_candidates (local validated finishing tool) OR clarification/error
 → Java /agent/matches/accept/{taskId} ONLY after explicit UI confirmation
```

- **决策**：DeepSeek 决定工具及下一步；LangGraph 管理会话和人工选单中断。
- **执行**：MCP v1 的真实 stdio ClientSession/Server initialize、list_tools、call_tool、ToolMessage 回传。MCP 子进程随每次路线请求启动，退出即销毁，Windows 不需要另外开放 MCP 端口。
- **认证**：Java 从 JWT 获取用户/校区，签发短期 HMAC principal；Python 为此请求启动隔离 MCP 进程。工具参数不接受 userId、campusId、任意 URL、任意 SQL。
- **限额**：每轮最多 9 次模型决策、16 次工具请求、40 个唯一候选、6 次单任务路线估算、45 秒总时间。Java 代理超时 65 秒。停止后报告当前实际查询页与未核实项。
- **可信数据**：Java 提供有效同校区公开任务与资格预检，任务详情不含联系方式/取件码。估算使用已核实路网边长、1.25m/s 假定速度和4分钟交接缓冲。**不是实时腾讯地图导航，也不是准时保证**。地图路网缺失时明示未知；不会自动虚构坐标。
- **人工确认**：模型只做推荐；用户选择后仍需点击确认调用 Java `TaskOrderService.acceptOrder`。无法代用户下单或扣款。
- **保持原业务**：智能委托、管理端、钱包与支付流程没有重写。

## Windows 本机启动（建议 PowerShell）

### 1. 获取独立分支

```powershell
git fetch origin codex/autonomous-campus-agent
git switch codex/autonomous-campus-agent
git pull --ff-only origin codex/autonomous-campus-agent
```

请先保存未提交的本机改动，避免 Git 切换分支覆盖本机文件。本分支尚未合并至 main。

### 2. Python 3.13 / Conda / DeepSeek

```powershell
conda create -n shunshou-agent python=3.13 -y
conda activate shunshou-agent
cd ai-service
python -m pip install -e .
Copy-Item .env.example .env
notepad .env
```

至少配置（只放入被 gitignore 的 `ai-service/.env`，**不要提交密钥**）：

```dotenv
SHUNSHOU_AI_ENVIRONMENT=development
SHUNSHOU_AI_INTERNAL_TOKEN=填入至少32个字符的随机密钥
SHUNSHOU_AI_JAVA_BASE_URL=http://127.0.0.1:8080/api
SHUNSHOU_AI_MODEL_PROVIDER=openai_compatible
SHUNSHOU_AI_MODEL_NAME=deepseek-chat
SHUNSHOU_AI_MODEL_BASE_URL=https://api.deepseek.com
SHUNSHOU_AI_MODEL_API_KEY=填入你自己的DeepSeek密钥
SHUNSHOU_AI_CHECKPOINT_PATH=data/checkpoints.sqlite3
SHUNSHOU_AI_REQUEST_TIMEOUT_SECONDS=15
```

随机内部密钥可通过 `python -c "import secrets; print(secrets.token_hex(32))"` 生成，与 Java **完全一致**。

然后在 `ai-service/` 目录运行：

```powershell
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

**MCP 启动方式**：不要单独用无身份的 `python -m app.mcp_server` 启动，它会因缺少 Java 授权 principal 而拒绝执行。这是预期安全机制。FastAPI 收到合法路由请求时，会用 Python 3.13 自动启动 `python -m app.mcp_server` 子进程，通过标准 MCP stdio 协议创建独立会话并执行工具。无需另一窗口、Docker 或 MCP 监听端口。

`GET http://127.0.0.1:8000/healthz` 仅检查 Python 服务是否存活，**不代表** DeepSeek/Java/路网联调成功。

### 3. IDEA 启动 Java

在 IDEA 用 JDK 21 打开 `backend/pom.xml`，沿用本地 `application-dev.yml`、MySQL/Redis 配置，在 IDEA Spring Boot Run Configuration 的环境变量增加：

```dotenv
SHUNSHOU_AI_INTERNAL_TOKEN=与Python相同的内部密钥
SHUNSHOU_AGENT_PYTHON_URL=http://127.0.0.1:8000
```

按原有方式运行后端，默认 `http://127.0.0.1:8080/api`。Java 正式 JWT 认证和学生校区认证不可省略，**不要从 uni-app 直接访问 Python 或 MCP**。测试须登录真实的已通过认证用户。

### 4. 数据库（保留现有数据）

本次改造**没有新增数据库迁移**。使用已存在的业务数据库，不要执行 `backend/runningerrands.sql`（该脚本包含重建数据内容）。如果本地尚未按旧项目文档应用过 `20261020_campus_scope.sql`、`20261021_agent_walk_network.sql` 等增量迁移：备份数据库、检查表和索引是否存在后，仅执行缺失迁移一次，**不要盲目重跑**。

任务筛选在 Java/MySQL 按校区、状态、类型、关键词、取送地址 SQL 下推及分页；已有 `idx_task_campus_hall` 索引。当前地址关键词 `LIKE '%关键字%'` 不是高性能空间索引，面向更大数据量时仍需要新的经纬度边界过滤和查询计划分析。

本仓库现有的是经核实步行边路网 `agent_walk_place` / `agent_walk_edge`。即便配置腾讯地图 Key，**也不会自动填充该路网**。如果本机有尚未提交的腾讯地图服务，实现前需先同步并完成独立代码评审；本分支不宣称已调用实时腾讯路线 API。

### 5. 管理端和用户端

管理端无需改造。另开终端：

```powershell
cd admin
npm ci
npm run dev
```

使用 HBuilderX 打开 `mobile/`，运行 uni-app。PC H5 的 Java 地址由 `mobile/utils/config.js` 决定，默认 localhost:8080；手机/真机要换成**可到达电脑的局域网地址**，不能使用手机自身的 localhost。

在智能助手切换到“顺路接单”，输入如“从图书馆去南门，最多绕路10分钟，只能拿轻的东西，帮我找任务”。界面在本轮请求返回后展示**已真实执行**的 MCP 工具名、状态、累计耗时和脱敏结果摘要；这是执行完成后时间线，**不是实时逐条推送**。缺少可信重量、取件等候时间、地图路网时必须按未知处理。

### 6. 运行受控 MCP 集成测试

```powershell
cd ai-service
python -m pip install pytest
python -m pytest -q tests/test_autonomous_mcp.py
```

测试会启动真实 MCP 子进程，通过签名只读 Java HTTP 适配器访问模拟业务服务，以可控模型替身产生真实的 `AIMessage.tool_calls` 并验证每轮 ToolMessage 反馈。它**不会调用收费的 DeepSeek**、不会调用腾讯地图，也不会写数据库。提交后 GitHub Actions 同时验证 Java 21 Maven 编译。

示例受控轨迹 A（测试用虚构 taskId=7）：

```text
search_tasks → get_task_detail → check_accept_eligibility
 → estimate_task_detour → present_candidates → choose_task
```

示例受控轨迹 B（空候选）：

```text
search_tasks → 模型读取空 records → 回答当前搜索页没有候选
```

这证明工具结果确实可以改变模型下一步的调用轨迹，**但不代表真实大模型输出稳定性已验收**。真实 DeepSeek 联调需要本机已有有效 API Key 以及 Java、认证、路网数据，用上述运行方式从 uni-app 验证。

## 当前限制 / 后续验收

1. 已完成真实 MCP 初始化、发现、调用、返回模型以及权限隔离；测试模型是可控替身。**尚无真实 DeepSeek API 联调证明**，模型推理不等于 Agent 自己获得数据库执行权限。
2. 地图估算目前基于已核实路网；未实现仓库中不存在的“腾讯实时步行路线客户端”，不得宣传成腾讯实时导航。
3. 浏览器/移动端事件是在 HTTP 结束后显示，非流式 SSE；HBuilderX 真机尚未验证。
4. 单请求启动 MCP 进程利于 Windows 和隔离，但吞吐及延迟没有做压力测试；单实例 SQLite Checkpointer 不支持多实例无缝共享。
5. 搜索最多 5 页且有候选上限；这是本轮有限检索，不是全库最优，缺少索引支持的地理边界预筛选。
6. Java 最终接单并发/余额/风控逻辑保持原状，本分支没有独立数据库并发压力环境。上线前要验证过期、抢单冲突及重放场景。
