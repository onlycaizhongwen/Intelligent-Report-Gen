<!-- skill: S24 -->
<skill id="S24" name="dockerize">

# 技能：容器化

## Meta
- DependsOn: S14, S16, S6, S7
- Category: devops
- Status: stable

## 一句话描述
生成 Docker 容器化配置。

## 输入
- `backend/`：DDD 后端代码目录
- `pages/`（前端页面代码）
- `docs/skill-chain/tech_stack.md`：技术栈选型文档
- `docs/skill-chain/module_design.md`：服务边界与模块设计文档
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `Dockerfile`（多阶段构建配置）
- `docker-compose.yml`（本地开发编排）

## Prompt

你是一位资深 DevOps 工程师。
请根据项目结构，生成容器化配置。

生成前必须读取 `docs/skill-chain/tech_stack.md` 和 `docs/skill-chain/module_design.md`：
- 容器服务必须与 S7 的部署单元一致，不得凭空新增或遗漏服务。
- 如 S6/S7 选择 Higress，生成 Higress 网关服务；如 S6/S7 选择 Go Gin 自研轻量网关，则生成 Go Gin 网关服务。
- Java、Python、前端、网关、数据库、缓存、向量库等服务名必须与模块设计中的服务边界一致。
- 容器服务、端口、健康检查和网关路由必须能追溯 OpenSpec capability / scenario。
- 必须按 S6 技术栈生成；前端框架、语言版本、数据库、检索、向量库、缓存、消息队列、对象存储和网关必须逐项对齐 S6/S7。
- 必须承接 S6 的生产环境 / 本地开发环境分层决策；本地 Docker 编排不得反向覆盖 S6 生产选型，也不得把生产 K8s/云服务配置当作本地默认。
- 本地 vLLM/GPU 仅作为可选适配；未在 S6/S7 明确启用本地推理或 GPU 资源时，不得在 Docker Compose 中强制加入 GPU 资源要求。

---

## 本地开发环境支持（强制）

1. **必须提供 `docker-compose.yml`**：
   - 定义所有本地依赖（DB / Redis / MQ / Vector DB）。
   - 使用 `healthcheck` 确保依赖启动顺序。
   - 端口映射必须避开常用宿主机端口；如 `3306`、`5432`、`6379`、`9200`、`19530` 等端口冲突，必须自动偏移或给出偏移端口方案。

2. **Profile 支持**：
   - Dockerfile 必须支持 `SPRING_PROFILES_ACTIVE=dev`。
   - 本地启动时应自动加载 dev profile，使用容器内依赖。

3. **资源限制**：
   - `docker-compose.yml` 中必须设置 `mem_limit: 2g`（或更低），防止吃掉宿主机资源。
   - 禁止默认开启 Swarm / K8s 配置。

4. **依赖自检**：
   - 若本地未安装 Docker，需提示用户安装 Docker Desktop 或 Colima。
   - 禁止假设宿主机已安装 Java / Python / Node 运行时（应通过容器解决）。

---

### 多服务 Docker Compose（混合选型项目）

