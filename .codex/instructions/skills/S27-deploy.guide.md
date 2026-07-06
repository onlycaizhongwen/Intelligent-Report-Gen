<!-- skill: S27 -->
<skill id="S27" name="deploy.guide">

# 技能：部署指南

## Meta
- DependsOn: S25, S26
- Category: devops
- Status: stable

## 一句话描述
生成完整的部署文档。

## 输入
- `.github/workflows/`（CI/CD 配置）
- `.env.example`（环境配置）
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `docs/skill-chain/DEPLOY.md`：部署指南文档（中文 Markdown）

## Prompt

你是一位资深 DevOps 工程师。
请生成完整的部署指南文档。

部署指南必须追溯 OpenSpec：
- 每个外部入口、服务、初始化步骤、健康检查和回滚验证必须关联 OpenSpec capability / scenario 或基础设施需求。
- 如果部署拓扑、网关路由或配置项偏离 S7 冻结后的 OpenSpec baseline，必须输出冲突报告。
- 必须读取 S6 技术栈和 scope guardrails；未在 S6/S7 明确要求时，不得引入高可用、灾备、私有化部署或本地 GPU/vLLM。
- 数据库、消息队列、检索、对象存储和网关必须读取 S6 技术栈；若 S6 选择 PostgreSQL 16，则不得生成 MySQL 作为主数据库。
- 必须承接 S05/S06/S24 的本地开发环境决策：本地开发优先 Docker Compose 最小集，复用已有 Docker 依赖，不能复用时再按最小资源部署。
- 部署文档必须明确生产环境与本地开发环境差异；本地命令不得默认连接生产数据库、生产 MQ、生产对象存储或生产模型密钥。

---

## 本地开发部署约束（强制）

- 本地开发必须优先使用 `docker-compose.yml` 一键启动；不得要求开发者手动安装 Java / Python / Node 或中间件运行时。
- 启动前必须提供 Docker 自检命令；若未安装 Docker，提示安装 Docker Desktop 或 Colima。
- 本地依赖端口必须以 S24 `docker-compose.yml` 为准；如 `3306`、`5432`、`6379`、`9200`、`19530` 等常用端口冲突，部署文档必须说明偏移端口。
- 本地资源必须保持最小化：单容器 `mem_limit` 不超过 `2g`；未在 S6/S7 明确选择时，不得启动 GPU、本地 vLLM、多节点集群、Swarm 或 K8s。
- 如本机 Docker 已有可复用依赖，部署指南必须说明优先复用策略；不能复用时再使用最小 Compose 服务补齐。
- 外部业务入口必须经网关进入业务核心服务；Python AI 服务仅作为内部服务或受控路由，不得在公开网关中直连暴露。

---

### 文档结构

```markdown
# 部署指南

## 一、环境准备

### 1.1 服务器要求

| 环境 | CPU | 内存 | 磁盘 | 说明 |
|------|-----|------|------|------|
| 开发环境 | 4 核 | 8GB | 50GB | 本地 Docker |
| 预发布 | 8 核 | 16GB | 100GB | 云端 K8s |
| 生产候选环境 | 8 核+ | 16GB+ | 200GB+ | K8s/Helm 预留；HA/DR/GPU 仅在 S6/S7 明确要求时生成 |

### 1.2 软件依赖

| 软件 | 版本 | 用途 |
|------|------|------|
| Docker | 24.0+ | 容器运行 |
| Docker Compose | 2.20+ | 本地编排 |
| Kubernetes | 1.28+ | 生产编排 |
| 网关 | 按 S6/S7 选型锁定 | K8s 入口、微服务网关、AI Gateway 或反向代理能力，按实际技术栈生成 |
| Certbot | latest | SSL 证书 |

### 1.3 AI 项目额外要求

| 软件 | 版本 | 用途 |
|------|------|------|
| GPU 驱动 / CUDA | 按 S6/S7 选型锁定 | 仅本地推理或 GPU 资源被明确选择时生成 |
| 向量数据库 | 按 S6 选型锁定 | 仅向量检索能力被明确选择时生成 |
| 本地推理服务 | 按 S6/S7 选型锁定 | 仅本地模型推理被明确选择时生成 |

---

## 二、部署方式

### 方式一：Docker Compose（开发 / 小规模）

```bash
# 0. 检查 Docker
docker version
docker compose version

# 1. 克隆代码
git clone https://github.com/your-org/your-repo.git
cd your-repo

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env，填入真实密钥

# 3. 启动服务
docker compose -f docker-compose.yml up -d

# 4. 查看服务状态
docker compose ps

# 5. 等待依赖健康（服务名以 S24 docker-compose.yml 为准）
docker compose ps --format json

