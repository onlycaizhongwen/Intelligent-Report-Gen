<!-- skill: S30 -->
<skill id="S30" name="monitor.setup">

# 技能：监控配置

## Meta
- DependsOn: S27
- Category: delivery
- Status: stable

## 一句话描述
配置系统监控、告警和日志聚合。

## 输入
- `docs/skill-chain/DEPLOY.md`（部署文档）
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `monitoring/`（监控配置文件目录）

## Prompt

你是一位资深 SRE 工程师。
请根据部署方案，生成监控配置。

监控配置必须追溯 OpenSpec：
- 每个业务、AI、安全、网关、SLO 和告警指标必须标注覆盖的 OpenSpec requirement / scenario 或基础设施风险。
- OpenSpec P0/P1 capability 必须至少有可观测指标、日志或告警覆盖。
- 必须读取 S6/S27 技术栈与部署方案；监控对象必须跟随实际选定的数据库、消息队列、检索、缓存、对象存储和网关。
- GPU/vLLM 监控仅在 S6/S7/S27 明确启用本地推理或 GPU 资源时生成；未明确启用时不得生成 GPU/vLLM 监控项。
- 必须承接 S27 的本地开发 / 生产部署分层；本地监控 Compose 不得默认占用常用端口，不得超过 S24/S27 的资源约束。

---

### Prometheus + Grafana（指标监控）

```yaml
# monitoring/prometheus/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Java 服务（Spring Boot Actuator）
  - job_name: 'java-service'
    static_configs:
      - targets: ['java-service:8080']
    metrics_path: /actuator/prometheus
    scrape_interval: 10s

  # Python AI 服务
  - job_name: 'python-ai'
    static_configs:
      - targets: ['python-ai:8000']
    metrics_path: /metrics
    scrape_interval: 10s

  # 网关（按 S6/S27 选型生成）
  - job_name: '<gateway-job-name>'
    static_configs:
      - targets: ['<gateway-metrics-target>']
    metrics_path: /metrics
    scrape_interval: 10s

  # Node Exporter（主机指标）
  - job_name: 'node'
    static_configs:
      - targets: ['node-exporter:9100']

  # Redis
  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

  # 数据库（按 S6/S27 选型生成）
  - job_name: '<database-job-name>'
    static_configs:
      - targets: ['<database-metrics-target>']

  # 消息队列（按 S6/S27 选型生成）
  - job_name: '<message-queue-job-name>'
    static_configs:
      - targets: ['<message-queue-metrics-target>']

  # 全文检索（按 S6/S27 选型生成）
  - job_name: '<search-job-name>'
    static_configs:
      - targets: ['<search-metrics-target>']

  # 对象存储（按 S6/S27 选型生成）
  - job_name: '<object-storage-job-name>'
    static_configs:
      - targets: ['<object-storage-metrics-target>']

  # 向量数据库（仅当 S6/OpenSpec 选择向量检索时生成）
  - job_name: '<vector-db-job-name>'
    static_configs:
      - targets: ['<vector-db-metrics-target>']
```

---

### Grafana 仪表盘

```json
// monitoring/grafana/dashboards/api-overview.json
{
  "dashboard": {
    "title": "API 总览仪表盘",
    "panels": [
      {
        "title": "请求 QPS",
        "type": "timeseries",
        "targets": [{
          "expr": "rate(http_requests_total[1m])",
          "legendFormat": "{{service}} - {{method}}"
        }]
      },
      {
        "title": "请求延迟 P99",
        "type": "timeseries",
        "targets": [{
          "expr": "histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))",
          "legendFormat": "{{service}}"
        }]
      },
      {
        "title": "错误率",
        "type": "timeseries",
        "targets": [{
          "expr": "rate(http_requests_total{status=~\"5..\"}[1m]) / rate(http_requests_total[1m])",
          "legendFormat": "{{service}} 错误率"
        }]
      }
    ]
  }
}
```

