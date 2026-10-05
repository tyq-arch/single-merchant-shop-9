"""安全模块：口令哈希、会话令牌、口令码生成。"""
import base64
import hashlib
import hmac
import secrets
import time
from pathlib import Path

from . import config

_PBKDF2_ITERATIONS = 200_000
# 去除易混淆字符（0/O/1/I/l），避免买家抄写口令码出错
_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
_CODE_LENGTH = 10

_SCHEMA_PATH = Path(__file__).resolve().parent / "schema.sql"


# --------------------------- 口令哈希 ---------------------------
def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, _PBKDF2_ITERATIONS)
    return f"pbkdf2_sha256${_PBKDF2_ITERATIONS}${salt.hex()}${digest.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        algo, iterations, salt_hex, digest_hex = stored.split("$")
        if algo != "pbkdf2_sha256":
            return False
        digest = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), bytes.fromhex(salt_hex), int(iterations)
        )
        return hmac.compare_digest(digest.hex(), digest_hex)
    except (ValueError, AttributeError):
        return False


# --------------------------- 会话令牌 ---------------------------
def _sign(payload: str) -> str:
    signature = hmac.new(config.SECRET_KEY.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256)
    return signature.hexdigest()


def create_token(seller_id: str, ttl_seconds: int | None = None) -> tuple[str, int]:
    """生成无状态签名令牌，返回 (token, 过期时间戳)。后台无退出登录，令牌自然过期。"""
    ttl = ttl_seconds if ttl_seconds is not None else config.TOKEN_TTL_SECONDS
    expires_at = int(time.time()) + ttl
    payload = f"{seller_id}.{expires_at}"
    token = base64.urlsafe_b64encode(f"{payload}.{_sign(payload)}".encode("utf-8")).decode("ascii")
    return token, expires_at


def verify_token(token: str) -> str | None:
    """校验令牌，成功返回 seller_id，失败返回 None。"""
    try:
        decoded = base64.urlsafe_b64decode(token.encode("ascii")).decode("utf-8")
        seller_id, expires_at, signature = decoded.rsplit(".", 2)
    except (ValueError, UnicodeDecodeError, base64.binascii.Error):
        return None
    if not hmac.compare_digest(_sign(f"{seller_id}.{expires_at}"), signature):
        return None
    if int(expires_at) < int(time.time()):
        return None
    return seller_id


# --------------------------- 口令码 ---------------------------
def generate_command_code() -> str:
    return "".join(secrets.choice(_CODE_ALPHABET) for _ in range(_CODE_LENGTH))


def unique_command_code(exists) -> str:
    """生成全局唯一口令码；exists(code) 由调用方提供唯一性校验。"""
    for _ in range(20):
        code = generate_command_code()
        if not exists(code):
            return code
    raise RuntimeError("无法生成唯一口令码")


def load_schema_sql() -> str:
    return _SCHEMA_PATH.read_text(encoding="utf-8")
