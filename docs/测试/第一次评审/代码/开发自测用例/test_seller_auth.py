"""卖家登录与鉴权测试。"""
import pytest


def test_login_success(client):
    resp = client.post("/api/seller/login", json={"username": "seller", "password": "seller123"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["token"]
    assert body["seller"]["username"] == "seller"


def test_login_wrong_password(client):
    resp = client.post("/api/seller/login", json={"username": "seller", "password": "bad"})
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "UNAUTHORIZED"


def test_no_seller_register_endpoint(client):
    # 无注册流程：卖家注册接口不存在
    assert client.post("/api/seller/register", json={}).status_code == 404


def test_seller_endpoint_requires_token(client):
    assert client.get("/api/seller/history").status_code == 401
    assert client.get("/api/seller/product/current",
                      headers={"Authorization": "Bearer bad-token"}).status_code == 401


def test_change_password(client, auth):
    bad = client.post("/api/seller/password",
                      json={"old_password": "wrong", "new_password": "newpass123"}, headers=auth)
    assert bad.status_code == 401

    ok = client.post("/api/seller/password",
                     json={"old_password": "seller123", "new_password": "newpass123"}, headers=auth)
    assert ok.status_code == 200

    assert client.post("/api/seller/login",
                       json={"username": "seller", "password": "seller123"}).status_code == 401
    assert client.post("/api/seller/login",
                       json={"username": "seller", "password": "newpass123"}).status_code == 200
