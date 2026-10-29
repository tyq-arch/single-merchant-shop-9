"""在合并用例生成脚本里追加 2 条手动用例，验证前后端密码规则不一致。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "gen_merged_cases.py")
t = open(p, encoding="utf-8").read()

anchor = '''    ("M-B1", "客户账号", "客户可用注册账号登录", "FR-018",'''

NEW = '''    ("M-A5", "密码强度", "6 位强密码：前端放行但后端拒绝（规则不一致）", "C-29 / NFR-003",
     "前端已启动；打开 /register 页",
     "1) 用户名 manual05、电话 13800000005、地点「实验室」\\n"
     "2) 密码填 Ab1!ab（6 位，4 类字符）\\n"
     "3) 点「注册」\\n"
     "4) 观察：前端是否拦下？提交后后端返回什么？",
     "password=Ab1!ab（6 位 4 类）",
     "**已知会不一致**：前端 validateCustomerPassword 只要求 >=6 位，会放行；"
     "后端要求 >=8 位+3 类，返回 422「密码长度不能少于 8 个字符」"),
    ("M-A6", "密码强度", "8 位单一类别：前端放行但后端拒绝", "C-29 / NFR-003",
     "前端已启动；打开 /register 页",
     "1) 用户名 manual06、电话 13800000006、地点「实验室」\\n"
     "2) 密码填 abcdefgh（8 位，仅小写）\\n"
     "3) 点「注册」\\n"
     "4) 观察前后端反馈差异",
     "password=abcdefgh（8 位 1 类）",
     "**已知会不一致**：前端只查长度 6，放行；后端返回 422「密码强度不足」"),
''' + anchor

assert anchor in t, "未找到插入锚点"
t = t.replace(anchor, NEW, 1)
open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("已追加 M-A5 / M-A6 两条手动用例")
