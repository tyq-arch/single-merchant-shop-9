"""挑战A 边界与异常对抗测试 —— 专攻容易出错的路径。

目的：证明测试不是"走过场"。这一轮故意构造异常输入和冲突操作，
      若实现有漏洞，这里应该暴露出来。
"""
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
_uid = "e" + str(int(time.time()))[-5:]


def api(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode("utf-8", "replace")
            try:
                return r.status, json.loads(raw or "{}"), raw
            except Exception:
                return r.status, {"_raw": raw[:400]}, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw or "{}"), raw
        except Exception:
            return e.code, {"_raw": raw[:400]}, raw
    except Exception as e:
        return "ERR", {"_err": str(e)}, ""


def case(cid, module, title, method, path, body, token, expected, check):
    ts = datetime.now().isoformat(sep=" ", timespec="milliseconds")
    status, resp, raw = api(method, path, body, token)
    try:
        ok, actual = check(status, resp)
    except Exception as e:
        ok, actual = False, "断言异常: %s" % e
    RESULTS.append({"id": cid, "module": module, "title": title, "time": ts,
                    "request": {"method": method, "path": path, "body": body},
                    "expected": expected, "http_status": status, "actual": actual,
                    "response": resp, "result": "PASS" if ok else "FAIL",
                    "raw_response": raw[:1200]})
    print("  [%s] %-5s %-36s HTTP %-4s %s"
          % ("PASS" if ok else "FAIL", cid, title[:36], status, actual[:56]))
    return status, resp


print("=" * 78)
print("挑战A 边界与异常对抗测试 —— 轮次 %s" % _uid)
print("=" * 78)

# 准备
s, b, _ = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
STOK = b["token"]
_, c1, _ = api("POST", "/api/seller/categories", {"name": "边界大类%s" % _uid}, STOK)
PP = (c1.get("category") or {}).get("id")
_, c2, _ = api("POST", "/api/seller/categories", {"name": "边界小类%s" % _uid, "parent_id": PP}, STOK)
CAT = (c2.get("category") or {}).get("id")


def pub(name, stock=5, price=100.0, cat=None):
    return api("POST", "/api/seller/products",
               {"name": name, "price": price, "description": "x", "stock": stock,
                "category_id": cat or CAT, "image_urls": []}, STOK)


def reg(u, p="Pass1234!"):
    return api("POST", "/api/customer/register",
               {"username": u, "password": p, "phone": "13700000000", "trade_location": "测试"})


for u in ("ea_%s" % _uid, "eb_%s" % _uid):
    reg(u)
TA = api("POST", "/api/customer/login", {"username": "ea_%s" % _uid, "password": "Pass1234!"})[1].get("token")
TB = api("POST", "/api/customer/login", {"username": "eb_%s" % _uid, "password": "Pass1234!"})[1].get("token")

_, ph, _ = pub("边界商品_%s" % _uid, stock=5)
PROD = (ph.get("product") or {}).get("id")

print("\n[H] 价格边界（BUG-001 回归）")
for cid, price, desc, want in [
    ("H01", 0, "价格 0 应被拒", 422),
    ("H02", 0.001, "价格 0.001 元（不足 1 分）应被拒而非 500", 422),
    ("H03", -5, "负数价格应被拒", 422),
    ("H04", 9999999.01, "价格超上限应被拒", 422),
    ("H05", 1.999, "价格三位小数应被拒", 422),
]:
    case(cid, "价格边界", desc, "POST", "/api/seller/products",
         {"name": "价格%s_%s" % (cid, _uid), "price": price, "description": "x",
          "stock": 1, "category_id": CAT, "image_urls": []}, STOK,
         "HTTP %d" % want, lambda s_, r, w=want: (s_ == w, "HTTP %s" % s_))