```yaml
# docker-compose.yml
version: '3.8'

services:
  # ===== Java 业务服务（Spring Boot / Spring Cloud Alibaba） =====
  java-service:
    build:
      context: .
      dockerfile: Dockerfile.java
    ports:
      - "18080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - DB_HOST=database
      - REDIS_HOST=redis
      - VECTOR_DB_HOST=vector-db
      - SEARCH_HOST=search-engine
      - MQ_HOST=message-queue
      - AI_SERVICE_URL=http://python-ai:8000
    depends_on:
      database:
        condition: service_healthy
      redis:
        condition: service_healthy
      vector-db:
        condition: service_healthy
      search-engine:
        condition: service_healthy
      message-queue:
        condition: service_healthy
    mem_limit: 1g
    volumes:
      - ./logs:/app/logs
    networks:
      - app-network

  # ===== Python AI 服务（FastAPI + LlamaIndex） =====
  python-ai:
    build:
      context: .
      dockerfile: Dockerfile.python
    ports:
      - "8000:8000"
    environment:
      - LLM_API_KEY=${LLM_API_KEY}
      - LLM_BASE_URL=${LLM_BASE_URL}
      - VECTOR_DB_HOST=vector-db
      - SEARCH_HOST=search-engine
      - MQ_URL=${MQ_URL}
      - EMBEDDING_MODEL=${EMBEDDING_MODEL}
    depends_on:
      vector-db:
        condition: service_healthy
      search-engine:
        condition: service_healthy
      redis:
        condition: service_healthy
      message-queue:
        condition: service_healthy
    mem_limit: 1g
    volumes:
      - ./uploads:/app/uploads
      - ./logs:/app/logs
    networks:
      - app-network

  # ===== 网关（仅当 S6/S7 选择 Higress 时生成） =====
  gateway:
    image: <gateway-image>:<gateway-version>
    ports:
      - "18000:80"
      - "18443:443"
      - "15020:15020"   # 健康检查 / 指标端口，按实际 Higress 版本校准
    environment:
      - JAVA_SERVICE_URL=http://java-service:8080
      - AI_SERVICE_URL=http://python-ai:8000
      - NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-nacos:8848}
      - ENABLE_AI_GATEWAY=true
    depends_on:
      java-service:
        condition: service_healthy
      python-ai:
        condition: service_healthy
    mem_limit: 1g
    networks:
      - app-network

  # ===== 前端（按 S6 技术栈：Vue 3 / React / Next.js 等） =====
  frontend:
    build:
      context: .
      dockerfile: Dockerfile.frontend
    ports:
      - "3000:80"
    environment:
      - VITE_API_BASE_URL=http://localhost:9090
    networks:
      - app-network
    mem_limit: 512m

  # ===== 数据库（按 S6 技术栈生成对应服务） =====
  database:
    image: <database-image>:<database-version>
    ports:
      - "15432:5432"
    environment:
      - DB_NAME=${DB_NAME}
      - DB_USERNAME=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
    volumes:
      - database_data:/var/lib/database
      - ./migrations:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "<database-healthcheck-command>"]
      interval: 10s
      timeout: 5s
      retries: 10
    mem_limit: 1g
    networks:
      - app-network

  # ===== Redis =====
  redis:
    image: redis:7-alpine
    ports:
      - "16379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
    mem_limit: 512m
    networks:
      - app-network

  # ===== 消息队列（仅当 S6/OpenSpec 选择异步队列时生成） =====
  message-queue:
    image: <message-queue-image>:<message-queue-version>
    ports:
      - "15672:5672"
      - "15673:15672"
    volumes:
      - message_queue_data:/var/lib/message-queue
    healthcheck:
      test: ["CMD-SHELL", "<message-queue-healthcheck-command>"]
      interval: 10s
      timeout: 5s
      retries: 10
    mem_limit: 1g
    networks:
      - app-network

  # ===== 全文检索（仅当 S6/OpenSpec 选择全文检索时生成） =====
  search-engine:
    image: <search-engine-image>:<search-engine-version>
    environment:
      - discovery.type=single-node
      - plugins.security.disabled=true
      - SEARCH_INITIAL_ADMIN_PASSWORD=${SEARCH_INITIAL_ADMIN_PASSWORD}
    ports:
      - "19200:9200"
    volumes:
      - search_data:/var/lib/search
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9200 >/dev/null"]
      interval: 20s
      timeout: 10s
      retries: 10
    mem_limit: 2g
    networks:
      - app-network

  # ===== 向量数据库（仅当 S6/OpenSpec 选择向量检索时生成） =====
  vector-db:
    image: <vector-db-image>:<vector-db-version>
    ports:
      - "19531:19530"
      - "19091:9091"
    environment:
      - VECTOR_DB_DEPENDENCY_ENDPOINTS=${VECTOR_DB_DEPENDENCY_ENDPOINTS}
    depends_on:
      etcd:
        condition: service_healthy
      object-storage:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "<vector-db-healthcheck-command>"]
      interval: 20s
      timeout: 10s
      retries: 10
    mem_limit: 2g
    volumes:
      - milvus_data:/var/lib/milvus
    networks:
      - app-network

  etcd:
    image: quay.io/coreos/etcd:v3.5.0
    ports:
      - "12379:2379"
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 10s
      timeout: 5s
      retries: 10
    mem_limit: 512m
    networks:
      - app-network

  object-storage:
    image: <object-storage-image>:<object-storage-version>
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      - STORAGE_ROOT_USER=${STORAGE_ROOT_USER}
      - STORAGE_ROOT_PASSWORD=${STORAGE_ROOT_PASSWORD}
    volumes:
      - object_storage_data:/data
    healthcheck:
      test: ["CMD-SHELL", "<object-storage-healthcheck-command>"]
      interval: 10s
      timeout: 5s
      retries: 10
    mem_limit: 1g
    networks:
      - app-network

volumes:
  database_data:
  redis_data:
  message_queue_data:
  search_data:
  object_storage_data:
  milvus_data:

networks:
  app-network:
    driver: bridge
```

