"""修正剩余 3 处「14 处」-> 15 处。（349 行是 E15 描述现象，保留）"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, "第2次评审测试")

FIX = [
    ("README.md",
     "│   └── AI出错与修正记录.md           14 处出错的现象、根因、修正、验证",
     "│   └── AI出错与修正记录.md           15 处出错的现象、根因、修正、验证"),
    ("汇报材料/06-口头汇报稿.md",
     "我把过程中 **14 处出错全部记下来了**",
     "我把过程中 **15 处出错全部记下来了**"),
    ("过程记录/AI出错与修正记录.md",
     "过程中一共出现 14 处错误，我全部记录在本文件里",
     "过程中一共出现 15 处错误，我全部记录在本文件里"),
]

for rel, old, new in FIX:
    p = os.path.join(D, rel)
    t = open(p, encoding="utf-8").read()
    if old in t:
        t = t.replace(old, new, 1)
        open(p, "w", encoding="utf-8").write(t)
        print("  已修正:", rel)
    else:
        print("  未找到:", rel, "|", old[:40])

print("\n复查「14 处」残留：")
import subprocess  # noqa: F401
for rel in ("README.md", "汇报材料/06-口头汇报稿.md", "过程记录/AI出错与修正记录.md"):
    t = open(os.path.join(D, rel), encoding="utf-8").read()
    n = t.count("14 处")
    print("  %-34s 仍有 %d 处" % (rel, n))
