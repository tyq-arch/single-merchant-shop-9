"""修正 suite_l 中 submit() 的解包（该函数返回 2 元组）。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "functional_suite.py")
t = open(p, encoding="utf-8").read()

old = '    _s, sb, _ = submit(pid, "性能买家甲", "13800000001")'
new = '    _s, sb = submit(pid, "性能买家甲", "13800000001")'
assert old in t, "未找到锚点"
t = t.replace(old, new, 1)

open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("已修正 submit 解包（语法 OK）")
