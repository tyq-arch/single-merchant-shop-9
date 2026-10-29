"""补齐 06 中遗漏的两处旧计数。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "缪茂锦-测试交付物", "06-阶段性评审报告素材-缪茂锦.md")
t = open(p, encoding="utf-8").read()

pairs = [
    ("| 5 | 汇总观察项与改进建议 10 项：覆盖上传接口鉴权缺失、文件类型校验、需求与文档不一致等 |",
     "| 5 | 汇总观察项与改进建议 13 项：覆盖上传接口鉴权缺失、文件类型校验、需求与实现不一致等 |"),
    ("| 7 | 功能验收校验：输出 47 项功能验收校验清单，逐项核对 FR-001 ~ FR-052 的验收点，通过 45 项 |",
     "| 7 | 功能验收校验：输出 47 项功能验收校验清单，逐项核对 FR-001 ~ FR-057 的验收点，通过 45 项 |"),
]
for old, new in pairs:
    assert old in t, "未找到锚点: %s" % old[:70]
    t = t.replace(old, new, 1)

open(p, "w", encoding="utf-8").write(t)
print("06 补齐 2 处")
