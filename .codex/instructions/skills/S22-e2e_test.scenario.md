<!-- skill: S22 -->
<skill id="S22" name="e2e_test.scenario">

# 技能：端到端测试场景生成

## Meta
- DependsOn: S16, S12
- Category: quality
- Status: stable

## 一句话描述
根据页面和接口，生成端到端测试场景。

## 输入
- `pages/`（前端页面代码）
- `backend/`：DDD 后端代码目录
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `tests/e2e/`（端到端测试文件）

## Prompt

你是一位资深测试工程师。
请根据前端页面和后端接口，生成 E2E 测试场景。

测试场景必须从 OpenSpec scenario 反推：
- 每个 E2E 测试必须标注覆盖的 OpenSpec capability / requirement / scenario。
- OpenSpec 中 P0/P1 用户旅程、权限分支、错误分支和 AI 流式场景必须有 E2E 覆盖或明确说明不可自动化原因。
- 如果页面或接口存在但 OpenSpec 无对应 scenario，必须输出冲突报告，不得擅自补测试语义。

---

### 测试场景清单

| 场景编号 | 场景名称 | 涉及页面 | 涉及接口 | 优先级 |
|----------|----------|----------|----------|--------|
| TC-001 | 用户登录成功 | Login | POST /api/v1/auth/login | P0 |
| TC-002 | 用户登录失败（密码错误） | Login | POST /api/v1/auth/login | P1 |
| TC-003 | 未登录访问被拦截 | Dashboard | GET /api/v1/users/me | P0 |
| TC-004 | 上传文档成功 | DocumentUpload | POST /api/v1/documents/upload | P0 |
| TC-005 | 上传不支持的文件格式 | DocumentUpload | — | P1 |
| TC-006 | AI 对话流式响应 | DocumentChat | POST /api/v1/chat | P0 |
| TC-007 | AI 对话返回引用来源 | DocumentChat | POST /api/v1/chat | P1 |
| TC-008 | 语义搜索返回相关结果 | DocumentChat | POST /api/v1/documents/search | P1 |
| TC-009 | 删除文档成功 | DocumentList | DELETE /api/v1/documents/{id} | P1 |
| TC-010 | 管理员查看用户列表 | Admin/UserManage | GET /api/v1/admin/users | P2 |

### OpenSpec 覆盖矩阵

| 场景编号 | OpenSpec capability | Requirement / Scenario | 涉及页面 | 涉及接口 | 覆盖状态 |
|----------|---------------------|------------------------|----------|----------|----------|

---

### Python 版（Pytest + Playwright）

```python
import pytest
from playwright.sync_api import Page, expect

class TestUserLogin:
    """用户登录测试"""

    def test_login_success(self, page: Page):
        page.goto("/login")
        page.fill('[data-testid="username"]', "admin")
        page.fill('[data-testid="password"]', "admin123")
        page.click('[data-testid="login-btn"]')
        # 等待跳转到仪表盘
        expect(page).to_have_url("/dashboard")
        expect(page.locator('[data-testid="user-name"]')).to_contain_text("admin")

    def test_login_failure(self, page: Page):
        page.goto("/login")
        page.fill('[data-testid="username"]', "admin")
        page.fill('[data-testid="password"]', "wrongpassword")
        page.click('[data-testid="login-btn"]')
        expect(page.locator('[data-testid="error-msg"]')).to_contain_text("密码错误")

class TestDocumentUpload:
    """文档上传测试"""

    def test_upload_success(self, page: Page, logged_in_user: Page):
        page.goto("/documents/upload")
        # 上传文件
        file_input = page.locator('[data-testid="file-input"]')
        file_input.set_input_files("tests/fixtures/sample.pdf")
        # 等待上传完成
        expect(page.locator('[data-testid="upload-success"]')).to_be_visible(timeout=30000)
        # 验证文档出现在列表中
        page.goto("/documents")
        expect(page.locator('[data-testid="doc-title"]')).to_contain_text("sample.pdf")

class TestAIChat:
    """AI 对话测试（流式响应）"""

    def test_chat_stream_response(self, page: Page, logged_in_user: Page):
        page.goto("/documents/chat")
        # 输入问题
        page.fill('[data-testid="chat-input"]', "公司的年假政策是什么？")
        page.click('[data-testid="send-btn"]')
        # 验证流式响应（打字机效果）
        message_container = page.locator('[data-testid="ai-message"]')
        # 等待首个 Token 出现（最多 10 秒）
        expect(message_container).to_be_visible(timeout=10000)
        # 等待流式传输完成（done 标记出现）
        expect(page.locator('[data-testid="stream-done"]')).to_be_visible(timeout=30000)
        # 验证引用来源
        references = page.locator('[data-testid="references"] details')
        expect(references).to_have_count_at_least(1)

    def test_chat_empty_question(self, page: Page, logged_in_user: Page):
        page.goto("/documents/chat")
        # 不输入内容直接发送
        page.click('[data-testid="send-btn"]')
        expect(page.locator('[data-testid="error-msg"]')).to_contain_text("请输入问题")

class TestDocumentSearch:
    """语义搜索测试"""

    def test_search_returns_relevant_results(self, page: Page, logged_in_user: Page):
        page.goto("/documents/chat")
        page.fill('[data-testid="chat-input"]', "年假有多少天")
        page.click('[data-testid="send-btn"]')
        # 验证检索到的文档片段
        expect(page.locator('[data-testid="reference-snippet"]')).to_contain_text("年假")
```