AI 专项仪表盘：
```json
// monitoring/grafana/dashboards/ai-metrics.json
{
  "dashboard": {
    "title": "AI 服务监控",
    "panels": [
      {
        "title": "LLM 请求延迟",
        "targets": [{
          "expr": "histogram_quantile(0.95, rate(llm_request_duration_seconds_bucket[5m]))",
          "legendFormat": "{{model}} P95"
        }]
      },
      {
        "title": "Token 用量（每分钟）",
        "targets": [{
          "expr": "rate(llm_tokens_used_total[1m])",
          "legendFormat": "{{model}} - {{type}}"
        }]
      },
      {
        "title": "Token 费用（美元/小时）",
        "targets": [{
          "expr": "rate(llm_cost_dollars_total[1h])",
          "legendFormat": "{{model}}"
        }]
      },
      {
        "title": "向量检索延迟 P95",
        "targets": [{
          "expr": "histogram_quantile(0.95, rate(vector_search_duration_seconds_bucket[5m]))",
          "legendFormat": "Milvus 检索"
        }]
      },
      {
        "title": "Embedding 生成 QPS",
        "targets": [{
          "expr": "rate(embedding_requests_total[1m])",
          "legendFormat": "{{model}}"
        }]
      },
      {
        "title": "Prompt 注入拦截次数",
        "targets": [{
          "expr": "increase(prompt_injection_blocked_total[5m])",
          "legendFormat": "拦截次数"
        }]
      },
      {
        "title": "幻觉率（用户反馈）",
        "targets": [{
          "expr": "rate(user_feedback_total{type=\"hallucination\"}[1d])",
          "legendFormat": "幻觉反馈/天"
        }]
      }
    ]
  }
}
```

---

### 告警规则

```yaml
# monitoring/prometheus/alerts.yml
groups:
  - name: 服务健康
    rules:
      - alert: 服务宕机
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.job }} 已宕机"
          description: "{{ $labels.instance }} 持续离线超过 1 分钟"

      - alert: API 高错误率
        expr: rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) > 0.05
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }} 错误率超过 5%"

      - alert: API 高延迟
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 2
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }} P95 延迟超过 2 秒"

  - name: AI 专项
    rules:
      - alert: LLM 超时率过高
        expr: rate(llm_request_timeout_total[5m]) / rate(llm_requests_total[5m]) > 0.1
        for: 3m
        labels:
          severity: critical
        annotations:
          summary: "LLM 超时率超过 10%"
          description: "模型响应超时频繁，请检查模型服务状态"

      - alert: Token 用量异常
        expr: rate(llm_tokens_used_total{type="prompt"}[1h]) > 100000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Token 用量异常偏高"
          description: "每小时 Prompt Token 超过 10 万，请检查是否有异常调用"

      - alert: Prompt 注入攻击
        expr: increase(prompt_injection_blocked_total[5m]) > 5
        labels:
          severity: critical
        annotations:
          summary: "检测到 Prompt 注入攻击"
          description: "5 分钟内拦截 {{ $value }} 次注入尝试"

      - alert: 向量检索失败
        expr: rate(vector_search_errors_total[5m]) > 0.05
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "Milvus 向量检索失败率过高"

  - name: 基础设施
    rules:
      - alert: 磁盘空间不足
        expr: node_filesystem_free_bytes{fstype!="tmpfs"} / node_filesystem_size_bytes < 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "磁盘空间不足 10%"

      - alert: GPU 利用率异常
        expr: (1 - nvidia_gpu_utilization) > 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "GPU 利用率低于 20%，可能存在资源浪费"
          description: "仅在本地推理/GPU 已启用时加载该规则"
```

---

### 日志聚合（ELK / Loki）

```yaml
# monitoring/loki/config.yml
server:
  http_listen_port: 3100

positions:
  filename: /tmp/loki/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: system
    static_configs:
      - targets: [localhost]
        labels:
          job: system
          __path__: /var/log/*.log
```

