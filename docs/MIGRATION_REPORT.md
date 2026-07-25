# 迁移审计报告

## 1. 迁移结果

当前仓库主版本已经调整为 Python FastAPI + Vue：

- Python 后端位于 `server/`。
- Vue 前端位于 `web/`。
- 旧 Java 后端位于 `legacy/java-server/`，仅作为迁移参考。

## 2. 旧实现保留策略

旧 Java 源码保留在 `legacy/java-server/`，便于面试时说明迁移前后差异。但旧 Java 构建产物、`target/`、`.class`、测试报告等不应进入 GitHub 或交付压缩包。

## 3. 当前 Python 实现重点

- FastAPI 路由和服务层。
- SQLAlchemy Async ORM 数据模型。
- Alembic 数据库迁移。
- JWT + Redis Refresh Token。
- Redis 聊天限流和检索缓存。
- Qdrant 向量召回链路。
- 结构化售后规则检索。
- 统一工具执行器和 Agent 审计表。
- 文档任务异步处理、原子抢占、失败重试和死信状态。

## 4. 安全处理

- 未迁移任何旧 API Key。
- `.env` 被 `.gitignore` 排除。
- `.env.example` 不包含真实密钥。
- 默认 Mock 模式可完成编译和自动化测试。

## 5. 面试表述建议

可以说项目完成了从旧版本到 Python Agent 架构的重构，但不要把 Mock Embedding、启发式重排、响应守卫图包装成真实语义模型、Cross-Encoder 或完整 LangGraph 工作流。
