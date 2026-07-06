import sys
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
PYTHON_SERVICE = ROOT / "backend" / "python-ai-service"

os.environ.setdefault("JWT_SECRET", "test-secret")

if str(PYTHON_SERVICE) not in sys.path:
    sys.path.insert(0, str(PYTHON_SERVICE))
