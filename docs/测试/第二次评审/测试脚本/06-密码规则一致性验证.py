"""验证新版前后端密码规则是否一致。

前端（utils/validate.js）：
  validateCustomerPassword  只要 >= 6 位
  validateSellerPassword    只要 >= 8 位（无强度要求）
后端（Validation.password）：
  >= 8 位 且 大写/小写/数字/特殊字符 至少 3 类
"""
import json
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8000"
uid = "pw" + str(int(time.time()))[-5:]


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


print("=" * 78)
print("前后端密码规则一致性验证")
print("=" * 78)
print("前端 validateCustomerPassword：>= 6 位即通过")
print("前端 validateSellerPassword  ：>= 8 位即通过（无强度要求）")
print("后端 Validation.password()   ：>= 8 位 且 至少 3 类字符")
print()

CASES = [
    ("Ab1!ab",  "6 位 4 类", "前端通过（>=6），后端应为 422（长度不足）"),
    ("Ab1!abc", "7 位 4 类", "前端通过（>=6），后端应为 422（长度不足）"),
    ("Abcdef12", "8 位 3 类", "前端通过，后端应为 200（3 类达标）"),
    ("abcdefgh", "8 位 1 类", "前端通过（>=6），后端应为 422（强度不足）"),
    ("12345678", "8 位 1 类", "前端通过（>=6），后端应为 422（强度不足）"),
]

print("=== 客户注册接口实测 ===")
for pwd, desc, note in CASES:
    s, b = api("POST", "/api/customer/register", {
        "username": "%s_%s" % (uid, abs(hash(pwd)) % 9999),
        "password": pwd, "phone": "13800000000", "trade_location": "测试",
    })
    msg = ""
    if isinstance(b, dict):
        msg = (b.get("error") or {}).get("message", "")
    print("  %-10s %-10s -> HTTP %-4s %s" % (pwd, desc, s, msg[:56]))
    print("             %s" % note)

# 卖家改密：前端只要 >=8，后端要 3 类
print()
print("=== 卖家改密接口实测 ===")
s, b = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
if s != 200:
    print("  卖家登录失败（可能密码已被改），跳过:", s, b)
else:
    tok = b["token"]
    for pwd, desc in [("abcdefgh", "8 位 1 类"), ("12345678", "8 位 1 类")]:
        s2, b2 = api("POST", "/api/seller/password",
                     {"old_password": "seller123", "new_password": pwd}, tok)
        m = (b2.get("error") or {}).get("message", "") if isinstance(b2, dict) else ""
        print("  %-10s %-10s -> HTTP %-4s %s" % (pwd, desc, s2, m[:56]))
        print("             前端 validateSellerPassword 只查长度 8，会放行 -> 用户会看到后端报错")
