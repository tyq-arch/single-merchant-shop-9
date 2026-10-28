"""合并后重写 README 的目录树、结果表、问题表。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "第2次评审测试", "README.md")
t = open(p, encoding="utf-8").read()

# A. 目录树
old_tree = """├── 测试材料/
│   ├── 02-测试报告.md        11 位手机号校验的定向测试
│   ├── 03-手动测试指南.md            打开网页手动验证的步骤与记录表
│   ├── 01-测试用例总表.xlsx     32 条用例（前端 17 + 后端 15）
│   ├── 02-测试报告.md    59 条用例的方法、结果、5 项发现
│   ├── 01-测试用例总表.xlsx   59 条用例 + 原始响应证据
│   └── 03-手动测试指南.md        21 项浏览器手工测试步骤 + 记录表"""
new_tree = """├── 测试材料/                          ← 已合并为 3 份
│   ├── 01-测试用例总表.xlsx          114 条用例（自动 91 + 手动 23）+ 原始响应证据
│   ├── 02-测试报告.md                测试方法、三批结果、6 项发现
│   └── 03-手动测试指南.md             23 项浏览器手工测试步骤 + 记录表"""
assert old_tree in t, "未找到目录树"
t = t.replace(old_tree, new_tree, 1)

# B. 编号说明
old_note = "> 编号 01~09 是**跨目录连续**的，表示建议的阅读顺序。"
new_note = ("> 各目录内编号连续。**测试材料已由 6 份合并为 3 份：**"
            "用例表 = 原 07 + 09 + 手动用例；报告 = 原 04 + 08；指南 = 原 05 + 10。")
assert old_note in t
t = t.replace(old_note, new_note, 1)

# C. 结果表
old_res = """| 测试批次 | 用例数 | 通过 | 结论 |
|---|---:|---:|---|
| 11 位手机号校验（前端函数 + 后端接口 + 一致性对照） | 32 | 23 PASS / 9 INCONSISTENT | 前端正确、后端未同步 |
| 挑战A 新增功能（功能验证） | 39 | 39 | 全部通过 |
| 挑战A 新增功能（边界与异常对抗） | 20 | 20 | 全部通过 |
| **合计（挑战A）** | **59** | **59** | **100%** |"""
new_res = """| 测试批次 | 方式 | 用例数 | 通过 | 结论 |
|---|---|:---:|---:|---|
| 11 位手机号校验（前端函数 / 后端接口 / 一致性） | 自动 | 32 | 23 PASS + 9 INCONSISTENT | 前端正确、后端未同步 |
| 挑战A 新增功能·功能验证 | 自动 | 39 | 39 | 全部通过 |
| 挑战A 新增功能·边界与异常对抗 | 自动 | 20 | 20 | 全部通过 |
| 浏览器手工走查 | 手动 | 23 | — | 待执行 |
| **合计** | | **114** | **82 自动通过** | **自动批 100%** |"""
assert old_res in t, "未找到结果表"
t = t.replace(old_res, new_res, 1)

# D. 问题表补一条
old_p = """| 5 | `schema.sql` 商品 `status` 缺 CHECK 约束 | 低 |"""
new_p = """| 5 | `schema.sql` 商品 `status` 缺 CHECK 约束 | 低 |
| 6 | **前端密码规则落后于后端**：`validateCustomerPassword` 只要 ≥6 位、`validateSellerPassword` 只查长度，后端已要求 ≥8 位 + 3 类 | 中 |"""
assert old_p in t
t = t.replace(old_p, new_p, 1)
t = t.replace("### 发现的 5 个问题（工程层面）", "### 发现的 6 个问题", 1)

# E. 证据表措辞
t = t.replace("| 59 条用例的**完整请求 + 响应体** |", "| 59 条自动用例的**完整请求 + 响应体** |", 1)

# F. 未测事项里的 21 项 -> 23 项
t = t.replace("（21 项）", "（23 项）")

open(p, "w", encoding="utf-8").write(t)
print("README 已更新")
for line in t.splitlines():
    if line.startswith("│   ├── 01-") or line.startswith("│   ├── 02-") or line.startswith("│   └── 03-"):
        print("  " + line)
