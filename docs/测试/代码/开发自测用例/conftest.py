"""pytest 公共夹具：每个用例使用独立、全新的临时 SQLite 数据库。"""
import os
import sys
import tempfile
from pathlib import Path

# 让 backend/ 目录下的 app 包可被导入
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

_TMP_DIR = tempfile.mkdtemp(prefix="shop_test_")
_DB_PATH = os.path.join(_TMP_DIR, "test_shop.db")

os.environ["SHOP_DATA_DIR"] = _TMP_DIR
os.environ["SHOP_DB_PATH"] = _DB_PATH
os.environ["SHOP_UPLOAD_DIR"] = os.path.join(_TMP_DIR, "uploads")
os.environ["SHOP_SELLER_USERNAME"] = "seller"
os.environ["SHOP_SELLER_PASSWORD"] = "seller123"

import pytest
from fastapi.testclient import TestClient

from app.main import app


def _wipe_db() -> None:
    for suffix in ("", "-wal", "-shm"):
        path = Path(_DB_PATH + suffix)
        if path.exists():
            path.unlink()


@pytest.fixture
def client():
    _wipe_db()
    with TestClient(app) as test_client:  # __enter__ 触发 lifespan -> init_db + 种子卖家
        yield test_client


@pytest.fixture
def auth(client):
    resp = client.post("/api/seller/login", json={"username": "seller", "password": "seller123"})
    assert resp.status_code == 200, resp.text
    return {"Authorization": f"Bearer {resp.json()['token']}"}


@pytest.fixture
def published(client, auth):
    """发布一件在售商品，返回商品 id。"""
    resp = client.post(
        "/api/seller/products",
        json={"name": "测试商品", "price": 99.9, "description": "描述"},
        headers=auth,
    )
    assert resp.status_code == 200, resp.text
    return resp.json()["product"]["id"]


def submit_intent(client, product_id, name, phone="13800000000"):
    resp = client.post(
        "/api/buyer/intents",
        json={"product_id": product_id, "buyer_name": name, "buyer_phone": phone},
    )
    return resp
