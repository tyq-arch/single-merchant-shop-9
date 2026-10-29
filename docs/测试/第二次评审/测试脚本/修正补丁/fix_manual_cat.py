"""修正手动用例脚本：二级类别在 children 里，需要递归查找。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "run_manual_cases.py")
t = open(p, encoding="utf-8").read()

old = '''cats = json.loads(craw).get("items", [])
leaf = next((c for c in cats if c.get("level") == 2), None)
CAT = leaf["id"] if leaf else None'''

new = '''cats = json.loads(craw).get("items", [])
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
print("     二级类别: %s" % (CAT or "未找到"))'''

assert old in t, "未找到类别查找段"
t = t.replace(old, new, 1)
open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("已修正二级类别查找逻辑")
