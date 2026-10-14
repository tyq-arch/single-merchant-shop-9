"""商品浏览、发布、唯一在售、冻结/解冻、下架测试。"""
from conftest import submit_intent


def test_no_product_in_sale(client):
    resp = client.get("/api/products/current")
    assert resp.status_code == 200
    assert resp.json()["state"] == "NONE"
    assert resp.json()["product"] is None


def test_publish_then_public_visible(client, auth, published):
    resp = client.get("/api/products/current")
    body = resp.json()
    assert body["state"] == "ON_SALE"
    assert body["product"]["name"] == "测试商品"
    assert body["product"]["price"] == 99.9

    detail = client.get(f"/api/products/{published}")
    assert detail.status_code == 200
    assert detail.json()["product"]["id"] == published


def test_only_one_product_on_sale(client, auth, published):
    resp = client.post("/api/seller/products", json={"name": "第二件", "price": 10}, headers=auth)
    assert resp.status_code == 409
    assert "在售" in resp.json()["error"]["message"]


def test_publish_validation(client, auth):
    assert client.post("/api/seller/products", json={"name": "x", "price": 0},
                       headers=auth).status_code == 422
    assert client.post("/api/seller/products", json={"name": "  ", "price": 10},
                       headers=auth).status_code == 422


def test_manual_freeze_blocks_new_intent(client, auth, published):
    freeze = client.post(f"/api/seller/products/{published}/freeze", headers=auth)
    assert freeze.status_code == 200
    assert freeze.json()["product"]["status"] == "FROZEN"

    cur = client.get("/api/products/current").json()
    assert cur["state"] == "FROZEN"
    assert cur["message"] == "商品交易中"

    blocked = submit_intent(client, published, "张三")
    assert blocked.status_code == 409
    assert "交易中" in blocked.json()["error"]["message"]

    unfreeze = client.post(f"/api/seller/products/{published}/unfreeze", headers=auth)
    assert unfreeze.status_code == 200
    assert unfreeze.json()["product"]["status"] == "ON_SALE"
    assert submit_intent(client, published, "张三").status_code == 200


def test_off_shelf_invalidates_command_code(client, auth, published):
    created = submit_intent(client, published, "李四").json()
    code = created["command_code"]
    assert client.get(f"/api/buyer/intents/{code}").status_code == 200

    off = client.post(f"/api/seller/products/{published}/off-shelf", headers=auth)
    assert off.status_code == 200
    assert client.get("/api/products/current").json()["state"] == "NONE"
    # 商品下架 -> 口令码失效
    assert client.get(f"/api/buyer/intents/{code}").status_code == 410
