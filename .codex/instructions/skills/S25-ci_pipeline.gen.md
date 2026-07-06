<!-- skill: S25 -->
<skill id="S25" name="ci_pipeline.gen">

# 技能：CI 流水线生成

## Meta
- DependsOn: S24
- Category: devops
- Status: stable

## 一句话描述
生成持续集成（CI）流水线配置。

## 输入
- `Dockerfile`（已有容器化配置）
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `.github/workflows/`（GitHub Actions 配置）

## Prompt

你是一位资深 DevOps 工程师。
请根据项目结构，生成 CI/CD 流水线。

流水线必须把 OpenSpec 作为验收门禁：
- 增加 OpenSpec 格式校验、API 契约一致性校验和 scenario 覆盖检查。
- OpenSpec baseline 冻结后，代码、测试、容器和部署变更不得绕过 OpenSpec 校验。
- 语言和工具版本必须读取 S6 技术栈；例如 S6 选择 Java 21、Python 3.11、Node 20 时，CI 必须使用对应版本。
- Go 检查仅在 S6/S7 明确选择 Go 服务时启用；不得因为模板存在 Go 示例而强制要求 Go 模块。
- E2E / smoke 必须承接 S24 `docker-compose.yml`；服务就绪判断优先读取 Compose `healthcheck`，不得硬编码 `localhost:8080/health` 之类固定宿主端口。
- CI 中的 Docker Compose 仅用于本地/测试闭环，不得默认开启 Swarm / K8s / GPU / 多节点中间件。

---

### GitHub Actions 主流水线

```yaml
# .github/workflows/ci.yml
name: 全栈项目 CI/CD

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

env:
  JAVA_VERSION: '21'
  PYTHON_VERSION: '3.11'
  NODE_VERSION: '20'
  GO_VERSION: '1.21'

jobs:
  # ===== 代码质量检查 =====
  lint:
    name: 代码质量检查
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # Java 代码规范检查
      - name: Java 代码检查
        if: hashFiles('**/pom.xml') != ''
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
      - name: Maven 静态检查
        if: hashFiles('**/pom.xml') != ''
        run: mvn checkstyle:check spotbugs:check

      # Python 代码规范检查
      - name: Python 代码检查
        if: hashFiles('**/requirements.txt') != ''
        uses: actions/setup-python@v5
        with:
          python-version: ${{ env.PYTHON_VERSION }}
      - name: Pylint 检查
        if: hashFiles('**/requirements.txt') != ''
        run: |
          pip install -r requirements.txt
          pylint src/ --disable=R,C0301

      # Go 代码规范检查
      - name: Go 代码检查
        if: hashFiles('**/go.mod') != ''
        uses: actions/setup-go@v5
        with:
          go-version: ${{ env.GO_VERSION }}
      - name: GoLint 检查
        if: hashFiles('**/go.mod') != ''
        run: |
          go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
          golangci-lint run ./...

      # 前端代码规范检查
      - name: 前端代码检查
        if: hashFiles('**/package.json') != ''
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
      - name: ESLint / Prettier 检查
        if: hashFiles('**/package.json') != ''
        run: |
          npm ci
          npm run lint
          npm run format:check

  # ===== 单元测试 =====
  test:
    name: 单元测试
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v4

      # Java 测试
      - name: Java 单元测试
        if: hashFiles('**/pom.xml') != ''
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
      - name: Maven 测试
        if: hashFiles('**/pom.xml') != ''
        run: mvn test

      # Python 测试
      - name: Python 单元测试
        if: hashFiles('**/requirements.txt') != ''
        uses: actions/setup-python@v5
        with:
          python-version: ${{ env.PYTHON_VERSION }}
      - name: Pytest 测试
        if: hashFiles('**/requirements.txt') != ''
        run: |
          pip install -r requirements.txt
          pytest tests/unit/ -v --cov=src --cov-report=xml

      # Go 测试
      - name: Go 单元测试
        if: hashFiles('**/go.mod') != ''
        uses: actions/setup-go@v5
        with:
          go-version: ${{ env.GO_VERSION }}
      - name: Go Test
        if: hashFiles('**/go.mod') != ''
        run: go test ./... -coverprofile=coverage.out

      # 前端测试
      - name: 前端测试
        if: hashFiles('**/package.json') != ''
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
      - name: Vitest 测试
        if: hashFiles('**/package.json') != ''
        run: |
          npm ci
          npm run test

  # ===== 构建 Docker 镜像 =====
  build:
    name: 构建 Docker 镜像
    runs-on: ubuntu-latest
    needs: [lint, test]
    outputs:
      image_tag: ${{ steps.meta.outputs.tags }}
    steps:
      - uses: actions/checkout@v4
      - name: Docker Buildx 设置
        uses: docker/setup-buildx-action@v3
      - name: Docker 元数据
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=ref,event=branch
            type=sha,prefix={{date 'YYYYMMDD'}}-}
      - name: 构建并推送镜像
        uses: docker/build-push-action@v5
        with:
          context: .
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}

  # ===== E2E 测试 =====
  e2e:
    name: 端到端测试
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v4
      - name: 启动服务
        run: docker compose -f docker-compose.yml up -d
      - name: 等待服务就绪
        run: |
          timeout 180 sh -c '
            until docker compose -f docker-compose.yml ps --format json | python - <<PY
import json, sys
raw = sys.stdin.read().strip()
if not raw:
    sys.exit(1)
items = [json.loads(line) for line in raw.splitlines()]
for item in items:
    state = (item.get("State") or "").lower()
    health = (item.get("Health") or "").lower()
    if state != "running":
        sys.exit(1)
    if health and health != "healthy":
        sys.exit(1)
sys.exit(0)
PY
            do sleep 3; done'
      - name: 运行 E2E 测试
        run: |
          pip install -r tests/e2e/requirements.txt
          pytest tests/e2e/ -v
      - name: 上传测试截图
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-screenshots
          path: tests/e2e/screenshots/

  # ===== 安全扫描 =====
  security:
    name: 安全漏洞扫描
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Trivy 漏洞扫描
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: '.'
          format: 'table'
```

