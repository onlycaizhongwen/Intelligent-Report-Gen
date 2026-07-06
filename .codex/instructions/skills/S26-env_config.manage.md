<!-- skill: S26 -->
<skill id="S26" name="env_config.manage">

# 技能：环境配置管理

## Meta
- DependsOn: S24
- Category: devops
- Status: stable

## 一句话描述
管理不同环境的配置和环境变量。

## 输入
- `Dockerfile`（容器化配置）
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `.env.example`（环境变量示例文件）
- `config/` 目录（配置文件模板）

## Prompt

你是一位资深 DevOps 工程师。
请生成环境配置管理方案。

配置项必须追溯 OpenSpec：
- 业务、AI、安全、网关和可观测配置必须能对应 OpenSpec requirement / scenario。
- 未被 OpenSpec 或技术栈选型使用的配置项不得默认加入 `.env.example`。
- 必须读取 S6 技术栈；数据库、消息队列、检索、对象存储、网关和模型供应商必须与 S6 选型逐项对齐。
- 未被 S6/S7/OpenSpec 明确选择的本地推理、GPU、数据库、消息队列、检索或网关配置，不得进入默认 `.env.example`。
- 必须承接 S6 的生产环境 / 本地开发环境分层决策；dev 配置默认连接 Docker Compose 容器内依赖，prod 配置必须连接生产受控资源。
- 必须承接 S24 的本地端口偏移与健康检查策略；`.env.example` 不得假设宿主机常用端口一定可用。

---

## 环境隔离规则（强制）

1. **dev / prod 强隔离**：
   - `application-dev.yml` 只能使用本地容器、内存组件或测试资源。
   - `application-prod.yml` 只能使用生产环境变量、Secret 或托管服务地址。
   - 禁止本地开发配置连接生产数据库、生产 MQ、生产对象存储或生产模型密钥。

2. **Docker Compose 本地默认**：
   - dev profile 中的主机名优先使用 Compose service name，例如 `database`、`redis`、`message-queue`、`vector-db`、`object-storage`、`gateway`。
   - `.env.example` 如写宿主机访问端口，必须说明该端口来自 S24 `docker-compose.yml` 的映射，端口冲突时按 S24 偏移方案调整。
   - 本地依赖账号、bucket、topic、索引名必须是开发隔离命名，不得复用生产命名空间。

3. **运行时来源**：
   - 禁止假设宿主机安装 Java / Python / Node；本地运行优先通过容器注入配置。
   - 敏感值只允许通过 `.env`、Secret 或 CI/CD Secret 注入，`.env.example` 只能给占位符。

---

### 环境变量分类

#### 基础服务

```bash
# ===== 应用基础 =====
APP_NAME=FullstackApp
APP_ENV=development          # development / staging / production
APP_PORT=8080
APP_DEBUG=true

# ===== 数据库（按 S6 技术栈选择 PostgreSQL / MySQL / 其他） =====
DB_TYPE=<database_type>
DB_HOST=database              # dev: Compose service name；宿主机访问端口按 S24 docker-compose.yml 映射
DB_PORT=<container_database_port>
DB_NAME=<database_name>
DB_USERNAME=<database_user>
DB_PASSWORD=<database_password>

# ===== Redis =====
REDIS_HOST=redis              # dev: Compose service name
REDIS_PORT=6379               # 容器内端口；宿主机端口冲突时按 S24 偏移
REDIS_PASSWORD=
REDIS_DB=0

# ===== 向量数据库（仅当 S6/OpenSpec 选择向量检索时生成） =====
VECTOR_DB_TYPE=<vector_db_type>
VECTOR_DB_HOST=vector-db      # dev: Compose service name
VECTOR_DB_PORT=<container_vector_db_port>
VECTOR_DB_USERNAME=<vector_db_user>
VECTOR_DB_PASSWORD=<vector_db_password>

# ===== 消息队列（仅当 S6/OpenSpec 选择异步队列时生成） =====
MQ_TYPE=<message_queue_type>
MQ_URL=<message_queue_url>

# ===== 全文检索（仅当 S6/OpenSpec 选择全文检索时生成） =====
SEARCH_TYPE=<search_engine_type>
SEARCH_HOST=search-engine     # dev: Compose service name
SEARCH_PORT=<container_search_engine_port>
SEARCH_USERNAME=
SEARCH_PASSWORD=

# ===== 对象存储（按 S6 技术栈选择 MinIO / S3 / OSS / 其他） =====
STORAGE_TYPE=<object_storage_type>
STORAGE_ENDPOINT=<object_storage_endpoint>
STORAGE_ACCESS_KEY=<object_storage_access_key>
STORAGE_SECRET_KEY=<object_storage_secret_key>
STORAGE_BUCKET=<object_storage_bucket>
```

