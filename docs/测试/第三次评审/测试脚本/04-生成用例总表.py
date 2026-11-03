"""生成《第3次评审》测试用例总表。"""
import glob
import json
import os

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
EV = os.path.join(ROOT, "_runtime", "evidence")
D3 = os.path.join(ROOT, "\u7b2c3\u6b21\u8bc4\u5ba1")
OUT = os.path.join(D3, "\u6d4b\u8bd5\u7528\u4f8b\u603b\u8868.xlsx")


def latest(pat):
    fs = sorted(glob.glob(os.path.join(EV, pat)))
    return fs[-1] if fs else None


fix = json.load(open(latest("r3fix-*.json"), encoding="utf-8"))
fe = json.load(open(os.path.join(EV, "r3-fe.json"), encoding="utf-8"))
sp = json.load(open(os.path.join(EV, "r3-seller-pwd.json"), encoding="utf-8"))
ch = json.load(open(latest("challengeA-2026110*.json"), encoding="utf-8"))
ed = json.load(open(latest("edge-2026110*.json"), encoding="utf-8"))
mn = json.load(open(latest("manual-2026110*.json"), encoding="utf-8"))

# ---------- 一致性对照 ----------
fe_phone = {r["id"]: r for r in fe["phone"]}
fe_pwd = {r["id"]: r for r in fe["customerPwd"]}
be = {r["id"]: r for r in fix["results"]}

consistency = []
for cid in ["H%02d" % i for i in range(1, 13)]:
    f, b = fe_phone.get(cid), be.get(cid)
    if not f or not b:
        continue
    b_ok = b["status"] == 200
    consistency.append({
        "id": "C-" + cid, "kind": "\u624b\u673a\u53f7", "input": f["input"],
        "fe": "\u901a\u8fc7" if f["ok"] else "\u62d2\u7edd",
        "be": "\u63a5\u53d7" if b_ok else "\u62d2\u7edd",
        "consistent": f["ok"] == b_ok,
        "fe_msg": f["msg"], "be_raw": b["raw"],
    })

for cid in ["P%02d" % i for i in range(1, 7)]:
    f, b = fe_pwd.get(cid), be.get(cid)
    if not f or not b:
        continue
    b_ok = b["status"] == 200
    consistency.append({
        "id": "C-" + cid, "kind": "\u5ba2\u6237\u5bc6\u7801", "input": f["input"],
        "fe": "\u901a\u8fc7" if f["ok"] else "\u62d2\u7edd",
        "be": "\u63a5\u53d7" if b_ok else "\u62d2\u7edd",
        "consistent": f["ok"] == b_ok,
        "fe_msg": f["msg"], "be_raw": b["raw"],
    })

for r in sp["results"]:
    if "pwd" not in r:
        continue
    consistency.append({
        "id": "C-S-" + r["pwd"][:6], "kind": "\u5356\u5bb6\u5bc6\u7801", "input": r["pwd"],
        "fe": "\u901a\u8fc7" if r["frontend_pass"] else "\u62d2\u7edd",
        "be": "\u63a5\u53d7" if r["backend_status"] == 200 else "\u62d2\u7edd",
        "consistent": r["consistent"],
        "fe_msg": "", "be_raw": r["backend_msg"],
    })

n_inc = sum(1 for c in consistency if not c["consistent"])

# ---------- 样式 ----------
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
HF = PatternFill("solid", fgColor="D9E2F3")
BF = Font(size=9)
WRAP = Alignment(wrap_text=True, vertical="top")
CEN = Alignment(horizontal="center", vertical="center", wrap_text=True)
OK = PatternFill("solid", fgColor="E2EFDA")
BAD = PatternFill("solid", fgColor="FCE4E4")


def head(ws, r=1):
    for c in ws[r]:
        if c.value is None:
            continue
        c.fill = HF
        c.font = Font(bold=True, size=10)
        c.alignment = CEN
        c.border = BORDER


def body(ws, s=2):
    for row in ws.iter_rows(min_row=s):
        for c in row:
            if c.value is None:
                continue
            c.font = BF
            c.border = BORDER
            c.alignment = WRAP


wb = Workbook()

