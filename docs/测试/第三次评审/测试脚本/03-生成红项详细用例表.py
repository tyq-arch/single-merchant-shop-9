"""生成《红项详细测试用例》工作簿 + markdown。"""
import json
import os
from datetime import datetime

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = r"C:\Users\33857\Desktop\软件工程实践作业"
EV = os.path.join(ROOT, "_runtime")
OUT_DIR = os.path.join(ROOT, "第三次评审")

fe = json.load(open(os.path.join(EV, "detail_fe.json"), encoding="utf-8"))
be = json.load(open(os.path.join(EV, "detail_be.json"), encoding="utf-8"))

fe_p = {r["id"]: r for r in fe["phone"]}
fe_s = {r["id"]: r for r in fe["seller"]}
be_p = {r["id"]: r for r in be["phone"]}
be_s = {r["id"]: r for r in be["seller"]}

rows = []

# ---------- 红项 1：卖家密码 ----------
for r in fe["seller"]:
    cid = r["id"]
    b = be_s.get(cid, {})
    fe_pass = r["pass"]
    be_pass = (b.get("status") == 200)
    rows.append({
        "id": cid, "kind": "红项1 · 卖家密码",
        "desc": r["desc"], "input": r["input"],
        "fe_msg": r["msg"], "fe_pass": fe_pass,
        "be_status": b.get("status"), "be_msg": b.get("msg") or "改密成功",
        "be_pass": be_pass, "same": fe_pass == be_pass,
        "step": "1) 卖家登录\n2) POST /api/seller/password {old_password, new_password=<输入>}\n"
                "3) 同时用 Node 调 validateSellerPassword(<输入>)\n4) 比对两侧结论",
        "root": "前端 validateSellerPassword（L57~L63）只查长度 8~128，未调用 passwordClassCount；"
                "后端 Validation.password() 要求 ≥8 位且 3 类",
    })

# ---------- 红项 2：手机号 ----------
for r in fe["phone"]:
    cid = r["id"]
    b = be_p.get(cid, {})
    fe_pass = r["pass"]
    be_pass = (b.get("status") == 200)
    rows.append({
        "id": cid, "kind": "红项2 · 手机号",
        "desc": r["desc"], "input": r["input"],
        "fe_msg": r["msg"], "fe_pass": fe_pass,
        "be_status": b.get("status"), "be_msg": b.get("msg") or "注册成功",
        "be_pass": be_pass, "same": fe_pass == be_pass,
        "step": "1) Node 调 validatePhone(<输入>)\n"
                "2) POST /api/customer/register {phone=<输入>, 其余字段合法}\n3) 比对两侧结论",
        "root": "前端 PHONE_RE（L7）= ^1[3-9]\\d{9}$，只认 11 位手机号；"
                "后端 Validation.phone() 接受手机号/带区号座机/7~8 位本地号，并做分隔符归一化",
    })

n_same = sum(1 for r in rows if r["same"])
n_diff = len(rows) - n_same

# ---------- 样式 ----------
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
HF = PatternFill("solid", fgColor="D9E2F3")
OK = PatternFill("solid", fgColor="E2EFDA")
BAD = PatternFill("solid", fgColor="FCE4E4")
CEN = Alignment(horizontal="center", vertical="center", wrap_text=True)
WRAP = Alignment(wrap_text=True, vertical="top")


def head(ws, r=1):
    for c in ws[r]:
        if c.value is None:
            continue
        c.fill = HF
        c.font = Font(bold=True, size=10)
        c.alignment = CEN
        c.border = BORDER


def body(ws, s=2, size=9):
    for row in ws.iter_rows(min_row=s):
        for c in row:
            if c.value is None:
                continue
            c.font = Font(size=size)
            c.border = BORDER
            c.alignment = WRAP


def widths(ws, w):
    for i, x in enumerate(w, 1):
        ws.column_dimensions[get_column_letter(i)].width = x


wb = Workbook()

# ---- 表1 总览 ----
ws = wb.active
ws.title = "用例总览"
ws.append(["第三次评审 · 两个红项的详细测试用例"])
ws.append(["被测版本：main = a9cff43　　测试人：缪茂锦　　测试日期：2026-11-03"])
ws.append(["目的：把两个「前后端不一致」红项拆到单条输入，逐条给前端与后端的实际结论并对照"])
ws.append([])
ws.append(["红项", "用例数", "一致", "不一致", "核心根因"])
head(ws, 5)
s_cnt = sum(1 for r in rows if r["kind"].startswith("红项1"))
p_cnt = sum(1 for r in rows if r["kind"].startswith("红项2"))
ws.append(["红项1 · 卖家密码前后端不一致", s_cnt,
           sum(1 for r in rows if r["kind"].startswith("红项1") and r["same"]),
           sum(1 for r in rows if r["kind"].startswith("红项1") and not r["same"]),
           "前端 validateSellerPassword 只查长度，未做 3 类字符判断"])
ws.append(["红项2 · 手机号前后端不一致", p_cnt,
           sum(1 for r in rows if r["kind"].startswith("红项2") and r["same"]),
           sum(1 for r in rows if r["kind"].startswith("红项2") and not r["same"]),
           "前端只认 11 位手机号；后端接受座机/本地号并归一化"])
ws.append(["合计", len(rows), n_same, n_diff, ""])
body(ws, 6)
for c in ws[1]:
    c.font = Font(bold=True, size=12)
widths(ws, [26, 10, 10, 10, 62])

