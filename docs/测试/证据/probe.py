"""缺陷定向验证脚本：针对代码走查中怀疑的边界问题，对运行中的后端逐一验证。

记录每个验证点的请求、实际响应与结论，作为缺陷报告的证据来源。
"""
import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8000"


def call(method, path, body=None, token=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            payload = r.read()
            return r.status, (payload if raw else json.loads(payload.decode("utf-8")))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except Exception:
            return e.code, payload.decode("utf-8", "replace")[:300]
    except Exception as e:
        return "ERR", f"{type(e).__name__}: {e}"


def login():
    s, b = call("POST", "/api/seller/login",
                {"username": "seller", "password": "seller123"})
    return b["token"]


def reset(token):
    """Bring the shop back to 'no active product' so each probe starts clean."""
    s, b = call("GET", "/api/seller/product/current", token=token)
    product = b.get("product") if isinstance(b, dict) else None
    if not product:
        return
    if product["status"] == "FROZEN":
        s, b = call("GET", f"/api/seller/products/{product['id']}/intents?page_size=100", token=token)
        for item in b.get("items", []):
            if item["status"] == "IN_TRANSACTION":
                call("POST", f"/api/seller/orders/{item['order_id']}/transaction",
                     {"result": "FAILED", "note": "验证脚本复位"}, token=token)
    call("POST", f"/api/seller/products/{product['id']}/off-shelf", token=token)


print("=" * 78)
print("验证 1：价格不足最小货币单位（0.001 元）")
print("=" * 78)
tok = login()
reset(tok)
s, b = call("POST", "/api/seller/products",
            {"name": "亚分价格商品", "price": 0.001}, token=tok)
print("POST /api/seller/products  price=0.001")
print("  HTTP", s)
print("  body:", json.dumps(b, ensure_ascii=False)[:400])
reset(tok)

print()
print("=" * 78)
print("验证 2：价格恰好等于文档规定上限 9999999")
print("=" * 78)
s, b = call("POST", "/api/seller/products",
            {"name": "上限价格", "price": 9999999}, token=tok)
print("  HTTP", s, "->", json.dumps(b, ensure_ascii=False)[:200])
reset(tok)

print()
print("=" * 78)
print("验证 3：价格略超上限 9999999.01，预期返回 422")
print("=" * 78)
s, b = call("POST", "/api/seller/products",
            {"name": "超限价格", "price": 9999999.01}, token=tok)
print("  HTTP", s, "->", json.dumps(b, ensure_ascii=False)[:200])
reset(tok)

print()
print("=" * 78)
print("验证 4：历史搜索关键词包含 SQL LIKE 通配符 _")
print("=" * 78)
# publish + sell two products with distinguishable names
for name in ["苹果A", "香蕉B"]:
    call("POST", "/api/seller/products", {"name": name, "price": 10}, token=tok)
    s, p = call("GET", "/api/seller/product/current", token=tok)
    pid = p["product"]["id"]
    s, intent = call("POST", "/api/buyer/intents",
                     {"product_id": pid, "buyer_name": "张三", "buyer_phone": "13800000000"})
    oid = intent["order"]["order_id"] if s == 200 else None
    call("POST", f"/api/seller/products/{pid}/start-transaction", token=tok)
    call("POST", f"/api/seller/orders/{oid}/transaction", {"result": "SUCCESS"}, token=tok)

s, b = call("GET", "/api/seller/history?keyword=%E8%8B%B9%E6%9E%9C", token=tok)
print("  keyword='苹果' -> total", b.get("total"), [i["name"] for i in b.get("items", [])])
s, b = call("GET", "/api/seller/history?keyword=_", token=tok)
print("  keyword='_'    -> total", b.get("total"), [i["name"] for i in b.get("items", [])],
      "  <-- '_' 被当作 LIKE 通配符")
s, b = call("GET", "/api/seller/history?keyword=%25", token=tok)
print("  keyword='%'    -> total", b.get("total"), "  <-- '%' 被当作 LIKE 通配符")

print()
print("=" * 78)
print("验证 5：分页接口的 page / page_size 边界取值")
print("=" * 78)
for q in ["page=0", "page_size=0", "page_size=101", "page=1&page_size=100", "page=999"]:
    s, b = call("GET", f"/api/seller/history?{q}", token=tok)
    print(f"  {q:24} -> HTTP {s}")

print()
print("=" * 78)
print("验证 6：未处理异常的表现（500 是否泄漏堆栈信息）")
print("=" * 78)
s, b = call("POST", "/api/buyer/intents",
            {"product_id": "nonexistent-id", "buyer_name": "张三", "buyer_phone": "13800000000"})
print("  unknown product_id ->", s, json.dumps(b, ensure_ascii=False)[:200])
