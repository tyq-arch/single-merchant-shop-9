"""把手动测试指南里的按钮文案改为界面实际文字。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "第2次评审测试", "05-手动测试指南.md")
t = open(p, encoding="utf-8").read()

pairs = [
    ("点「**我要购买**」", "点「**我 要 购 买**」"),
    ("| A1 | 什么都不填 | 点「提交」 |", "| A1 | 什么都不填 | 点「提 交 意 向」 |"),
    ("填好姓名 → 点「提交」", "填好姓名 → 点「提 交 意 向」"),
    ("1. 点「**修改联系方式**」", "1. 点「**修改我的信息**」"),
    ("4. 点保存 → 预期：保存成功", "4. 点「保存修改」→ 预期：保存成功"),
]
for old, new in pairs:
    assert old in t, "未找到锚点: %s" % old[:50]
    t = t.replace(old, new, 1)

open(p, "w", encoding="utf-8").write(t)
print("已修正 %d 处按钮文案" % len(pairs))
