"""修正测试结果文档中 12345 的提示文案，并同步到仓库发布版。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGETS = [
    os.path.join(ROOT, "第2次评审测试", "04-本周更新测试结果.md"),
    os.path.join(ROOT, "_work", "shop9", "docs", "测试", "08-第2次评审-本周更新测试结果.md"),
]

OLD = "| `12345` | 5 位短号 | ❌ 拒绝 | 联系电话只能输入数字 |"
NEW = "| `12345` | 5 位短号 | ❌ 拒绝 | 请输入 11 位手机号（当前 5 位） |"

for p in TARGETS:
    if not os.path.exists(p):
        print("  跳过（不存在）:", os.path.relpath(p, ROOT))
        continue
    t = open(p, encoding="utf-8").read()
    if OLD not in t:
        print("  无需修正:", os.path.relpath(p, ROOT))
        continue
    t = t.replace(OLD, NEW, 1)
    open(p, "w", encoding="utf-8").write(t)
    print("  已修正:", os.path.relpath(p, ROOT))