#### AI 服务配置

```bash
# ===== LLM 配置（按 S6 选择模型供应商和网关） =====
LLM_PROVIDER=<llm_provider>
LLM_API_KEY=<llm_api_key>       # 仅网关/服务端环境变量，不下发前端
LLM_MODEL=<llm_model>
LLM_BASE_URL=<llm_base_url>

# ===== 本地模型（仅当 S6/S7 明确选择本地推理时生成） =====
# LOCAL_LLM_URL=<local_llm_url>
# LOCAL_LLM_MODEL=<local_llm_model>

# ===== Embedding 配置 =====
EMBEDDING_MODEL=bge-m3        # bge-m3 / bert-base-chinese / text-embedding-3-large
EMBEDDING_DIMENSION=1024      # bge-m3=1024, bert=768, openai=1536
EMBEDDING_BATCH_SIZE=32

# ===== 向量检索配置 =====
VECTOR_TOP_K=5                 # 默认检索 Top-K
VECTOR_SCORE_THRESHOLD=0.7     # 相似度阈值

# ===== RAG 配置 =====
RAG_CHUNK_SIZE=500             # 文档切片大小（字符）
RAG_CHUNK_OVERLAP=50           # 切片重叠大小
RAG_MAX_CONTEXT_LENGTH=4000     # 最大上下文长度

# ===== Token 计费 =====
TOKEN_BUDGET_PER_USER=10000    # 每用户每日 Token 预算
TOKEN_COST_PER_1K=0.03        # 每 1K Token 成本（美元）

# ===== Prompt 安全 =====
PROMPT_MAX_LENGTH=2000         # 用户 Prompt 最大长度
ENABLE_PROMPT_INJECTION_CHECK=true
BLOCKED_PATTERNS_FILE=config/blocked_patterns.txt
```

#### Java 专属配置

```bash
# ===== Java / Spring Boot =====
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
JAVA_OPTS=-Xmx2g -Xms512m

# Spring Cloud Alibaba
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=dev
SENTINEL_DASHBOARD=localhost:8080
SEATA_SERVER_ADDR=localhost:8091

# JPA / MyBatis
SPRING_JPA_HIBERNATE_DDL_AUTO=update   # 开发环境
SPRING_JPA_SHOW_SQL=true
```

#### Python 专属配置

```bash
# ===== Python / FastAPI =====
PYTHONPATH=/app/src
UVICORN_WORKERS=4
UVICORN_TIMEOUT=120

# LlamaIndex
LLAMA_INDEX_CHUNK_SIZE=512
LLAMA_INDEX_CHUNK_OVERLAP=50

# LlamaIndex / RAG
LLAMA_INDEX_CALLBACK_ENABLED=true
```

#### Go 专属配置

```bash
# ===== Go / Gin（可选：仅自研轻量网关时使用） =====
GIN_MODE=debug                 # debug / release
GATEWAY_PORT=9090
GATEWAY_TIMEOUT=30

# 上游服务地址
USER_SERVICE_URL=http://localhost:8080
AI_SERVICE_URL=http://localhost:8000

# 限流
RATE_LIMIT_REQUESTS=100
RATE_LIMIT_PERIOD=1m
```

#### 网关配置

```bash
# ===== API Gateway / AI Gateway（按 S6/S7 网关选型生成） =====
GATEWAY_TYPE=<gateway_type>
GATEWAY_ENABLED=true
GATEWAY_HTTP_PORT=80
GATEWAY_HTTPS_PORT=443
GATEWAY_METRICS_PORT=<gateway_metrics_port>

# 微服务发现
SERVICE_DISCOVERY_TYPE=<service_discovery_type>
NACOS_SERVER_ADDR=
EUREKA_SERVER_URL=
CONSUL_ADDR=

# AI Gateway（仅当 S6/S7 选择 AI Gateway 能力时生成）
AI_GATEWAY_ENABLED=<true_or_false>
AI_GATEWAY_LLM_FALLBACK_ENABLED=<true_or_false>
AI_GATEWAY_TOKEN_RATE_LIMIT_ENABLED=<true_or_false>
AI_GATEWAY_PROMPT_SECURITY_ENABLED=<true_or_false>
AI_GATEWAY_SEMANTIC_CACHE_ENABLED=<true_or_false>
AI_GATEWAY_MCP_PROXY_ENABLED=<true_or_false>

# 安全与可观测（按网关选型生成）
GATEWAY_WAF_ENABLED=<true_or_false>
GATEWAY_JWT_OIDC_ENABLED=<true_or_false>
GATEWAY_PROMETHEUS_ENABLED=<true_or_false>
GATEWAY_TRACING_ENABLED=<true_or_false>
```

