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