# 表1 总览
ws = wb.active
ws.title = "\u7528\u4f8b\u603b\u89c8"
ws.append(["\u7b2c 3 \u6b21\u8bc4\u5ba1 \u00b7 \u6d4b\u8bd5\u7528\u4f8b\u603b\u8868"])
ws.append(["\u88ab\u6d4b\u7248\u672c\uff1amain = a9cff43\uff08tag v2.0 \u4e4b\u540e\uff0911-03"])
ws.append(["\u6d4b\u8bd5\u4eba\uff1a\u7f2a\u8302\u9526\uff08\u6d4b\u8bd5\u5c97\uff09\u3000\u6d4b\u8bd5\u65e5\u671f\uff1a2026-11-03"])
ws.append([])
ws.append(["\u5e8f\u53f7", "\u6279\u6b21", "\u5185\u5bb9", "\u6761\u6570", "\u901a\u8fc7", "\u5931\u8d25/\u4e0d\u4e00\u81f4", "\u8bf4\u660e"])
head(ws, 5)
rows = [
    (1, "\u4fee\u590d\u9a8c\u8bc1", "\u9488\u5bf9\u672c\u5468 7 \u9879\u4fee\u590d\u9010\u9879\u9a8c\u8bc1", fix["total"], fix["passed"], fix["failed"],
     "\u5bc6\u7801/\u624b\u673a\u53f7/\u4ef7\u683c/LIKE/\u4e0a\u4f20\u9274\u6743/\u5206\u9875/405\u00b7415/\u4e8b\u52a1"),
    (2, "\u4e00\u81f4\u6027\u5bf9\u7167", "\u524d\u7aef validate.js \u4e0e\u540e\u7aef\u63a5\u53e3\u9010\u9879\u5bf9\u6bd4", len(consistency),
     len(consistency) - n_inc, n_inc, "\u624b\u673a\u53f7 12 + \u5ba2\u6237\u5bc6\u7801 6 + \u5356\u5bb6\u5bc6\u7801 4"),
    (3, "\u56de\u5f52\u00b7\u529f\u80fd", "\u6cbf\u7528\u7b2c 2 \u6b21\u8bc4\u5ba1\u7684\u6311\u6218A \u529f\u80fd\u7528\u4f8b", ch["total"], ch["passed"], ch["failed"], "\u65e0\u9700\u4fee\u6539\uff0c\u76f4\u63a5\u91cd\u8dd1"),
    (4, "\u56de\u5f52\u00b7\u8fb9\u754c", "\u6cbf\u7528\u7b2c 2 \u6b21\u8bc4\u5ba1\u7684\u8fb9\u754c\u5bf9\u6297\u7528\u4f8b", ed["total"], ed["passed"], ed["failed"], "\u65e0\u9700\u4fee\u6539\uff0c\u76f4\u63a5\u91cd\u8dd1"),
    (5, "\u56de\u5f52\u00b7\u624b\u52a8", "\u6cbf\u7528\u7b2c 2 \u6b21\u8bc4\u5ba1\u7684\u624b\u52a8\u7528\u4f8b", mn["total"], mn["passed"], mn["total"] - mn["passed"], "\u6309\u6267\u884c\u6b21\u6570\u8ba1"),
    (6, "\u56e2\u961f\u96c6\u6210\u6d4b\u8bd5", "\u56e2\u961f\u81ea\u5e26 mvn test\uff08BackendIntegrationTest + ValidationTest\uff09", 6, 5, 1,
     "uploadPng \u672a\u5e26 token \u5bfc\u81f4 401"),
]
for r in rows:
    ws.append(list(r))
tot = sum(r[3] for r in rows)
tot_p = sum(r[4] for r in rows)
tot_f = sum(r[5] for r in rows)
ws.append(["\u5408\u8ba1", "", "", tot, tot_p, tot_f, ""])
body(ws, 6)
ws.cell(row=ws.max_row, column=1).font = Font(bold=True, size=9)
for c in ws[1]:
    c.font = Font(bold=True, size=12)
for i, w in enumerate([8, 16, 46, 10, 10, 14, 40], 1):
    ws.column_dimensions[get_column_letter(i)].width = w

# 表2 修复验证明细
ws2 = wb.create_sheet("\u4fee\u590d\u9a8c\u8bc1\u660e\u7ec6")
ws2.append(["\u7528\u4f8b\u7f16\u53f7", "\u6a21\u5757", "\u7528\u4f8b\u5185\u5bb9", "\u9884\u671f", "HTTP", "\u5b9e\u9645\u54cd\u5e94", "\u7ed3\u679c", "\u8bf4\u660e"])
for r in fix["results"]:
    ws2.append([r["id"], r["module"], r["title"], r["expected"], r["status"],
                str(r["raw"])[:400], r["result"], r["note"]])
head(ws2)
body(ws2)
for i, r in enumerate(fix["results"], 2):
    ws2.cell(row=i, column=7).fill = OK if r["result"] == "PASS" else BAD
for i, w in enumerate([10, 14, 40, 14, 8, 60, 10, 18], 1):
    ws2.column_dimensions[get_column_letter(i)].width = w
ws2.freeze_panes = "A2"

