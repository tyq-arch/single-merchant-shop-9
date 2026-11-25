"""挑战C 全链路功能测试（63 条）
A 收货地址 / O 下单 / Y 支付 / E 卖家推进 / L 物流 / C 完成 / F 售后 / G 卖家订单

用法：
    # 1) 先起后端（干净数据目录）
    SHOP_DATA_DIR=./data-c java -jar target/shop-backend-1.0.0.jar --server.port=8000
    # 2) 跑本脚本
    python 01-全链路功能测试.py

依赖：Python 3.8+，仅用标准库（urllib / json / sqlite3）
数据库路径：默认 <工作区>/_runtime/jdata-c2/shop-v2.db，可用环境变量 SHOP_DATA_DIR 覆盖
"""
import json
import os
import sqlite3
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

BASE = "http://127.0.0.1:8000"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA_DIR = os.environ.get("SHOP_DATA_DIR", os.path.join(ROOT, "_runtime", "jdata-c2"))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "证据")
DB = os.path.join(DATA_DIR, "shop-v2.db")
UID = "D" + str(int(time.time()))[-6:]
RES = []
LAST = {}


def api(m, p, body=None, token=None, headers=None):
    if "?" in p:
        a, q = p.split("?", 1)
        p = a + "?" + urllib.parse.quote(q, safe="=&%")
    d = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    r = urllib.request.Request(BASE + p, data=d, method=m)
    if d is not None:
        r.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    LAST.clear()
    LAST.update({"method": m, "path": p, "body": body, "auth": bool(token)})
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
                "status": st, "raw": raw, "result": "PASS" if ok else "FAIL",
                "note": note, "req": dict(LAST)})
    print("  [%s] %-5s %-4s %s" % ("PASS" if ok else "FAIL", cid, st, title[:58]))
    return st, raw


def ec(raw):
    return (J(raw).get("error") or {}).get("code", "")


def em(raw):
    return (J(raw).get("error") or {}).get("message", "")


def order_status(raw):
    """从响应里取订单状态"""
    return obj(raw, "order").get("status")


def ship_status(raw):
    """从响应里取物流状态"""
    return obj(raw, "shipment", "item").get("status")


def after_status(raw):
    """从响应里取售后状态"""
    return obj(raw, "after_sale", "item").get("status")


def obj(raw, *keys):
    j = J(raw)
    for k in keys:
        v = j.get(k)
        if isinstance(v, dict):
            return v
    for k in keys:
        v = j.get(k)
        if isinstance(v, list) and v and isinstance(v[0], dict):
            return v[0]
    return j


print("=" * 82)
print("挑战C 全链路功能测试   轮次 %s" % UID)
print("=" * 82)

s, b = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
if s != 200:
    raise SystemExit("卖家登录失败 %s" % s)
STOK = J(b)["token"]

api("POST", "/api/seller/categories", {"name": "C大类%s" % UID}, STOK)
s, c = api("GET", "/api/seller/categories", token=STOK)
TOP = [x for x in J(c)["items"] if x["name"] == "C大类%s" % UID][0]["id"]
api("POST", "/api/seller/categories", {"name": "C小类%s" % UID, "parent_id": TOP}, STOK)
s, c2 = api("GET", "/api/seller/categories", token=STOK)
CAT = [x for ch in J(c2)["items"] for x in (ch.get("children") or [])
       if x["name"] == "C小类%s" % UID][0]["id"]
s, pr = api("POST", "/api/seller/products", {"name": "C商品_%s" % UID, "price": 100.0,
    "description": "x", "stock": 50, "category_id": CAT, "image_urls": []}, STOK)
PID = J(pr)["product"]["id"]
STOCK0 = (J(pr).get("product") or {}).get("stock")


def new_customer(tag):
    u = "%s_%s" % (tag, UID)
    api("POST", "/api/customer/register", {"username": u, "password": "Pass1234!",
        "phone": "13800000000", "trade_location": "测试"})
    return J(api("POST", "/api/customer/login", {"username": u, "password": "Pass1234!"})[1]).get("token")


T1 = new_customer("u1")
T2 = new_customer("u2")
T3 = new_customer("u3")
print("\n准备：商品 100 元 / 库存 %s；3 个客户（u3 不加地址）" % STOCK0)

