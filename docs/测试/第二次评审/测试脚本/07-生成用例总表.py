"""合并 07（32 条手机号）+ 09（59 条挑战A）+ 10 的 21 条手动用例 -> 一份测试用例总表。

目标：
  1. 一个工作簿看完所有用例（自动 + 手动）
  2. 每条用例都要有「分步操作 + 具体测试数据 + 明确预期」，不再是只给一个 HTTP 调用
"""
import json
import os

from openpyxl import Workbook, load_workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
D = os.path.join(ROOT, "第2次评审测试", "测试材料")
OUT = os.path.join(D, "01-测试用例总表.xlsx")

F07 = os.path.join(D, "07-手机号校验测试用例.xlsx")
F09 = os.path.join(D, "09-挑战A新增功能测试用例.xlsx")

# ---------------------------------------------------------------- 读取已有用例
def read_detail(path):
    wb = load_workbook(path)
    ws = wb["测试用例明细"]
    rows = []
    for r in ws.iter_rows(min_row=2, values_only=True):
        if not r or not r[0]:
            continue
        rows.append({
            "id": r[0], "module": r[1], "title": r[2], "trace": r[3],
            "precond": r[4], "step": r[5] or "", "expected": r[6],
            "actual": r[7], "result": r[8], "issue": r[9] or "", "note": r[10] or "",
        })
    return rows


phone = read_detail(F07)
cha = read_detail(F09)


def split_http(step):
    """把 'POST /api/x\\n{json}' 拆成 (方法路径, body)"""
    lines = [l for l in str(step).splitlines() if l.strip()]
    if not lines:
        return step, ""
    first = lines[0].strip()
    rest = "\n".join(lines[1:]).strip()
    return first, rest


def numbered_http(step, expected):
    """把 HTTP 调用改写成 4 步操作"""
    api, body = split_http(step)
    out = ["1) %s" % api]
    if body:
        out.append("2) 请求体：%s" % body.replace("\n", " "))
    else:
        out.append("2) 无需请求体")
    out.append("3) 读取 HTTP 状态码与响应体")
    out.append("4) 校验：%s" % str(expected).replace("\n", " "))
    return "\n".join(out)


