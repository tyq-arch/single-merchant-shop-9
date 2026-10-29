"""在 AI 出错记录中补充 E15（后端规模数字写少）。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "第2次评审测试", "过程记录", "AI出错与修正记录.md")
t = open(p, encoding="utf-8").read()

# 1) 汇总表追加 E15
old_row = "| E14 | HTTP 测试 | `curl.exe -d` 传 JSON 被吃引号 | 否 | 改用文件 |"
new_row = old_row + "\n| E15 | 文档·后端规模 | 文件数/接口数写少（写 44/30，实际 45/39） | 否 | 已修正 |"
assert old_row in t, "未找到 E14 行"
t = t.replace(old_row, new_row, 1)

# 2) 详情追加 E15
anchor = "\n---\n\n## 三、哪些错误影响了结论"
detail = '''
---

### E15　后端规模数字写少

**现象**
文档里多处写成「44 个文件、30 个接口」，与实际不符。

**根因**
接口数是**手工数的**——我去数各 `Controller` 里的 `@GetMapping` / `@PostMapping`，漏算了 `SystemController` 的 2 个，也没把同一路径上的多方法合并，把 39 数成了 30。
文件数是**早期一次目录列举的旧结果**，后来新增了 `后端密码策略改动说明.md` 等文件，没有更新。

**怎么发现的**
重写汇报稿时，我自查"这些数字到底对不对"，于是直接问后端要它自己生成的规范：

```
GET http://127.0.0.1:8000/v3/api-docs
```

统计结果是 **35 条路径 / 39 个接口**。再用 `find` 数文件，得到 **45 个文件（其中 41 个 Java 源文件）**。

**修正**
把 4 份文档里的 `44 / 30` 全部改为 `45 / 39`，并把"AI 出错 14 处"同步改为 15 处。

**验证**
以 `/v3/api-docs` 的返回为准——这是后端**自己生成**的接口规范，比人工点算可靠。

**教训**
**能用系统自己给出的数据，就不要手工数。** 接口数用 OpenAPI 规范，文件数用 `find`/目录统计，都比人工清点可信。

''' + anchor
assert anchor in t, "未找到第三节锚点"
t = t.replace(anchor, detail, 1)

# 3) 影响表补充 E15
old_eff = "| **不影响结论** | E01、E03、E04、E05、E09~E14 |"
new_eff = "| **不影响结论** | E01、E03、E04、E05、E09~E15 |"
assert old_eff in t, "未找到影响表行"
t = t.replace(old_eff, new_eff, 1)

open(p, "w", encoding="utf-8").write(t)
print("E15 已补入出错记录")
print("当前错误条目数:", t.count("### E"))