ADDR = {"receiver_name": "张三", "receiver_phone": "13800000000", "province": "广东省",
        "city": "深圳市", "district": "南山区", "detail": "科技园 1 号", "postal_code": "518000"}


def new_order(qty=1, tok=None):
    tok = tok or T1
    api("POST", "/api/customer/cart", {"product_id": PID, "quantity": qty}, token=tok)
    s2, r2 = api("POST", "/api/customer/orders", {"product_ids": [PID]}, token=tok)
    return obj(r2, "order").get("id")


def pay(order, result="SUCCESS", tok=None):
    return api("POST", "/api/customer/orders/%s/pay" % order,
               {"payment_method": "ALIPAY", "simulate_result": result}, token=tok or T1)


# ================= A 地址 =================
print("\n[A] 收货地址")
s, raw = api("POST", "/api/customer/addresses", ADDR, token=T1)
AID1 = obj(raw, "address", "item", "items").get("id")
case("A01", "收货地址", "新增第一条地址（自动设默认）", "200 且有 ID", s, raw, s == 200 and AID1 is not None)
s, raw = api("GET", "/api/customer/addresses", token=T1)
it = J(raw).get("items", [])
case("A02", "收货地址", "列表 1 条且为默认", "1 条 is_default=true", s, raw,
     len(it) == 1 and bool(it[0].get("is_default")))
s, raw = api("POST", "/api/customer/addresses", dict(ADDR, receiver_name="李四", is_default=True), token=T1)
AID2 = obj(raw, "address", "item", "items").get("id")
s, raw = api("GET", "/api/customer/addresses", token=T1)
it = J(raw).get("items", [])
case("A03", "收货地址", "第二条设默认后，默认唯一", "2 条，仅 1 条默认", s, raw,
     len(it) == 2 and len([x for x in it if x.get("is_default")]) == 1)
s, raw = api("PATCH", "/api/customer/addresses/%s" % AID2, dict(ADDR, detail="改后地址"), token=T1)
case("A04", "收货地址", "修改地址后详情为新值", "改后地址", s, raw,
     s == 200 and "改后地址" in raw, ec(raw))
s, raw = api("POST", "/api/customer/addresses", dict(ADDR, receiver_phone="abc"), token=T1)
case("A05", "收货地址", "电话非法 → 422 字段级错误", "422 VALIDATION_ERROR", s, raw,
     s == 422 and ec(raw) == "VALIDATION_ERROR" and "receiver_phone" in raw, ec(raw))
s, raw = api("POST", "/api/customer/addresses", dict(ADDR, receiver_name=""), token=T1)
case("A06", "收货地址", "姓名为空 → 422 字段级错误", "422 VALIDATION_ERROR", s, raw,
     s == 422 and ec(raw) == "VALIDATION_ERROR" and "receiver_name" in raw, ec(raw))
s, raw = api("POST", "/api/customer/addresses", dict(ADDR, postal_code="12"), token=T1)
case("A07", "收货地址", "邮编非 6 位 → 422 字段级错误", "422 VALIDATION_ERROR", s, raw,
     s == 422 and ec(raw) == "VALIDATION_ERROR" and "postal_code" in raw, ec(raw))
