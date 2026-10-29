"""调整边界测试的改密段：不再尝试改回 seller123（强度不足会被拒），
改为验证「新密码生效、旧密码失效」，并把 STOK 换成新令牌。
"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "challengeA_edge.py")
t = open(p, encoding="utf-8").read()

old = '''# 改回原密码，避免影响后续
api("POST", "/api/seller/login", {"username": "seller", "password": "Seller123!"})
s2, b2, _ = api("POST", "/api/seller/login", {"username": "seller", "password": "Seller123!"})
if s2 == 200:
    api("POST", "/api/seller/password",
        {"old_password": "Seller123!", "new_password": "seller123"}, b2["token"])'''

new = '''# 注意：seller123 只有 2 类字符，改密后无法再改回（强度校验会拒绝）。
# 因此这里只验证「新密码生效、旧密码失效」，后续用例改用新令牌。
case("K04", "卖家改密", "改密后新密码 Seller123! 可登录", "POST", "/api/seller/login",
     {"username": "seller", "password": "Seller123!"}, None, "HTTP 200",
     lambda s_, r: (s_ == 200, "HTTP %s" % s_))

case("K05", "卖家改密", "改密后旧密码 seller123 已失效", "POST", "/api/seller/login",
     {"username": "seller", "password": "seller123"}, None, "HTTP 401",
     lambda s_, r: (s_ == 401, "HTTP %s" % s_))

STOK = api("POST", "/api/seller/login",
           {"username": "seller", "password": "Seller123!"})[1].get("token")
print("     卖家密码已变为 Seller123!（重跑需重置数据库）")'''

assert old in t, "未找到锚点"
t = t.replace(old, new, 1)
open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("改密段已调整，语法 OK")
