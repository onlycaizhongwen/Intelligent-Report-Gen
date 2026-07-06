# 本地网关与全文检索部署状态

日期：2026-06-23

## 当前结论

本地 Docker 已按最小资源策略补充部署 OpenSearch 与 Higress：

- OpenSearch 已完成真实写入、查询、删除烟测，可用于后续全文检索闭环验证。
- Higress all-in-one 已启动，Console 与 Gateway HTTP/HTTPS 端口可访问，可用于后续网关路由配置验证。
- 未停止、替换或重建既有 PostgreSQL、Redis、MinIO、RocketMQ、Milvus、etcd 容器。

## 本地编排

文件：`docker-compose.local-gateway-search.yml`

| 服务 | 容器 | 镜像 | 本地端口 | 资源限制 | 网络 |
| --- | --- | --- | --- | --- | --- |
| OpenSearch | `ir-opensearch` | `docker.m.daocloud.io/opensearchproject/opensearch:2.18.0` | `9200`, `9600` | `1 CPU`, `1g`, JVM `512m` | `intelligent-report-infra_default` |
| Higress | `ir-higress` | `higress-registry.cn-hangzhou.cr.aliyuncs.com/higress/all-in-one:2.0.6` | `18000->8080`, `18443->8443`, `18001->8001` | `1 CPU`, `768m` | `intelligent-report-infra_default` |

选择非默认 Higress 宿主端口的原因：本地 `80` 端口可能被占用；Higress all-in-one 容器内端口为 Console `8001`、Gateway HTTP `8080`、Gateway HTTPS `8443`。

## 启停命令

```bash
docker compose -f docker-compose.local-gateway-search.yml up -d
docker compose -f docker-compose.local-gateway-search.yml ps
docker compose -f docker-compose.local-gateway-search.yml down
```

如需保留 OpenSearch 数据，停止时不要追加 `-v`。

## 当前 Docker 状态

| 组件 | 容器 | 状态 | 端口 |
| --- | --- | --- | --- |
| OpenSearch | `ir-opensearch` | running, healthy | `9200`, `9600` |
| Higress | `ir-higress` | running | `18000`, `18001`, `18443` |

## 已完成验证

| 验证项 | 命令或入口 | 结果 |
| --- | --- | --- |
| OpenSearch HTTP | `GET http://localhost:9200` | HTTP 200 |
| OpenSearch 写读删 | 创建临时索引、写入 `_doc/1`、搜索 `system:intelligent-report`、删除索引 | `create_ack=true`、`doc_result=created`、`hits=1`、`delete_ack=true` |
| Higress Console | `GET http://localhost:18001` | HTTP 200 |
| Higress Gateway HTTP | `GET http://localhost:18000` | HTTP 200 |
| Higress Envoy ready | 容器内 `curl http://127.0.0.1:15021/healthz/ready` | `LIVE` |
| Higress 进程 | `docker logs ir-higress` | `apiserver`、`controller`、`pilot`、`gateway`、`console` 均为 running |

## 当前边界

Higress 当前默认路由仍指向 all-in-one 默认入口。`GET http://localhost:18000/api/v1/health` 返回默认页面，不代表 `/api/v1/** -> java-report-core` 业务路由已经绑定。

后续进行真实业务网关闭环时，需要在 Java 服务容器或稳定本机服务地址确认后配置 Higress：

- 对外 `/api/v1/**` 统一路由到 Java 业务核心。
- Python AI 服务 `/api/v1/chat` 保持内部或受控路由，不直接公开暴露。
- 路由配置完成后再执行经 Higress 的登录、上传、报告生成、导出下载链路烟测。

## 依据

- Higress all-in-one 部署约定使用 `/data` 持久化配置，并暴露 Console `8001`、Gateway HTTP `8080`、Gateway HTTPS `8443`。
- OpenSearch 单节点开发部署可关闭安全插件并降低 JVM 资源，仅用于本地开发验证，不用于生产。
## 2026-07-02 路由状态补充

Higress 本地 `/api/v1/** -> java-report-core` 业务路由已具备真实 smoke 证据：

- `GET http://127.0.0.1:18000/api/v1/roles/permission-matrix` 返回 Java 认证边界 JSON：`code=401`。
- `POST http://127.0.0.1:18000/api/v1/chat` 同样返回 Java 认证边界 JSON：`code=401`，未直接暴露 Python AI `/chat` 响应。
- 可重复验证脚本：`node scripts/higress-gateway-smoke.mjs`。
- 路由数据文件：`config/higress/local-data/ingresses/intelligent-report-routes.yaml`、`services/java-report-core.yaml`、`endpoints/java-report-core.yaml`。

仍未覆盖：OIDC 登录、TLS 证书、WAF 策略、生产 K8s Gateway API 对象、以及逐端点 403/200 浏览器验证。