s, raw = api("PATCH", "/api/customer/addresses/no-such", ADDR, token=T1)
case("A08", "收货地址", "改不存在地址 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
s, raw = api("PATCH", "/api/customer/addresses/%s" % AID1, ADDR, token=T2)
case("A09", "收货地址", "改他人地址 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
s, raw = api("GET", "/api/customer/addresses", token=T2)
case("A10", "收货地址", "他人看不到我的地址", "0 条", s, raw, len(J(raw).get("items", [])) == 0)
s, raw = api("GET", "/api/customer/addresses", token=T3)
case("A11", "收货地址", "无地址客户列表为空", "0 条", s, raw, len(J(raw).get("items", [])) == 0)

# ================= O 下单 =================
print("\n[O] 下单")
s, raw = api("POST", "/api/customer/orders", {"product_ids": [PID]}, token=T3)
case("O01", "下单", "无地址客户下单 → 409（FR-048）", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
api("POST", "/api/customer/cart", {"product_id": PID, "quantity": 1}, token=T3)
s, raw = api("POST", "/api/customer/orders", {"product_ids": [PID], "address_id": AID1}, token=T3)
case("O02", "下单", "用他人地址下单 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
s, raw = api("POST", "/api/customer/orders", {"product_ids": [PID], "address_id": "no-such"}, token=T1)
case("O03", "下单", "地址 ID 不存在 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
O1 = new_order(2)
s, raw = api("GET", "/api/customer/orders/%s" % O1, token=T1)
o = obj(raw, "order")
case("O04", "下单", "不传 address_id 自动带默认地址并下单", "200", "200" if O1 else "FAIL",
     raw, O1 is not None and o.get("status") == "PENDING_PAYMENT", "status=%s" % o.get("status"))
case("O05", "下单", "新订单初始状态 PENDING_PAYMENT", "PENDING_PAYMENT", "200", raw,
     o.get("status") == "PENDING_PAYMENT", o.get("status"))
case("O06", "下单", "订单含收货地址快照", "含地址字段", "200", raw,
     any(k in o for k in ("address", "receiver_name", "shipping_address", "address_snapshot")),
     "字段 %s" % list(o.keys())[:10])
print("     订单 O1 =", O1)

# ================= Y 支付 =================
print("\n[Y] 线上支付")
s, raw = pay(O1, "FAILED")
case("Y01", "支付", "模拟支付失败 → 支付记录 FAILED、订单不变", "FAILED", s, raw,
     s == 200 and "FAILED" in raw, "含 FAILED" if "FAILED" in raw else raw[:60])
s, raw = api("GET", "/api/customer/orders/%s" % O1, token=T1)
case("Y02", "支付", "失败后仍 PENDING_PAYMENT", "PENDING_PAYMENT", s, raw,
     obj(raw, "order").get("status") == "PENDING_PAYMENT")
s, raw = pay(O1, "SUCCESS")
case("Y03", "支付", "模拟支付成功 → 支付记录 SUCCESS", "SUCCESS", s, raw,
     s == 200 and "SUCCESS" in raw, "含 SUCCESS" if "SUCCESS" in raw else raw[:60])
s, raw = api("GET", "/api/customer/orders/%s" % O1, token=T1)
case("Y04", "支付", "订单转 PAID", "PAID", s, raw, obj(raw, "order").get("status") == "PAID")
s, raw = pay(O1)
case("Y05", "支付", "重复支付 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
s, raw = api("POST", "/api/customer/orders/no-such/pay", {"payment_method": "ALIPAY"}, token=T1)
case("Y06", "支付", "支付不存在订单 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
O2 = new_order(1)
s, raw = api("POST", "/api/customer/orders/%s/pay" % O2, {"payment_method": "BITCOIN"}, token=T1)
case("Y07", "支付", "非法支付方式 → 422 字段级错误", "422 VALIDATION_ERROR", s, raw,
     s == 422 and ec(raw) == "VALIDATION_ERROR" and "payment_method" in raw, ec(raw))
s, raw = api("POST", "/api/customer/orders/%s/pay" % O2,
             {"payment_method": "WECHAT", "simulate_result": "MAYBE"}, token=T1)
case("Y08", "支付", "非法 simulate_result → 422 字段级错误", "422 VALIDATION_ERROR", s, raw,
     s == 422 and ec(raw) == "VALIDATION_ERROR" and "simulate_result" in raw, ec(raw))
s, raw = api("POST", "/api/payments/callback",
             {"payment_id": "no-such", "transaction_no": "T", "status": "SUCCESS", "signature": "bad"})
case("Y09", "支付回调", "签名错误 → 401", "401", s, raw, s == 401 and ec(raw) == "UNAUTHORIZED", ec(raw))
s, raw = api("POST", "/api/payments/callback",
             {"payment_id": "no-such", "transaction_no": "T", "status": "XX", "signature": ""})
case("Y10", "支付回调", "状态非法 → 422", "422", s, raw, s == 422 and ec(raw) == "VALIDATION_ERROR", ec(raw))

# ================= E 卖家推进 =================
print("\n[E] 卖家推进")
Od = new_order(1)   # 未支付
s, raw = api("POST", "/api/seller/orders/%s/advance" % Od, {}, token=STOK)
case("E01", "卖家推进", "未支付订单推进 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
s, raw = api("POST", "/api/seller/orders/%s/advance" % O1, {}, token=STOK)
case("E02", "卖家推进", "已支付 → 商家确认", "200 且 SELLER_CONFIRMED", s, raw,
     s == 200 and obj(raw, "order").get("status") == "SELLER_CONFIRMED")
s, raw = api("POST", "/api/seller/orders/%s/advance" % O1, {}, token=STOK)
case("E03", "卖家推进", "商家确认 → 备货完成", "200 且 STOCK_READY", s, raw,
     s == 200 and obj(raw, "order").get("status") == "STOCK_READY")
s, raw = api("POST", "/api/seller/orders/%s/advance" % O1, {}, token=STOK)
case("E04", "卖家推进", "备货完成直接推进 → 409（必须走发货 FR-049）", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
s, raw = api("POST", "/api/seller/orders/%s/ship" % O1, {"carrier": ""}, token=STOK)
case("E05", "卖家推进", "发货缺承运商 → 422", "422", s, raw, s == 422 and ec(raw) == "VALIDATION_ERROR", ec(raw))
s, raw = api("POST", "/api/seller/orders/%s/ship" % O1,
             {"carrier": "SF", "tracking_no": "SF123456789"}, token=STOK)
case("E06", "卖家推进", "填物流信息发货 → 订单转 SHIPPING", "200 且 SHIPPING", s, raw,
     s == 200 and obj(raw, "order").get("status") == "SHIPPING",
     obj(raw, "order").get("status"))
s, raw = api("POST", "/api/seller/orders/%s/ship" % O1,
             {"carrier": "SF", "tracking_no": "SF999"}, token=STOK)
case("E07", "卖家推进", "重复发货 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))

# ================= L 物流 =================
print("\n[L] 物流")
s, raw = api("GET", "/api/customer/orders/%s/shipment" % Od, token=T1)
case("L01", "物流", "未发货订单查物流 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
s, raw = api("GET", "/api/customer/orders/%s/shipment" % O1, token=T1)
sh = obj(raw, "shipment", "item")
case("L02", "物流", "发货后可查物流（含承运商/运单号）", "200 且有 carrier/tracking", s, raw,
     s == 200 and (sh.get("carrier") or sh.get("tracking_no")),
     "carrier=%s tracking=%s status=%s" % (sh.get("carrier"), sh.get("tracking_no"), sh.get("status")))
s, raw = api("PATCH", "/api/seller/orders/%s/shipment" % O1, {"status": "IN_TRANSIT"}, token=STOK)
case("L03", "物流", "物流 SHIPPED → IN_TRANSIT", "IN_TRANSIT", s, raw,
     s == 200 and ship_status(raw) == "IN_TRANSIT", ship_status(raw))
s, raw = api("PATCH", "/api/seller/orders/%s/shipment" % O1, {"status": "DELIVERED"}, token=STOK)
case("L04", "物流", "跳状态 IN_TRANSIT → DELIVERED → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
s, raw = api("PATCH", "/api/seller/orders/%s/shipment" % O1, {"status": "DELIVERING"}, token=STOK)
case("L05", "物流", "IN_TRANSIT → DELIVERING", "DELIVERING", s, raw,
     s == 200 and ship_status(raw) == "DELIVERING", ship_status(raw))
s, raw = api("PATCH", "/api/seller/orders/%s/shipment" % O1, {"status": "DELIVERED"}, token=STOK)
case("L06", "物流", "DELIVERING → DELIVERED（已签收）", "DELIVERED", s, raw,
     s == 200 and ship_status(raw) == "DELIVERED", ship_status(raw))
s, raw = api("PATCH", "/api/seller/orders/%s/shipment" % O1, {"status": "DELIVERED"}, token=STOK)
case("L07", "物流", "已签收再更新 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
s, raw = api("GET", "/api/customer/orders/%s/shipment" % O1, token=T2)
case("L08", "物流", "查他人订单物流 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
s, raw = api("GET", "/api/customer/orders/%s/shipment" % O1, token=T1)
tracks = obj(raw, "shipment", "item").get("tracks") or obj(raw, "shipment", "item").get("track") or []
case("L09", "物流", "物流轨迹可读且含 4 条状态变更", "含 4 条轨迹", s, raw,
     isinstance(tracks, list) and len(tracks) >= 4, "轨迹 %s 条" % (len(tracks) if isinstance(tracks, list) else "n/a"))

# ================= C 交易完成 =================
print("\n[C] 交易完成")


def db_stock(pid):
    try:
        con = sqlite3.connect(DB, timeout=20)
        con.execute("PRAGMA busy_timeout=20000")
        row = con.execute("SELECT stock FROM product WHERE id=?", (pid,)).fetchone()
        con.close()
        return row[0] if row else None
    except Exception as e:
        print("     [DB 查询失败]", type(e).__name__)
        return None


S_BEFORE = db_stock(PID)
s, raw = api("POST", "/api/seller/orders/%s/advance" % O1, {}, token=STOK)
case("C01", "交易完成", "发货后推进 → COMPLETED", "200 且 COMPLETED", s, raw,
     s == 200 and obj(raw, "order").get("status") == "COMPLETED", obj(raw, "order").get("status"))
S_AFTER = db_stock(PID)
case("C02", "交易完成", "完成后库存实减 2（库表 stock %s → %s）" % (S_BEFORE, S_AFTER),
     "减 2", s, raw, isinstance(S_BEFORE, int) and isinstance(S_AFTER, int)
     and S_BEFORE - S_AFTER == 2, "%s → %s" % (S_BEFORE, S_AFTER))
s, raw = api("POST", "/api/seller/orders/%s/advance" % O1, {}, token=STOK)
case("C03", "交易完成", "已完成再推进 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))

# ================= F 售后 =================
print("\n[F] 售后")
s, raw = api("GET", "/api/customer/orders/%s" % O1, token=T1)
ITEMS = obj(raw, "order").get("items") or []
OITEM = (ITEMS[0] or {}).get("id") if ITEMS else None
O2b = new_order(1)
pay(O2b)
s, raw = api("GET", "/api/customer/orders/%s" % O2b, token=T1)
I2B = (obj(raw, "order").get("items") or [{}])[0].get("id")
s, raw = api("POST", "/api/customer/after-sales",
             {"order_item_id": I2B, "title": "未完成单申请", "description": "x"}, token=T1)
case("F01", "售后", "未完成（已支付未完成）订单申请售后 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
s, raw = api("POST", "/api/customer/after-sales",
             {"order_item_id": OITEM, "title": "商品有裂痕", "description": "收到时有裂痕",
              "image_urls": []}, token=T1)
AS1 = obj(raw, "after_sale", "item", "items").get("id")
case("F02", "售后", "对已完成订单项申请售后 → 成功", "200 且有售后 ID", s, raw,
     s == 200 and AS1 is not None, ec(raw))
s, raw = api("POST", "/api/customer/after-sales",
             {"order_item_id": OITEM, "title": "再申请", "description": "x"}, token=T1)
case("F03", "售后", "同一订单项重复申请 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
s, raw = api("GET", "/api/customer/after-sales", token=T1)
case("F04", "售后", "客户售后列表 ≥1 条", ">=1", s, raw, len(J(raw).get("items", [])) >= 1)
s, raw = api("GET", "/api/customer/after-sales/%s" % AS1, token=T1)
case("F05", "售后", "客户售后详情可读且 ID 一致", "id == AS1", s, raw,
     s == 200 and AS1 in raw, obj(raw, "after_sale", "item").get("id"))
s, raw = api("GET", "/api/customer/after-sales/%s" % AS1, token=T2)
case("F06", "售后", "他人售后详情 → 404", "404 NOT_FOUND", s, raw,
     s == 404 and ec(raw) == "NOT_FOUND", ec(raw))
s, raw = api("GET", "/api/seller/aftersales?page_size=100", token=STOK)
case("F07", "售后", "商家售后列表非空", ">=1 条", s, raw,
     s == 200 and len(J(raw).get("items", [])) >= 1, "%d 条" % len(J(raw).get("items", [])))
s, raw = api("GET", "/api/seller/aftersales/%s" % AS1, token=STOK)
case("F08", "售后", "商家售后详情 ID 一致", "id == AS1", s, raw,
     s == 200 and AS1 in raw, obj(raw, "after_sale", "item").get("id"))
s, raw = api("POST", "/api/seller/aftersales/%s/accept" % AS1, {}, token=STOK)
case("F09", "售后", "商家受理 → NEGOTIATING（协商中）", "NEGOTIATING", s, raw,
     s == 200 and after_status(raw) == "NEGOTIATING", after_status(raw))
s, raw = api("POST", "/api/seller/aftersales/%s/resolve" % AS1,
             {"result": "REFUND", "note": "同意退款", "refund_amount": 100.0}, token=STOK)
case("F10", "售后", "商家结案（退款 100）→ RESOLVED", "RESOLVED", s, raw,
     s == 200 and after_status(raw) == "RESOLVED", after_status(raw))
s, raw = api("GET", "/api/customer/after-sales/%s" % AS1, token=T1)
a2 = obj(raw, "after_sale", "item")
case("F11", "售后", "客户可见结案结果与退款金额", "含 result/refund",
     s, raw, s == 200 and (a2.get("result") or a2.get("refund_amount_cents") or a2.get("refund_amount")),
     "result=%s refund=%s" % (a2.get("result"), a2.get("refund_amount_cents") or a2.get("refund_amount")))

# 拒绝分支：新订单走完整链路再申请
O3 = new_order(1)
pay(O3)
for _ in range(2):
    api("POST", "/api/seller/orders/%s/advance" % O3, {}, token=STOK)
api("POST", "/api/seller/orders/%s/ship" % O3,
    {"carrier": "ZTO", "tracking_no": "ZT001"}, token=STOK)
api("POST", "/api/seller/orders/%s/advance" % O3, {}, token=STOK)
s, raw = api("GET", "/api/customer/orders/%s" % O3, token=T1)
I3 = (obj(raw, "order").get("items") or [{}])[0].get("id")
s, raw = api("POST", "/api/customer/after-sales",
             {"order_item_id": I3, "title": "不需要了", "description": "想退"},
             token=T1)
AS2 = obj(raw, "after_sale", "item", "items").get("id")
case("F12", "售后", "第二笔订单申请售后成功并返回售后单", "有 after_sale id", s, raw,
     s == 200 and AS2 is not None, "AS2=%s" % AS2)
s, raw = api("POST", "/api/seller/aftersales/%s/reject" % AS2, {"reason": "已使用无法退"}, token=STOK)
case("F13", "售后", "商家拒绝 → REJECTED", "REJECTED", s, raw,
     s == 200 and after_status(raw) == "REJECTED", after_status(raw))

# ================= G 卖家订单 =================
print("\n[G] 卖家订单")
s, raw = api("GET", "/api/seller/orders?page_size=100", token=STOK)
case("G01", "卖家订单", "卖家订单列表非空", ">=1 单", s, raw,
     s == 200 and len(J(raw).get("items", [])) >= 1, "%d 单" % len(J(raw).get("items", [])))
s, raw = api("GET", "/api/seller/orders/%s" % O1, token=STOK)
case("G02", "卖家订单", "卖家订单详情 ID 一致", "id == O1", s, raw,
     s == 200 and O1 in raw, obj(raw, "order").get("id"))
s, raw = api("POST", "/api/seller/orders/%s/cancel" % O1, {}, token=STOK)
case("G03", "卖家订单", "已完成订单商家取消 → 409", "409 CONFLICT", s, raw,
     s == 409 and ec(raw) == "CONFLICT", ec(raw))
O4 = new_order(1)
pay(O4)
s, raw = api("POST", "/api/seller/orders/%s/cancel" % O4, {}, token=STOK)
case("G04", "卖家订单", "已支付订单商家取消 → CANCELLED", "CANCELLED", s, raw,
     s == 200 and order_status(raw) == "CANCELLED", order_status(raw))

# ================= 汇总 =================
print("\n" + "=" * 82)
p = sum(1 for r in RES if r["result"] == "PASS")
print("全链路：%d 条，通过 %d，失败 %d" % (len(RES), p, len(RES) - p))
print("=" * 82)
for r in RES:
    if r["result"] != "PASS":
        print("  [FAIL] %-5s %-8s %s -> HTTP %s（预期 %s）%s"
              % (r["id"], r["module"], r["title"][:40], r["status"], r["expected"],
                 ("| " + r["note"]) if r["note"] else ""))

os.makedirs(OUT, exist_ok=True)
f = os.path.join(OUT, "challengeC-full-%s.json" % datetime.now().strftime("%Y%m%d-%H%M%S"))
with open(f, "w", encoding="utf-8") as fh:
    json.dump({"base": BASE, "run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "version": "d34fdd9", "total": len(RES), "passed": p, "results": RES},
              fh, ensure_ascii=False, indent=2)
print("证据:", os.path.relpath(f, ROOT))
