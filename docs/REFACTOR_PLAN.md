# 受控 LLM Workflow 面试版重构计划

## 当前真实实现

- 后端主版本是 Python 3.12 + FastAPI + SQLAlchemy Async ORM。
- 前端是 Vue 3 + TypeScript + Element Plus。
- 数据库迁移使用 Alembic，业务数据存储在 MySQL。
- Redis 当前用于 Refresh Token、聊天滑动窗口限流和检索结果短期缓存。
- Qdrant 当前用于 Dense Vector 召回链路；默认 Embedding 是 Mock 哈希向量，用于本地演示和自动化测试，非 Mock 模式支持 OpenAI Compatible Embedding。
- LangGraph 当前用于受控 Workflow 的输入守卫和响应收口，不承担多 Agent 编排。
- 旧 Java 后端位于 `legacy/java-server/`，仅作为迁移参考。

## 已完成重点

- 真实后端登录和角色权限。
- 商品、订单、物流、售后规则和工单基础业务模型。
- 统一工具执行器：角色校验、参数校验、超时、重试、脱敏审计、耗时记录。
- 三路混合召回：关键词、Dense Vector、结构化规则。
- RRF 融合与轻量级启发式重排。
- 文档 Worker 条件更新抢占任务、失败重试和 DEAD_LETTER。
- `/liveness` 与 `/readiness` 健康检查。

## 后续可改方向

- 接入真实 Embedding 模型或云端 Embedding 服务。
- 接入真实 Cross-Encoder/Reranker 或把重排明确保持为启发式算法。
- 继续完善“输入守卫 + LLM/规则结构化规划 + Pydantic 校验 + 规则二次约束”。
- 增加 Redis Embedding 缓存、Agent 状态持久化和更细粒度幂等控制。
- 增加集成测试、压测和链路追踪。

## 不能虚构的边界

- 不说当前是多 Agent 系统，或模型可以自由执行工具。
- 不说当前默认向量具备真实语义相似能力。
- 不说当前已经接入真实 Cross-Encoder。
- 不说 Redis 已经承担所有 Agent 状态或文档任务状态。
- 不在仓库中保存真实 API Key、真实 `.env`、上传文件、数据库数据或模型大文件。
