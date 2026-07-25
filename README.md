# Smart Support System

智服通是一个面向电商售后场景的企业知识库客服与人工工单协同平台。

当前主版本定位为 **基于受控 LLM Workflow 的电商售后智能处置与人工协同平台**。它不是多 Agent 系统，也不允许大模型自由执行高风险操作；核心链路采用确定性业务工作流 + 受控 LLM 节点 + 三路混合检索 + Tool Use + Human-in-the-loop 审批。

## 与旧 Java 版本的区别

| 对比项 | 旧 Java 版本 | 当前 Python Agent 版本 |
| --- | --- | --- |
| 后端技术栈 | Java Spring Boot | Python FastAPI |
| 数据访问 | Java ORM / Mapper 风格 | SQLAlchemy 2.x Async ORM |
| 数据迁移 | Flyway SQL | Alembic |
| 智能客服 | 偏规则和普通知识库问答 | 受控 LLM Workflow + 工具调用 + RAG |
| 鉴权 | 演示/基础鉴权 | Access JWT + Redis Refresh Token |
| 知识库检索 | 基础关键词/向量思路 | 三路混合召回 + RRF + rerank |
| 高风险操作 | 普通业务接口 | 取消订单、退款必须进入管理员审批 |
| 审计能力 | 较弱 | Agent run、step、tool call、retrieval trace 全链路记录 |
| 部署入口 | Java server | `server/` Python 后端 + `web/` Vue 前端 |

旧 Java 代码目录：

```text
legacy/java-server/
```

这个目录不再作为当前系统的启动入口。面试或演示时应重点介绍当前 Python Agent 版本。

## 核心功能

- 用户端客服聊天：支持商品咨询、订单查询、物流查询、退款/退货/破损售后咨询。
- 商品与订单模拟：内置演示商品、下单、订单状态和物流信息。
- 后台管理端：商品、订单、物流、工单、知识库文档、模型参数、Agent 运行轨迹。
- 真实后端登录：用户端和管理端分角色登录。
- 高风险动作审批：退款、取消订单不会由模型直接改库，必须进入管理员审批。
- Agent 审计：记录每次 Agent 运行、执行步骤、工具调用、审批动作和检索轨迹。

## RAG 检索能力

当前版本实现的是：

**三路混合召回：关键词检索、Dense Vector 语义检索、结构化业务规则检索；使用 RRF 进行结果融合，并通过轻量级启发式重排提升结构化规则和关键词覆盖率高的结果。**

三路召回不是把 RRF、rerank 或 metadata filter 包装成检索通道，而是三条独立召回链路：

- 关键词检索：基于 MySQL 知识库 chunk 的关键词匹配，适合政策术语、商品名、时间限制等精确匹配。
- Dense Vector 检索：默认使用 `MockEmbeddingClient` + Qdrant 验证向量库链路；关闭 `EMBEDDING_MOCK_ENABLED` 并配置 OpenAI Compatible Embedding 后，才具备真实语义检索能力。
- 结构化规则检索：基于售后规则表，根据商品分类、订单状态、发货/签收状态、签收天数、售后类型、规则版本和有效期筛选规则。

结构化规则相关表：

```text
after_sale_rule
after_sale_rule_condition
after_sale_rule_version
```

检索轨迹表：

```text
agent_retrieval_trace
```

当通用文档和结构化业务规则冲突时，系统优先采用当前有效的结构化业务规则；如果冲突无法自动判断，应转人工处理。

## 受控 LLM Workflow 与可靠性边界

当前链路不是“让大模型自由决定并直接改库”，而是：

```text
输入安全检查
→ 上下文补全
→ LLM/规则结构化规划
→ 策略二次校验
→ 统一工具执行器 / RAG / 审批申请
→ 基于证据的回答生成
→ 输出安全检查
→ 审计收口
```

LLM 只用于结构化规划候选和基于证据的回答生成。身份权限、订单归属、订单状态机、退款/取消前置条件、幂等控制、管理员审批、库存回补、数据库写入和审计记录都由确定性代码控制。

统一工具执行器会读取 `TOOL_REGISTRY` 中的工具策略，执行角色校验、Pydantic 参数校验、超时、重试、参数脱敏、耗时统计和 `agent_tool_call` 审计记录。取消订单、退款等高风险工具只创建审批请求，不由模型直接修改订单状态。

Redis 当前真实承担三类能力：

- Refresh Token 保存与轮换。
- 聊天接口滑动窗口限流。
- RAG 检索结果短期缓存。

文档 Worker 使用任务表异步处理文档，通过条件更新把 `PENDING` 原子抢占为 `PROCESSING`，并支持 `retry_count`、`next_retry_at`、最大重试次数和 `DEAD_LETTER` 状态。

检索通道出现故障时，系统会记录日志并降级使用其他通道；失败通道、错误类型和降级原因会写入 `agent_retrieval_trace`，便于区分“没有资料”和“依赖服务故障”。

健康检查拆分为：

