"""修正 challengeA_suite.py 中 case() 的返回值解包（返回 2 元组）。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "challengeA_suite.py")
t = open(p, encoding="utf-8").read()

fixes = [
    ('s_, lg, _ = case("B04"', 's_, lg = case("B04"'),
    ('_, pb, _ = case("C02"', '_, pb = case("C02"'),
    ('s_, i1, _ = case("F01"', 's_, i1 = case("F01"'),
    ('s_, i2, _ = case("F02"', 's_, i2 = case("F02"'),
]
for old, new in fixes:
    if old in t:
        t = t.replace(old, new, 1)
        print("  修正:", old)
    else:
        print("  跳过（未找到）:", old)

open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("语法 OK")
