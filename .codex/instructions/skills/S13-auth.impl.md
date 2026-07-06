<!-- skill: S13 -->
<skill id="S13" name="auth.impl">

# 技能：鉴权实现

## Meta
- DependsOn: S8, S12
- Category: backend
- Status: stable

## 一句话描述
根据安全设计方案，实现鉴权与权限控制代码。

## 输入
- `docs/skill-chain/security_design.md`：安全设计文档
- `backend/`：已有 DDD 后端代码目录
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `backend/`：鉴权相关代码，按 S7 DDD 目录结构写入对应层

## Prompt

你是一位资深安全工程师。
请完成以下工作：

生成前必须读取 OpenSpec：
- 每个登录、鉴权、RBAC、审计、Prompt 安全策略必须标注覆盖的 requirement / scenario。
- 如果安全实现需要新增角色、权限、Token 声明或 AI 安全策略，必须先形成 OpenSpec change/delta，不得直接写入代码。

### Java 版（Spring Security + JWT）

JWT 工具类：
```java
@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String secret;

    public String generateToken(Long userId, String role) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + 2 * 60 * 60 * 1000))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(Jwts.parser().setSigningKey(secret)
                .parseClaimsJws(token).getBody().getSubject());
    }
}
```

鉴权拦截器：
```java
@Component
public class JwtInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtTokenProvider jwtProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }
        try {
            Long userId = jwtProvider.getUserId(token.substring(7));
            request.setAttribute("userId", userId);
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }
}
```

权限注解：
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    String[] value();
}

@Aspect
@Component
public class RoleAspect {
    @Around("@annotation(RequiresRole)")
    public Object checkRole(ProceedingJoinPoint pjp) throws Throwable {
        // 从 ThreadLocal 获取当前用户角色
        // 校验是否匹配注解中的角色
        return pjp.proceed();
    }
}
```

### Python 版（FastAPI + JWT）

```python
from fastapi.security import HTTPBearer
from jose import jwt
from functools import wraps

security = HTTPBearer()

def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    try:
        payload = jwt.decode(credentials.credentials, SECRET_KEY, algorithms=["HS256"])
        user_id = payload.get("sub")
        return {"user_id": int(user_id), "role": payload.get("role")}
    except Exception:
        raise HTTPException(status_code=401, detail="无效的 Token")

# 权限装饰器
def requires_role(*roles: str):
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            current_user = kwargs.get("current_user")
            if current_user["role"] not in roles:
                raise HTTPException(status_code=403, detail="权限不足")
            return await func(*args, **kwargs)
        return wrapper
    return decorator

# 使用示例
@app.get("/api/v1/admin/users")
@requires_role("admin")
async def list_users(current_user: dict = Depends(get_current_user)):
    return await user_service.list_all()
```

### Go 版（Gin + JWT）

```go
func AuthMiddleware() gin.HandlerFunc {
    return func(c *gin.Context) {
        tokenString := c.GetHeader("Authorization")
        if tokenString == "" || !strings.HasPrefix(tokenString, "Bearer ") {
            c.AbortWithStatusJSON(401, ApiResponse{Code: 401, Message: "未授权"})
            return
        }
        tokenString = tokenString[7:]
        claims, err := ParseToken(tokenString)
        if err != nil {
            c.AbortWithStatusJSON(401, ApiResponse{Code: 401, Message: "Token 无效"})
            return
        }
        c.Set("userId", claims.UserID)
        c.Set("role", claims.Role)
        c.Next()
    }
}

// 权限校验中间件
func RequireRole(roles ...string) gin.HandlerFunc {
    return func(c *gin.Context) {
        role := c.GetString("role")
        for _, r := range roles {
            if r == role {
                c.Next()
                return
            }
        }
        c.AbortWithStatusJSON(403, ApiResponse{Code: 403, Message: "权限不足"})
    }
}
```

### AI 项目额外安全（Prompt 注入防护）

```python
# Prompt 注入检测
class PromptGuard:
    BLOCKED_PATTERNS = [
        r"ignore\s+(all\s+)?previous\s+(instructions|prompts?)",
        r"you\s+are\s+now\s+a\s+\w+",
        r"act\s+as\s+(a|an)\s+\w+",
    ]

    def is_injection(self, text: str) -> bool:
        for pattern in self.BLOCKED_PATTERNS:
            if re.search(pattern, text, re.IGNORECASE):
                return True
        return False

# 在对话接口中使用
@app.post("/api/v1/chat")
async def chat(request: ChatRequest, current_user: dict = Depends(get_current_user)):
    # 检查 Prompt 注入
    if prompt_guard.is_injection(request.question):
        raise HTTPException(status_code=400, detail="检测到不安全的输入内容")
    # 正常处理...
```
```

## 行为规则

- ✅ Token 必须设置合理过期时间（建议 2 小时）
- ✅ 密码必须使用 BCrypt 加密
- ✅ 所有接口必须校验权限（除了登录/注册）
- ✅ 鉴权、权限、审计和 Prompt 安全必须追溯 OpenSpec requirement / scenario
- ✅ AI 项目必须包含 Prompt 注入检测
- ✅ 敏感操作必须记录审计日志
- ❌ Token 不得存放在前端 LocalStorage（推荐 HttpOnly Cookie）
- ❌ 不得硬编码密钥（使用环境变量）
- ❌ 不得实现 OpenSpec 未定义的角色、权限或安全流程
- ❌ 不得返回详细错误信息给前端

## 使用示例

```
加载 <skill id="S13">，输入：docs/skill-chain/security_design.md、backend/、openspec/specs/
请实现完整的鉴权系统，包含 JWT 生成/校验和 RBAC 权限控制。
```
</skill>
<!-- end -->