---

### Node.js 版（Jest + Playwright）

```javascript
describe('用户登录', () => {
    test('登录成功', async () => {
        await page.goto('/login');
        await page.fill('[data-testid="username"]', 'admin');
        await page.fill('[data-testid="password"]', 'admin123');
        await page.click('[data-testid="login-btn"]');
        await expect(page).toHaveURL('/dashboard');
    });
});

describe('AI 对话（流式）', () => {
    test('收到流式响应', async () => {
        await page.goto('/documents/chat');
        await page.fill('[data-testid="chat-input"]', '测试问题');
        await page.click('[data-testid="send-btn"]');
        // 等待 AI 响应出现
        await expect(page.locator('[data-testid="ai-message"]')).toBeVisible({ timeout: 10000 });
    });
});
```

---

### 测试数据准备

```python
# tests/fixtures/sample.pdf
# 一个包含"年假政策"内容的 PDF 文件

# tests/fixtures/conftest.py
import pytest

@pytest.fixture
def logged_in_user(page: Page):
    """已登录用户 Fixture"""
    page.goto("/login")
    page.fill('[data-testid="username"]', "testuser")
    page.fill('[data-testid="password"]', "test123")
    page.click('[data-testid="login-btn"]')
    expect(page).to_have_url("/dashboard")
    return page
```

---

### AI 项目专项测试

| 测试场景 | 验证点 | 断言 |
|----------|--------|------|
| 流式响应完整性 | 收到 done 事件 | `expect(page.locator('[data-testid="stream-done"]')).toBeVisible()` |
| 引用来源正确性 | 返回的 references 非空 | `expect(refs).toHaveCountAtLeast(1)` |
| 空问题处理 | 显示错误提示 | `expect(errorMsg).toBeVisible()` |
| 文档未就绪时提问 | 提示"暂无相关知识" | `expect(msg).toContainText("暂无")` |
| Token 用量展示 | 显示消耗的 Token 数 | `expect(page.locator('[data-testid="token-usage"]')).toBeVisible()` |

## 行为规则

- ✅ 覆盖所有关键用户路径（Happy Path）
- ✅ 包含异常场景（Sad Path）
- ✅ 每个 E2E 测试必须追溯 OpenSpec requirement / scenario
- ✅ AI 项目必须测试 SSE 流式响应
- ✅ 测试数据使用 Fixtures，不得硬编码
- ✅ 每个测试必须包含断言
- ❌ 不得依赖执行顺序（每个测试独立）
- ❌ 不得截图代替断言
- ❌ 不得测试第三方服务（Mock 外部 API）
- ❌ 不得为 OpenSpec 未定义的用户旅程生成 E2E 场景

## 使用示例

```
加载 <skill id="S22">，输入：pages/、backend/、openspec/specs/
请生成 E2E 测试，包含 AI 对话流式响应的完整测试。
```
</skill>
<!-- end -->
