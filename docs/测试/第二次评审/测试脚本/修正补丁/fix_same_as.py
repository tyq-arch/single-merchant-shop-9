"""让同源需求支持映射到多个主需求（并集），避免覆盖用例列不完整。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "gen_cases.py")
t = open(p, encoding="utf-8").read()

old_map = '''SAME_AS = {
    "FR-036": "FR-001", "FR-037": "FR-004", "FR-038": "FR-005",
    "FR-039": "FR-017", "FR-040": "FR-009", "FR-041": "FR-035",
    "FR-047": "FR-013", "FR-049": "FR-022",
}'''
new_map = '''SAME_AS = {
    "FR-036": ["FR-001"],
    "FR-037": ["FR-004"],
    "FR-038": ["FR-005"],
    "FR-039": ["FR-017"],
    "FR-040": ["FR-009", "FR-010"],
    "FR-041": ["FR-035"],
    "FR-047": ["FR-013"],
    "FR-049": ["FR-022", "FR-023", "FR-024"],
}'''

old_branch = '''    elif req in SAME_AS:
        n_same += 1
        ids = sorted(req_cases.get(SAME_AS[req], []))
        conclusion = "同源需求（同 %s），用例见该条" % SAME_AS[req]'''
new_branch = '''    elif req in SAME_AS:
        n_same += 1
        prim = SAME_AS[req]
        ids = sorted({i for k in prim for i in req_cases.get(k, [])})
        conclusion = "同源需求（同 %s），用例见该条" % "、".join(prim)'''

for old, new in [(old_map, new_map), (old_branch, new_branch)]:
    assert old in t, "未找到锚点: %s" % old[:60]
    t = t.replace(old, new, 1)

open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("同源需求映射已改为多对多（语法 OK）")