# ---- 表2/3 明细 ----
for title, prefix in (("红项1-卖家密码明细", "红项1"), ("红项2-手机号明细", "红项2")):
    ws2 = wb.create_sheet(title)
    ws2.append(["用例编号", "分类", "测试输入", "前端实际", "前端结论",
                "后端 HTTP", "后端实际", "后端结论", "是否一致", "根因", "复现步骤"])
    sub = [r for r in rows if r["kind"].startswith(prefix)]
    for r in sub:
        ws2.append([r["id"], r["desc"], r["input"], r["fe_msg"],
                    "通过" if r["fe_pass"] else "拒绝",
                    r["be_status"], r["be_msg"],
                    "接受" if r["be_pass"] else "拒绝",
                    "一致" if r["same"] else "不一致",
                    r["root"], r["step"]])
    head(ws2)
    body(ws2)
    for i, r in enumerate(sub, 2):
        ws2.cell(row=i, column=9).fill = OK if r["same"] else BAD
    widths(ws2, [10, 22, 22, 30, 9, 9, 32, 9, 10, 46, 44])
    ws2.freeze_panes = "A2"

# ---- 表4 环境 ----
ws4 = wb.create_sheet("环境与说明")
ws4.append(["项目", "内容"])
for k, v in [
    ("被测版本", "main = a9cff43（2026-11-03）"),
    ("后端", "backend-java（Spring Boot 2.7.18 + SQLite），起在 127.0.0.1:8000"),
    ("前端规则", "直接 import _work/shop9/frontend/src/utils/validate.js，调用其中两个函数"),
    ("卖家密码基线", "Seller123!（每跑完一条成功的改密都复位，保证后续用例状态干净）"),
    ("为什么两侧都测", "只看界面测不出来 —— 前端拦住时不报错，页面上看不到任何提示"),
    ("判「是否一致」的依据", "同一输入，前端通过 = 后端接受，才算一致；否则为不一致"),
    ("判「谁对」的依据", "密码：后端规则即需求 C-29；手机号：需求只说「联系电话必填」未定格式，属需求缺失"),
    ("导出时间", datetime.now().isoformat(sep=" ", timespec="seconds")),
]:
    ws4.append([k, v])
head(ws4)
body(ws4, size=9)
widths(ws4, [20, 110])

xlsx = os.path.join(OUT_DIR, "红项详细测试用例.xlsx")
wb.save(xlsx)
print("已生成:", os.path.relpath(xlsx, ROOT))

# ---------- markdown ----------
md = ["# 第三次评审 · 两个红项的详细测试用例", "",
      "> 被测版本：`main` = `a9cff43`　｜　测试人：缪茂锦　｜　测试日期：2026-11-03　",
      "> 目的：把两个「前后端不一致」红项拆到**单条输入**，逐条给前端与后端的实际结论并对照",
      "",
      "---", "", "## 一、总览", "",
      "| 红项 | 用例数 | 一致 | 不一致 |", "|---|:---:|:---:|:---:|",
      "| 红项1 · 卖家密码 | %d | %d | %d |" % (s_cnt,
        sum(1 for r in rows if r["kind"].startswith("红项1") and r["same"]),
        sum(1 for r in rows if r["kind"].startswith("红项1") and not r["same"])),
      "| 红项2 · 手机号 | %d | %d | %d |" % (p_cnt,
        sum(1 for r in rows if r["kind"].startswith("红项2") and r["same"]),
        sum(1 for r in rows if r["kind"].startswith("红项2") and not r["same"])),
      "| **合计** | **%d** | **%d** | **%d** |" % (len(rows), n_same, n_diff),
      ""]

for title, prefix, root in (
    ("二、红项 1 · 卖家密码（13 条）", "红项1",
     "前端 `validateSellerPassword`（L57~L63）只查长度 8~128，**没有调用 `passwordClassCount`**；"
     "后端 `Validation.password()` 要求 ≥8 位且 3 类字符"),
    ("三、红项 2 · 手机号（18 条）", "红项2",
     "前端 `PHONE_RE`（L7）= `^1[3-9]\\d{9}$`，只认 11 位手机号；后端 `Validation.phone()` "
     "接受手机号/带区号座机/7~8 位本地号，并做分隔符归一化"),
):
    md += ["---", "", "## " + title, "",
           "> **根因**：" + root, "",
           "| 编号 | 输入 | 前端 | 后端 | 一致 |",
           "|:---:|---|:---:|:---:|:---:|"]
    for r in rows:
        if not r["kind"].startswith(prefix):
            continue
        md.append("| %s | `%s` | %s | %s | %s |" % (
            r["id"], r["input"],
            "通过" if r["fe_pass"] else "拒绝",
            ("接受" if r["be_pass"] else "拒绝") + " (%s)" % r["be_status"],
            "✅" if r["same"] else "❌"))
    md.append("")

md += ["---", "", "## 四、怎么复现", "",
       "**前端侧**", "",
       "```bash",
       "node 测试脚本/05-红项详细用例-前端.mjs   # 直接 import validate.js 调函数",
       "```", "",
       "**后端侧**", "",
       "```bash",
       "python 测试脚本/06-红项详细用例-后端.py",
       "```", "",
       "**判定口径**", "",
       "| 项 | 说明 |",
       "|---|---|",
       "| 是否一致 | 同一输入，前端通过 = 后端接受，才算一致 |",
       "| 谁对 | 密码：后端规则即需求 C-29；手机号：需求只说「联系电话必填」，未定格式，属需求缺失 |",
       "| 为什么两侧都测 | 只看界面测不出来——前端拦住时不报错，页面上看不到任何提示 |",
       ""]

mdp = os.path.join(OUT_DIR, "红项详细测试用例.md")
open(mdp, "w", encoding="utf-8").write("\n".join(md))
print("已生成:", os.path.relpath(mdp, ROOT))
print()
print("用例 %d 条：一致 %d / 不一致 %d" % (len(rows), n_same, n_diff))
