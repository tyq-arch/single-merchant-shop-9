"""重置后端数据库并预置手动测试所需数据。

重置后卖家密码恢复为 seller123；预置：1 个一级 + 1 个二级类别、2 件在售商品。
"""
import json
import os
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8000"


def api(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw or "{}")
        except Exception:
            return e.code, {"_raw": raw[:200]}
    except Exception as e:
        return "ERR", {"_err": str(e)}


# 1) 卖家登录（重置后应为 seller123）
s, b = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
print("卖家登录:", s, "->", "成功" if s == 200 else b)
if s != 200:
    raise SystemExit("卖家登录失败，请确认数据库已重置")

tok = b["token"]

# 2) 建类别
s, c1 = api("POST", "/api/seller/categories", {"name": "手动测试大类"}, tok)
pid = (c1.get("category") or {}).get("id")
print("一级类别:", s, "id=", pid)

s, c2 = api("POST", "/api/seller/categories", {"name": "手动测试小类", "parent_id": pid}, tok)
cid = (c2.get("category") or {}).get("id")
print("二级类别:", s, "id=", cid)

# 3) 发布两件商品
for name, price, stock in [("手动测试商品A", 88.0, 3), ("手动测试商品B", 199.0, 1)]:
    s, p = api("POST", "/api/seller/products", {
        "name": name, "price": price, "description": "手动测试用商品",
        "stock": stock, "category_id": cid, "image_urls": [],
    }, tok)
    prod = p.get("product") or {}
    print("发布 %-12s HTTP %s  id=%s stock=%s" % (name, s, prod.get("id", "-")[:8], prod.get("stock")))

# 4) 确认现状
s, lst = api("GET", "/api/products?page_size=100")
print()
print("当前在售商品数:", lst.get("total"))
for it in lst.get("items", []):
    print("   ", it.get("status"), "| stock=%s |" % it.get("stock"), it.get("name"))
