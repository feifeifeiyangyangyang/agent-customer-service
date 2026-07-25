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
