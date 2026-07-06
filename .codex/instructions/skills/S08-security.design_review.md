<!-- skill: S8 -->
<skill id="S8" name="security.design_review">

# 技能：安全设计审查

## Meta
- DependsOn: S7
- Category: security
- Status: stable

## 一句话描述
设计鉴权、权限与安全策略。

## 输入
- `docs/skill-chain/module_design.md`：模块设计文档
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `docs/skill-chain/security_design.md`：安全设计文档（中文 Markdown）

## Prompt

你是一位资深安全架构师。
请完成以下工作：

安全设计必须追溯 OpenSpec：每个鉴权、权限、安全和 AI 防护策略必须标注覆盖的 requirement / scenario。

1. **认证方式设计**：

   | 认证方式 | 适用场景 | 实现方案 |
   |----------|----------|----------|
   | JWT Token | 前后端分离 | Spring Security + JWT / Go JWT / Python PyJWT |
   | OAuth 2.0 | 第三方登录 | Spring OAuth / Go OAuth2 |
   | Session + Cookie | 传统 Web | Redis Session |

2. **权限模型（RBAC / ABAC）**：

   | 角色 | 权限范围 | 可访问资源 |
   |------|----------|-------------|

   ABAC 策略示例（如适用）：
   | 策略 | 条件 | 效果 |
   |------|------|------|

3. **敏感数据保护**：

   | 数据类型 | 保护方式 | 技术方案 |
   |----------|----------|----------|
   | 密码 | BCrypt 哈希 | Spring Security Crypto / Go bcrypt |
   | Token | HTTPS 传输 + 短期过期 | JWT TTL = 2h |
   | 个人信息 | 脱敏展示 | 后端脱敏处理 |
   | API Key | 加密存储 | AES-256 加密 |

4. **API 安全防护**：

   | 威胁 | 防护措施 | 实现方式 |
   |------|----------|----------|
   | SQL 注入 | 参数化查询 | MyBatis / ORM |
   | XSS | 输入过滤 + CSP | 前端框架内置 |
   | CSRF | Token 验证 | Spring CSRF / Double Submit |
   | 暴力破解 | 限流 + 验证码 | Higress Token/IP 限流 + Sentinel / Rate Limiter |
   | Prompt 注入（AI 项目） | 输入过滤 + 输出检测 | Higress Prompt 安全过滤 + 黑名单 + 语义检测 |
   | 越权访问 | 接口级权限校验 | 拦截器 / 注解 |
   | DDoS | WAF + 限流 + 降级 | Higress WAF + Sentinel |
   | 网关认证 | 统一入口鉴权 | Higress JWT/OIDC + Spring Security |

5. **如果是 AI 项目，额外设计 AI 安全**：

   | 安全维度 | 风险 | 防护措施 |
   |----------|------|----------|
   | Prompt 注入 | 用户通过提示词操控 AI | 输入白名单 + 系统提示词隔离 |
   | 敏感信息泄露 | AI 输出包含隐私 | 输出过滤 + 脱敏 |
   | 幻觉攻击 | AI 生成虚假信息 | 置信度阈值 + 人工审核 |
   | Token 滥用 | API Key 被盗用 | Higress Token 级限流/计费 + 配额限制 + 异常检测 |
   | 模型 API 故障 | 单模型不可用或超时 | Higress 多模型 Fallback + 熔断降级 |
   | 语义重复请求 | 高频相似 Prompt 消耗成本 | Higress 语义缓存 + 配额审计 |
   | 模型投毒 | 训练数据被污染 | 数据来源验证 |

6. **安全审计策略**：

   | 审计项 | 记录内容 | 存储方式 |
   |--------|----------|----------|
   | 登录日志 | 时间/IP/设备 | 数据库 |
   | 操作日志 | 谁/何时/做了什么 | ELK |
   | AI 调用日志 | Token 用量/耗时/结果 | LangSmith / LangFuse |

## 行为规则

- ✅ 默认拒绝原则（白名单机制）
- ✅ 必须覆盖 OpenSpec 中的安全、权限、审计、AI 安全 requirement / scenario
- ✅ AI 项目必须包含 Prompt 注入防护
- ✅ 如模块设计包含 Higress，安全方案必须体现 Higress WAF、JWT/OIDC、Token 级限流/计费、Prompt 安全过滤、多模型 Fallback、语义缓存和 MCP Server 代理安全边界
- ✅ 密码不得明文存储
- ✅ 所有接口必须有鉴权（除了公开接口）
- ❌ 不得跳过权限校验
- ❌ 不得输出敏感信息到日志

## 使用示例

```
加载 <skill id="S8">，输入：docs/skill-chain/module_design.md
请设计完整的鉴权与权限安全方案，包含 AI 安全策略。
```
</skill>
<!-- end -->
