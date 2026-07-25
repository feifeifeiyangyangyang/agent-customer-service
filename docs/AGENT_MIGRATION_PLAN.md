# Agent Python 迁移说明

## 当前定位

当前项目已经从旧 Java 后端迁移为 Python FastAPI 主版本，定位为：

**Smart Support System：电商智能客服与人工工单协同平台**

迁移目标不是做一个只会聊天的 RAG Demo，而是围绕商品、订单、物流、售后规则、人工工单和审批请求形成可审计闭环。

## 目录边界

- `server/`：当前 Python FastAPI 后端。
- `web/`：当前 Vue 前端。
- `deploy/`：Docker Compose、Dockerfile 和 Nginx 配置。
- `docs/`：当前 Python 版本说明。
- `legacy/java-server/`：旧 Java 后端源码，仅作迁移参考，不参与当前运行。

## 已迁移能力

- 认证：JWT Access Token + Redis Refresh Token。
- 业务：商品、订单、物流、售后规则、工单。
- Agent：确定性路由、统一工具执行器、高风险审批请求、运行步骤和工具调用审计。
- RAG：关键词检索、Dense Vector 检索、结构化规则检索、RRF 融合、启发式重排。
- 文档：上传、解析、切片、任务表异步处理、Qdrant 写入、失败重试。
- 运维：Docker Compose、liveness/readiness、Redis 限流和检索缓存。

## 真实边界

- 当前没有完整 LLM Planner，规划主要是确定性规则路由。
- 当前 LangGraph 只做 response guard graph。
- 当前 Dense Vector 默认是 MockEmbedding，不代表真实语义 Embedding。
- 当前重排是启发式算法，不是真实 Cross-Encoder。

## 安全要求

真实 API Key 只能写入本地 `.env`，不能写入源码、模板、README、测试文件、日志或压缩包。默认使用 Mock 模式完成本地构建和自动化测试。