# ---------------------------------------------------------------- 手动用例（21 条）
MANUAL = [
    ("M-A1", "密码强度", "弱密码 pass1234 注册应被拒", "C-29 / NFR-003 / FR-017",
     "前端已启动；打开 /register 页",
     "1) 打开 http://127.0.0.1:5173/register\n2) 用户名填 manual01、电话填 13800000001、默认交易地点填「图书馆门口」\n3) 密码填 pass1234\n4) 点「注册」",
     "username=manual01, password=pass1234, phone=13800000001, trade_location=图书馆门口",
     "页面出现红字：密码强度不足：至少包含大写字母、小写字母、数字、特殊字符中的 3 类，且不少于 8 位；不注册"),
    ("M-A2", "密码强度", "强密码 Pass1234! 注册应成功", "C-29 / NFR-003 / FR-017",
     "同上",
     "1) 沿用上一步表单\n2) 密码改为 Pass1234!\n3) 点「注册」",
     "password=Pass1234!（4 类字符）", "注册成功，提示成功或跳转登录"),
    ("M-A3", "密码强度", "7 位 4 类密码应被拒（长度不足）", "C-29 / NFR-003",
     "打开 /register 页",
     "1) 用户名 manual02、电话 13800000002、地点「食堂」\n2) 密码填 Ab1!xyz（7 位）\n3) 点「注册」",
     "password=Ab1!xyz", "被拒，提示长度不能少于 8 个字符"),
    ("M-A4", "密码强度", "3 类字符（大小写+数字）应通过", "C-29 / NFR-003",
     "打开 /register 页",
     "1) 用户名 manual03、电话 13800000003、地点「教学楼」\n2) 密码填 Abcd1234\n3) 点「注册」",
     "password=Abcd1234", "注册成功"),
    ("M-A5", "密码强度", "6 位强密码：前端放行但后端拒绝（规则不一致）", "C-29 / NFR-003",
     "前端已启动；打开 /register 页",
     "1) 用户名 manual05、电话 13800000005、地点「实验室」\n"
     "2) 密码填 Ab1!ab（6 位，4 类字符）\n"
     "3) 点「注册」\n"
     "4) 观察：前端是否拦下？提交后后端返回什么？",
     "password=Ab1!ab（6 位 4 类）",
     "**已知会不一致**：前端 validateCustomerPassword 只要求 >=6 位，会放行；"
     "后端要求 >=8 位+3 类，返回 422「密码长度不能少于 8 个字符」"),
    ("M-A6", "密码强度", "8 位单一类别：前端放行但后端拒绝", "C-29 / NFR-003",
     "前端已启动；打开 /register 页",
     "1) 用户名 manual06、电话 13800000006、地点「实验室」\n"
     "2) 密码填 abcdefgh（8 位，仅小写）\n"
     "3) 点「注册」\n"
     "4) 观察前后端反馈差异",
     "password=abcdefgh（8 位 1 类）",
     "**已知会不一致**：前端只查长度 6，放行；后端返回 422「密码强度不足」"),
    ("M-B1", "客户账号", "客户可用注册账号登录", "FR-018",
     "已用 M-A2 注册 manual01",
     "1) 打开 http://127.0.0.1:5173/login\n2) 用户名 manual01、密码 Pass1234!\n3) 点登录",
     "username=manual01 / password=Pass1234!", "登录成功，跳转商品列表或我的页面"),
    ("M-B2", "客户账号", "个人资料显示正确", "FR-018 / NFR-004",
     "已登录",
     "1) 打开 http://127.0.0.1:5173/my/profile\n2) 查看页面字段",
     "——", "显示用户名 manual01、电话 13800000001、交易地点、注册时间"),
    ("M-B3", "访问控制", "未登录点「我要购买」被引导登录", "C-07 / FR-020",
     "已退出登录；有在售商品",
     "1) 打开 http://127.0.0.1:5173/\n2) 点进任意商品详情\n3) 点「我 要 购 买」",
     "——", "被带到登录/注册页，不能直接下单（不得出现下单成功）"),
    ("M-C2", "多商品", "卖家商品列表有多件在售", "C-01 / FR-001",
     "已登录卖家 seller/seller123",
     "1) 打开 http://127.0.0.1:5173/seller/products\n2) 数列表条数",
     "——", "列表显示多件商品（挑战A 前只能有一件）"),
    ("M-C3", "多商品", "已有在售商品时再发布一件仍成功", "C-01 / FR-002",
     "已登录卖家；已建二级类别",
     "1) 打开 /seller/publish\n2) 名称「手动测试商品」、价格 88、库存 3\n3) 必须选择一个二级类别\n4) 提交",
     "name=手动测试商品, price=88, stock=3, category=二级类别", "发布成功，列表中多一件，不被「已有在售商品」拦截"),
    ("M-D1", "库存", "库存调 0 自动下架", "C-03 / C-27",
     "已登录卖家；有在售商品",
     "1) 打开 /seller/products\n2) 找到目标商品，把库存改为 0\n3) 保存",
     "stock=0", "商品状态变为「售罄下架」"),
    ("M-D2", "库存", "补货后恢复在售", "C-03 / C-27",
     "上一步商品已售罄下架",
     "1) 同一商品把库存改为 5\n2) 保存",
     "stock=5", "状态恢复「在售」"),
    ("M-D3", "库存", "库存调负数被拒", "C-18",
     "已登录卖家",
     "1) 同一商品把库存改为 -3\n2) 保存",
     "stock=-3", "被拒，提示库存不能为负数；库存保持原值"),
    ("M-E1", "类别", "可新建大类与二级小类", "C-02 / FR-003",
     "已登录卖家",
     "1) 打开 /seller/categories\n2) 新增大类「手动大类」\n3) 在其下新增小类「手动小类」",
     "name=手动大类 / 手动小类, parent=手动大类", "两级树形结构正确显示"),
    ("M-E2", "类别", "有商品的类别禁止删除", "C-22 / FR-004",
     "该小类下已有商品",
     "1) 在类别页选中该小类\n2) 点删除并确认",
     "——", "被拒，提示该类目下存在商品不可删除"),
    ("M-E3", "类别", "发布商品不选类别被拒", "C-22 / FR-002",
     "已登录卖家",
     "1) 打开 /seller/publish\n2) 名称价格库存都填，但不选类别\n3) 提交",
     "category_id 留空", "被拒，提示必须选择二级类别"),
    ("M-F1", "队列", "两个客户排队位次依次为 1、2", "C-12 / FR-022 / NFR-006",
     "两个客户账号；同一在售商品",
     "1) 客户 manual01 登录，对该商品提交意向\n2) 记录排队位次\n3) 客户 manual03 登录，对同一商品提交意向\n4) 记录排队位次",
     "同一 product_id，两个不同 customer", "manual01 位次 1，manual03 位次 2"),
    ("M-F2", "队列", "同一客户重复提交被拒", "FR-021",
     "manual01 已有一条有效意向",
     "1) manual01 对同一商品再次提交意向",
     "同一 customer + 同一 product", "被拒，提示已存在有效意向"),
    ("M-F3", "交易", "卖家进入交易，队首转入且商品冻结", "C-17 / FR-027",
     "该商品队列中已有客户",
     "1) 卖家打开 /seller/intents\n2) 对目标商品点「进入交易」\n3) 回商品列表看状态",
     "——", "队首意向状态变「进入交易」，商品状态变「冻结」"),
    ("M-F4", "交易", "冻结期间新客户不能下单", "C-13 / FR-026",
     "商品已冻结",
     "1) 用第三个客户账号打开该商品\n2) 尝试提交购买意向",
     "——", "被拒，页面提示商品交易中"),
    ("M-F5", "交易", "标记交易失败并作废，下一位补位", "C-26 / FR-030",
     "该商品有一笔进行中交易",
     "1) 卖家打开 /seller/intents\n2) 对该笔交易标记「失败」\n3) 失败者处理选「作废」\n4) 查看队列",
     "result=FAILED, failed_action=VOID", "失败者变「已作废」；队列中下一位成为队首"),
    ("M-G1", "售罄", "库存 1 成交后其余排队意向自动作废", "C-19 / FR-034",
     "库存为 1 的商品；两名客户已排队",
     "1) 卖家对队首进入交易\n2) 标记交易「成功」\n3) 回商品列表看状态\n4) 看另一名客户的意向状态",
     "stock=1, result=SUCCESS", "库存变 0、商品「售罄下架」；另一客户意向变「售罄作废」"),
]

