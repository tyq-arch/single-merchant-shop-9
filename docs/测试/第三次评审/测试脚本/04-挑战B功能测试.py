"""挑战B 全量功能测试（干净库，按功能模块组织）
L 商品与搜索 / A 收藏 / B 购物车 / C 下单 / D 转收藏 / E 订单流转 / F 取消
G 库存 / H 查询台账 / I 游客 / J 上限 / M 基础校验
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

BASE = "http://127.0.0.1:8000"
ROOT = r"C:\Users\33857\Desktop\软件工程实践作业"
OUT = os.path.join(ROOT, "_runtime", "evidence")
UID = "F" + str(int(time.time()))[-6:]
RES = []
LAST = {}


def api(m, p, body=None, token=None, guest=None, headers=None,
        raw_body=None, ctype=None):
    if "?" in p:
        path_, q = p.split("?", 1)
        p = path_ + "?" + urllib.parse.quote(q, safe="=&%")
    if raw_body is not None:
        d = raw_body
    else:
        d = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    r = urllib.request.Request(BASE + p, data=d, method=m)
    if d is not None:
        r.add_header("Content-Type", ctype or "application/json; charset=utf-8")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    if guest:
        r.add_header("X-Guest-Id", guest)
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    LAST.clear()
    LAST.update({"method": m, "path": p, "body": body if raw_body is None else "(binary)",
                 "auth": bool(token), "guest": guest})
    try:
        with urllib.request.urlopen(r, timeout=25) as x:
            return x.status, x.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return "ERR", json.dumps({"err": str(e)})


def J(raw):
    try:
        return json.loads(raw)
    except Exception:
        return {}


def case(cid, mod, title, exp, st, raw, ok, note=""):
    RES.append({"id": cid, "module": mod, "title": title, "expected": exp,
                "status": st, "raw": raw[:420], "result": "PASS" if ok else "FAIL",
                "note": note, "req": dict(LAST)})
    print("  [%s] %-5s %-4s %s" % ("PASS" if ok else "FAIL", cid, st, title[:56]))
    return st, raw


print("=" * 82)
print("挑战B 全量功能测试   轮次 %s" % UID)
print("=" * 82)

# ================= 准备 =================
s, b = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
if s != 200:
    raise SystemExit("卖家登录失败 %s —— 需要干净库" % s)
STOK = J(b)["token"]

api("POST", "/api/seller/categories", {"name": "全量大类%s" % UID}, STOK)
s, c = api("GET", "/api/seller/categories", token=STOK)
TOP = [x for x in J(c)["items"] if x["name"] == "全量大类%s" % UID][0]["id"]
api("POST", "/api/seller/categories", {"name": "全量小类%s" % UID, "parent_id": TOP}, STOK)
s, c2 = api("GET", "/api/seller/categories", token=STOK)
CAT = [x for ch in J(c2)["items"] for x in (ch.get("children") or [])
       if x["name"] == "全量小类%s" % UID][0]["id"]


def pub(key, price, stock):
    s, p = api("POST", "/api/seller/products", {"name": "%s_%s" % (key, UID), "price": price,
        "description": "x", "stock": stock, "category_id": CAT, "image_urls": []}, STOK)
    return (J(p).get("product") or {}).get("id")


P = {k: pub("商品" + k, pr, st) for k, pr, st in
     [("A", 10.0, 10), ("B", 20.0, 5), ("C", 5.0, 3), ("D", 8.0, 2),
      ("E", 15.0, 100), ("F", 9.0, 5), ("G", 7.0, 5), ("H", 30.0, 4)]}
CUS = {}
for k in ("U1", "U2", "U3"):
    u = "%s_%s" % (k.lower(), UID)
    api("POST", "/api/customer/register", {"username": u, "password": "Pass1234!",
        "phone": "13800000000", "trade_location": "测试"})
    CUS[k] = J(api("POST", "/api/customer/login", {"username": u, "password": "Pass1234!"})[1]).get("token")
T1, T2, T3 = CUS["U1"], CUS["U2"], CUS["U3"]
print("\n准备完成：8 件商品 + 3 个客户")

# ================= L 商品与搜索 =================
print("\n[L] 商品与搜索")
st, raw = api("GET", "/api/products?page_size=100")
items = J(raw).get("items", [])
case("L01", "商品与搜索", "公开列表只返回在售商品", "无 FROZEN/OFF_SHELF",
     st, raw, all(x.get("status") in ("ON_SALE", None) for x in items),
     "状态集合 %s" % sorted({x.get("status") for x in items}))
st, raw = api("POST", "/api/seller/products/%s/freeze" % P["F"], {}, token=STOK)
case("L02", "商品与搜索", "冻结商品成功", "200", st, raw, st == 200)
st, raw = api("GET", "/api/products?keyword=商品F_%s&page_size=100" % UID)
case("L03", "商品与搜索", "冻结后公开列表查不到", "0", st, raw,
     J(raw).get("total", 0) == 0, "total=%s" % J(raw).get("total"))
st, raw = api("GET", "/api/seller/products?page_size=100", token=STOK)
case("L04", "商品与搜索", "冻结后卖家后台仍可见", "包含", st, raw,
     any(("商品F_%s" % UID) in (x.get("name") or "") for x in J(raw).get("items", [])))
st, raw = api("POST", "/api/seller/products/%s/off-shelf" % P["G"], {}, token=STOK)
case("L05", "商品与搜索", "下架商品成功", "200", st, raw, st == 200)
st, raw = api("GET", "/api/products?keyword=商品G_%s&page_size=100" % UID)
case("L06", "商品与搜索", "下架后公开列表查不到", "0", st, raw,
     J(raw).get("total", 0) == 0, "total=%s" % J(raw).get("total"))
st, raw = api("GET", "/api/products?keyword=商品A_%s&page_size=100" % UID)
case("L07", "商品与搜索", "按名称搜索能命中", ">=1", st, raw,
     J(raw).get("total", 0) >= 1, "total=%s" % J(raw).get("total"))
st, raw = api("GET", "/api/products?keyword=ZZ不存在ZZ&page_size=100")
case("L08", "商品与搜索", "搜不到时 total=0（前端显示空状态）", "0", st, raw,
     J(raw).get("total", 0) == 0, "total=%s" % J(raw).get("total"))
st, raw = api("GET", "/api/products?keyword=%25&page_size=100")
case("L09", "商品与搜索", "LIKE 通配符 % 应被转义（只匹配字面 %）", "0", st, raw,
     J(raw).get("total", 0) == 0, "total=%s" % J(raw).get("total"))
st, raw = api("GET", "/api/products?keyword=_&page_size=100")
case("L10", "商品与搜索", "LIKE 通配符 _ 应被转义", "0", st, raw,
     J(raw).get("total", 0) == 0, "total=%s" % J(raw).get("total"))
st, raw = api("GET", "/api/products/%s" % P["A"])
case("L11", "商品与搜索", "商品详情可访问", "200", st, raw, st == 200)
st, raw = api("GET", "/api/products/not-exist-id")
case("L12", "商品与搜索", "不存在商品详情应 404", "404", st, raw, st == 404)

# ================= A 收藏 =================
print("\n[A] 收藏")
st, raw = api("POST", "/api/customer/favorites", {"product_id": P["A"]}, token=T1)
case("A01", "收藏", "加收藏成功", "200", st, raw, st == 200)
st, raw = api("POST", "/api/customer/favorites", {"product_id": P["A"]}, token=T1)
case("A02", "收藏", "重复收藏 409", "409", st, raw, st == 409)
st, raw = api("GET", "/api/customer/favorites?page_size=100", token=T1)
case("A03", "收藏", "收藏列表 1 条", "1", st, raw, len(J(raw).get("items", [])) == 1)
st, raw = api("DELETE", "/api/customer/favorites/%s" % P["A"], token=T1)
case("A04", "收藏", "取消收藏成功", "200", st, raw, st == 200)
st, raw = api("GET", "/api/customer/favorites?page_size=100", token=T1)
case("A05", "收藏", "取消后为空", "0", st, raw, len(J(raw).get("items", [])) == 0)
st, raw = api("POST", "/api/customer/favorites", {"product_id": "no-such"}, token=T1)
case("A06", "收藏", "收藏不存在商品 404", "404", st, raw, st == 404)
st, raw = api("GET", "/api/customer/favorites?page_size=100", token=T2)
case("A07", "收藏", "他人收藏不可见", "0", st, raw, len(J(raw).get("items", [])) == 0)
st, raw = api("POST", "/api/customer/favorites", {"product_id": P["G"]}, token=T1)
case("A08", "收藏", "收藏已下架商品（记录现状）", "409", st, raw, st == 409, "HTTP %s" % st)
st, raw = api("POST", "/api/customer/favorites", {"product_id": P["B"]}, token=T1)
case("A09", "收藏", "再收藏一件成功", "200", st, raw, st == 200)

# ================= B 购物车 =================
print("\n[B] 购物车")
st, raw = api("POST", "/api/customer/cart", {"product_id": P["A"], "quantity": 2}, token=T1)
case("B01", "购物车", "加购成功", "200", st, raw, st == 200)
st, raw = api("POST", "/api/customer/cart", {"product_id": P["A"], "quantity": 3}, token=T1)
q = [x for x in J(raw).get("items", []) if x.get("product_id") == P["A"]]
case("B02", "购物车", "重复加购合并数量 2+3=5", "5", st, raw,
     bool(q) and q[0].get("quantity") == 5, "qty=%s" % (q[0].get("quantity") if q else None))
st, raw = api("PATCH", "/api/customer/cart/%s" % P["A"], {"quantity": 4}, token=T1)
case("B03", "购物车", "改数量为 4", "200", st, raw, st == 200)
st, raw = api("PATCH", "/api/customer/cart/%s" % P["A"], {"quantity": 0}, token=T1)
case("B04", "购物车", "数量 0 → 422（下限 1）", "422", st, raw, st == 422)
st, raw = api("PATCH", "/api/customer/cart/%s" % P["A"], {"quantity": 100}, token=T1)
case("B05", "购物车", "数量 100 → 422（上限 99）", "422", st, raw, st == 422)
st, raw = api("PATCH", "/api/customer/cart/%s" % P["A"], {"quantity": 99}, token=T1)
case("B06", "购物车", "数量 99 通过", "200", st, raw, st == 200)
st, raw = api("POST", "/api/customer/cart", {"product_id": P["A"], "quantity": 1}, token=T1)
case("B07", "购物车", "合并后超 99 → 409", "409", st, raw, st == 409)
st, raw = api("DELETE", "/api/customer/cart/%s" % P["A"], token=T1)
case("B08", "购物车", "移除条目成功", "200", st, raw, st == 200)
st, raw = api("POST", "/api/customer/cart", {"product_id": "no-such", "quantity": 1}, token=T1)
case("B09", "购物车", "加购不存在商品 404", "404", st, raw, st == 404)
st, raw = api("GET", "/api/customer/cart", token=T2)
case("B10", "购物车", "他人购物车不可见", "0", st, raw, len(J(raw).get("items", [])) == 0)
st, raw = api("POST", "/api/customer/cart", {"product_id": P["G"], "quantity": 1}, token=T1)
case("B11", "购物车", "加购已下架商品（记录现状）", "409", st, raw, st == 409, "HTTP %s" % st)

# ================= C 下单 =================
print("\n[C] 下单")
api("POST", "/api/customer/cart", {"product_id": P["A"], "quantity": 2}, token=T1)
api("POST", "/api/customer/cart", {"product_id": P["B"], "quantity": 2}, token=T1)
st, raw = api("POST", "/api/customer/orders", {"product_ids": [P["A"], P["B"]]}, token=T1)
o = J(raw).get("order") or {}
OM = o.get("id")
case("C01", "下单", "两件商品一次下单", "200 且 2 个订单项", st, raw,
     st == 200 and OM is not None and len(o.get("items") or []) == 2,
     "订单项 %d" % len(o.get("items") or []))
amt = o.get("total_amount") or o.get("total") or o.get("amount")
case("C02", "下单", "订单金额 10×2 + 20×2 = 60", "60", st, raw, str(amt) in ("60", "60.0", "60.00"),
     "实际 %s" % amt)
st, raw = api("GET", "/api/customer/cart", token=T1)
case("C03", "下单", "下单后购物车清空对应条目", "0", st, raw, len(J(raw).get("items", [])) == 0)
st, raw = api("POST", "/api/customer/orders", {"product_ids": []}, token=T1)
case("C04", "下单", "空 product_ids → 422", "422", st, raw, st == 422)
st, raw = api("POST", "/api/customer/orders", {"product_ids": [P["C"]]}, token=T1)
case("C05", "下单", "商品不在购物车 → 409", "409", st, raw, st == 409)
st, raw = api("POST", "/api/customer/orders", {"product_ids": [P["A"]]})
case("C06", "下单", "未登录下单 → 401", "401", st, raw, st == 401)
st, raw = api("POST", "/api/customer/orders", {"product_ids": ["no-such"]}, token=T1)
case("C07", "下单", "下单不存在商品（记录现状）", "409", st, raw, st == 409, "HTTP %s" % st)

# ================= D 转收藏 =================
print("\n[D] 购物车转收藏")
api("POST", "/api/customer/cart", {"product_id": P["C"], "quantity": 1}, token=T2)
st, raw = api("POST", "/api/customer/cart/favorites", {"product_ids": [P["C"]]}, token=T2)
case("D01", "转收藏", "购物车转收藏成功", "200", st, raw, st == 200)
st, raw = api("GET", "/api/customer/cart", token=T2)
case("D02", "转收藏", "转后原购物车条目移除", "0", st, raw, len(J(raw).get("items", [])) == 0)
st, raw = api("GET", "/api/customer/favorites?page_size=100", token=T2)
case("D03", "转收藏", "收藏中出现该商品", ">=1", st, raw, len(J(raw).get("items", [])) >= 1)
st, raw = api("POST", "/api/customer/cart/favorites", {"product_ids": [P["E"]]}, token=T2)
case("D04", "转收藏", "转不在购物车的商品 → 409", "409", st, raw, st == 409)

# ================= E 订单流转 =================
print("\n[E] 订单 5 态流转")
st, raw = api("GET", "/api/customer/orders/%s" % OM, token=T1)
case("E01", "订单流转", "新订单初始态 CUSTOMER_PLACED", "CUSTOMER_PLACED", st, raw,
     (J(raw).get("order") or {}).get("status") == "CUSTOMER_PLACED")
for i, want in enumerate(["SELLER_CONFIRMED", "STOCK_READY", "SHIPPING", "COMPLETED"], 1):
    st, raw = api("POST", "/api/seller/orders/%s/advance" % OM, {}, token=STOK)
    cur = (J(raw).get("order") or {}).get("status")
    case("E1%d" % i, "订单流转", "推进第 %d 步 → %s" % (i, want), want, st, raw,
         st == 200 and cur == want, "实际 %s" % cur)
st, raw = api("POST", "/api/seller/orders/%s/advance" % OM, {}, token=STOK)
case("E20", "订单流转", "已完成再推进 → 409", "409", st, raw, st == 409)
st, raw = api("POST", "/api/customer/orders/%s/cancel" % OM, {}, token=T1)
case("E21", "订单流转", "已完成客户取消 → 409", "409", st, raw, st == 409)

# ================= F 取消 =================
print("\n[F] 双向取消")


def mk(pid, qty=1, adv=0, tok=T1):
    api("POST", "/api/customer/cart", {"product_id": pid, "quantity": qty}, token=tok)
    s2, r2 = api("POST", "/api/customer/orders", {"product_ids": [pid]}, token=tok)
    oid = (J(r2).get("order") or {}).get("id")
    for _ in range(adv):
        api("POST", "/api/seller/orders/%s/advance" % oid, {}, token=STOK)
    return oid


for i, adv in enumerate([0, 1, 2], 1):
    oid = mk(P["C"], 1, adv)
    st, raw = api("POST", "/api/customer/orders/%s/cancel" % oid, {}, token=T1)
    case("F1%d" % i, "取消", "客户在第 %d 态可取消" % (adv + 1), "200", st, raw, st == 200)
for i, adv in enumerate([0, 1, 2, 3], 1):
    oid = mk(P["C"], 1, adv)
    st, raw = api("POST", "/api/seller/orders/%s/cancel" % oid, {}, token=STOK)
    case("F2%d" % i, "取消", "商家在第 %d 态可取消" % (adv + 1), "200", st, raw, st == 200)
oid = mk(P["C"], 1, 3)
st, raw = api("POST", "/api/customer/orders/%s/cancel" % oid, {}, token=T1)
case("F31", "取消", "已发货后客户取消 → 409", "409", st, raw, st == 409)
st, raw = api("POST", "/api/customer/orders/%s/cancel" % oid, {}, token=T1)
case("F32", "取消", "重复取消 → 409", "409", st, raw, st == 409)
oid = mk(P["C"], 1, 4)
st, raw = api("POST", "/api/seller/orders/%s/cancel" % oid, {}, token=STOK)
case("F33", "取消", "已完成后商家取消 → 409", "409", st, raw, st == 409)

# ================= G 库存 =================
print("\n[G] 库存模型")
s1, r1 = api("GET", "/api/products/%s" % P["D"])
before = (J(r1).get("product") or {}).get("stock")
api("POST", "/api/customer/cart", {"product_id": P["D"], "quantity": 2}, token=T1)
s2, r2 = api("POST", "/api/customer/orders", {"product_ids": [P["D"]]}, token=T1)
OD = (J(r2).get("order") or {}).get("id")
s3, r3 = api("GET", "/api/products/%s" % P["D"])
after = (J(r3).get("product") or {}).get("stock")
case("G01", "库存", "下单占用：%s → %s" % (before, after), "减 2", s3, r3,
     isinstance(before, int) and isinstance(after, int) and before - after == 2)
st, raw = api("POST", "/api/customer/orders/%s/cancel" % OD, {}, token=T1)
s4, r4 = api("GET", "/api/products/%s" % P["D"])
case("G02", "库存", "取消回补：回到 %s" % before, str(before), s4, r4,
     (J(r4).get("product") or {}).get("stock") == before)
api("POST", "/api/customer/cart", {"product_id": P["D"], "quantity": 2}, token=T1)
s2, r2 = api("POST", "/api/customer/orders", {"product_ids": [P["D"]]}, token=T1)
OD2 = (J(r2).get("order") or {}).get("id")
for _ in range(4):
    api("POST", "/api/seller/orders/%s/advance" % OD2, {}, token=STOK)
s5, r5 = api("GET", "/api/products/%s" % P["D"])
case("G03", "库存", "完成扣减：不重复扣，仍为 %s" % after, str(after), s5, r5,
     (J(r5).get("product") or {}).get("stock") == after)
api("POST", "/api/customer/cart", {"product_id": P["H"], "quantity": 10}, token=T1)
st, raw = api("POST", "/api/customer/orders", {"product_ids": [P["H"]]}, token=T1)
det = (J(raw).get("error") or {}).get("details")
case("G04", "库存", "库存不足下单 → 409 + shortages 明细", "409 + details", st, raw,
     st == 409 and isinstance(det, list) and len(det) > 0,
     "details %d 项" % (len(det) if isinstance(det, list) else 0))
st, raw = api("POST", "/api/customer/cart", {"product_id": P["H"], "quantity": 1}, token=T1)
case("G05", "库存", "加购允许超库存（购物车不锁库存）", "200", st, raw, st == 200)

# ================= H 查询台账 =================
print("\n[H] 查询与台账")
st, raw = api("GET", "/api/customer/orders?page_size=100", token=T1)
n_o = len(J(raw).get("items", []))
case("H01", "查询台账", "客户订单列表可读", ">=1", st, raw, n_o >= 1, "%d 单" % n_o)
st, raw = api("GET", "/api/customer/orders?status=COMPLETED&page_size=100", token=T1)
its = J(raw).get("items", [])
case("H02", "查询台账", "按状态筛选 COMPLETED", "全部 COMPLETED", st, raw,
     all(x.get("status") == "COMPLETED" for x in its), "%d 单" % len(its))
st, raw = api("GET", "/api/customer/orders/%s" % OM, token=T2)
case("H03", "查询台账", "他人订单详情 → 404", "404", st, raw, st == 404)
st, raw = api("GET", "/api/seller/orders?page_size=100", token=STOK)
case("H04", "查询台账", "商家订单列表可读", "200", st, raw, st == 200)
st, raw = api("GET", "/api/seller/orders/%s" % OM, token=STOK)
case("H05", "查询台账", "商家订单详情可读", "200", st, raw, st == 200)
st, raw = api("GET", "/api/seller/customers?page_size=100", token=STOK)
case("H06", "查询台账", "客户台账可读", "200", st, raw, st == 200)
st, raw = api("GET", "/api/seller/history?page_size=100", token=STOK)
case("H07", "查询台账", "商家历史可读", "200", st, raw, st == 200)
st, raw = api("GET", "/api/seller/customers/no-such", token=STOK)
case("H08", "查询台账", "不存在客户 → 404", "404", st, raw, st == 404)

# ================= I 游客 =================
print("\n[I] 游客会话")
G1 = "f1-%s" % UID
G2 = "f2-%s" % UID
st, raw = api("POST", "/api/guest/cart", {"product_id": P["E"], "quantity": 1}, guest=G1)
case("I01", "游客会话", "游客加购成功", "200", st, raw, st == 200)
st, raw = api("GET", "/api/guest/cart", guest=G1)
case("I02", "游客会话", "游客能读到自己购物车", "1", st, raw, len(J(raw).get("items", [])) == 1)
st, raw = api("GET", "/api/guest/cart", guest=G2)
case("I03", "游客会话", "另一游客看不到（隔离）", "0", st, raw, len(J(raw).get("items", [])) == 0)
st, raw = api("POST", "/api/guest/favorites", {"product_id": P["E"]}, guest=G1)
case("I04", "游客会话", "游客可收藏", "200", st, raw, st == 200)
st, raw = api("GET", "/api/customer/favorites?page_size=100", token=T3)
case("I05", "游客会话", "游客收藏不混入客户收藏", "0", st, raw, len(J(raw).get("items", [])) == 0)
st, raw = api("POST", "/api/guest/orders", {"product_ids": [P["E"]]}, guest=G1)
case("I06", "游客会话", "游客不能下单 → 401", "401", st, raw, st == 401)
st, raw = api("GET", "/api/guest/cart")
case("I07", "游客会话", "缺 X-Guest-Id 查购物车 → 422", "422", st, raw, st in (401, 422))

# ================= J 上限 =================
print("\n[J] 上限与分页")
st, raw = api("GET", "/api/customer/favorites?page_size=100", token=T1)
case("J01", "上限分页", "收藏 page_size=100 可用（原来 422）", "200", st, raw, st == 200)
st, raw = api("GET", "/api/customer/favorites?page_size=101", token=T1)
case("J02", "上限分页", "page_size=101 → 422", "422", st, raw, st == 422)
st, raw = api("GET", "/api/customer/cart", token=T1)
case("J03", "上限分页", "购物车条目数 ≤ 50", "<=50", st, raw, len(J(raw).get("items", [])) <= 50)
st, raw = api("GET", "/api/products?page_size=100")
case("J04", "上限分页", "商品列表 page_size=100 可用", "200", st, raw, st == 200)

# ================= M 基础校验 =================
print("\n[M] 基础校验回归")
for cid, pwd, want in [("M01", "pass1234", 422), ("M02", "Ab1!ab", 422),
                       ("M03", "Abcd1234", 200), ("M04", "Pass1234!", 200)]:
    st, raw = api("POST", "/api/customer/register", {"username": "m%s_%s" % (cid, UID),
        "password": pwd, "phone": "13800000000", "trade_location": "测试"})
    case(cid, "基础校验", "注册密码 %r" % pwd, "HTTP %d" % want, st, raw, st == want)
for cid, ph, want in [("M05", "13800000000", 200), ("M06", "01012345678", 200),
                      ("M07", "12345", 422)]:
    st, raw = api("POST", "/api/customer/register", {"username": "mp%s_%s" % (cid, UID),
        "password": "Pass1234!", "phone": ph, "trade_location": "测试"})
    case(cid, "基础校验", "注册电话 %r" % ph, "HTTP %d" % want, st, raw, st == want)
st, raw = api("POST", "/api/customer/register", {"username": "u1_%s" % UID,
    "password": "Pass1234!", "phone": "13800000000", "trade_location": "测试"})
case("M08", "基础校验", "重复用户名 → 409", "409", st, raw, st == 409)
st, raw = api("POST", "/api/customer/login", {"username": "u1_%s" % UID, "password": "Bad12345!"})
case("M09", "基础校验", "错误密码登录 → 401", "401", st, raw, st == 401)
for cid, price, want in [("M10", 0, 422), ("M11", -5, 422), ("M12", 9999999.01, 422)]:
    st, raw = api("POST", "/api/seller/products", {"name": "mp%s_%s" % (cid, UID), "price": price,
        "description": "x", "stock": 1, "category_id": CAT, "image_urls": []}, STOK)
    case(cid, "基础校验", "价格 %s" % price, "HTTP %d" % want, st, raw, st == want)
for cid, q, want in [("M13", "page=0", 422), ("M14", "page_size=101", 422), ("M15", "page=abc", 422)]:
    st, raw = api("GET", "/api/products?" + q)
    case(cid, "基础校验", q, "HTTP %d" % want, st, raw, st == want)
st, raw = api("POST", "/api/seller/products", {"name": "m16_%s" % UID, "price": 10.0,
    "description": "x", "stock": 1, "image_urls": []}, STOK)
case("M16", "基础校验", "发布不选类别 → 422", "422", st, raw, st == 422)
BND = "----f"
png = bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) + b"\x00" * 32
mpb = (("--%s\r\n" % BND).encode()
       + ('Content-Disposition: form-data; name="file"; filename="t.png"\r\n').encode()
       + b"Content-Type: image/png\r\n\r\n" + png + ("\r\n--%s--\r\n" % BND).encode())
st, raw = api("POST", "/api/files/images", raw_body=mpb,
              ctype="multipart/form-data; boundary=%s" % BND)
case("M17", "基础校验", "未带 token 上传 → 401", "401", st, raw, st == 401)
st, raw = api("POST", "/api/files/images", raw_body=mpb, token=STOK,
              ctype="multipart/form-data; boundary=%s" % BND)
case("M18", "基础校验", "带卖家 token 上传 → 200", "200", st, raw, st == 200)

# ================= 汇总 =================
print("\n" + "=" * 82)
p = sum(1 for r in RES if r["result"] == "PASS")
print("全量功能测试：%d 条，通过 %d，失败 %d" % (len(RES), p, len(RES) - p))
print("=" * 82)
for r in RES:
    if r["result"] == "FAIL":
        print("  [FAIL] %-5s %-10s %s -> HTTP %s（预期 %s）"
              % (r["id"], r["module"], r["title"][:44], r["status"], r["expected"]))

os.makedirs(OUT, exist_ok=True)
f = os.path.join(OUT, "challengeB-all-%s.json" % datetime.now().strftime("%Y%m%d-%H%M%S"))
with open(f, "w", encoding="utf-8") as fh:
    json.dump({"base": BASE, "run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "version": "bc01eaf", "total": len(RES), "passed": p, "results": RES},
              fh, ensure_ascii=False, indent=2)
print("证据:", os.path.relpath(f, ROOT))
