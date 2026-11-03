"""第 3 次评审 · 修复验证套件

针对本周团队实际修改的点逐项验证：
  P  密码强度规则（问题 6）
  H  手机号新规则（原 OBS-014）
  L  LIKE 通配符转义（BUG-002）
  U  上传接口鉴权（OBS-001）
  R  价格校验（BUG-001）
  G  分页溢出
  E  405 / 415 异常处理
  T  注册事务
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
RES = []
_uid = "r3" + str(int(time.time()))[-5:]
PWD = "Pass1234!"


def api(method, path, body=None, token=None, raw_body=None, ctype=None):
    if raw_body is not None:
        data = raw_body
    else:
        data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", ctype or "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return "ERR", json.dumps({"err": str(e)}, ensure_ascii=False)


def case(cid, mod, title, exp, got_status, got_raw, ok, note=""):
    RES.append({"id": cid, "module": mod, "title": title, "expected": exp,
                "status": got_status, "raw": got_raw, "result": "PASS" if ok else "FAIL",
                "note": note, "time": datetime.now().isoformat(sep=" ", timespec="milliseconds")})
    print("  [%s] %-6s HTTP %-4s %s" % ("PASS" if ok else "FAIL", cid, got_status, title[:52]))
    return got_status, got_raw


print("=" * 80)
print("第 3 次评审 · 修复验证   轮次 %s" % _uid)
print("=" * 80)

# 卖家登录
s, b = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
if s != 200:
    raise SystemExit("卖家登录失败: %s %s" % (s, b[:200]))
STOK = json.loads(b)["token"]
print("\n卖家登录成功")

# ---------- 建基础数据 ----------
api("POST", "/api/seller/categories", {"name": "R3大类%s" % _uid}, STOK)
s, c = api("GET", "/api/seller/categories", token=STOK)
top = json.loads(c)["items"]
TOPCAT = [x for x in top if x["name"] == "R3大类%s" % _uid][0]["id"]
s, c2 = api("POST", "/api/seller/categories", {"name": "R3小类%s" % _uid, "parent_id": TOPCAT}, STOK)
CAT = json.loads(c2)["category"]["id"]

# ---------- P 密码强度 ----------
print("\n[P] 密码强度规则（问题 6）")
P_CASES = [
    ("P01", "pass1234", 422, "2 类字符，强度不足"),
    ("P02", "Ab1!ab", 422, "6 位，长度不足"),
    ("P03", "Ab1!xyz", 422, "7 位，长度不足"),
    ("P04", "abcdefgh", 422, "8 位 1 类，强度不足"),
    ("P05", "Abcd1234", 200, "8 位 3 类，应通过"),
    ("P06", "Pass1234!", 200, "8 位 4 类，应通过"),
]
for cid, pwd, want, desc in P_CASES:
    st, raw = api("POST", "/api/customer/register", {
        "username": "%s_%s" % (cid.lower(), _uid), "password": pwd,
        "phone": "13800000000", "trade_location": "测试"})
    case(cid, "密码强度", "注册密码=%s（%s）" % (pwd, desc), "HTTP %d" % want, st, raw, st == want)

# ---------- H 手机号新规则 ----------
print("\n[H] 手机号新规则（原 OBS-014，后端已重写）")
H_CASES = [
    ("H01", "13800000000", 200, "11 位手机号"),
    ("H02", "19900000000", 200, "11 位手机号 19x"),
    ("H03", "01012345678", 200, "带区号座机（0 开头 11 位）"),
    ("H04", "12345678", 200, "8 位本地号码"),
    ("H05", "1234567", 200, "7 位本地号码"),
    ("H06", "138-0000-0000", 200, "带连字符，归一化后合法"),
    ("H07", "138 0000 0000", 200, "带空格，归一化后合法"),
    ("H08", "+8613800000000", 200, "带 +86 前缀"),
    ("H09", "12345", 422, "5 位，太短"),
    ("H10", "23800000000", 422, "2 开头非座机"),
    ("H11", "abcdefghijk", 422, "含字母"),
    ("H12", "", 422, "空"),
]
for cid, phone, want, desc in H_CASES:
    st, raw = api("POST", "/api/customer/register", {
        "username": "%s_%s" % (cid.lower(), _uid), "password": PWD,
        "phone": phone, "trade_location": "测试"})
    case(cid, "手机号规则", "phone=%r（%s）" % (phone, desc), "HTTP %d" % want, st, raw, st == want)

# ---------- R 价格校验 ----------
print("\n[R] 价格校验（BUG-001 回归）")
R_CASES = [
    ("R01", 0, 422, "价格 0"),
    ("R02", 0.001, 422, "0.001 元，不足 1 分"),
    ("R03", -5, 422, "负数"),
    ("R04", 9999999.01, 422, "超上限"),
    ("R05", 1.999, 422, "三位小数"),
    ("R06", 0.01, 200, "1 分（最小合法）"),
    ("R07", 9999999, 200, "上限边界"),
]
for cid, price, want, desc in R_CASES:
    st, raw = api("POST", "/api/seller/products", {
        "name": "%s_%s" % (cid, _uid), "price": price, "description": "x",
        "stock": 1, "category_id": CAT, "image_urls": []}, STOK)
    case(cid, "价格校验", "price=%s（%s）" % (price, desc), "HTTP %d" % want, st, raw, st == want)

# ---------- L LIKE 转义 ----------
print("\n[L] LIKE 通配符转义（BUG-002 回归）")
api("POST", "/api/seller/products", {"name": "陶瓷杯%s" % _uid, "price": 10.0,
    "description": "x", "stock": 3, "category_id": CAT, "image_urls": []}, STOK)
api("POST", "/api/seller/products", {"name": "玻璃杯%s" % _uid, "price": 10.0,
    "description": "x", "stock": 3, "category_id": CAT, "image_urls": []}, STOK)

s_all, raw_all = api("GET", "/api/products?keyword=&page_size=100")
total_all = json.loads(raw_all).get("total", 0)
s1, r1 = api("GET", "/api/products?keyword=%25")          # % 通配符
t1 = json.loads(r1).get("total", 0)
case("L01", "LIKE 转义", "keyword=%% 不应匹配全部", "total < %d" % total_all, s1, r1,
     s1 == 200 and t1 < total_all, "实际 total=%d，全量=%d" % (t1, total_all))

s2, r2 = api("GET", "/api/products?keyword=_")
t2 = json.loads(r2).get("total", 0)
case("L02", "LIKE 转义", "keyword=_ 不应匹配任意单字符", "total < %d" % total_all, s2, r2,
     s2 == 200 and t2 < total_all, "实际 total=%d" % t2)

s3, r3 = api("GET", "/api/products?keyword=%E9%99%B6%E7%93%B7")   # 陶瓷
t3 = json.loads(r3).get("total", 0)
case("L03", "LIKE 转义", "keyword=陶瓷 应正常命中", "total >= 1", s3, r3, s3 == 200 and t3 >= 1,
     "实际 total=%d" % t3)

# ---------- U 上传鉴权 ----------
print("\n[U] 上传接口鉴权（OBS-001 回归）")
png = bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) + b"\x00" * 64
BOUND = "----r3boundary"


def multipart(field="file", fn="t.png"):
    body = (
        ("--%s\r\n" % BOUND).encode()
        + ('Content-Disposition: form-data; name="%s"; filename="%s"\r\n' % (field, fn)).encode()
        + b"Content-Type: image/png\r\n\r\n" + png + ("\r\n--%s--\r\n" % BOUND).encode()
    )
    return body, "multipart/form-data; boundary=%s" % BOUND


body, ct = multipart()
st, raw = api("POST", "/api/files/images", raw_body=body, ctype=ct)
case("U01", "上传鉴权", "未带 token 上传图片应被拒", "HTTP 401", st, raw, st == 401)

body, ct = multipart()
st, raw = api("POST", "/api/files/images", raw_body=body, ctype=ct, token=STOK)
case("U02", "上传鉴权", "带卖家 token 上传图片应成功", "HTTP 200", st, raw, st == 200)

body, ct = multipart()
st, raw = api("POST", "/api/files/media", raw_body=body, ctype=ct)
case("U03", "上传鉴权", "未带 token 上传媒体应被拒", "HTTP 401", st, raw, st == 401)

# ---------- G 分页溢出 ----------
print("\n[G] 分页溢出")
G_CASES = [
    ("G01", "page=2147483647&page_size=100", 200, "page 接近 int 上限，不应 500"),
    ("G02", "page=999999999&page_size=100", 200, "超大 page"),
    ("G03", "page=0", 422, "page=0 应被拒"),
    ("G04", "page_size=101", 422, "page_size 超上限应被拒"),
]
for cid, q, want, desc in G_CASES:
    st, raw = api("GET", "/api/products?" + q)
    case(cid, "分页", q + "（%s）" % desc, "HTTP %d" % want, st, raw, st == want)

# ---------- E 405 / 415 ----------
print("\n[E] 405 / 415 异常处理")
st, raw = api("GET", "/api/seller/login")
case("E01", "异常处理", "对 POST 接口用 GET，应 405 而非 500", "HTTP 405", st, raw, st == 405)

st, raw = api("POST", "/api/seller/login", raw_body=b"username=seller",
              ctype="text/plain")
case("E02", "异常处理", "错误 Content-Type，应 415 而非 500", "HTTP 415", st, raw, st == 415)

# ---------- T 注册事务 ----------
print("\n[T] 注册事务")
uniq = "t_dup_%s" % _uid
s1, _ = api("POST", "/api/customer/register", {"username": uniq, "password": PWD,
            "phone": "13800000000", "trade_location": "测试"})
s2, raw2 = api("POST", "/api/customer/register", {"username": uniq, "password": PWD,
               "phone": "13800000000", "trade_location": "测试"})
case("T01", "注册事务", "重复用户名应 409", "HTTP 409", s2, raw2, s1 == 200 and s2 == 409)

s3, raw3 = api("POST", "/api/customer/login", {"username": uniq, "password": PWD})
case("T02", "注册事务", "重复注册后原账号仍可登录（无脏数据）", "HTTP 200", s3, raw3, s3 == 200)

# ---------- 汇总 ----------
print("\n" + "=" * 80)
p = sum(1 for r in RES if r["result"] == "PASS")
print("修复验证：%d 条，通过 %d，失败 %d" % (len(RES), p, len(RES) - p))
print("=" * 80)
for r in RES:
    if r["result"] == "FAIL":
        print("  [FAIL] %s %s -> HTTP %s" % (r["id"], r["title"], r["status"]))
        print("         预期 %s" % r["expected"])

os.makedirs(OUT, exist_ok=True)
f = os.path.join(OUT, "r3fix-%s.json" % datetime.now().strftime("%Y%m%d-%H%M%S"))
with open(f, "w", encoding="utf-8") as fh:
    json.dump({"base": BASE, "run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "total": len(RES), "passed": p, "failed": len(RES) - p,
               "results": RES}, fh, ensure_ascii=False, indent=2)
print("证据:", os.path.relpath(f, ROOT))
