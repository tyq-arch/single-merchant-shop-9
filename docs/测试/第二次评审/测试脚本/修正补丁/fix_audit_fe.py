"""修正审计脚本中前端手机号用例的「预期」判定。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "gen_audit.py")
t = open(p, encoding="utf-8").read()

old = '''        audit.append({
            "id": r["id"], "module": "手机号校验·前端",
            "content": "联系电话=%r（%s）" % (r["input"], r["desc"]), "mode": "自动",
            "process": "1) 调用 validatePhone(%r)\\n2) 读取返回文案\\n3) 返回空串=通过，非空=拒绝"
                       % r["input"],
            "expected": "返回空串（通过）" if r["pass_expected"] else "返回错误文案（拒绝）"
                        if "pass_expected" in r else ("通过" if r["pass"] else "拒绝"),
            "actual": r["msg"],
            "raw": json.dumps({"调用": "validatePhone(%r)" % r["input"],
                               "返回": r["msg"], "规则": d["rule"]}, ensure_ascii=False),
            "strength": "强（校验返回文案）", "recheck": "通过", "suspect": "无",
            "note": "本轮按当前 validate.js 补跑",
        })'''

new = '''        # 设计上应被接受的三个合法号码
        accept = r["id"] in ("TC-P13", "TC-P14", "TC-P15")
        agree = (r["pass"] == accept)
        audit.append({
            "id": r["id"], "module": "手机号校验·前端",
            "content": "联系电话=%r（%s）" % (r["input"], r["desc"]), "mode": "自动",
            "process": "1) 调用 validatePhone(%r)\\n2) 读取返回文案\\n3) 返回空串=通过，非空=拒绝"
                       % r["input"],
            "expected": "通过（返回空串）" if accept else "拒绝（返回错误文案）",
            "actual": r["msg"],
            "raw": json.dumps({"调用": "validatePhone(%r)" % r["input"],
                               "返回": r["msg"], "规则": d["rule"]}, ensure_ascii=False),
            "strength": "强（校验返回文案）",
            "recheck": "通过" if agree else "不通过",
            "suspect": "无" if agree else "❌ 疑似假通过",
            "note": "本轮按当前 validate.js 补跑" if agree else "实际结果与设计预期相反",
        })'''

assert old in t, "未找到前端手机号段"
t = t.replace(old, new, 1)
open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("已修正前端手机号预期判定")