# 6. 查看日志
docker compose logs -f java-service
```

> 如果本地端口已被占用，必须按 S24 生成的端口偏移方案访问，例如数据库宿主机端口可从 `5432` 偏移到 `15432`，Redis 可从 `6379` 偏移到 `16379`。容器内部仍使用 Compose service name 通信。

### 方式二：Kubernetes（生产环境）

```bash
# 1. 创建命名空间
kubectl create namespace fullstack-app

# 2. 创建密钥（从 .env 生成）
kubectl create secret generic app-secrets \
  --from-env-file=.env \
  -n fullstack-app

# 3. 部署基础服务
kubectl apply -f k8s/base/postgres.yaml -n fullstack-app
kubectl apply -f k8s/base/redis.yaml -n fullstack-app
kubectl apply -f k8s/base/rabbitmq.yaml -n fullstack-app
kubectl apply -f k8s/base/opensearch.yaml -n fullstack-app
kubectl apply -f k8s/base/milvus.yaml -n fullstack-app

# 4. 部署应用服务
kubectl apply -f k8s/app/java-service.yaml -n fullstack-app
kubectl apply -f k8s/app/python-ai.yaml -n fullstack-app
kubectl apply -f k8s/app/frontend.yaml -n fullstack-app

# 5. 部署网关配置（按 S6/S7 网关选型）
kubectl apply -f k8s/gateway/<selected-gateway>.yaml -n <namespace>
kubectl apply -f k8s/gateway/<selected-gateway-routes>.yaml -n <namespace>

# 6. 查看部署状态
kubectl get pods -n fullstack-app
kubectl get svc -n fullstack-app
```

---

## 三、Kubernetes 部署清单

### PostgreSQL

```yaml
# k8s/base/postgres.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 20Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
spec:
  replicas: 1
  selector:
    matchLabels:
      app: database
  template:
    metadata:
      labels:
        app: database
    spec:
      containers:
        - name: database
          image: <database-image>:<database-version>
          envFrom:
            - secretRef:
                name: app-secrets
          ports:
            - containerPort: <database-port>
          volumeMounts:
            - name: postgres-storage
              mountPath: /var/lib/postgresql/data
      volumes:
        - name: postgres-storage
          persistentVolumeClaim:
            claimName: postgres-pvc
```

### Java 服务

```yaml
# k8s/app/java-service.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: java-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: java-service
  template:
    metadata:
      labels:
        app: java-service
    spec:
      containers:
        - name: java-service
          image: ghcr.io/your-org/your-repo/java-service:<version-or-sha>
          envFrom:
            - secretRef:
                name: app-secrets
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: 500m
              memory: 1Gi
            limits:
              cpu: 2
              memory: 2Gi
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: java-service
spec:
  selector:
    app: java-service
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

### Python AI 服务

```yaml
# k8s/app/python-ai.yaml
# 内部服务：不得通过公开网关直连暴露；外部 AI 请求必须先进入 Java 业务核心。
apiVersion: apps/v1
kind: Deployment
metadata:
  name: python-ai
spec:
  replicas: 1
  selector:
    matchLabels:
      app: python-ai
  template:
    metadata:
      labels:
        app: python-ai
    spec:
      containers:
        - name: python-ai
          image: ghcr.io/your-org/your-repo/python-ai:<version-or-sha>
          envFrom:
            - secretRef:
                name: app-secrets
          ports:
            - containerPort: 8000
          resources:
            requests:
              cpu: 1
              memory: 2Gi
            limits:
              cpu: 4
              memory: 8Gi
          livenessProbe:
            httpGet:
              path: /health
              port: 8000
            initialDelaySeconds: 60
            periodSeconds: 30
```

### 网关配置（按 S6/S7 选型生成）

```yaml
# k8s/gateway/<selected-gateway>.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: <selected-gateway>
  annotations:
    gateway.example.com/enable-ai-gateway: "<true_or_false>"
    gateway.example.com/enable-prometheus: "<true_or_false>"
spec:
  ingressClassName: <ingress-class-name>
  rules:
    - host: api.yourdomain.com
      http:
        paths:
          - path: /api/
            pathType: Prefix
            backend:
              service:
                name: java-service
                port:
                  number: 8080
          # Python AI 服务不得作为公开业务入口直连暴露；
          # 如需 AI 能力，由 Java 业务核心鉴权、创建任务和写审计后内部调用。
```

### 网关路由与 AI Gateway 配置（按 S6/S7 选型生成）

```yaml
# k8s/gateway/<selected-gateway-routes>.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-gateway-routes
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    gateway.example.com/ai-fallback: "<true_or_false>"
    gateway.example.com/token-rate-limit: "<true_or_false>"
    gateway.example.com/prompt-security: "<true_or_false>"
    gateway.example.com/semantic-cache: "<true_or_false>"
    gateway.example.com/mcp-server-proxy: "<true_or_false>"
spec:
  ingressClassName: <ingress-class-name>
  tls:
    - hosts:
        - api.yourdomain.com
      secretName: api-tls
  rules:
    - host: api.yourdomain.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: java-service
                port:
                  number: 8080
```

