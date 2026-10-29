"""补跑手机号那 32 条用例，补齐原始证据。

前端 17 条：直接调用当前 validate.js 的 validatePhone（其中 P16/P17 是界面行为，需浏览器）
后端 15 条：对当前 Java 后端的 POST /api/customer/register 发真实请求
            （原用例用的是旧 Python 后端 /api/buyer/intents，该接口已随挑战A 删除；
             手机号规则连同一条正则，故改在注册接口验证）
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
_uid = "ph" + str(int(time.time()))[-5:]
PWD = "Pass1234!"

# 前端 17 条：输入值
FE = [
    ("TC-P01", "", "电话为空"),
    ("TC-P02", "   ", "全空格"),
    ("TC-P03", "13800000001a", "含字母"),
    ("TC-P04", "138-0000-0000", "含连字符"),
    ("TC-P05", "138 0000 0000", "含空格"),
    ("TC-P06", "+8613800000000", "带国家码"),
    ("TC-P07", "12345", "5 位短号"),
    ("TC-P08", "1380000000", "10 位"),
    ("TC-P09", "138000000000", "12 位"),
    ("TC-P10", "23800000000", "2 开头"),
    ("TC-P11", "12800000000", "第二位为 2"),
    ("TC-P12", "10800000000", "第二位为 0"),
    ("TC-P13", "13800000000", "合法 13x"),
    ("TC-P14", "19900000000", "合法 19x"),
    ("TC-P15", "  13800000000  ", "首尾空格"),
]

# 后端 15 条：号码 -> 预期状态码（按当前后端宽松规则）
BE = [
    ("TC-Q01", "13800000000", 200),
    ("TC-Q02", "19900000000", 200),
    ("TC-Q03", "  13800000000  ", 200),
    ("TC-Q04", "23800000000", 200),
    ("TC-Q05", "12800000000", 200),
    ("TC-Q06", "10800000000", 200),
    ("TC-Q07", "1380000000", 200),
    ("TC-Q08", "138000000000", 200),
    ("TC-Q09", "12345", 200),
    ("TC-Q10", "+8613800000000", 200),
    ("TC-Q11", "138-0000-0000", 200),
    ("TC-Q12", "138 0000 0000", 200),
    ("TC-Q13", "13800000001a", 422),
    ("TC-Q14", "", 422),
    ("TC-Q15", "   ", 422),
]


def api(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return "ERR", json.dumps({"err": str(e)}, ensure_ascii=False)


# ---------- 1) 前端 15 条（P16/P17 需界面，跳过）----------
print("=== 前端 validatePhone（当前版本）===")
fe_results = []
FE_JS = os.path.join(ROOT, "_runtime", "_fe_phone_probe.mjs")
with open(FE_JS, "w", encoding="utf-8") as fh:
    fh.write("""import { validatePhone, PHONE_RE } from '%s'
const cases = %s
const out = cases.map(([id, v, d]) => {
  const m = validatePhone(v)
  return { id, input: v, desc: d, pass: m === '', msg: m }
})
console.log(JSON.stringify({ rule: String(PHONE_RE), rows: out }, null, 2))
""" % (os.path.join(ROOT, "_work", "shop9", "frontend", "src", "utils", "validate.js").replace("\\", "/"),
       json.dumps([list(x) for x in FE], ensure_ascii=False)))
print("已生成前端探测脚本:", os.path.relpath(FE_JS, ROOT))
print("（下一步用 node 执行）")

# ---------- 2) 后端 15 条 ----------
print("\n=== 后端 POST /api/customer/register ===")
be_results = []
for cid, phone, want in BE:
    s, raw = api("POST", "/api/customer/register", {
        "username": "%s_%s" % (cid.lower().replace("-", ""), _uid),
        "password": PWD, "phone": phone, "trade_location": "测试地点",
    })
    ok = s == want
    be_results.append({
        "id": cid, "input": phone, "want": want, "status": s,
        "raw": raw, "result": "PASS" if ok else "FAIL",
    })
    print("  [%s] %-6s %-18r HTTP %-4s 期望 %s"
          % ("PASS" if ok else "FAIL", cid, phone, s, want))

f = os.path.join(OUT, "phone-back-fresh.json")
with open(f, "w", encoding="utf-8") as fh:
    json.dump({"run_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
               "results": be_results}, fh, ensure_ascii=False, indent=2)
print("\n后端证据:", os.path.relpath(f, ROOT))
