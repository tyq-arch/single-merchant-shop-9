"""修复 gen_cases.py 中「需求覆盖矩阵」的错误生成逻辑。

原 bug：区间正则 ^(FR|NFR)-(\\d+)~?(?:FR-)?(\\d+)$ 因数字可回溯，
会把单条编号（如 FR-001）误判为区间（FR 0..1），产生 FR-000 等不存在的编号，
并把源需求的用例错误复制给每个展开编号。

修复后：
  1. 直接从每条用例的 trace 中用正则提取需求编号（FR / NFR / TBD）
  2. 输出全部需求（FR-001~FR-057、NFR-001~NFR-015），逐条列出覆盖用例
  3. 补充「需求名称」列，便于验收时逐条对照
  4. 无功能用例的过程需求（FR-053~FR-057）单独标注
"""
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
R = os.path.join(ROOT, "_runtime")

# ---------- 1. 固化需求来源 ----------
SRC_REQ = os.path.join(ROOT, "_work", "shop9", "Requirement Analysis", "需求规格说明书.md")
DST_REQ = os.path.join(R, "requirements-current.md")
assert os.path.exists(SRC_REQ), "未找到当前需求文件: %s" % SRC_REQ
shutil.copyfile(SRC_REQ, DST_REQ)
print("已固化需求来源 ->", os.path.relpath(DST_REQ, ROOT))

# ---------- 2. 替换 gen_cases.py 的矩阵生成段 ----------
p = os.path.join(R, "gen_cases.py")
text = open(p, encoding="utf-8").read()

start = text.index("trace_map = {}")
end = text.index('ws3.freeze_panes = "A2"') + len('ws3.freeze_panes = "A2"')
old_block = text[start:end]

new_block = '''# 需求编号匹配：从 trace 中提取 FR-xxx / NFR-xxx / TBD-xx
REQ_ID_RE = re.compile(r"(?:FR|NFR)-\\d{1,3}|TBD-\\d{1,2}")

# 需求名称表：从当前需求规格说明书解析，便于逐条对照
REQ_DOC = os.path.join(HERE, "requirements-current.md")
req_names = {}
if os.path.exists(REQ_DOC):
    for line in open(REQ_DOC, encoding="utf-8"):
        m = re.match(r"^\\|\\s*(FR-\\d{3})\\s*\\|\\s*([^|]+?)\\s*\\|", line)
        if m:
            req_names[m.group(1)] = m.group(2)
            continue
        m = re.match(r"^\\|\\s*(NFR-\\d{3})\\s*\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|", line)
        if m:
            req_names[m.group(1)] = "%s：%s" % (m.group(2), m.group(3))

# 需求 -> 覆盖用例
req_cases = {}
for r in DATA["results"]:
    for rid in REQ_ID_RE.findall(r["trace"]):
        req_cases.setdefault(rid, set()).add(r["id"])

# 过程需求（无对应功能用例）
PROCESS_REQS = {"FR-%03d" % n for n in range(53, 58)}

ALL_REQS = (["FR-%03d" % n for n in range(1, 58)]
            + ["NFR-%03d" % n for n in range(1, 16)])

covered_n = 0
for req in ALL_REQS:
    ids = sorted(req_cases.get(req, []))
    name = req_names.get(req, "")
    if ids:
        covered_n += 1
        conclusion = "已覆盖（%d 条用例）" % len(ids)
    elif req in PROCESS_REQS:
        conclusion = "过程需求，无功能用例"
    else:
        conclusion = "未覆盖"
    ws3.append([req, name, len(ids), "、".join(ids), conclusion])

# 其他追溯对象（非需求编号，但用例引用了）
others = sorted(k for k in req_cases if not re.match(r"^(FR|NFR)-\\d{1,3}$", k))
for k in others:
    ids = sorted(req_cases[k])
    ws3.append([k, "（接口文档章节）", len(ids), "、".join(ids), "已覆盖（%d 条用例）" % len(ids)])

ws3.append([])
ws3.append(["统计", "需求总数 %d 条" % len(ALL_REQS),
            "已覆盖 %d 条" % covered_n,
            "覆盖率 %.1f%%" % (100.0 * covered_n / len(ALL_REQS)),
            "其中 FR-053~FR-057 为 Scrum 过程需求，无功能用例"])

style_header(ws3)
style_body(ws3)
set_widths(ws3, [12, 34, 10, 52, 26])
ws3.freeze_panes = "A2"'''

text = text[:start] + new_block + text[end:]
open(p, "w", encoding="utf-8").write(text)
print("已替换 gen_cases.py 的矩阵生成段（%d 字符 -> %d 字符）" % (len(old_block), len(new_block)))

# 语法校验
import ast
ast.parse(open(p, encoding="utf-8").read())
print("gen_cases.py 语法 OK")
