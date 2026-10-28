"""交易流程测试：进入交易、标记成败、自动递补、失败者作废/重新排队。"""
from conftest import submit_intent


def _submit(client, product_id, name):
    return submit_intent(client, product_id, name).json()


def test_start_then_success(client, auth, published):
    a = _submit(client, published, "A")
    b = _submit(client, published, "B")

    start = client.post(f"/api/seller/products/{published}/start-transaction", headers=auth)
    assert start.status_code == 200
    assert start.json()["order"]["order_id"] == a["order"]["order_id"]
    assert start.json()["order"]["status"] == "IN_TRANSACTION"
    assert start.json()["product"]["status"] == "FROZEN"

    # 进入交易后买家不可撤销
    blocked = client.post("/api/buyer/intents/cancel", json={"code": a["command_code"]})
    assert blocked.status_code == 409
    # 冻结状态下不可再提交新意向
    assert submit_intent(client, published, "C").status_code == 409

    mark = client.post(f"/api/seller/orders/{a['order']['order_id']}/transaction",
                       json={"result": "SUCCESS"}, headers=auth)
    assert mark.status_code == 200
    assert mark.json()["product"]["status"] == "OFF_SHELF"
    # 商品下架后关联口令码全部失效
    assert client.get(f"/api/buyer/intents/{a['command_code']}").status_code == 410
    assert client.get(f"/api/buyer/intents/{b['command_code']}").status_code == 410


def test_failure_auto_advance_then_void(client, auth, published):
    a = _submit(client, published, "A")
    b = _submit(client, published, "B")
    client.post(f"/api/seller/products/{published}/start-transaction", headers=auth)

    mark = client.post(f"/api/seller/orders/{a['order']['order_id']}/transaction",
                       json={"result": "FAILED"}, headers=auth)
    assert mark.status_code == 200
    assert mark.json()["order"]["status"] == "FAILED"
    assert mark.json()["product"]["status"] == "FROZEN"   # 有人递补 -> 再次冻结

    # 失败者口令码失效
    assert client.get(f"/api/buyer/intents/{a['command_code']}").status_code == 410
    # 下一位自动递补进入交易
    b_status = client.get(f"/api/buyer/intents/{b['command_code']}").json()
    assert b_status["order"]["status"] == "IN_TRANSACTION"

    void = client.post(f"/api/seller/orders/{a['order']['order_id']}/void", headers=auth)
    assert void.status_code == 200
    assert void.json()["order"]["status"] == "VOIDED"


def test_failure_with_empty_queue_returns_on_sale(client, auth, published):
    a = _submit(client, published, "A")
    client.post(f"/api/seller/products/{published}/start-transaction", headers=auth)
    mark = client.post(f"/api/seller/orders/{a['order']['order_id']}/transaction",
                       json={"result": "FAILED"}, headers=auth)
    assert mark.json()["product"]["status"] == "ON_SALE"

    # 商品恢复在售，可继续接收新意向
    assert submit_intent(client, published, "B").status_code == 200
    # 失败者可重新排队
    requeue = client.post(f"/api/seller/orders/{a['order']['order_id']}/requeue", headers=auth)
    assert requeue.status_code == 200
    assert requeue.json()["queue_position"] == 2      # 排到队尾
    assert requeue.json()["command_code"]
    # 新口令码可正常查询
    assert client.get(f"/api/buyer/intents/{requeue.json()['command_code']}").status_code == 200


def test_requeue_only_once(client, auth, published):
    a = _submit(client, published, "A")
    b = _submit(client, published, "B")
    client.post(f"/api/seller/products/{published}/start-transaction", headers=auth)
    client.post(f"/api/seller/orders/{a['order']['order_id']}/transaction",
                json={"result": "FAILED"}, headers=auth)

    assert client.post(f"/api/seller/orders/{a['order']['order_id']}/requeue",
                       headers=auth).status_code == 200
    # 最多重新排队 1 次
    again = client.post(f"/api/seller/orders/{a['order']['order_id']}/requeue", headers=auth)
    assert again.status_code == 409


def test_void_requires_failed_state(client, auth, published):
    a = _submit(client, published, "A")
    assert client.post(f"/api/seller/orders/{a['order']['order_id']}/void",
                       headers=auth).status_code == 409


def test_start_transaction_requires_queue(client, auth, published):
    resp = client.post(f"/api/seller/products/{published}/start-transaction", headers=auth)
    assert resp.status_code == 409
    assert "队列为空" in resp.json()["error"]["message"]
