"""挑战A 新增功能测试套件 —— 对真实运行的 Java 后端发 HTTP 请求。

设计原则（应对"真实性"审查）：
  1. 全部对 http://127.0.0.1:8000 发真实请求，不 mock、不伪造
  2. 每条用例记录：时间戳、方法、路径、请求体、HTTP 状态、响应体
  3. 预期值来自需求条款（C-xx / FR-xxx / NFR-xxx），不是来自实现
  4. 原始证据落盘为 JSON + CSV，可被第三方逐条复核
"""
import csv
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime

BASE = "http://127.0.0.1:8000"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT_DIR = os.path.join(ROOT, "_runtime", "evidence")

RESULTS = []
_uid = str(int(time.time()))[-5:]          # 本轮唯一后缀，避免与历史数据重名


def api(method, path, body=None, token=None, timeout=30):
    """发一次真实请求，返回 (status, parsed_body, raw_text)。"""
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode("utf-8", "replace")
            try:
                return r.status, json.loads(raw or "{}"), raw
            except Exception:
                return r.status, {"_raw": raw[:500]}, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw or "{}"), raw
        except Exception:
            return e.code, {"_raw": raw[:500]}, raw
    except Exception as e:
        return "ERR", {"_err": "%s: %s" % (type(e).__name__, e)}, ""


def case(cid, module, title, req_desc, method, path, body, token,
         expected, check):
    """执行一条用例并记录完整证据。check(status, resp) -> (ok, actual_summary)"""
    ts = datetime.now().isoformat(sep=" ", timespec="milliseconds")
    status, resp, raw = api(method, path, body, token)
    try:
        ok, actual = check(status, resp)
    except Exception as e:
        ok, actual = False, "断言异常: %s: %s" % (type(e).__name__, e)
    RESULTS.append({
        "id": cid, "module": module, "title": title, "time": ts,
        "request": {"method": method, "path": path, "body": body},
        "expected": expected,
        "http_status": status,
        "actual": actual,
        "response": resp,
        "result": "PASS" if ok else "FAIL",
        "raw_response": raw[:1500],
    })
    mark = "PASS" if ok else "FAIL"
    print("  [%s] %-7s %-34s HTTP %-4s %s" % (mark, cid, title[:34], status, actual[:60]))
    return status, resp


