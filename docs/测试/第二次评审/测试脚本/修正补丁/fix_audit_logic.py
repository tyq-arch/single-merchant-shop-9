"""修正审计脚本：200 响应的判定改为「能否解析为合法 JSON 且不含 error」，
而不是简单看长度阈值。并为 K02 补充「用例标题与实际请求不符」的说明。
"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "gen_audit.py")
t = open(p, encoding="utf-8").read()

old = '''        if code == "200":
            if len(raw) < 30:
                ok, reason = False, "响应体过短，可能不是真实载荷"'''
new = '''        if code == "200":
            # 判定标准：能解析为合法 JSON 对象、非空、且不含 error 字段
            try:
                obj = json.loads(raw)
                if not isinstance(obj, dict) or not obj:
                    ok, reason = False, "响应体不是有效 JSON 对象"
                elif "error" in obj:
                    ok, reason = False, "返回 200 但响应体含 error"
                elif len(raw) < 12:
                    ok, reason = False, "响应体几乎为空"
            except Exception:
                ok, reason = False, "响应体不是合法 JSON"'''
assert old in t, "未找到 200 判定段"
t = t.replace(old, new, 1)

# K02 特殊说明
old2 = '''# ---------- 5) 写工作簿 ----------'''
new2 = '''# ---------- 4.5) 已知用例表述问题（不会造成假通过，但要记） ----------
for a in audit:
    if a["id"] == "K02":
        a["note"] = ("用例标题写「新密码与旧密码相同」，但实际请求 old=seller123、"
                     "new=Seller123! 并不相同 —— 属**用例表述错误**，已在报告中记问题 7")

# ---------- 5) 写工作簿 ----------'''
assert old2 in t
t = t.replace(old2, new2, 1)

open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("审计逻辑已修正")