# ---------------------------------------------------------------- 组装
records = []

for r in phone:
    records.append({
        "id": r["id"], "module": r["module"], "title": r["title"],
        "mode": "自动", "trace": r["trace"], "precond": r["precond"],
        "step": r["step"], "data": "", "expected": r["expected"],
        "actual": r["actual"], "result": r["result"],
        "issue": r["issue"], "note": r["note"],
    })

for r in cha:
    api, body = split_http(r["step"])
    records.append({
        "id": r["id"], "module": r["module"], "title": r["title"],
        "mode": "自动", "trace": r["trace"], "precond": r["precond"],
        "step": numbered_http(r["step"], r["expected"]),
        "data": body if body else "（无请求体）",
        "expected": r["expected"], "actual": r["actual"], "result": r["result"],
        "issue": r["issue"], "note": r["note"],
    })

for cid, mod, title, trace, pre, step, data, exp in MANUAL:
    records.append({
        "id": cid, "module": mod, "title": title, "mode": "手动", "trace": trace,
        "precond": pre, "step": step, "data": data, "expected": exp,
        "actual": "（待执行）", "result": "待执行", "issue": "", "note": "见 03-手动测试指南",
    })

# ---------------------------------------------------------------- 写工作簿
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
HEAD_FILL = PatternFill("solid", fgColor="D9E2F3")
HEAD_FONT = Font(bold=True, size=10)
BODY_FONT = Font(size=9)
WRAP = Alignment(wrap_text=True, vertical="top")
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)
PASS_FILL = PatternFill("solid", fgColor="E2EFDA")
FAIL_FILL = PatternFill("solid", fgColor="FCE4E4")
TODO_FILL = PatternFill("solid", fgColor="FFF2CC")


def head(ws, row=1):
    for c in ws[row]:
        if c.value is None:
            continue
        c.fill = HEAD_FILL
        c.font = HEAD_FONT
        c.alignment = CENTER
        c.border = BORDER


def body(ws, start=2):
    for r in ws.iter_rows(min_row=start):
        for c in r:
            if c.value is None:
                continue
            c.font = BODY_FONT
            c.border = BORDER
            c.alignment = WRAP


