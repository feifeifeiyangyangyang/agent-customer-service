# 简历描述

## 项目标题

Smart Support System：基于受控 LLM Workflow 的电商售后智能处置与人工协同平台

## 技术栈

Python 3.12、FastAPI、SQLAlchemy Async ORM、Alembic、MySQL、Redis、Qdrant、Vue 3、TypeScript、Element Plus、Docker Compose

## 项目描述

- 基于 Python FastAPI + Vue 实现电商客服平台，覆盖商品浏览、模拟下单、订单查询、物流咨询、售后问答、人工工单和后台管理。
- 实现 JWT Access Token + Redis Refresh Token 登录体系，用户端和管理端按角色隔离访问。
- 将客服链路拆成输入安全守卫、LLM/规则结构化规划、策略二次校验、统一工具执行器、RAG 检索和响应安全守卫，避免模型直接修改业务数据。
- 实现统一工具执行框架，接入工具注册表中的角色权限、Pydantic 参数校验、超时、重试、脱敏审计、耗时记录和幂等审批请求。
- 实现三路混合召回：MySQL 关键词检索、Qdrant Dense Vector 检索、结构化售后规则检索，并使用 RRF 与轻量级启发式重排融合结果。
- 针对订单相关问题优先查询真实业务表，支持“最近订单”“第二个订单”“我买的杯子”等指代表达，减少纯 RAG 编造。
- 使用 Redis 实现 Refresh Token 轮换、聊天滑动窗口限流和检索结果短期缓存。
- 文档处理采用任务表异步执行，使用条件更新原子抢占任务，并支持 retry_count、next_retry_at、最大重试和 DEAD_LETTER 状态。

## 1 分钟介绍

这是一个面向电商售后场景的智能客服项目。用户可以浏览商品、模拟下单、查询订单和物流；咨询发货、物流、退款、破损、退货运费等问题时，系统会先查订单和结构化业务规则，再使用知识库检索补充依据。Agent 不是直接让大模型自由执行操作，而是通过确定性路由和工具执行器控制权限、参数、审计和高风险审批。项目适合展示后端业务建模、RAG 检索、Redis、异步任务和工程可靠性。

## 3 分钟介绍

项目当前主版本是 Python FastAPI 后端和 Vue 前端。后端使用 SQLAlchemy Async ORM 和 Alembic 管理 MySQL 表结构，Redis 用于 Refresh Token、聊天限流和检索缓存，Qdrant 用于向量召回链路验证。客服链路先做输入安全检查，再通过 LLM/规则生成结构化候选计划，并由策略层校验工具、权限和高风险审批要求。工具执行器会读取注册表中的权限、超时、重试、幂等和脱敏策略，所有调用写入 `agent_tool_call`，检索候选写入 `agent_retrieval_trace`。RAG 部分实现关键词、Dense Vector、结构化规则三路独立召回，RRF 融合后使用轻量级启发式重排。文档 Worker 使用条件更新抢占任务，避免多 Worker 重复处理，并支持失败重试和死信状态。

## 面试时不要夸大的点

- 当前没有真实 Cross-Encoder 模型，重排是 RRF + 关键词覆盖 + 结构化规则优先级的启发式重排。
- 当前默认 Embedding 是 Mock 哈希向量，可验证链路但不代表真实语义向量效果。
- 当前不是多 Agent 系统，LLM 规划只是候选计划，不能越过策略层和工具执行器。
- 默认 Mock 模式不会真实调用大模型；真实模型和真实 Embedding 需本地 `.env` 配置 Key 后验证。

## 设计取舍与面试追问

### 1. 受控 Workflow，而不是完全自主 Agent

简历建议：

- 设计受控 LLM Workflow，将输入守卫、结构化规划、策略校验、工具执行、RAG、人工审批和审计收口串成确定性链路，避免大模型直接执行退款、取消订单等高风险操作。

面试追问预留点：

- 为什么 LLM 不能直接改库？
- 高风险动作为什么要 Human-in-the-loop？
- 工具调用失败时怎么降级？

代码支撑：

- `server/app/agent/routing.py`
- `server/app/agent/tools/executor.py`
- `server/app/services/agent_service.py`

不能这样写：

- “实现多 Agent 协同”
- “大模型自主完成退款和取消订单”

### 2. 三路混合召回

简历建议：

- 针对电商售后中商品型号、政策术语、订单状态和口语化表达混杂的问题，实现关键词检索、Dense Vector 检索和结构化业务规则检索三路召回，使用 RRF 融合并保留召回来源、原始分数和失败样本用于错误分析。

面试追问预留点：

- 为什么纯向量不够？
- 结构化规则和知识库文档冲突怎么办？
- RRF 参数和阈值怎么解释？

代码支撑：

- `server/app/services/knowledge_service.py`
- `server/app/schemas/retrieval.py`
- `server/app/rag/retrieval_config.py`

不能这样写：

- “已接入真实 Cross-Encoder”
- “Mock Embedding 具备真实语义召回能力”

### 3. 工具安全、幂等和审计

简历建议：

- 建立统一工具注册表和执行器，对工具角色权限、Pydantic 参数校验、风险等级、超时重试、脱敏和审计统一处理；退款和取消订单只生成审批请求，由管理员审批后再执行确定性业务动作。

面试追问预留点：

- 重复提交退款如何处理？
- 管理员审批前为什么还要重新校验订单？
- 工具参数里有敏感信息怎么记录日志？

代码支撑：

- `server/app/agent/tools/registry.py`
- `server/app/agent/tools/executor.py`
- `server/app/services/action_execution_service.py`

不能这样写：

- “LLM 直接调用支付退款接口”
- “权限由提示词保证”

### 4. 评测集和消融实验

简历建议：

- 构建 63 条版本化售后离线评测集，覆盖商品咨询、订单查询、物流发货、退款/取消、破损售后、Prompt Injection、越权查询、多轮指代和检索故障；实现规划路由评测与检索消融脚本，输出准确率、Recall@K、MRR、失败样本和配置版本。

面试追问预留点：

- 这个评测是测 LLM 还是测 Workflow？
- 检索消融需要哪些依赖？
- 没有真实 Embedding 时结果怎么解释？

代码支撑：

- `server/evals/datasets/after_sale_v1.jsonl`
- `server/evals/run_eval.py`
- `server/evals/run_retrieval_ablation.py`

不能这样写：

- “线上准确率提升 XX%”
- “Mock 结果证明真实模型效果”
