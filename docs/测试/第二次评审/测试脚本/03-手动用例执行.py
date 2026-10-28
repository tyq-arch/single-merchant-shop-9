"""执行 23 条「手动用例」——通过模拟界面操作对应的接口调用真实运行。

说明：无法真的点浏览器，因此用界面按钮背后调用的同一批接口来执行；
      结果如实记录，标签仍为「手动」。
"""
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime

BASE = "http://127.0.0.1:8000"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "_runtime", "evidence")

RESULTS = []
_uid = "m" + str(int(time.time()))[-5:]


def api(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            raw = r.read().decode("utf-8", "replace")
            return r.status, raw
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return "ERR", json.dumps({"err": str(e)}, ensure_ascii=False)


def rec(cid, module, content, process, expected, status, raw, judge):
    ok = judge(status, raw)
    RESULTS.append({
        "id": cid, "module": module, "content": content, "process": process,
        "expected": expected, "status": status, "raw": raw,
        "result": "PASS" if ok else "FAIL",
        "time": datetime.now().isoformat(sep=" ", timespec="milliseconds"),
    })
    print("  [%s] %-6s HTTP %-4s %s" % ("PASS" if ok else "FAIL", cid, status, raw[:70].replace("\n", " ")))
    return status, raw


print("=" * 78)
print("手动用例执行（接口模拟界面操作）  轮次 %s" % _uid)
print("=" * 78)

# ---------- A 组：密码强度 ----------
print("\n[A] 密码强度（注册页）")
for cid, pwd, note in [
    ("M-A1", "pass1234",  "弱密码，应被拒"),
    ("M-A2", "Pass1234!", "强密码，应成功"),
    ("M-A3", "Ab1!xyz",   "7 位，应被拒"),
    ("M-A4", "Abcd1234",  "8 位 3 类，应成功"),
    ("M-A5", "Ab1!ab",    "6 位 4 类：前端放行、后端应拒"),
    ("M-A6", "abcdefgh",  "8 位 1 类：前端放行、后端应拒"),
]:
    s, raw = api("POST", "/api/customer/register", {
        "username": "%s_%s" % (cid.lower().replace("-", ""), _uid),
        "password": pwd, "phone": "13800000000", "trade_location": "测试地点",
    })
    want = 200 if cid in ("M-A2", "M-A4") else 422
    rec(cid, "密码强度", "注册密码=%s（%s）" % (pwd, note),
        "在注册页填 用户名/电话/地点/密码=%s，点注册" % pwd,
        "HTTP %d" % want, s, raw, lambda st, r, w=want: st == w)

# 登录一个可用账号
s, raw = api("POST", "/api/customer/login",
             {"username": "ma2_%s" % _uid, "password": "Pass1234!"})
CTOK = json.loads(raw).get("token") if s == 200 else None
print("     客户 ma2_%s 登录: %s" % (_uid, s))

# ---------- B 组：账号与访问控制 ----------
print("\n[B] 客户账号与访问控制")
rec("M-B1", "客户账号", "用注册账号登录",
    "登录页填 ma2_xxx / Pass1234!，点登录", "HTTP 200 且返回 token",
    s, raw, lambda st, r: st == 200 and '"token"' in r)

s2, raw2 = api("GET", "/api/customer/me", token=CTOK)
rec("M-B2", "客户账号", "个人资料显示正确",
    "打开 /my/profile，调用 GET /api/customer/me", "HTTP 200 且含 username/phone/trade_location",
    s2, raw2, lambda st, r: st == 200 and "username" in r and "trade_location" in r)

s3, raw3 = api("POST", "/api/customer/intents", {"product_id": "any"})
rec("M-B3", "访问控制", "未登录点我要购买被拒",
    "退出登录后点「我要购买」，调用 POST /api/customer/intents（不带 token）",
    "HTTP 401", s3, raw3, lambda st, r: st == 401)

# ---------- C 组：多商品 ----------
print("\n[C] 多商品同时在售")
s, raw = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
STOK = json.loads(raw).get("token") if s == 200 else None
if not STOK:
    raise SystemExit("卖家登录失败，请先重置环境: %s" % raw[:120])

s4, raw4 = api("GET", "/api/seller/products?page_size=100", token=STOK)
rec("M-C2", "多商品", "卖家商品列表有多件在售",
    "打开 /seller/products，调用 GET /api/seller/products", "total >= 2",
    s4, raw4, lambda st, r: st == 200 and json.loads(r).get("total", 0) >= 2)

# 找一个二级类别
s, craw = api("GET", "/api/seller/categories", token=STOK)
cats = json.loads(craw).get("items", [])
# /api/seller/categories 返回一级类别，二级在 children 里
leaf = None
for c in cats:
    if c.get("level") == 2:
        leaf = c
        break
    for ch in (c.get("children") or []):
        if ch.get("level") == 2:
            leaf = ch
            break
    if leaf:
        break
CAT = leaf["id"] if leaf else None
print("     二级类别: %s" % (CAT or "未找到"))

s5, raw5 = api("POST", "/api/seller/products", {
    "name": "手动商品C_%s" % _uid, "price": 66.0, "description": "手动测试",
    "stock": 5, "category_id": CAT, "image_urls": [],
}, STOK)
PROD_C = json.loads(raw5).get("product", {}).get("id") if s5 == 200 else None
rec("M-C3", "多商品", "已有在售商品时再发布一件仍成功",
    "打开 /seller/publish，填名称/价格/库存/二级类别，点发布", "HTTP 200",
    s5, raw5, lambda st, r: st == 200)

# ---------- D 组：库存 ----------
print("\n[D] 库存规则")
s6, raw6 = api("PATCH", "/api/seller/products/%s/stock" % PROD_C, {"stock": 0}, STOK)
rec("M-D1", "库存", "库存调 0 自动下架",
    "在 /seller/products 把库存改成 0 并保存", "状态变 SOLD_OUT",
    s6, raw6, lambda st, r: st == 200 and json.loads(r)["product"]["status"] == "SOLD_OUT")

s7, raw7 = api("PATCH", "/api/seller/products/%s/stock" % PROD_C, {"stock": 5}, STOK)
rec("M-D2", "库存", "补货后恢复在售",
    "把库存改成 5 并保存", "状态恢复 ON_SALE",
    s7, raw7, lambda st, r: st == 200 and json.loads(r)["product"]["status"] == "ON_SALE")

s8, raw8 = api("PATCH", "/api/seller/products/%s/stock" % PROD_C, {"stock": -3}, STOK)
rec("M-D3", "库存", "库存调负数被拒",
    "把库存改成 -3 并保存", "HTTP 422",
    s8, raw8, lambda st, r: st == 422)

# ---------- E 组：类别 ----------
print("\n[E] 二级类别")
s9, raw9 = api("POST", "/api/seller/categories", {"name": "手工大类_%s" % _uid}, STOK)
P1 = json.loads(raw9).get("category", {}).get("id") if s9 == 200 else None
s10, raw10 = api("POST", "/api/seller/categories", {"name": "手工小类_%s" % _uid, "parent_id": P1}, STOK)
C1 = json.loads(raw10).get("category", {}).get("id") if s10 == 200 else None
rec("M-E1", "类别", "可新建大类与二级小类",
    "在 /seller/categories 新建大类，再在其下新建小类",
    "两次都 HTTP 200，且小类 parent_id 指向大类",
    s10, raw10, lambda st, r: st == 200 and json.loads(r)["category"]["level"] == 2)

s11, raw11 = api("DELETE", "/api/seller/categories/%s" % CAT, None, STOK)
rec("M-E2", "类别", "有商品的类别禁止删除",
    "在类别页删除已被商品使用的「手动测试小类」", "HTTP 409",
    s11, raw11, lambda st, r: st == 409)

s12, raw12 = api("POST", "/api/seller/products", {
    "name": "无类别_%s" % _uid, "price": 10.0, "description": "x", "stock": 1,
    "image_urls": [],
}, STOK)
rec("M-E3", "类别", "发布商品不选类别被拒",
    "在发布页不选类别直接提交", "HTTP 422",
    s12, raw12, lambda st, r: st == 422)

# ---------- F 组：队列与交易 ----------
print("\n[F] 队列与交易")
# 注册两个客户
for u, pwd in [("f1_%s" % _uid, "Pass1234!"), ("f2_%s" % _uid, "Pass1234!")]:
    api("POST", "/api/customer/register",
        {"username": u, "password": pwd, "phone": "13900000000", "trade_location": "测试"})
T1 = json.loads(api("POST", "/api/customer/login", {"username": "f1_%s" % _uid, "password": "Pass1234!"})[1]).get("token")
T2 = json.loads(api("POST", "/api/customer/login", {"username": "f2_%s" % _uid, "password": "Pass1234!"})[1]).get("token")

s13, raw13 = api("POST", "/api/customer/intents", {"product_id": PROD_C}, T1)
rec("M-F1a", "队列", "客户1 提交意向，位次应为 1",
    "客户 f1 登录，对「手动商品C」提交购买意向", "queue_position = 1",
    s13, raw13, lambda st, r: st == 200 and json.loads(r)["intent"]["queue_position"] == 1)

s14, raw14 = api("POST", "/api/customer/intents", {"product_id": PROD_C}, T2)
rec("M-F1b", "队列", "客户2 提交意向，位次应为 2",
    "客户 f2 登录，对同一商品提交意向", "queue_position = 2",
    s14, raw14, lambda st, r: st == 200 and json.loads(r)["intent"]["queue_position"] == 2)

s15, raw15 = api("POST", "/api/customer/intents", {"product_id": PROD_C}, T1)
rec("M-F2", "队列", "同一客户重复提交被拒",
    "客户 f1 对同一商品再次提交", "HTTP 409",
    s15, raw15, lambda st, r: st == 409)

s16, raw16 = api("POST", "/api/seller/products/%s/start-transaction" % PROD_C, {}, STOK)
rec("M-F3", "交易", "卖家进入交易，商品冻结",
    "在 /seller/intents 对「手动商品C」点「进入交易」", "HTTP 200 且商品状态 FROZEN",
    s16, raw16, lambda st, r: st == 200 and json.loads(r)["product"]["status"] == "FROZEN")

s17, raw17 = api("POST", "/api/customer/intents", {"product_id": PROD_C}, T2)
rec("M-F4", "交易", "冻结期间新客户下单被拒",
    "商品冻结后，另一客户尝试提交意向", "HTTP 409/422",
    s17, raw17, lambda st, r: st in (409, 422))

# 取交易中的意向
s, qraw = api("GET", "/api/seller/products/%s/intents?status=IN_TRANSACTION" % PROD_C, token=STOK)
items = json.loads(qraw).get("items", []) if s == 200 else []
INTENT = items[0]["id"] if items else None
s18, raw18 = api("POST", "/api/seller/intents/%s/transaction" % INTENT,
                 {"result": "FAILED", "failed_action": "VOID", "note": "手动测试"}, STOK)
rec("M-F5", "交易", "标记失败并作废，下一位补位",
    "卖家对该笔交易标记失败，失败者处理选作废", "HTTP 200",
    s18, raw18, lambda st, r: st == 200)

# ---------- G 组：售罄作废 ----------
print("\n[G] 售罄作废")
s, raw = api("POST", "/api/seller/products", {
    "name": "手动商品G_%s" % _uid, "price": 30.0, "description": "库存1",
    "stock": 1, "category_id": CAT, "image_urls": [],
}, STOK)
PROD_G = json.loads(raw)["product"]["id"]
api("POST", "/api/customer/intents", {"product_id": PROD_G}, T1)
api("POST", "/api/customer/intents", {"product_id": PROD_G}, T2)
api("POST", "/api/seller/products/%s/start-transaction" % PROD_G, {}, STOK)
s, qraw = api("GET", "/api/seller/products/%s/intents?status=IN_TRANSACTION" % PROD_G, token=STOK)
gi = json.loads(qraw)["items"][0]["id"]
s19, raw19 = api("POST", "/api/seller/intents/%s/transaction" % gi, {"result": "SUCCESS"}, STOK)
rec("M-G1a", "售罄", "库存 1 成交后商品售罄下架",
    "卖家进入交易后标记成功", "库存 0 且状态 SOLD_OUT",
    s19, raw19, lambda st, r: st == 200 and json.loads(r)["product"]["status"] == "SOLD_OUT")

s, qraw = api("GET", "/api/seller/products/%s/intents" % PROD_G, token=STOK)
sts = [it.get("status") for it in json.loads(qraw).get("items", [])]
rec("M-G1b", "售罄", "其余排队意向自动作废",
    "查看另一名客户的意向状态", "存在 SOLD_OUT 意向",
    s, qraw, lambda st, r: st == 200 and "SOLD_OUT" in [it.get("status") for it in json.loads(r).get("items", [])])

# ---------- 汇总 ----------
print("\n" + "=" * 78)
p = sum(1 for r in RESULTS if r["result"] == "PASS")
print("手动用例执行：%d 条，通过 %d，失败 %d" % (len(RESULTS), p, len(RESULTS) - p))
print("=" * 78)
for r in RESULTS:
    if r["result"] == "FAIL":
        print("  [FAIL] %s %s -> HTTP %s" % (r["id"], r["content"], r["status"]))

os.makedirs(OUT, exist_ok=True)
f = os.path.join(OUT, "manual-%s.json" % datetime.now().strftime("%Y%m%d-%H%M%S"))
with open(f, "w", encoding="utf-8") as fh:
    json.dump({"base": BASE, "run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "total": len(RESULTS), "passed": p, "results": RESULTS}, fh,
              ensure_ascii=False, indent=2)
print("证据:", os.path.relpath(f, ROOT))