日志收集配置：
```yaml
# docker-compose.monitoring.yml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus:v2.48.0
    volumes:
      - ./monitoring/prometheus:/etc/prometheus
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    ports:
      - "19090:9090"
    mem_limit: 512m

  grafana:
    image: grafana/grafana:10.2.0
    volumes:
      - ./monitoring/grafana:/etc/grafana/provisioning
      - grafana_data:/var/lib/grafana
    ports:
      - "13000:3000"
    environment:
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
    mem_limit: 512m

  node-exporter:
    image: prom/node-exporter:v1.6.0
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    command:
      - '--path.procfs=/host/proc'
      - '--path.rootfs=/rootfs'
    mem_limit: 256m

  loki:
    image: grafana/loki:2.9.0
    volumes:
      - ./monitoring/loki:/etc/loki
    ports:
      - "13100:3100"
    mem_limit: 512m

  promtail:
    image: grafana/promtail:2.9.0
    volumes:
      - ./monitoring/promtail:/etc/promtail
      - /var/log:/var/log:ro
    command: -config.file=/etc/promtail/config.yml
    mem_limit: 256m
```

---

### 告警通知渠道

```yaml
# monitoring/prometheus/notify.yml
receivers:
  - name: 'web.hook'
    webhook_configs:
      - url: 'http://alertmanager-webhook:30500/webhook'

  - name: 'email'
    email_configs:
      - to: 'team@example.com'
        from: 'alerts@example.com'
        smarthost: 'smtp.gmail.com:587'
        auth_username: 'alerts@example.com'
        auth_password: '{{ env "SMTP_PASSWORD" }}'

  - name: 'wechat'
    wechat_configs:
      - api_url: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key={{ env "WECHAT_KEY" }}'

  - name: 'slack'
    slack_configs:
      - api_url: '{{ env "SLACK_WEBHOOK_URL" }}'
        channel: '#alerts'
```

---

### 自定义指标埋点

Java 版（Micrometer）：
```java
@Slf4j
@Component
public class MetricsCollector {
    private final MeterRegistry registry;
    private final Counter llmTokensCounter;
    private final Timer llmDurationTimer;
    private final Counter promptInjectionCounter;

    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        this.llmTokensCounter = Counter.builder("llm.tokens.used")
                .description("LLM Token 用量")
                .tag("model", "<llm_model>")
                .register(registry);
        this.llmDurationTimer = Timer.builder("llm.request.duration")
                .description("LLM 请求耗时")
                .register(registry);
        this.promptInjectionCounter = Counter.builder("prompt.injection.blocked")
                .description("Prompt 注入拦截次数")
                .register(registry);
    }

    public void recordTokenUsage(int tokens, String type) {
        llmTokensCounter.increment(tokens);
    }

    public Timer.Sample startLlmTimer() {
        return Timer.start(registry);
    }

    public void recordPromptInjection() {
        promptInjectionCounter.increment();
    }
}
```

Python 版（Prometheus Client）：
```python
from prometheus_client import Counter, Histogram, Gauge
import time

# LLM 指标
llm_tokens = Counter('llm_tokens_used_total', 'LLM Token 用量', ['model', 'type'])
llm_duration = Histogram('llm_request_duration_seconds', 'LLM 请求耗时', ['model'])
llm_timeouts = Counter('llm_request_timeout_total', 'LLM 超时次数', ['model'])
prompt_injections = Counter('prompt_injection_blocked_total', 'Prompt 注入拦截', ['reason'])

# 向量检索指标
vector_search_duration = Histogram('vector_search_duration_seconds', '向量检索耗时')
vector_search_errors = Counter('vector_search_errors_total', '向量检索错误')

# Embedding 指标
embedding_requests = Counter('embedding_requests_total', 'Embedding 请求数', ['model'])

# 使用示例
@llm_duration.time()
async def call_llm(prompt: str):
    try:
        response = await client.chat(messages=[{"role": "user", "content": prompt}])
        llm_tokens.inc(response.usage.total_tokens, {"model": model_name, "type": "total"})
        return response
    except TimeoutError:
        llm_timeouts.inc({"model": model_name})
        raise

def record_injection(reason: str):
    prompt_injections.inc({"reason": reason})
```