print("\n[I] 意向与队列冲突")
case("I01", "意向冲突", "客户首次提交意向应成功（前置条件）",
     "POST", "/api/customer/intents", {"product_id": PROD}, TA,
     "HTTP 200（首次提交合法）",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

case("I02", "意向冲突", "重复提交第二次仍应被拒",
     "POST", "/api/customer/intents", {"product_id": PROD}, TA,
     "HTTP 409", lambda s_, r: (s_ == 409, "HTTP %s" % s_))

# 客户B 也排队（不同客户允许）
api("POST", "/api/customer/intents", {"product_id": PROD}, TB)

# 客户A 查客户B 的意向
_, qb, _ = api("GET", "/api/seller/products/%s/intents" % PROD, None, STOK)
other_intent = None
for it in (qb.get("items") or []):
    other_intent = it.get("id")
    break

case("I03", "越权", "客户不能查看不属于自己的意向", "GET",
     "/api/customer/intents/%s" % other_intent, None, TB,
     "HTTP 404（他人意向不可见）",
     lambda s_, r: (s_ in (403, 404), "HTTP %s" % s_))

print("\n[J] 库存与售罄联动")
_, pz, _ = pub("零库存_%s" % _uid, stock=0)
ZERO = (pz.get("product") or {}).get("id")
case("J01", "售罄", "库存为 0 的商品发布后应不可购买（状态非在售）",
     "GET", "/api/products/%s" % ZERO, None, None,
     "商品不可下单（SOLD_OUT 或不可见）",
     lambda s_, r: (s_ == 404 or (r.get("product") or r).get("status") == "SOLD_OUT",
                    "HTTP %s status=%s" % (s_, (r.get("product") or r).get("status"))))

case("J02", "售罄", "对库存 0 的商品提交意向应被拒", "POST",
     "/api/customer/intents", {"product_id": ZERO}, TA, "HTTP 409/422",
     lambda s_, r: (s_ in (409, 422), "HTTP %s" % s_))

print("\n[K] 卖家改密与登录（FR-037 / NFR-003）")
case("K01", "卖家改密", "卖家把密码改成弱密码应被拒", "POST", "/api/seller/password",
     {"old_password": "seller123", "new_password": "pass1234"}, STOK,
     "HTTP 422（强度不足）", lambda s_, r: (s_ == 422, "HTTP %s" % s_))

case("K02", "卖家改密", "卖家新密码与旧密码相同应被拒或允许（记录行为）", "POST",
     "/api/seller/password",
     {"old_password": "seller123", "new_password": "Seller123!"}, STOK,
     "HTTP 200（强密码应通过）",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

case("K03", "卖家改密", "旧密码错误应被拒", "POST", "/api/seller/password",
     {"old_password": "wrong-old", "new_password": "Seller123!"}, STOK,
     "HTTP 401", lambda s_, r: (s_ == 401, "HTTP %s" % s_))

# 注意：seller123 只有 2 类字符，改密后无法再改回（强度校验会拒绝）。
# 因此这里只验证「新密码生效、旧密码失效」，后续用例改用新令牌。
case("K04", "卖家改密", "改密后新密码 Seller123! 可登录", "POST", "/api/seller/login",
     {"username": "seller", "password": "Seller123!"}, None, "HTTP 200",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

case("K05", "卖家改密", "改密后旧密码 seller123 已失效", "POST", "/api/seller/login",
     {"username": "seller", "password": "seller123"}, None, "HTTP 401",
     lambda s_, r: (s_ == 401, "HTTP %s" % s_))

STOK = api("POST", "/api/seller/login",
           {"username": "seller", "password": "Seller123!"})[1].get("token")
print("     卖家密码已变为 Seller123!（重跑需重置数据库）")

print("\n[L] 冻结与下单")
_, pf, _ = pub("冻结商品_%s" % _uid, stock=5)
FRZ = (pf.get("product") or {}).get("id")
case("L01", "冻结", "卖家手动冻结商品", "POST",
     "/api/seller/products/%s/freeze" % FRZ, {}, STOK, "HTTP 200，状态 FROZEN",
     lambda s_, r: (s_ == 200 and (r.get("product") or {}).get("status") == "FROZEN",
                    "status=%s" % (r.get("product") or {}).get("status")))
case("L02", "冻结", "冻结期间提交意向应被拒", "POST",
     "/api/customer/intents", {"product_id": FRZ}, TA, "HTTP 409/422",
     lambda s_, r: (s_ in (409, 422), "HTTP %s" % s_))

print("\n[M] 分页与参数")
case("M01", "分页", "page=0 应被拒", "GET", "/api/products?page=0", None, None,
     "HTTP 422", lambda s_, r: (s_ == 422, "HTTP %s" % s_))
case("M02", "分页", "page_size=101 应被拒", "GET", "/api/products?page_size=101", None, None,
     "HTTP 422", lambda s_, r: (s_ == 422, "HTTP %s" % s_))
case("M03", "分页", "page=abc 应被拒", "GET", "/api/products?page=abc", None, None,
     "HTTP 422", lambda s_, r: (s_ == 422, "HTTP %s" % s_))

# 汇总
print("\n" + "=" * 78)
p = sum(1 for r in RESULTS if r["result"] == "PASS")
f = len(RESULTS) - p
print("合计：%d 条，通过 %d，失败 %d，通过率 %.1f%%"
      % (len(RESULTS), p, f, 100.0 * p / max(1, len(RESULTS))))
print("=" * 78)
if f:
    print("失败用例（真实发现的问题）：")
    for r in RESULTS:
        if r["result"] == "FAIL":
            print("  - [%s] %s / %s" % (r["id"], r["module"], r["title"]))
            print("      预期：%s" % r["expected"])
            print("      实际：HTTP %s  %s" % (r["http_status"], r["actual"]))
    print()

os.makedirs(OUT_DIR, exist_ok=True)
stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
jf = os.path.join(OUT_DIR, "edge-%s.json" % stamp)
with open(jf, "w", encoding="utf-8") as fh:
    json.dump({"base": BASE, "run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "total": len(RESULTS), "passed": p, "failed": f, "results": RESULTS},
              fh, ensure_ascii=False, indent=2)
print("证据已落盘：" + os.path.relpath(jf, ROOT))