---

## 四、初始化操作

```bash
# 1. 执行数据库迁移
kubectl exec -it $(kubectl get pod -n fullstack-app -l app=java-service -o jsonpath='{.items[0].metadata.name}') \
  -n fullstack-app -- java -jar app.jar --spring.sql.init.mode=always

# 2. 导入种子数据
kubectl exec -it $(kubectl get pod -n fullstack-app -l app=postgres -o jsonpath='{.items[0].metadata.name}') \
  -n fullstack-app -- psql -U app_user -d report_db -f /docker-entrypoint-initdb.d/V001__init.sql

# 3. 初始化向量索引（仅当 S6/OpenSpec 选择向量检索时生成）
kubectl exec -it $(kubectl get pod -n fullstack-app -l app=python-ai -o jsonpath='{.items[0].metadata.name}') \
  -n fullstack-app -- <vector-index-init-command>
```

---

## 五、验证部署

| 检查项 | 命令 | 预期结果 |
|--------|------|----------|
| 所有 Pod 运行正常 | `kubectl get pods -n fullstack-app` | STATUS=Running |
| 服务可访问 | `curl http://api.yourdomain.com/health` | `{"status":"ok"}` |
| 数据库可连接 | `kubectl exec -it postgres-pod -- psql -U app_user -d report_db` | 成功登录 |
| 向量数据库可连接 | `<vector-db-health-check-command>` | 成功连接 |
| 前端可访问 | 浏览器打开 `https://yourdomain.com` | 显示登录页 |

---

## 六、回滚方案

```bash
# 回滚到上一版本
kubectl rollout undo deployment/java-service -n fullstack-app

# 回滚到指定版本
kubectl rollout undo deployment/java-service --to-revision=3 -n fullstack-app

# 查看历史版本
kubectl rollout history deployment/java-service -n fullstack-app
```

---

## 七、AI 项目额外说明

### GPU 资源调度（仅 S6/S7 明确选择时生成）

```yaml
# 仅当 S6/S7 明确选择本地推理或 GPU 资源时启用 GPU 节点池
apiVersion: v1
kind: NodeSelector
metadata:
  name: gpu-nodes
spec:
  selector:
    matchLabels:
      accelerator: nvidia
```

### 模型版本管理

```bash
# 模型存储（PVC）
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: model-pvc
spec:
  accessModes: [ReadWriteMany]
  resources:
    requests:
      storage: 100Gi
```

### Token 计费与限流

```yaml
# 在 S6/S7 选定的 AI Gateway 中配置
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: selected-ai-gateway
  annotations:
    gateway.example.com/token-rate-limit: "true"
    gateway.example.com/token-limit-per-minute: "100"
    gateway.example.com/prompt-security: "true"
    gateway.example.com/llm-fallback: "true"
```
```

## 行为规则

- ✅ 文档必须是中文 Markdown
- ✅ 包含开发 / 预发布 / 生产三套方案
- ✅ 本地开发方案必须优先 Docker Compose 最小集，说明 Docker 自检、端口偏移、依赖复用、资源限制和 dev profile
- ✅ 生产环境与本地开发环境必须分开描述；本地不得连接生产数据库、生产 MQ、生产对象存储或生产模型密钥
- ✅ 仅当 S6/S7 明确选择本地推理或 vLLM 时，才包含 GPU 调度和本地模型管理
- ✅ 如技术栈选择 Higress，部署指南必须包含 Higress 网关部署、Ingress/Gateway 路由、AI Gateway、Token 限流、Prompt 安全和可观测配置
- ✅ 必须遵循 S6 当前范围：不默认高可用、灾备、私有化部署或本地模型推理
- ✅ 部署验证项必须覆盖 OpenSpec P0/P1 capability / scenario
- ✅ 所有命令必须可直接复制执行
- ✅ 必须包含回滚方案
- ❌ 不得部署 OpenSpec baseline 未定义的业务入口或服务
- ❌ 不得包含真实密钥（用占位符）
- ❌ 不得跳过健康检查配置
- ❌ 未在 S6/S7/S27 明确选择时，不得把 MySQL、GPU/vLLM、高可用或灾备写成部署方案
- ❌ 公开网关不得绕过业务核心直连 Python AI 服务

## 使用示例

```
加载 <skill id="S27">，输入：.github/workflows/、.env.example、openspec/specs/
请根据 S6 技术栈、S7 服务边界和 OpenSpec 生成完整部署指南。
```
</skill>
<!-- end -->
