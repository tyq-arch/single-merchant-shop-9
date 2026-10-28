"""修正 I01 用例：首次提交应成功（200），重复提交才应被拒（I02 已验证）。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "challengeA_edge.py")
t = open(p, encoding="utf-8").read()

old = '''case("I01", "意向冲突", "同一客户对同一商品重复提交应被拒（FR-021）",
     "POST", "/api/customer/intents", {"product_id": PROD}, TA,
     "HTTP 409（同商品仅一个有效意向）",
     lambda s_, r: (s_ == 409, "HTTP %s" % s_))'''
new = '''case("I01", "意向冲突", "客户首次提交意向应成功（前置条件）",
     "POST", "/api/customer/intents", {"product_id": PROD}, TA,
     "HTTP 200（首次提交合法）",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))'''
assert old in t, "未找到 I01 锚点"
t = t.replace(old, new, 1)
open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("I01 预期已修正为 200（首次提交）")
