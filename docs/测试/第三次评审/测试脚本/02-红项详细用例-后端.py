"""红项详细用例 · 后端侧执行（卖家密码 + 手机号）"""
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime

BASE = "http://127.0.0.1:8000"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UID = "d" + str(int(time.time()))[-6:]
BASE_PWD = "Seller123!"          # 合规基线密码，用于每次复位


def api(m, p, body=None, token=None):
    d = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    r = urllib.request.Request(BASE + p, data=d, method=m)
    if d:
        r.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as x:
            return x.status, x.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return "ERR", json.dumps({"err": str(e)})


def msg_of(raw):
    try:
        return json.loads(raw).get("error", {}).get("message", "")
    except Exception:
        return raw[:80]


# ---------- 登录并把密码统一到基线 ----------
TOK = None
CUR = None
for cand in ["seller123", BASE_PWD, "Abcd1234", "Ab1!abcd", "Abcd123456"]:
    s, b = api("POST", "/api/seller/login", {"username": "seller", "password": cand})
    if s == 200:
        TOK = json.loads(b)["token"]
        CUR = cand
        break
if not TOK:
    raise SystemExit("卖家密码三种候选都失败，请清库重启")

if CUR != BASE_PWD:
    s, _ = api("POST", "/api/seller/password",
               {"old_password": CUR, "new_password": BASE_PWD}, TOK)
    if s == 200:
        CUR = BASE_PWD
    else:
        raise SystemExit("无法把卖家密码复位到基线: %s" % s)
print("卖家密码基线: %s" % CUR)

cases = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "红项用例定义.json"), encoding="utf-8"))
out = {"run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
       "base_pwd": BASE_PWD, "seller": [], "phone": []}

# ---------- 卖家密码 ----------
print("\n=== 后端·卖家改密 ===")
for cid, desc, value, note in cases["seller"]:
    v = value
    if v == "__LONG128__":
        v = "Ab1!" + "x" * 124
    elif v == "__LONG129__":
        v = "Ab1!" + "x" * 125

    s, raw = api("POST", "/api/seller/password",
                 {"old_password": CUR, "new_password": v}, TOK)
    rec = {"id": cid, "desc": desc, "note": note,
           "input": value if not value.startswith("__") else "%s…（共 %d 位）" % (v[:4], len(v)),
           "raw_input_len": len(v), "status": s, "msg": msg_of(raw), "raw": raw[:400]}
    out["seller"].append(rec)
    print("  %-7s HTTP %-4s %s" % (cid, s, rec["msg"][:44] or "改密成功"))

    if s == 200:
        # 密码被改成 v，复位回基线
        CUR = v
        s2, raw2 = api("POST", "/api/seller/password",
                       {"old_password": CUR, "new_password": BASE_PWD}, TOK)
        if s2 != 200:
            print("     ⚠ 复位失败:", s2, msg_of(raw2)[:60])
        else:
            CUR = BASE_PWD

# ---------- 手机号 ----------
print("\n=== 后端·注册手机号 ===")
for cid, desc, value in cases["phone"]:
    s, raw = api("POST", "/api/customer/register", {
        "username": "%s_%s" % (cid.lower().replace("-", ""), UID),
        "password": "Pass1234!", "phone": value, "trade_location": "测试地点"})
    rec = {"id": cid, "desc": desc, "input": value, "status": s,
           "msg": msg_of(raw), "raw": raw[:400]}
    out["phone"].append(rec)
    print("  %-7s %-18r HTTP %-4s %s" % (cid, value, s, rec["msg"][:44]))

f = os.path.join(ROOT, "_runtime", "detail_be.json")
with open(f, "w", encoding="utf-8") as fh:
    json.dump(out, fh, ensure_ascii=False, indent=2)
print("\n后端结果:", os.path.relpath(f, ROOT))
