"""买家意向：提交、口令码查询/修改/撤销、FIFO 排队测试。"""
from conftest import submit_intent


def test_submit_intent_returns_code_and_position(client, published):
    body = submit_intent(client, published, "张三", "13800001111").json()
    assert body["command_code"]
    assert body["queue_position"] == 1
    assert body["order"]["status"] == "QUEUED"


def test_queue_is_first_come_first_served(client, published):
    for index, name in enumerate(["A", "B", "C", "D", "E"], start=1):
        body = submit_intent(client, published, name, f"1380000000{index}").json()
        assert body["queue_position"] == index


def test_query_by_code(client, published):
    created = submit_intent(client, published, "张三").json()
    code = created["command_code"]
    body = client.get(f"/api/buyer/intents/{code}").json()
    assert body["order"]["buyer_name"] == "张三"
    assert body["position"] == 1
    assert body["can_edit"] is True and body["can_cancel"] is True


def test_update_info_keeps_position(client, published):
    first = submit_intent(client, published, "张三").json()
    submit_intent(client, published, "李四")
    code = first["command_code"]

    resp = client.patch("/api/buyer/intents",
                        json={"code": code, "buyer_phone": "13900002222"})
    assert resp.status_code == 200
    assert resp.json()["buyer_phone"] == "13900002222"
    assert resp.json()["buyer_name"] == "张三"       # 未提交的字段保持不变
    assert resp.json()["queue_position"] == 1        # 位次不变

    assert client.get(f"/api/buyer/intents/{code}").json()["position"] == 1


def test_cancel_shifts_queue(client, published):
    first = submit_intent(client, published, "张三").json()
    second = submit_intent(client, published, "李四").json()

    cancel = client.post("/api/buyer/intents/cancel", json={"code": first["command_code"]})
    assert cancel.status_code == 200

    # 撤销者口令码失效
    assert client.get(f"/api/buyer/intents/{first['command_code']}").status_code == 410
    # 后者自动补位到第 1 位
    assert client.get(f"/api/buyer/intents/{second['command_code']}").json()["position"] == 1


def test_invalid_code_rejected(client):
    assert client.get("/api/buyer/intents/NOTEXIST00").status_code == 410


def test_submit_requires_valid_phone(client, published):
    resp = client.post("/api/buyer/intents",
                       json={"product_id": published, "buyer_name": "张三", "buyer_phone": "abc"})
    assert resp.status_code == 422