def widths(ws, ws_w):
    for i, w in enumerate(ws_w, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w


wb = Workbook()

# 表1 总览
ws1 = wb.active
ws1.title = "用例总览"
ws1.append(["《在线购物系统（单卖家版）》测试用例总表"])
ws1.append(["合并来源：07-手机号校验测试用例（32 条）+ 09-挑战A新增功能测试用例（59 条）+ 手动用例（21 条）"])
ws1.append(["编写人：缪茂锦（测试岗）　整理日期：2026-10-28　代码版本：main = 8d10723"])
ws1.append([])
ws1.append(["序号", "测试模块", "自动用例", "手动用例", "合计", "通过", "待执行", "说明"])
head(ws1, row=5)

mods = []
agg = {}
for r in records:
    m = r["module"]
    if m not in agg:
        agg[m] = {"auto": 0, "manual": 0, "pass": 0, "todo": 0}
        mods.append(m)
    agg[m]["auto" if r["mode"] == "自动" else "manual"] += 1
    if r["result"] == "PASS":
        agg[m]["pass"] += 1
    if r["result"] == "待执行":
        agg[m]["todo"] += 1

for i, m in enumerate(mods, 1):
    a = agg[m]
    ws1.append([i, m, a["auto"], a["manual"], a["auto"] + a["manual"],
                a["pass"], a["todo"], ""])

tot = len(records)
pas = sum(1 for r in records if r["result"] == "PASS")
todo = sum(1 for r in records if r["result"] == "待执行")
inc = sum(1 for r in records if r["result"] == "INCONSISTENT")
ws1.append(["合计", "", sum(a["auto"] for a in agg.values()),
            sum(a["manual"] for a in agg.values()), tot, pas, todo,
            "另有 %d 条 INCONSISTENT（前后端不一致，见观察项 OBS-014）" % inc])
body(ws1, start=6)
for c in ws1[1]:
    c.font = Font(bold=True, size=12)
widths(ws1, [8, 18, 10, 10, 8, 8, 10, 52])

# 表2 用例明细
ws2 = wb.create_sheet("测试用例明细")
COLS = ["用例编号", "所属模块", "用例标题", "执行方式", "需求追溯", "前置条件",
        "操作步骤", "测试数据", "预期结果", "实际结果", "执行结果", "关联问题", "备注"]
ws2.append(COLS)
for r in records:
    ws2.append([r["id"], r["module"], r["title"], r["mode"], r["trace"],
                r["precond"], r["step"], r["data"], r["expected"],
                r["actual"], r["result"], r["issue"], r["note"]])
head(ws2)
body(ws2)
for i, r in enumerate(records, start=2):
    cell = ws2.cell(row=i, column=11)
    if r["result"] == "PASS":
        cell.fill = PASS_FILL
    elif r["result"] == "待执行":
        cell.fill = TODO_FILL
    else:
        cell.fill = FAIL_FILL
widths(ws2, [10, 14, 34, 8, 24, 24, 46, 34, 34, 30, 11, 10, 20])
ws2.freeze_panes = "A2"

# 表3 原始响应证据（仅自动用例有）
ws3 = wb.create_sheet("原始响应证据")
ws3.append(["用例编号", "执行时间", "HTTP", "完整响应体（供第三方复核）"])
wb9 = load_workbook(F09)
ws9 = wb9["原始响应证据"]
for r in ws9.iter_rows(min_row=2, values_only=True):
    if r and r[0]:
        ws3.append([r[0], r[1], r[2], r[3]])
head(ws3)
body(ws3)
widths(ws3, [10, 24, 8, 130])
ws3.freeze_panes = "A2"

# 表4 环境与复核
ws4 = wb.create_sheet("环境与复核方法")
ws4.append(["项目", "内容"])
for k, v in [
    ("被测系统", "在线购物系统（单卖家版）挑战A 版"),
    ("后端", "backend-java（Spring Boot 2.7.18 / SQLite / 39 个接口）"),
    ("前端", "Vue 3 + Vite（19 个视图）"),
    ("自动化用例", "由 Python 脚本对 127.0.0.1:8000 发真实 HTTP 请求，逐条记录请求与响应"),
    ("手动用例", "按「操作步骤」在浏览器中操作，结果填入「实际结果」列"),
    ("预期值来源", "需求条款（C-xx / FR-xxx / NFR-xxx），不取自代码实现"),
    ("", ""),
    ("复核方式一", "打开本表『原始响应证据』页，任选一条，用同样请求体重放并比对返回"),
    ("复核方式二", "清空数据库、启动后端，运行 _runtime/challengeA_suite.py 与 challengeA_edge.py"),
    ("复核方式三", "用 Swagger UI（http://127.0.0.1:8000/docs）手工发单条请求"),
    ("复核方式四", "手动用例按「操作步骤」在浏览器重做一遍"),
]:
    ws4.append([k, v])
head(ws4)
body(ws4)
widths(ws4, [16, 100])

wb.save(OUT)
print("已生成:", os.path.relpath(OUT, ROOT))
print("用例总数:", tot, "（自动", sum(a['auto'] for a in agg.values()),
      "+ 手动", sum(a['manual'] for a in agg.values()), "）")
print("PASS:", pas, " 待执行:", todo, " INCONSISTENT:", inc)
print("模块数:", len(mods))
