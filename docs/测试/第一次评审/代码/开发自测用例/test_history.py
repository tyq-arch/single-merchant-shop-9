"""历史记录、意向购买人列表与导出测试。"""
from conftest import submit_intent


def _submit(client, product_id, name, phone):
    return submit_intent(client, product_id, name, phone).json()


def test_multi_round_history_kept(client, auth, published):
    a = _submit(client, published, "A", "13800000001")
    b = _submit(client, published, "B", "13800000002")

    # 第 1 轮：A 失败
    client.post(f"/api/seller/products/{published}/start-transaction", headers=auth)
    client.post(f"/api/seller/orders/{a['order']['order_id']}/transaction",
                json={"result": "FAILED"}, headers=auth)
    client.post(f"/api/seller/orders/{a['order']['order_id']}/void", headers=auth)
    # 第 2 轮：B 成功 -> 商品下架
    client.post(f"/api/seller/orders/{b['order']['order_id']}/transaction",
                json={"result": "SUCCESS"}, headers=auth)

    # 历史列表
    history = client.get("/api/seller/history", headers=auth).json()
    assert history["total"] == 1
    item = history["items"][0]
    assert item["transaction_summary"]["failed_rounds"] == 1
    assert item["transaction_summary"]["final_result"] == "SUCCESS"

    # 历史详情：商品快照 + 多次交易记录全部保留
    detail = client.get(f"/api/seller/history/{published}", headers=auth).json()
    assert len(detail["transactions"]) == 2
    assert [t["result"] for t in detail["transactions"]] == ["FAILED", "SUCCESS"]
    assert len(detail["buyers"]) == 2
    # 历史买家信息脱敏
    assert detail["buyers"][0]["buyer_phone"] == "138****0001"


def test_intents_list_and_csv_export(client, auth, published):
    _submit(client, published, "A", "13800000001")
    _submit(client, published, "B", "13800000002")

    listing = client.get(f"/api/seller/products/{published}/intents", headers=auth).json()
    assert listing["total"] == 2
    # 商品仍在售 -> 未终结订单不脱敏，卖家可看到完整电话
    assert listing["items"][0]["buyer_phone"] == "13800000001"
    assert [i["queue_position"] for i in listing["items"]] == [1, 2]

    export = client.get(f"/api/seller/products/{published}/intents/export", headers=auth)
    assert export.status_code == 200
    assert "text/csv" in export.headers["content-type"]
    text = export.content.decode("utf-8-sig")
    assert "姓名" in text and "联系电话" in text
    assert "13800000001" in text


def test_history_detail_requires_off_shelf(client, auth, published):
    resp = client.get(f"/api/seller/history/{published}", headers=auth)
    assert resp.status_code == 409


def test_operation_logs_recorded(client, auth, published):
    logs = client.get("/api/seller/logs", headers=auth).json()
    actions = [row["action"] for row in logs["items"]]
    assert "LOGIN" in actions
    assert "PUBLISH_PRODUCT" in actions
