"""后端配置：路径、卖家初始账号、安全参数等。

所有配置均支持通过环境变量覆盖，便于测试与部署。
"""
import os
from pathlib import Path

# backend/ 目录
BASE_DIR = Path(__file__).resolve().parent.parent

DATA_DIR = Path(os.getenv("SHOP_DATA_DIR", str(BASE_DIR / "data")))
DB_PATH = Path(os.getenv("SHOP_DB_PATH", str(DATA_DIR / "shop.db")))
UPLOAD_DIR = Path(os.getenv("SHOP_UPLOAD_DIR", str(DATA_DIR / "uploads")))

# 唯一的卖家账号（系统初始化时写入，无注册流程）
SELLER_USERNAME = os.getenv("SHOP_SELLER_USERNAME", "seller")
SELLER_PASSWORD = os.getenv("SHOP_SELLER_PASSWORD", "seller123")

# 会话令牌
SECRET_KEY = os.getenv("SHOP_SECRET_KEY", "dev-secret-key-change-me")
TOKEN_TTL_SECONDS = int(os.getenv("SHOP_TOKEN_TTL_SECONDS", str(12 * 3600)))

# 口令码有效期（天）；口令码还会在交易结束/商品下架/买家撤销时立即失效
COMMAND_CODE_TTL_DAYS = int(os.getenv("SHOP_COMMAND_CODE_TTL_DAYS", "30"))

# 图片上传限制
MAX_IMAGE_BYTES = int(os.getenv("SHOP_MAX_IMAGE_BYTES", str(5 * 1024 * 1024)))
ALLOWED_IMAGE_EXT = {".jpg", ".jpeg", ".png", ".gif", ".webp"}

# 列表分页
DEFAULT_PAGE_SIZE = int(os.getenv("SHOP_DEFAULT_PAGE_SIZE", "10"))
MAX_PAGE_SIZE = int(os.getenv("SHOP_MAX_PAGE_SIZE", "100"))


def ensure_dirs() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