# 表3 一致性对照
ws3 = wb.create_sheet("\u524d\u540e\u7aef\u4e00\u81f4\u6027")
ws3.append(["\u5bf9\u7167\u7f16\u53f7", "\u7c7b\u578b", "\u8f93\u5165", "\u524d\u7aef", "\u540e\u7aef", "\u662f\u5426\u4e00\u81f4", "\u524d\u7aef\u63d0\u793a", "\u540e\u7aef\u54cd\u5e94"])
for c in consistency:
    ws3.append([c["id"], c["kind"], c["input"], c["fe"], c["be"],
                "\u4e00\u81f4" if c["consistent"] else "\u4e0d\u4e00\u81f4",
                c["fe_msg"], str(c["be_raw"])[:200]])
head(ws3)
body(ws3)
for i, c in enumerate(consistency, 2):
    ws3.cell(row=i, column=6).fill = OK if c["consistent"] else BAD
for i, w in enumerate([14, 12, 20, 8, 8, 12, 40, 60], 1):
    ws3.column_dimensions[get_column_letter(i)].width = w
ws3.freeze_panes = "A2"

# 表4 回归汇总
ws4 = wb.create_sheet("\u56de\u5f52\u6c47\u603b")
ws4.append(["\u6279\u6b21", "\u7528\u4f8b\u7f16\u53f7", "\u7528\u4f8b\u5185\u5bb9", "\u7ed3\u679c"])
for tag, d in (("\u529f\u80fd", ch), ("\u8fb9\u754c", ed), ("\u624b\u52a8", mn)):
    for r in d["results"]:
        title = r.get("title") or r.get("content") or ""
        ws4.append([tag, r["id"], title, r["result"]])
head(ws4)
body(ws4)
for i in range(2, ws4.max_row + 1):
    ws4.cell(row=i, column=4).fill = OK if ws4.cell(row=i, column=4).value == "PASS" else BAD
for i, w in enumerate([8, 12, 56, 10], 1):
    ws4.column_dimensions[get_column_letter(i)].width = w
ws4.freeze_panes = "A2"

# 表5 环境与说明
ws5 = wb.create_sheet("\u73af\u5883\u4e0e\u8bf4\u660e")
ws5.append(["\u9879\u76ee", "\u5185\u5bb9"])
for k, v in [
    ("\u88ab\u6d4b\u7248\u672c", "main = a9cff43\uff082026-11-03\uff0c\u542b\u672c\u5468 11 \u4e2a\u63d0\u4ea4\uff09"),
    ("\u540e\u7aef", "backend-java\uff08Spring Boot 2.7.18 + SQLite\uff09\uff0cPython \u540e\u7aef\u5df2\u4e8e eb51437 \u5220\u9664"),
    ("\u524d\u7aef", "Vue 3 + Vite\uff08\u672c\u5468\u4e3a Element Plus \u6309\u9700\u5f15\u5165\uff09"),
    ("\u542f\u52a8\u65b9\u5f0f", "java -jar target/shop-backend-1.0.0.jar --server.port=8000"),
    ("\u6570\u636e\u9694\u79bb", "\u6bcf\u8f6e\u6e05\u7a7a SHOP_DATA_DIR\uff0c\u6570\u636e\u5e26\u65f6\u95f4\u6233\u540e\u7f00"),
    ("\u8bc1\u636e", "\u89c1\u300c\u6d4b\u8bd5\u811a\u672c/\u8bc1\u636e/\u300d\u4e0b\u7684 JSON"),
    ("", ""),
    ("\u4e3a\u4ec0\u4e48\u8fd8\u8981\u56de\u5f52", "\u672c\u5468\u540e\u7aef\u6539\u4e86 14 \u4e2a\u6587\u4ef6\uff08\u542b Validation/ProductService/FileController \u7b49\uff09\uff0c"
     "\u6539\u52a8\u9762\u6d89\u53ca\u516c\u5171\u63a5\u53e3\uff0c\u5fc5\u987b\u91cd\u8dd1\u539f\u6709\u7528\u4f8b"),
    ("\u7528\u4f8b\u53ef\u91cd\u8dd1\u6027", "\u5168\u90e8\u7528\u4f8b\u5747\u4e3a\u811a\u672c\u5316\uff0c\u6e05\u5e93\u540e\u6309\u987a\u5e8f\u6267\u884c\u53ef\u590d\u73b0"),
]:
    ws5.append([k, v])
head(ws5)
body(ws5)
for i, w in enumerate([18, 110], 1):
    ws5.column_dimensions[get_column_letter(i)].width = w

os.makedirs(D3, exist_ok=True)
wb.save(OUT)
print("已生成:", os.path.relpath(OUT, ROOT))
print("修复验证 %d / 一致性 %d（不一致 %d）/ 回归 %d / 集成 %d"
      % (fix["total"], len(consistency), n_inc,
         ch["total"] + ed["total"] + mn["total"], 6))
print("合计用例:", tot)
