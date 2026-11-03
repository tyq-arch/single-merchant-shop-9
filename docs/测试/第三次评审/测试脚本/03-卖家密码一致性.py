"""验证：卖家改密前端只查长度，后端要 3 类字符 → 不一致
（边界套件可能已把密码改成 Seller123!，这里两种都试）
"""
import json
import os
import urllib.error
import urllib.request
from datetime import datetime

BASE = "http://127.0.0.1:8000"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def api(m, p, body=None, token=None):
    d = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + p, data=d, method=m)
    if d:
        r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as x:
            return x.status, x.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


TOK = None
CUR = None
for cand in ["seller123", "Seller123!", "Abcd1234"]:
    s, b = api("POST", "/api/seller/login", {"username": "seller", "password": cand})
    if s == 200:
        TOK = json.loads(b)["token"]
        CUR = cand
        print("当前卖家密码: %s" % cand)
        break
if not TOK:
    raise SystemExit("三种候选密码都登录失败，需重置数据库")

out = [{"current_password": CUR}]
print()
print("=== 卖家改密：前端只查长度(>=8)，后端要 3 类 ===")
for pwd, fe_ok in [("abcdefgh", True), ("12345678", True), ("Ab1!ab", False), ("Abcd1234", True)]:
    s, raw = api("POST", "/api/seller/password",
                 {"old_password": CUR, "new_password": pwd}, TOK)
    msg = ""
    try:
        msg = json.loads(raw).get("error", {}).get("message", "")
    except Exception:
        msg = raw[:80]
    consistent = (fe_ok and s == 200) or ((not fe_ok) and s == 422)
    out.append({"pwd": pwd, "frontend_pass": fe_ok, "backend_status": s,
                "backend_msg": msg, "consistent": consistent})
    print("  %-10s 前端=%-4s 后端=%-4s → %s %s"
          % (pwd, "通过" if fe_ok else "拒绝", s,
             "一致" if consistent else "**不一致**", msg[:36]))
    if s == 200:
        print("             （密码已被改为 %s，后续用例用新密码）" % pwd)
        break

f = os.path.join(ROOT, "_runtime", "evidence", "r3-seller-pwd.json")
with open(f, "w", encoding="utf-8") as fh:
    json.dump({"run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "results": out}, fh, ensure_ascii=False, indent=2)
print("\n证据:", os.path.relpath(f, ROOT))
