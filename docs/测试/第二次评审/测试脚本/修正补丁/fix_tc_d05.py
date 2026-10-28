"""修正 TC-D05 的断言：不存在的方法在路径匹配时返回 405 而非 404。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "functional_suite.py")
t = open(p, encoding="utf-8").read()

pairs = [
    ('''           "查询返回 410；找回接口不存在（404）",''',
     '''           "查询返回 410；找回接口不存在（404 或 405）",'''),
    ('''           s == 410 and s_rec == 404)''',
     '''           s == 410 and s_rec in (404, 405))'''),
]
for old, new in pairs:
    assert old in t, "未找到锚点: %s" % old[:60]
    t = t.replace(old, new, 1)

open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("TC-D05 断言已修正（语法 OK）")