---

### AI 项目额外流水线

```yaml
# .github/workflows/ai-checks.yml
name: AI 模型与安全检查

on:
  push:
    branches: [ main ]
    paths: [ 'src/ai/**', 'requirements.txt' ]

jobs:
  model-quality:
    name: 模型质量检查
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: 设置 Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.10'
      - name: 运行模型测试
        run: |
          pip install -r requirements.txt
          # Embedding 一致性测试
          pytest tests/unit/test_embedding.py -v
          # RAG 召回率测试
          pytest tests/unit/test_rag_recall.py -v
          # Prompt 注入防御测试
          pytest tests/unit/test_prompt_injection.py -v

  prompt-security:
    name: Prompt 安全扫描
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: 扫描 Prompt 模板
        run: |
          # 检查 Prompt 中是否有敏感信息泄露风险
          python scripts/scan_prompts.py
      - name: 检查 Prompt 注入漏洞
        run: |
          python scripts/test_injection_patterns.py
```

---

### 部署流水线（CD）

```yaml
# .github/workflows/cd.yml
name: 持续部署

on:
  push:
    branches: [ main ]

jobs:
  deploy-staging:
    name: 部署到预发布环境
    runs-on: ubuntu-latest
    needs: [ci.yml::build]
    environment:
      name: staging
      url: https://staging.example.com
    steps:
      - uses: actions/checkout@v4
      - name: 部署到 K8s
        run: |
          kubectl apply -f k8s/staging/
          kubectl set image deployment/java-service java-service=${{ needs.build.outputs.image_tag }}
          kubectl set image deployment/python-ai python-ai=${{ needs.build.outputs.image_tag }}
          kubectl rollout status deployment/java-service
          kubectl rollout status deployment/python-ai

  deploy-production:
    name: 部署到生产环境
    runs-on: ubuntu-latest
    needs: deploy-staging
    environment:
      name: production
      url: https://example.com
    steps:
      - uses: actions/checkout@v4
      - name: 蓝绿部署
        run: |
          # 部署新版本到绿组
          kubectl apply -f k8s/production/green/
          kubectl rollout status deployment/java-service-green
          # 验证通过后切换流量
          kubectl patch service app-service -p '{"spec":{"selector":{"version":"green"}}}'
```

## 行为规则

- ✅ 流水线必须包含：lint → test → build → e2e → deploy
- ✅ AI 项目必须包含模型质量检查和 Prompt 安全扫描
- ✅ 所有密码使用 GitHub Secrets
- ✅ 构建产物必须有版本标签
- ✅ 部署必须有预发布验证
- ✅ CI 必须包含 OpenSpec / API / 测试覆盖一致性校验
- ✅ E2E / smoke 必须使用 S24 生成的 `docker-compose.yml`，并优先等待 compose healthcheck，不得硬编码固定宿主端口
- ✅ CI Compose 测试不得默认启用 Swarm / K8s / GPU / 多节点中间件
- ❌ 不得在 CI 中硬编码密钥
- ❌ 不得跳过测试直接部署
- ❌ 不得允许实现偏离 OpenSpec baseline 后继续部署
- ❌ 不得单阶段部署到生产（必须经过 staging）

## 使用示例

```
加载 <skill id="S25">，输入：Dockerfile、openspec/specs/
请生成 GitHub Actions 流水线，包含 AI 模型质量检查。
```
</skill>
<!-- end -->
