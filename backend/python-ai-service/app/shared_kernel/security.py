from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
import os
import re

security = HTTPBearer()
SECRET_KEY = os.environ["JWT_SECRET"]


def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)) -> dict:
    """OpenSpec: permission-collaboration / REQ-AUTH-001 / Python AI 服务只接收受控任务上下文。"""
    try:
        payload = jwt.decode(credentials.credentials, SECRET_KEY, algorithms=["HS256"])
        return {
            "user_id": int(payload["sub"]),
            "roles": payload.get("roles", []),
            "permissions": payload.get("permissions", []),
        }
    except (JWTError, KeyError, ValueError):
        raise HTTPException(status_code=401, detail="请登录后继续操作")


class PromptGuard:
    """OpenSpec: report-generation / REQ-AI-001 / Prompt 注入检测。"""

    blocked_patterns = [
        r"ignore\s+(all\s+)?previous\s+(instructions|prompts?)",
        r"you\s+are\s+now\s+a\s+\w+",
        r"act\s+as\s+(a|an)\s+\w+",
        r"忽略(以上|之前|所有).*指令",
        r"泄露.*(系统提示|prompt|上下文)",
    ]

    def is_injection(self, text: str) -> bool:
        return any(re.search(pattern, text, re.IGNORECASE) for pattern in self.blocked_patterns)


prompt_guard = PromptGuard()
