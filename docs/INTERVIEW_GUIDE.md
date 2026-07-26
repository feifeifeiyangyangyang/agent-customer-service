# 面试讲解指南

## 1. 当前项目到底是什么架构？

当前主版本是 Python FastAPI + Vue 的模块化单体。后端负责认证、商品订单、物流、售后规则、知识库、Agent 工具执行和工单；前端提供用户端客服和管理端后台。旧 Java 代码只保留在 `legacy/java-server/` 作为迁移参考。

## 2. 为什么不是完全交给大模型？

客服系统里很多问题必须查业务状态，例如订单是否发货、是否签收、是否超过售后期。项目采用“输入守卫 + LLM/规则结构化规划 + 策略二次校验 + 工具执行器 + RAG”的方式，让订单、退款、取消订单等操作走受控工具，而不是让大模型直接决定和改库。

## 3. Tool Calling 做到了什么？

项目有统一工具注册表和执行器。执行器会读取工具策略，做角色校验、Pydantic 参数校验、超时、重试、参数脱敏、耗时统计和审计落库。高风险工具只创建审批请求，不直接修改订单状态。

代码路径：`server/app/agent/tools/registry.py`、`server/app/agent/tools/executor.py`

## 4. LangGraph 在项目里承担什么？

当前 LangGraph 用于受控 Workflow 的输入守卫和响应收口，主业务执行仍由 FastAPI 服务层和工具执行器完成。面试时不要说成多 Agent 协作，也不要说模型能自由执行工具。

代码路径：`server/app/agent/graph.py`

## 5. RAG 是怎么做的？

RAG 使用三路独立召回：

- 关键词检索：MySQL chunk 关键词匹配。
- Dense Vector 检索：默认 MockEmbedding + Qdrant，主要验证向量召回链路；非 Mock 模式支持 OpenAI Compatible Embedding。
- 结构化规则检索：根据订单状态、商品分类、签收天数、售后类型、规则版本和有效期筛选规则。

之后用 RRF 融合，再做轻量级启发式重排。当前没有真实 Cross-Encoder。

代码路径：`server/app/services/knowledge_service.py`

## 6. Redis 用在哪里？

当前 Redis 真实承担三类能力：

- Refresh Token 保存与轮换。
- 聊天接口滑动窗口限流。
- 检索结果短期缓存。

不要说 Redis 已经承担 Agent 状态持久化、Embedding 缓存或文档任务状态。

## 7. 文档 Worker 如何避免重复处理？

Worker 通过条件更新把任务从 `PENDING` 抢占为 `PROCESSING`，检查影响行数，只有更新成功的 Worker 能处理该任务。失败后根据 `retry_count`、`max_retry_count` 和 `next_retry_at` 决定重试或进入 `DEAD_LETTER`。

代码路径：`server/app/services/document_processing_service.py`

## 8. 健康检查如何设计？

`/liveness` 只表示进程存活；`/readiness` 检查 MySQL、Redis 和 Qdrant。依赖不可用时 readiness 返回 503，方便部署平台判断是否接流量。

代码路径：`server/app/api/v1/health.py`

## 9. 哪些地方不能夸大？

- 默认向量是 MockEmbedding，不是真实语义模型。
- 重排是启发式重排，不是真实 Cross-Encoder。
- LangGraph 不是多 Agent 编排。
- LLM 规划只是候选计划，必须经过确定性策略层二次校验。

## 10. 评测系统怎么讲？

项目新增 `server/evals/`，包含版本化评测集 `after_sale_v1` 和两个命令行评测器。

规划与安全路由评测：

```powershell
cd server
.\.venv\Scripts\python.exe -m evals.run_eval --output evals\reports\workflow_eval_latest.json --pretty
```

检索消融实验：

```powershell
cd server
.\.venv\Scripts\python.exe -m evals.run_retrieval_ablation --output evals\reports\retrieval_ablation_latest.json --pretty
```

评测集当前有 63 条样本，覆盖商品咨询、订单查询、物流发货、退款规则、退款操作、取消订单、破损售后、无答案拒答、信息不完整澄清、Prompt Injection、越权查询、多轮指代和检索通道故障。样本中保存期望意图、期望工具、风险等级、是否需要确认、期望召回来源和禁止出现的事实。

面试边界：`run_eval` 主要验证结构化规划和安全路由，不代表真实大模型回答质量。检索消融依赖 MySQL 和 Qdrant；默认 Mock Embedding 只能验证工程链路，不代表真实语义召回。

## 11. 设计取舍与面试追问

### 为什么选择受控 Workflow，而不是完全自主 Agent？

简历可以写：将客服链路拆成输入守卫、结构化规划、策略校验、工具执行、RAG、人工审批和审计收口，限制模型只生成候选计划，业务副作用由确定性代码执行。

面试官可能追问：为什么不让模型直接退款？回答时强调订单归属、状态机、幂等、审批和审计必须由服务端保证，LLM 不能作为权限边界。

代码路径：`server/app/agent/routing.py`、`server/app/agent/tools/executor.py`、`server/app/services/agent_service.py`

可展示：`python -m evals.run_eval`、`tests/test_agent_routing.py`、`tests/test_tool_registry.py`

不能夸大：不是 Multi-Agent，不是开放式 ReAct。

### 为什么使用关键词、Dense Vector、结构化规则三路召回？

简历可以写：针对商品型号、订单时效和售后状态难以靠纯向量稳定处理的问题，实现关键词、Dense Vector 与结构化规则三路混合召回，并使用 RRF 融合和启发式重排。

面试官可能追问：三路分别解决什么问题？关键词处理型号和政策术语，Dense 处理口语化表达，结构化规则处理订单状态、售后类型、规则版本和有效期。

代码路径：`server/app/services/knowledge_service.py`、`server/app/schemas/retrieval.py`、`server/app/rag/retrieval_config.py`

可展示：`python -m evals.run_retrieval_ablation`

不能夸大：默认 Mock Embedding 不代表真实语义检索；当前没有真实 Cross-Encoder。

### 模型生成工具调用后，系统如何保证权限、安全、幂等和审计？

简历可以写：统一工具注册表描述角色、风险等级、只读性、幂等性、超时、重试和脱敏策略；执行器统一做参数校验、权限校验、审计记录，高风险工具只生成审批申请。

面试官可能追问：重复退款怎么办？回答时说明业务幂等键、审批状态控制和审批前实时订单状态校验。

代码路径：`server/app/agent/tools/registry.py`、`server/app/agent/tools/executor.py`、`server/app/services/action_execution_service.py`

可展示：`tests/test_action_execution_service.py`、`tests/test_controlled_workflow_components.py`

不能夸大：不要说 LLM 直接执行退款或取消订单。

### 如何通过评测集、消融实验和失败案例证明改进有效？

简历可以写：建立版本化售后评测集，分别评估意图识别、工具选择、风险拦截、Prompt Injection 拦截和检索消融，报告保留失败样本和失败原因。

面试官可能追问：评测是否等于线上效果？回答时说明当前是离线回归与工程链路验证，真实模型效果需要配置真实 LLM 和 Embedding 后单独验证。

代码路径：`server/evals/datasets/after_sale_v1.jsonl`、`server/evals/run_eval.py`、`server/evals/run_retrieval_ablation.py`

可展示：`server/evals/reports/workflow_eval_latest.json`、`server/evals/reports/retrieval_ablation_latest.json`

不能夸大：没有真实执行结果时，不写提升百分比。