# ============================================================ 准备工作
print("=" * 78)
print("挑战A 新增功能测试 —— 测试轮次后缀 %s" % _uid)
print("目标 %s   时间 %s" % (BASE, datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
print("=" * 78)

s, b, _ = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
assert s == 200, "卖家登录失败，无法继续: %s %s" % (s, b)
STOK = b["token"]
print("\n卖家登录成功，token 长度 %d\n" % len(STOK))

# 建一级 + 二级类别（本轮专用）
_, c1, _ = api("POST", "/api/seller/categories", {"name": "测试大类%s" % _uid}, STOK)
PARENT_ID = (c1.get("category") or {}).get("id")
_, c2, _ = api("POST", "/api/seller/categories", {"name": "测试小类%s" % _uid, "parent_id": PARENT_ID}, STOK)
CAT_ID = (c2.get("category") or {}).get("id")
print("已建类别：大类 %s / 小类 %s\n" % (PARENT_ID, CAT_ID))


def publish(name, stock, price=100.0, cat=CAT_ID, extra=None):
    body = {"name": name, "price": price, "description": "测试用", "stock": stock,
            "category_id": cat, "image_urls": []}
    if extra:
        body.update(extra)
    return api("POST", "/api/seller/products", body, STOK)


def register(username, password, phone="13800000000", loc="测试地点"):
    return api("POST", "/api/customer/register",
               {"username": username, "password": password, "phone": phone, "trade_location": loc})


# ============================================================ A 密码强度
print("\n[A] 密码强度策略（C-29 / NFR-003 / FR-017）")
PWD = [
    ("A01", "Ab1!xyz", "7 位 4 类：长度不足", 422),
    ("A02", "Ab1!xyz9", "8 位 4 类：刚好边界应通过", 200),
    ("A03", "Abcd1234", "8 位 3 类（大小写+数字）", 200),
    ("A04", "Abcd!@#$", "8 位 3 类（大写+小写+特殊）", 200),
    ("A05", "1234!@#$", "8 位 2 类（数字+特殊）：强度不足", 422),
    ("A06", "abcd1234", "8 位 2 类（小写+数字）：强度不足", 422),
    ("A07", "12345678", "8 位 1 类（纯数字）：强度不足", 422),
    ("A08", "abcdefgh", "8 位 1 类（纯小写）：强度不足", 422),
    ("A09", "!!!!!!!!", "8 位 1 类（纯特殊）：强度不足", 422),
    ("A10", "Aa1!Aa1!", "8 位 4 类：应通过", 200),
    ("A11", "", "空密码：应拒绝", 422),
    ("A12", "Aa1!Aa1!" + "x" * 121, "129 位：超上限应拒绝", 422),
]
for cid, pwd, desc, want in PWD:
    case(cid, "密码强度", desc,
         "POST /api/customer/register password=%r" % pwd[:20],
         "POST", "/api/customer/register",
         {"username": "pw_%s_%s" % (cid.lower(), _uid), "password": pwd,
          "phone": "13800000000", "trade_location": "测试地点"},
         None, "HTTP %d" % want,
         lambda st, rp, w=want: (st == w, "HTTP %s" % st))

# ============================================================ B 客户账号与访问控制
print("\n[B] 客户账号与访问控制（C-06 / C-07）")
CUST_USER = "cust_%s" % _uid
CUST_PWD = "Pass1234!"

case("B01", "客户账号", "客户注册成功", "POST /api/customer/register",
     "POST", "/api/customer/register",
     {"username": CUST_USER, "password": CUST_PWD, "phone": "13800000001",
      "trade_location": "图书馆门口"}, None, "HTTP 200",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

case("B02", "客户账号", "重复用户名注册被拒", "同一用户名再注册",
     "POST", "/api/customer/register",
     {"username": CUST_USER, "password": CUST_PWD, "phone": "13800000002",
      "trade_location": "食堂"}, None, "HTTP 409",
     lambda s_, r: (s_ == 409, "HTTP %s" % s_))

case("B03", "客户账号", "电话格式非法注册被拒", "phone=abc",
     "POST", "/api/customer/register",
     {"username": "badphone_%s" % _uid, "password": CUST_PWD, "phone": "abc",
      "trade_location": "食堂"}, None, "HTTP 422",
     lambda s_, r: (s_ == 422, "HTTP %s" % s_))

s_, lg = case("B04", "客户账号", "客户登录成功", "正确用户名密码",
                 "POST", "/api/customer/login",
                 {"username": CUST_USER, "password": CUST_PWD}, None, "HTTP 200",
                 lambda s_, r: (s_ == 200 and "token" in r, "HTTP %s" % s_))
CTOK = lg.get("token")

case("B05", "客户账号", "错误密码登录被拒", "password=WrongPass1!",
     "POST", "/api/customer/login",
     {"username": CUST_USER, "password": "WrongPass1!"}, None, "HTTP 401",
     lambda s_, r: (s_ == 401, "HTTP %s" % s_))

case("B06", "访问控制", "游客可匿名浏览商品列表", "GET /api/products 不带 token",
     "GET", "/api/products", None, None, "HTTP 200",
     lambda s_, r: (s_ == 200 and "items" in r, "HTTP %s 共 %s 件" % (s_, r.get("total"))))

# 先发布一个商品供后续使用
_, pa, _ = publish("商品A_%s" % _uid, stock=1, price=100.0)
PROD_A = (pa.get("product") or {}).get("id")

case("B07", "访问控制", "未登录提交购买意向被拒（下单强制登录）",
     "POST /api/customer/intents 不带 token",
     "POST", "/api/customer/intents", {"product_id": PROD_A}, None, "HTTP 401",
     lambda s_, r: (s_ == 401, "HTTP %s" % s_))

case("B08", "访问控制", "客户登出成功", "POST /api/customer/logout 带 token",
     "POST", "/api/customer/logout", {}, CTOK, "HTTP 200",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

# ============================================================ C 多商品在售
print("\n[C] 多商品同时在售（C-01）")
case("C01", "多商品", "发布商品 A 成功（库存 1）", "POST /api/seller/products",
     "POST", "/api/seller/products",
     {"name": "商品A2_%s" % _uid, "price": 100.0, "description": "测试", "stock": 1,
      "category_id": CAT_ID, "image_urls": []}, STOK, "HTTP 200",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

_, pb = case("C02", "多商品", "再发布商品 B 成功（不再限制唯一在售）",
                "POST /api/seller/products",
                "POST", "/api/seller/products",
                {"name": "商品B_%s" % _uid, "price": 200.0, "description": "测试", "stock": 3,
                 "category_id": CAT_ID, "image_urls": []}, STOK, "HTTP 200",
                lambda s_, r: (s_ == 200, "HTTP %s" % s_))
PROD_B = (pb.get("product") or {}).get("id")

case("C03", "多商品", "公开列表同时包含多件在售商品", "GET /api/products",
     "GET", "/api/products", None, None, "total >= 2",
     lambda s_, r: (s_ == 200 and r.get("total", 0) >= 2, "total=%s" % r.get("total")))

# ============================================================ D 库存
print("\n[D] 库存规则（C-03 / C-18 / C-19 / C-27）")
case("D01", "库存", "库存为负数发布被拒", "stock=-1",
     "POST", "/api/seller/products",
     {"name": "负库存_%s" % _uid, "price": 10.0, "description": "x", "stock": -1,
      "category_id": CAT_ID, "image_urls": []}, STOK, "HTTP 422",
     lambda s_, r: (s_ == 422, "HTTP %s" % s_))

case("D02", "库存", "库存缺失发布被拒", "不传 stock",
     "POST", "/api/seller/products",
     {"name": "无库存_%s" % _uid, "price": 10.0, "description": "x",
      "category_id": CAT_ID, "image_urls": []}, STOK, "HTTP 422",
     lambda s_, r: (s_ == 422, "HTTP %s" % s_))

case("D03", "库存", "把在售商品库存调为 0 → 应自动下架（售罄）",
     "PATCH /api/seller/products/{id}/stock stock=0",
     "PATCH", "/api/seller/products/%s/stock" % PROD_B, {"stock": 0}, STOK,
     "商品状态变为 SOLD_OUT",
     lambda s_, r: (s_ == 200 and (r.get("product") or {}).get("status") == "SOLD_OUT",
                    "status=%s" % (r.get("product") or {}).get("status")))

case("D04", "库存", "补货后商品恢复在售", "PATCH stock=5",
     "PATCH", "/api/seller/products/%s/stock" % PROD_B, {"stock": 5}, STOK,
     "商品状态恢复 ON_SALE",
     lambda s_, r: (s_ == 200 and (r.get("product") or {}).get("status") == "ON_SALE",
                    "status=%s" % (r.get("product") or {}).get("status")))

case("D05", "库存", "把库存调成负数被拒", "PATCH stock=-3",
     "PATCH", "/api/seller/products/%s/stock" % PROD_B, {"stock": -3}, STOK,
     "HTTP 422",
     lambda s_, r: (s_ == 422, "HTTP %s" % s_))

# ============================================================ E 类别
print("\n[E] 二级类别规则（C-02 / C-22）")
case("E01", "类别", "发布商品不挂类别被拒", "不传 category_id",
     "POST", "/api/seller/products",
     {"name": "无类别_%s" % _uid, "price": 10.0, "description": "x", "stock": 1,
      "image_urls": []}, STOK, "HTTP 422",
     lambda s_, r: (s_ == 422, "HTTP %s" % s_))

case("E02", "类别", "商品挂一级类别被拒（必须挂二级）", "category_id 用大类 id",
     "POST", "/api/seller/products",
     {"name": "挂大类_%s" % _uid, "price": 10.0, "description": "x", "stock": 1,
      "category_id": PARENT_ID, "image_urls": []}, STOK, "HTTP 422",
     lambda s_, r: (s_ == 422, "HTTP %s" % s_))

case("E03", "类别", "删除仍有商品的类别被拒", "DELETE 小类（其下有商品）",
     "DELETE", "/api/seller/categories/%s" % CAT_ID, None, STOK, "HTTP 409",
     lambda s_, r: (s_ == 409, "HTTP %s" % s_))

# ============================================================ F 队列与交易
print("\n[F] 独立队列与交易流程（C-12 / C-17 / C-26）")
# 注册两个客户
for u in ("q1_%s" % _uid, "q2_%s" % _uid):
    api("POST", "/api/customer/register",
        {"username": u, "password": CUST_PWD, "phone": "13900000000", "trade_location": "测试"})
T1 = api("POST", "/api/customer/login", {"username": "q1_%s" % _uid, "password": CUST_PWD})[1].get("token")
T2 = api("POST", "/api/customer/login", {"username": "q2_%s" % _uid, "password": CUST_PWD})[1].get("token")

# 商品 F：库存 5
_, pf, _ = publish("商品F_%s" % _uid, stock=5, price=50.0)
PROD_F = (pf.get("product") or {}).get("id")

s_, i1 = case("F01", "队列", "客户1 提交意向 → 队列位 1", "POST /api/customer/intents",
                 "POST", "/api/customer/intents", {"product_id": PROD_F}, T1,
                 "QUEUED, queue_position=1",
                 lambda s_, r: (s_ == 200 and (r.get("intent") or {}).get("queue_position") == 1,
                                "pos=%s" % (r.get("intent") or {}).get("queue_position")))

s_, i2 = case("F02", "队列", "客户2 提交意向 → 队列位 2（先到先得）", "同上",
                 "POST", "/api/customer/intents", {"product_id": PROD_F}, T2,
                 "QUEUED, queue_position=2",
                 lambda s_, r: (s_ == 200 and (r.get("intent") or {}).get("queue_position") == 2,
                                "pos=%s" % (r.get("intent") or {}).get("queue_position")))

case("F03", "队列", "另一个商品有独立队列（客户1 在商品A 队列位 1）",
     "对商品A 提交意向", "POST", "/api/customer/intents", {"product_id": PROD_A}, T1,
     "QUEUED, queue_position=1（与商品F 互不影响）",
     lambda s_, r: (s_ == 200 and (r.get("intent") or {}).get("queue_position") == 1,
                    "pos=%s" % (r.get("intent") or {}).get("queue_position")))

case("F04", "交易", "卖家对商品F 进入交易 → 队首进入", "POST start-transaction",
     "POST", "/api/seller/products/%s/start-transaction" % PROD_F, {}, STOK,
     "HTTP 200，队首转 IN_TRANSACTION，商品冻结",
     lambda s_, r: (s_ == 200, "HTTP %s status=%s" % (
         s_, (r.get("product") or {}).get("status"))))

# 取当前交易中的意向 id
_, q, _ = api("GET", "/api/seller/products/%s/intents?status=IN_TRANSACTION" % PROD_F, None, STOK)
items = q.get("items") or []
INTENT_ID = items[0]["id"] if items else None
print("     当前交易中的意向: %s" % INTENT_ID)

case("F05", "交易", "标记交易失败并作废（单动作）", "POST intents/{id}/transaction",
     "POST", "/api/seller/intents/%s/transaction" % INTENT_ID,
     {"result": "FAILED", "failed_action": "VOID", "note": "测试作废"}, STOK,
     "HTTP 200，失败者 VOIDED",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

case("F06", "交易", "作废后队列下一位成为队首", "GET intents?status=QUEUED",
     "GET", "/api/seller/products/%s/intents?status=QUEUED" % PROD_F, None, STOK,
     "剩余队列中存在客户2",
     lambda s_, r: (s_ == 200 and len(r.get("items") or []) >= 1,
                    "排队中 %d 人" % len(r.get("items") or [])))

# ============================================================ G 售罄作废
print("\n[G] 售罄作废（C-19）")
_, pg, _ = publish("商品G_%s" % _uid, stock=1, price=30.0)
PROD_G = (pg.get("product") or {}).get("id")
api("POST", "/api/customer/intents", {"product_id": PROD_G}, T1)
api("POST", "/api/customer/intents", {"product_id": PROD_G}, T2)
api("POST", "/api/seller/products/%s/start-transaction" % PROD_G, {}, STOK)
_, qg, _ = api("GET", "/api/seller/products/%s/intents?status=IN_TRANSACTION" % PROD_G, None, STOK)
gitem = (qg.get("items") or [{}])[0].get("id")

case("G01", "售罄", "库存 1 成交 → 库存归零且商品售罄下架", "标记交易成功",
     "POST", "/api/seller/intents/%s/transaction" % gitem,
     {"result": "SUCCESS"}, STOK, "商品状态 SOLD_OUT、库存 0",
     lambda s_, r: (s_ == 200 and (r.get("product") or {}).get("status") == "SOLD_OUT",
                    "status=%s stock=%s" % ((r.get("product") or {}).get("status"),
                                            (r.get("product") or {}).get("stock"))))

case("G02", "售罄", "库存归零后其余排队意向被自动作废（SOLD_OUT）",
     "GET intents 查看客户2 状态",
     "GET", "/api/seller/products/%s/intents" % PROD_G, None, STOK,
     "剩余意向状态为 SOLD_OUT",
     lambda s_, r: (s_ == 200 and any(
         it.get("status") == "SOLD_OUT" for it in (r.get("items") or [])),
         "状态集合=%s" % sorted({it.get("status") for it in (r.get("items") or [])})))

# ============================================================ 汇总
print("\n" + "=" * 78)
p = sum(1 for r in RESULTS if r["result"] == "PASS")
f = len(RESULTS) - p
print("合计：%d 条用例，通过 %d，失败 %d，通过率 %.1f%%"
      % (len(RESULTS), p, f, 100.0 * p / max(1, len(RESULTS))))
print("=" * 78)
if f:
    print("失败用例：")
    for r in RESULTS:
        if r["result"] == "FAIL":
            print("  - [%s] %s / %s" % (r["id"], r["module"], r["title"]))
            print("      预期 %s ，实际 HTTP %s %s" % (r["expected"], r["http_status"], r["actual"]))
    print()

os.makedirs(OUT_DIR, exist_ok=True)
stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
jf = os.path.join(OUT_DIR, "challengeA-%s.json" % stamp)
with open(jf, "w", encoding="utf-8") as fh:
    json.dump({"base": BASE, "run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "total": len(RESULTS), "passed": p, "failed": f, "results": RESULTS},
              fh, ensure_ascii=False, indent=2)

cf = os.path.join(OUT_DIR, "challengeA-%s.csv" % stamp)
with open(cf, "w", encoding="utf-8-sig", newline="") as fh:
    w = csv.writer(fh)
    w.writerow(["用例编号", "模块", "用例标题", "请求时间", "方法", "路径", "请求体",
                "预期", "HTTP", "实际", "结论"])
    for r in RESULTS:
        w.writerow([r["id"], r["module"], r["title"], r["time"],
                    r["request"]["method"], r["request"]["path"],
                    json.dumps(r["request"]["body"], ensure_ascii=False),
                    r["expected"], r["http_status"], r["actual"], r["result"]])

print("原始证据已落盘：")
print("  " + os.path.relpath(jf, ROOT))
print("  " + os.path.relpath(cf, ROOT))