Go 版（Prometheus Go Client）：
```go
import (
    "github.com/prometheus/client_golang/prometheus"
    "github.com/prometheus/client_golang/prometheus/promauto"
)

var (
    requestCounter = promauto.NewCounterVec(
        prometheus.CounterOpts{
            Name: "http_requests_total",
            Help: "HTTP 请求总数",
        },
        []string{"method", "status"},
    )
    requestDuration = promauto.NewHistogramVec(
        prometheus.HistogramOpts{
            Name:    "http_request_duration_seconds",
            Help:    "HTTP 请求耗时",
            Buckets: prometheus.DefBuckets,
        },
        []string{"method"},
    )
)
```

---

## 监控清单

| 监控维度 | 工具 | 关键指标 |
|----------|------|----------|
| 应用性能 | Prometheus + Grafana | QPS、延迟、错误率 |
| 网关 | 按 S6/S27 选型配置 Metrics | Ingress 流量、路由命中、限流次数、WAF 拦截、JWT/OIDC 失败、上游错误率 |
| 日志 | Loki + Promtail | 请求日志、错误日志 |
| 主机 | Node Exporter | CPU、内存、磁盘、网络 |
| 数据库 | 按 S6/S27 选型配置 Exporter | 连接数、慢查询、锁等待 |
| 缓存 | Redis Exporter | 命中率、内存使用 |
| 消息队列 | 按 S6/S27 选型配置 Metrics | 队列堆积、消费失败、DLQ |
| 全文检索 | 按 S6/S27 选型配置 Exporter | 查询延迟、索引状态、节点健康 |
| 向量数据库 | 按 S6/S27 选型配置 Metrics | 检索延迟、索引大小 |
| 对象存储 | 按 S6/S27 选型配置 Metrics | 存储容量、请求错误率 |
| GPU（可选） | NVIDIA DCGM Exporter | 仅本地推理启用时监控 GPU 利用率、显存 |
| AI 专项 | 自定义指标 | Token 用量、费用、注入拦截 |
| AI Gateway | 按 S6/S27 网关选型配置 Metrics | 多模型 Fallback 次数、Token 级限流、语义缓存命中率、MCP Server 代理错误 |

---

## 行为规则

- ✅ 必须包含应用 + 基础设施 + AI 三层监控
- ✅ AI 项目必须监控 Token 用量和费用
- ✅ AI 项目必须监控 Prompt 注入拦截
- ✅ 如技术栈选择 Higress，必须包含 Higress 网关和 AI Gateway 指标：路由、限流、WAF、JWT/OIDC、Fallback、Token、语义缓存、MCP Server
- ✅ 必须覆盖 S6/S27 实际选定的数据库、消息队列、检索、向量库、缓存和对象存储
- ✅ 本地监控 Compose 必须使用偏移端口和 `mem_limit`，避免占用应用常用端口或吃掉宿主机资源
- ✅ 监控目标必须区分本地 Compose service name 与生产 K8s/云服务地址
- ✅ 告警必须分级（critical / warning）
- ✅ 告警通知必须配置通知渠道
- ✅ 所有仪表盘必须有中文标题和说明
- ✅ P0/P1 OpenSpec capability 必须有监控或告警覆盖
- ❌ 不得遗漏 AI 专项指标
- ❌ 不得为 OpenSpec 未定义的业务能力配置虚假成功指标
- ❌ 不得配置过于敏感的告警（避免告警风暴）
- ❌ 不得将密钥硬编码在监控配置中
- ❌ 不得生成未被 S6/S7/S27 明确选择的数据库、GPU、本地推理或网关监控
- ❌ 本地监控不得默认启用 Swarm / K8s / GPU 监控项

## 使用示例

```
加载 <skill id="S30">，输入：docs/skill-chain/DEPLOY.md、openspec/specs/
请生成完整的监控配置，包含 AI 服务专项指标和告警规则。
```
</skill>
<!-- end -->
