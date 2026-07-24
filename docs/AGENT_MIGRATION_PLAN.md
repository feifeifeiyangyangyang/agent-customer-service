# 智服通 Agent Python 迁移计划

## 目标定位

将现有 Java Spring Boot 后端迁移为 Python FastAPI 后端，并把项目定位为：

**智服通 Agent：电商售后智能处置与人工协同平台**

本迁移不把项目包装成“普通 RAG 聊天机器人”。核心目标是让 Agent 围绕订单、物流、售后、取消订单、退款和人工审批形成可审计闭环。

## 现状审查

现有后端位于 `server/`，技术栈为 Java 17、Spring Boot 3.3.5、Spring Security、MyBatis-Plus、Flyway、MySQL、Redis、Qdrant、ONNX Runtime。

现有前端位于 `web/`，技术栈为 Vue 3、TypeScript、Vite、Element Plus。前端统一通过 `/api/v1` 调用后端接口。

现有数据库迁移位于 `server/src/main/resources/db/migration/`，共 7 个 Flyway SQL：

- `V1__init_schema.sql`：知识库文档、会话、消息、工单。
- `V2__auth_and_ticket_ownership.sql`：用户、会话归属、工单归属、乐观锁。
- `V3__document_tasks_and_chunks.sql`：文档处理任务、知识片段。
- `V4__message_sources_and_ticket_log.sql`：回答来源快照、工单操作日志。
- `V5__commerce_order_logistics.sql`：商品、订单、物流事件。
- `V6__model_runtime_config.sql`：模型运行参数。
- `V7__model_runtime_min_retrieval_score.sql`：最低检索分数。

## Java 到 Python 模块映射

| Java 模块 | Python 目标模块 | 迁移策略 |
| --- | --- | --- |
| `controller/AuthController.java` | `app/api/v1/auth.py` | 保持 `/auth/login`、`/auth/refresh`、`/auth/logout`、`/auth/me` 响应结构 |
| `CommerceController.java` | `app/api/v1/commerce.py` | 保持商品、订单、管理员订单接口 |
| `ConversationController.java` | `app/api/v1/conversations.py` | 保持会话和消息接口 |
| `ChatController.java` | `app/api/v1/chat.py`、`app/api/v1/agent.py` | 旧 `/chat` 兼容，新 Agent 接口新增 |
| `TicketController.java` | `app/api/v1/tickets.py` | 保持用户工单和管理员工单接口 |
| `AdminDocumentController.java` | `app/api/v1/documents.py` | 已迁移文档上传、处理、重试和删除基础接口 |
| `ModelRuntimeConfigController.java` | `app/api/v1/model_config.py` | 保持管理端模型参数接口 |
| `application/*Service.java` | `app/services/` | 领域规则使用普通 Python 服务，不塞进路由 |
| `entity/*.java` | `app/db/models/` | 使用 SQLAlchemy 2.x ORM |
| `mapper/*.java` | `app/repositories/` | 查询逻辑集中封装 |
| `rag/*` | `app/rag/` | 已迁移文档解析和切片；混合检索、阈值拒答后续完善 |
| `client/*ChatModel*` | `app/model_providers/` | Mock 与 OpenAI-compatible 适配 |
| `client/*Embedding*` | `app/embeddings/` | Mock、本地模型、OpenAI-compatible |

## 数据库迁移策略

1. 使用 Alembic 管理 Python 版本迁移。
2. 首个 Alembic 版本重建现有核心表，并新增 Agent 审计表。
3. 不使用 `drop_all()` 初始化正式数据库。
4. 对已有 Flyway 数据库，后续提供 Alembic `stamp` 说明。
5. JSON 字段仅保存可审计摘要，不保存模型隐藏思维链。

## 新增 Agent 表

- `agent_run`
- `agent_step`
- `agent_tool_call`
- `agent_action_request`
- `agent_feedback`

## 迁移阶段

1. 移动 Java 后端到 `legacy/java-server/`。
2. 创建新的 `server/` Python 项目骨架。
3. 建立 SQLAlchemy 模型和 Alembic 初始迁移。
4. 迁移认证、商品、订单、物流、会话、聊天、工单最小兼容接口。
5. 实现 Agent 工具薄封装。
6. 实现显式状态工作流、审批请求、审计记录。
7. 迁移 RAG 文档处理和检索。
8. 增加 Vue Agent 状态、审批和运行追踪页面。
9. 增加 pytest、评测集和 Docker 配置。

## 当前首批落地范围

本批优先完成：

- Java 后端移动到 `legacy/java-server/`。
- Python 后端项目骨架。
- 核心数据库模型与 Alembic 初始迁移。
- Mock 模式下可启动的 FastAPI 应用。
- 兼容前端登录、商品、订单、会话、聊天的基础接口。
- Access JWT + Redis Refresh Token 轮换认证。
- Agent 工具元数据、规则路由和运行审计雏形。
- 管理员审批通过后的取消订单、退款/售后基础状态机。
- 文档上传、txt/md/markdown/csv/json/pdf/docx 解析、切片、Mock Embedding、Qdrant 入库和 SQL 兜底检索基础版。
- `scripts/e2e_rag_smoke.py` 已验证上传文档、处理入库、知识库检索和 Agent 聊天引用链路。
- Python 3.12 虚拟环境安装验证。
- Agent 路由、工具注册、安全 Token、输入防护、审批状态机、RAG 组件基础单元测试。

暂不把以下内容声称为完成：

- 完整 LangGraph Redis 持久化 interrupt/resume。
- 完整混合检索、RRF、重排序、Prompt Injection 检索安全评测。
- 完整审批后台 UI。
- 完整 LangGraph resume、20 条评测指标报告。