---

### `.env.example` 文件生成

```bash
# .env.example - 环境变量示例（不可包含真实密钥）
# 复制为 .env 并填入实际值

# ===== 基础 =====
APP_ENV=development
APP_PORT=8080

# ===== 数据库（按 S6 技术栈生成） =====
DB_TYPE=<database_type>
DB_HOST=database
DB_PORT=<container_database_port>
DB_NAME=<database_name>
DB_USERNAME=<database_user>
# DB_PASSWORD=your_password   # 不要写在 example 中

# ===== Redis =====
REDIS_HOST=redis
REDIS_PORT=6379

# ===== AI =====
# LLM_API_KEY=your_api_key   # 不要写在 example 中
LLM_PROVIDER=<llm_provider>
EMBEDDING_MODEL=<embedding_model>

# ===== 向量数据库（仅当 S6/OpenSpec 选择向量检索时生成） =====
VECTOR_DB_TYPE=<vector_db_type>
VECTOR_DB_HOST=vector-db
VECTOR_DB_PORT=<container_vector_db_port>
```

---

### 多环境配置

```
config/
├── application-dev.yml       # 开发环境
├── application-staging.yml   # 预发布环境
├── application-prod.yml     # 生产环境
├── prompt_templates.yml     # Prompt 模板
└── blocked_patterns.txt    # Prompt 注入黑名单
```

`application-dev.yml` 示例：
```yaml
spring:
  datasource:
    url: ${DB_JDBC_URL:jdbc:postgresql://database:5432/app_dev}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  profiles:
    active: ${APP_ENV:dev}

logging:
  level:
    root: INFO
    com.example: DEBUG

app:
  ai:
    embedding-model: ${EMBEDDING_MODEL:bge-m3}
    chunk-size: ${RAG_CHUNK_SIZE:500}
    top-k: ${VECTOR_TOP_K:5}
```

`application-prod.yml` 必须使用生产 Secret / 环境变量，不得设置本地默认值：
```yaml
spring:
  datasource:
    url: ${DB_JDBC_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

logging:
  level:
    root: INFO
```

---

### Prompt 注入黑名单

```
# config/blocked_patterns.txt
ignore all previous instructions
you are now a
act as a
pretend you are
forget your instructions
```

## 行为规则

- ✅ `.env.example` 不得包含真实密钥
- ✅ 生产环境必须禁用 DEBUG 模式
- ✅ AI 配置必须包含 Token 预算限制
- ✅ 如技术栈选择具体网关，必须提供入口端口、服务发现、AI Gateway、安全与可观测配置项
- ✅ 默认配置必须使用 S6 选定的数据库、消息队列、检索、对象存储、模型和网关技术
- ✅ dev 配置必须默认连接 Docker Compose 容器内依赖，prod 配置必须通过 Secret / 环境变量连接生产受控资源
- ✅ `.env.example` 中的本地端口必须与 S24 `docker-compose.yml` 端口映射一致，并允许端口冲突时偏移
- ✅ 配置项必须追溯 OpenSpec requirement / scenario 或技术栈基础设施需求
- ✅ 必须提供多环境配置（dev / staging / prod）
- ✅ 敏感配置使用环境变量，不写死在代码中
- ❌ 不得新增 OpenSpec 未定义且未被技术栈使用的业务配置
- ❌ 不得将 `.env` 提交到 Git（已在 `.gitignore` 中）
- ❌ 不得在配置文件中硬编码密钥
- ❌ 不得在生产环境使用 `ddl-auto: update`
- ❌ 不得在 `application-dev.yml`、`.env.example` 或本地 profile 中连接生产数据库、生产 MQ、生产对象存储或生产模型密钥
- ❌ 不得默认启用未被 S6/S7/OpenSpec 选择的本地推理、GPU、数据库、消息队列、检索或网关配置

## 使用示例

```
加载 <skill id="S26">，输入：Dockerfile、openspec/specs/
请生成环境变量配置，包含 AI 服务相关配置项。
```
</skill>
<!-- end -->