- `/api/v1/liveness`：进程存活。
- `/api/v1/readiness`：检查 MySQL、Redis、Qdrant 是否可用。

## 技术栈

后端：

- Python 3.12
- FastAPI
- SQLAlchemy 2.x Async ORM
- Alembic
- MySQL
- Redis
- Qdrant
- PyJWT
- LangGraph 响应安全守卫图

前端：

- Vue 3
- TypeScript
- Vite
- Element Plus
- Axios

测试与质量：

- pytest
- ruff
- mypy
- Vue TypeScript build

## 目录结构

```text
smart-customer-service/
├── server/                 # 当前 Python FastAPI 后端
│   ├── app/                # 后端业务代码
│   ├── alembic/            # 数据库迁移
│   ├── scripts/            # 演示数据和验证脚本
│   └── tests/              # 后端测试
├── web/                    # Vue 前端
├── deploy/                 # Docker Compose、Nginx、部署脚本
├── legacy/java-server/     # 旧 Java 后端，仅作迁移参考
├── docs/                   # 项目说明文档
└── .env.example            # 本地配置模板，不包含真实 API Key
```

## 本地启动

### 1. 准备环境

需要安装：

- Python 3.12.x
- Node.js 18+
- Docker Desktop

不建议使用 Python 3.14 作为本项目运行环境。

### 2. 安装后端依赖

```powershell
cd server
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
```

### 3. 启动基础服务

```powershell
cd ..
docker compose -f deploy/docker-compose.yml up -d mysql redis qdrant
```

### 4. 初始化数据库和演示数据

```powershell
cd server
.\.venv\Scripts\alembic.exe upgrade head
.\.venv\Scripts\python.exe scripts/seed_demo.py
```

### 5. 启动后端

```powershell
cd server
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 18080
```

### 6. 启动前端

```powershell
cd web
npm ci
npm run dev
```

访问地址：

- 前端：`http://127.0.0.1:5173`
- 后端健康检查：`http://127.0.0.1:18080/api/v1/health`

## 演示账号

```text
用户端：
账号：user
密码：123456

管理端：
账号：admin
密码：admin123
```

## API Key 说明

默认使用 Mock 模型，可不配置真实大模型 API Key：

```text
LLM_MOCK_ENABLED=true
EMBEDDING_MOCK_ENABLED=true
LLM_API_KEY=
EMBEDDING_API_KEY=
```

如需接入 DeepSeek 或其他 OpenAI Compatible 模型，只能把真实 Key 写入本地 `.env`：

```text
LLM_MOCK_ENABLED=false
LLM_API_KEY=
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL_NAME=deepseek-v4-flash
```

如需真实语义 Embedding：

```text
EMBEDDING_MOCK_ENABLED=false
EMBEDDING_API_KEY=
EMBEDDING_BASE_URL=https://api.openai.com
EMBEDDING_MODEL_NAME=text-embedding-3-small
EMBEDDING_DIMENSION=384
```

安全要求：

- 不要把真实 API Key 写入源码。
- 不要把真实 API Key 写入 README、测试文件、日志或压缩包。
- `.env` 必须保留在 `.gitignore` 中。
- 没有真实 Key 时，使用 Mock 模型完成编译、测试和演示。

## 常用验证命令

后端：

```powershell
cd server
.\.venv\Scripts\python.exe -m compileall app scripts alembic tests
.\.venv\Scripts\python.exe -m ruff check .
.\.venv\Scripts\python.exe -m mypy app tests scripts
.\.venv\Scripts\python.exe -m pytest
```

前端：

```powershell
cd web
npm run build
```

Docker Compose 配置：

```powershell
docker compose -f deploy/docker-compose.yml config --quiet
```

敏感信息扫描：提交或打包前需要检查源码、文档、日志和测试文件中是否出现真实 API Key；不要扫描 `.venv`、`node_modules`、`dist`、`.git` 等生成目录。

## 当前已验证

- 后端 lint 通过。
- mypy 类型检查通过。
- 后端 pytest 通过。
- 前端构建通过。
- Docker Compose 配置通过。
- 敏感信息扫描未发现 API Key。
- 本地接口验证通过：
  - “收到破损商品怎么办？”会按当前有效售后规则回答。
  - “这个拆封后还能退吗？”会按结构化售后规则和知识库资料回答。
  - “查询所有订单”会查询用户真实订单列表。

## 适合面试讲解的亮点

- 从旧 Java 项目迁移为 Python Agent 后端，体现重构和架构演进能力。
- 把普通客服问答升级为受控 LLM Workflow：结构化规划、工具调用、风险控制、审批流、审计记录。
- RAG 不只是向量库查询，而是关键词、Dense Vector、结构化规则三路独立召回。
- 高风险业务动作采用“Workflow 创建申请，管理员审批执行”的安全闭环，模型不能直接改库。
- 登录鉴权使用 Access JWT + Redis Refresh Token，而不是前端演示登录。
- 每次 Agent 运行都有可追踪记录，便于排查回答依据和工具调用过程。
