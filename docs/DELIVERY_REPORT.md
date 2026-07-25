# 交付验证报告

## 1. 当前交付范围

本项目位于 `smart-customer-service/`，当前主版本是受控 LLM Workflow 客服系统：

- `server/`：FastAPI 后端、SQLAlchemy 模型、Alembic 迁移、受控 Workflow 服务、RAG 检索、文档 Worker 和自动化测试。
- `web/`：Vue 3 + TypeScript 前端。
- `deploy/`：Docker Compose、后端/前端 Dockerfile、Nginx 配置。
- `docs/`：Python 版本面试说明、迁移说明和交付报告。
- `legacy/java-server/`：旧 Java 后端源码，仅作迁移参考，不参与当前运行。
- `.env.example`、`.gitignore`、`README.md`。

## 2. 推荐验证命令

```powershell
cd server
.\.venv\Scripts\python.exe -m ruff check app tests scripts
.\.venv\Scripts\python.exe -m mypy app tests scripts
.\.venv\Scripts\python.exe -m pytest

cd ..\web
npm run build

cd ..
docker compose -f deploy\docker-compose.yml config --quiet
```

## 3. 当前实现状态

- 已实现 JWT Access Token + Redis Refresh Token 登录认证。
- 已实现用户端和管理端角色隔离。
- 已实现商品、订单、物流、售后规则、会话、工单和 Agent 审计数据模型。
- 已实现统一工具执行器，接入角色权限、Pydantic 参数校验、超时、重试、脱敏审计、耗时记录。
- 已实现输入安全前置、LLM/规则结构化规划、策略二次校验，订单/物流/商品问题优先查询业务表，知识类问题进入 RAG。
- 已实现三路混合召回：关键词检索、Dense Vector 检索、结构化业务规则检索。
- 已实现 RRF 融合和轻量级启发式重排；没有宣传为真实 Cross-Encoder。
- 已实现 Redis 聊天滑动窗口限流和检索结果短期缓存。
- 已实现文档任务条件更新抢占、失败重试、next_retry_at、最大重试和 DEAD_LETTER。
- 已实现 `/liveness` 与 `/readiness`，readiness 检查 MySQL、Redis、Qdrant。

## 4. 安全说明

- `.env` 已被 `.gitignore` 排除，不提交仓库。
- `.env.example` 默认不填写 `LLM_API_KEY`，并启用 Mock 模式。
- 真实 API Key 只能放在本地 `.env`，不能写入源码、模板、README、测试文件、日志或交付压缩包。

## 5. 尚未完成的生产级能力

- 默认 MockEmbedding 只用于链路验证；非 Mock 模式已支持 OpenAI Compatible Embedding，但真实语义效果需配置真实 Embedding Key 后验证。
- 未接入真实 Cross-Encoder/Reranker。
- LLM 结构化规划和回答生成已接入 OpenAI Compatible 客户端；默认 Mock 模式不会访问外网，真实模型调用需本地 `.env` 配置 Key 后验证。
- 未接入真实支付、物流、订单系统。
- 未做完整压测、链路追踪和生产级密钥管理。