---

### Java Dockerfile

```dockerfile
# Dockerfile.java
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Python Dockerfile

```dockerfile
# Dockerfile.python
FROM python:3.11-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

FROM python:3.11-slim
WORKDIR /app
COPY --from=builder /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
COPY . .
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1
ENTRYPOINT ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]
```

### Go Dockerfile（可选）

仅当 `docs/skill-chain/tech_stack.md` 明确选择 Go Gin 自研轻量网关时，才生成 Go 网关 Dockerfile；如 S6/S7 选择 Higress，则生成 Higress 网关配置。

```dockerfile
# Dockerfile.go
FROM golang:1.21-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o gateway ./cmd/gateway

FROM alpine:3.20
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /app/gateway .
EXPOSE 9090
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:9090/health || exit 1
ENTRYPOINT ["./gateway"]
```

### 前端 Dockerfile

```dockerfile
# Dockerfile.frontend
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
```

---

### 本地开发 Docker Compose（开发模式，热重载）

```yaml
# docker-compose.dev.yml
version: '3.8'
services:
  java-service:
    build:
      context: .
      dockerfile: Dockerfile.java.dev
    volumes:
      - ./src:/app/src
      - ./target:/app/target
    command: mvn spring-boot:run -Dspring-boot.run.fork=false

  python-ai:
    build:
      context: .
      dockerfile: Dockerfile.python.dev
    volumes:
      - ./:/app
    command: uvicorn main:app --host 0.0.0.0 --port 8000 --reload

  frontend:
    build:
      context: .
      dockerfile: Dockerfile.frontend.dev
    ports:
      - "5173:5173"
    volumes:
      - ./src:/app/src
      - ./vite.config.ts:/app/vite.config.ts
    command: npm run dev -- --host 0.0.0.0
```

## 行为规则

- ✅ 必须包含多阶段构建（减小镜像体积）
- ✅ 必须包含 HEALTHCHECK（健康检查）
- ✅ 敏感信息使用环境变量（不得硬编码）
- ✅ AI 项目只有在 S6/S7 明确选择本地推理或 vLLM 时才加入 GPU 支持
- ✅ 必须包含 .dockerignore
- ✅ 必须遵循 S6 技术栈；数据库、队列、检索、对象存储、语言版本和网关均不得写死为固定模板
- ✅ 必须承接 S6 的生产环境 / 本地开发环境分层决策，并为本地开发生成可一键启动的 `docker-compose.yml`
- ✅ `docker-compose.yml` 必须覆盖本地依赖（DB / Redis / MQ / Vector DB）、健康检查、启动顺序、端口冲突偏移方案和 `mem_limit: 2g` 或更低资源限制
- ✅ Dockerfile / Compose 必须支持 dev profile（如 Spring Boot 的 `SPRING_PROFILES_ACTIVE=dev`），并使用容器内依赖
- ✅ 必须包含 Docker 依赖自检说明；不得假设宿主机已安装 Java / Python / Node 运行时
- ✅ 如 S6/S7 选择 Higress，Docker Compose 必须包含 Higress 网关服务，并体现 K8s 入口网关、微服务网关、AI 网关三合一职责
- ✅ Go Gin 网关只能在 S6/S7 明确选择自研轻量代理时生成
- ✅ 容器服务和网关路由必须追溯 OpenSpec capability / scenario
- ❌ 不得将代码 COPY 到生产镜像（先 build）
- ❌ 不得使用 latest 标签（指定具体版本）
- ❌ 不得暴露不必要的端口
- ❌ 本地开发不得默认开启 Swarm / K8s 配置
- ❌ 不得部署 OpenSpec baseline 未定义的业务服务

## 使用示例

```
加载 <skill id="S24">，输入：backend/、pages/、docs/skill-chain/tech_stack.md、docs/skill-chain/module_design.md、openspec/specs/
请根据 S6 技术栈和 S7 服务边界生成 Dockerfile 和 docker-compose.yml。
```
</skill>
<!-- end -->
