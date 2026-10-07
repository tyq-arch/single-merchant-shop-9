"""黑盒功能测试套件 —— 缪茂锦（测试岗）执行脚本。

针对已启动的后端（http://127.0.0.1:8000）与前端（http://127.0.0.1:5173）
执行功能测试，逐条记录「预期 vs 实际」，输出：
  - 控制台可读报告
  - 同目录下的 test_results.json（供测试报告引用）

运行前建议重置后端数据库以获得确定性结果。
"""
import io
import json
import os
import threading
import urllib.error
import urllib.request
import uuid
from datetime import datetime

BASE = "http://127.0.0.1:8000"
FRONT = "http://127.0.0.1:5173"
OUT_JSON = os.path.join(os.path.dirname(os.path.abspath(__file__)), "test_results.json")

RESULTS = []
_T0 = datetime.now()


# --------------------------------------------------------------------------
# 基础设施
# --------------------------------------------------------------------------
def api(method, path, body=None, token=None, raw=False, timeout=30):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            payload = r.read()
            if raw:
                return r.status, payload, _headers(r)
            return r.status, json.loads(payload.decode("utf-8")), _headers(r)
    except urllib.error.HTTPError as e:
        payload = e.read()
        if raw:
            return e.code, payload, _headers(e)
        try:
            return e.code, json.loads(payload.decode("utf-8")), _headers(e)
        except Exception:
            return e.code, payload.decode("utf-8", "replace")[:200], _headers(e)
    except Exception as e:
        return "ERR", f"{type(e).__name__}: {e}", {}


def _headers(response):
    """HTTP 头统一转为小写键，避免大小写差异导致断言误判。"""
    return {k.lower(): v for k, v in response.headers.items()}


def front_get(path):
    try:
        req = urllib.request.Request(FRONT + path, method="GET")
        with urllib.request.urlopen(req, timeout=25) as r:
            return r.status, r.read(), _headers(r)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), _headers(e)
    except Exception as e:
        return "ERR", str(e).encode(), {}


def upload(filename, content, field="file"):
    boundary = "----DSHBoundary" + uuid.uuid4().hex
    body = b""
    body += ("--" + boundary + "\r\n").encode()
    body += ('Content-Disposition: form-data; name="%s"; filename="%s"\r\n'
             % (field, filename)).encode()
    body += b"Content-Type: application/octet-stream\r\n\r\n"
    body += content
    body += ("\r\n--" + boundary + "--\r\n").encode()
    req = urllib.request.Request(BASE + "/api/files/images", data=body, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except Exception:
            return e.code, payload.decode("utf-8", "replace")[:200]
    except Exception as e:
        return "ERR", str(e)


def record(cid, module, title, trace, expected, actual, ok, note=""):
    RESULTS.append({
        "id": cid, "module": module, "title": title, "trace": trace,
        "expected": expected, "actual": actual,
        "status": "PASS" if ok else "FAIL", "note": note,
    })
    mark = "PASS" if ok else "FAIL"
    line = "  [%s] %-7s %s" % (mark, cid, title)
    if not ok:
        line += "\n         预期: %s\n         实际: %s" % (expected, actual)
    print(line, flush=True)


def expect(cid, module, title, trace, expected, actual, ok, note=""):
    record(cid, module, title, trace, expected, actual, ok, note)
    return ok


# --------------------------------------------------------------------------
# 业务辅助
# --------------------------------------------------------------------------
def login(user="seller", pwd="seller123"):
    s, b, _ = api("POST", "/api/seller/login", {"username": user, "password": pwd})
    return b.get("token") if isinstance(b, dict) else None


def reset_shop(tok):
    """把店铺恢复到「无在售商品」的干净起点。"""
    s, b, _ = api("GET", "/api/seller/product/current", token=tok)
    product = b.get("product") if isinstance(b, dict) else None
    if not product:
        return
    pid = product["id"]
    s, lst, _ = api("GET", "/api/seller/products/%s/intents?page_size=100" % pid, token=tok)
    for item in (lst.get("items", []) if isinstance(lst, dict) else []):
        if item["status"] == "IN_TRANSACTION":
            api("POST", "/api/seller/orders/%s/transaction" % item["order_id"],
                {"result": "FAILED", "note": "suite reset"}, token=tok)
    api("POST", "/api/seller/products/%s/off-shelf" % pid, token=tok)


def publish(tok, name="测试商品", price=99.9, description=None, image_url=None):
    body = {"name": name, "price": price}
    if description is not None:
        body["description"] = description
    if image_url is not None:
        body["image_url"] = image_url
    s, b, _ = api("POST", "/api/seller/products", body, token=tok)
    return s, b


def submit(pid, name="张三", phone="13800000000"):
    s, b, _ = api("POST", "/api/buyer/intents",
                  {"product_id": pid, "buyer_name": name, "buyer_phone": phone})
    return s, b


def current_product():
    s, b, _ = api("GET", "/api/products/current")
    return b


def png_bytes(size=64):
    """最小合法 PNG（1x1）后补零，用于上传测试。"""
    head = bytes.fromhex(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4"
        "890000000a49444154789c6360000002000100ffff03000006000557bfabd400"
        "00000049454e44ae426082")
    return head + b"\x00" * max(0, size - len(head))


# ==========================================================================
# A. 公开商品浏览  FR-043~046
# ==========================================================================
def suite_a(tok):
    print("\n[A] 公开商品浏览 (FR-043~046)")
    reset_shop(tok)

    b = current_product()
    expect("TC-A01", "商品浏览", "无商品在售时返回 NONE 空状态", "FR-044",
           "state=NONE, product=null, message=暂无商品在售",
           json.dumps(b, ensure_ascii=False),
           b.get("state") == "NONE" and b.get("product") is None)

    pid = publish(tok, "浏览测试商品", 88.5)[1]["product"]["id"]
    b = current_product()
    expect("TC-A02", "商品浏览", "在售时返回 ON_SALE 与完整商品信息", "FR-045",
           "state=ON_SALE, price=88.5, status_label=在售",
           "state=%s price=%s label=%s" % (b.get("state"), b.get("product", {}).get("price"),
                                           b.get("product", {}).get("status_label")),
           b.get("state") == "ON_SALE" and b["product"]["price"] == 88.5)

    s, d, _ = api("GET", "/api/products/%s" % pid)
    expect("TC-A03", "商品浏览", "商品详情接口按 id 返回商品", "FR-043",
           "HTTP 200 且 product.id 一致",
           "HTTP %s id=%s" % (s, d.get("product", {}).get("id") if isinstance(d, dict) else "-"),
           s == 200 and d["product"]["id"] == pid)

    api("POST", "/api/seller/products/%s/freeze" % pid, token=tok)
    b = current_product()
    expect("TC-A04", "商品浏览", "冻结时返回 FROZEN 与交易中提示", "FR-046",
           "state=FROZEN, message=商品交易中",
           "state=%s message=%s" % (b.get("state"), b.get("message")),
           b.get("state") == "FROZEN" and b.get("message") == "商品交易中")

    api("POST", "/api/seller/products/%s/off-shelf" % pid, token=tok)
    s, d, _ = api("GET", "/api/products/%s" % pid)
    expect("TC-A05", "商品浏览", "已下架商品详情返回 404", "FR-043",
           "HTTP 404 NOT_FOUND",
           "HTTP %s %s" % (s, d.get("error", {}).get("code") if isinstance(d, dict) else d),
           s == 404)

    s, d, _ = api("GET", "/api/products/current")
    expect("TC-A06", "商品浏览", "/current 路由优先于 /{product_id} 通配路由", "接口设计§2",
           "返回三态结构（含 state 字段），而非商品详情结构",
           "keys=%s" % sorted(d.keys()),
           "state" in d and "product" in d)


# ==========================================================================
# B. 卖家登录与鉴权  FR-032~035
# ==========================================================================
def suite_b(tok):
    print("\n[B] 卖家登录与鉴权 (FR-032~035)")

    s, b, _ = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
    expect("TC-B01", "登录鉴权", "正确账号密码登录成功并下发令牌", "FR-032",
           "HTTP 200 + token + expires_at + seller.username=seller",
           "HTTP %s token=%s" % (s, bool(b.get("token")) if isinstance(b, dict) else b),
           s == 200 and bool(b.get("token")) and b["seller"]["username"] == "seller")

    for label, user, pwd, cid in [("密码错误", "seller", "wrongpass", "TC-B02"),
                                  ("用户名不存在", "nosuchuser", "seller123", "TC-B03")]:
        s, b, _ = api("POST", "/api/seller/login", {"username": user, "password": pwd})
        expect(cid, "登录鉴权", "%s 时返回 401" % label, "FR-032",
               "HTTP 401 UNAUTHORIZED",
               "HTTP %s %s" % (s, b.get("error", {}).get("code") if isinstance(b, dict) else b),
               s == 401)

    s, b, _ = api("GET", "/api/seller/history")
    expect("TC-B04", "登录鉴权", "无令牌访问受保护接口返回 401", "NFR-006",
           "HTTP 401", "HTTP %s" % s, s == 401)

    s, b, _ = api("GET", "/api/seller/history", token="forged-token-xxx")
    expect("TC-B05", "登录鉴权", "伪造令牌被拒绝", "NFR-006",
           "HTTP 401", "HTTP %s" % s, s == 401)

    s, b, _ = api("POST", "/api/seller/register", {})
    expect("TC-B06", "登录鉴权", "不提供注册接口", "FR-033",
           "HTTP 404", "HTTP %s" % s, s == 404)

    s, b, _ = api("POST", "/api/seller/password",
                  {"old_password": "wrongold", "new_password": "newpass123"}, token=tok)
    expect("TC-B07", "登录鉴权", "修改密码时旧密码校验失败返回 401", "FR-035",
           "HTTP 401", "HTTP %s" % s, s == 401)

    s, b, _ = api("POST", "/api/seller/password",
                  {"old_password": "seller123", "new_password": "abc"}, token=tok)
    expect("TC-B08", "登录鉴权", "新密码少于 6 位被拒绝", "FR-035",
           "HTTP 422 VALIDATION_ERROR", "HTTP %s" % s, s == 422)

    # 正例改密 + 复原
    s, b, _ = api("POST", "/api/seller/password",
                  {"old_password": "seller123", "new_password": "newpass123"}, token=tok)
    ok1 = s == 200
    s2, _, _ = api("POST", "/api/seller/login", {"username": "seller", "password": "seller123"})
    s3, _, _ = api("POST", "/api/seller/login", {"username": "seller", "password": "newpass123"})
    expect("TC-B09", "登录鉴权", "改密成功后旧密码失效、新密码生效", "FR-035",
           "改密 200；旧密码登录 401；新密码登录 200",
           "改密=%s 旧密码=%s 新密码=%s" % (s, s2, s3),
           ok1 and s2 == 401 and s3 == 200)
    # 复原为初始密码
    newtok = login("seller", "newpass123")
    api("POST", "/api/seller/password",
        {"old_password": "newpass123", "new_password": "seller123"}, token=newtok)

    s, b, _ = api("GET", "/api/seller/history?page=0", token=tok)
    expect("TC-B10", "登录鉴权", "令牌有效但参数非法时正常返回 422", "接口设计§1",
           "HTTP 422", "HTTP %s" % s, s == 422)


# ==========================================================================
# C. 商品发布与管理  FR-001~012
# ==========================================================================
def suite_c(tok):
    print("\n[C] 商品发布与管理 (FR-001~012)")
    reset_shop(tok)

    s, b = publish(tok, "商品A", 99.9, "描述文本")
    expect("TC-C01", "商品管理", "发布商品成功并置为在售", "FR-001",
           "HTTP 200, status=ON_SALE, price=99.9",
           "HTTP %s status=%s" % (s, b.get("product", {}).get("status") if isinstance(b, dict) else b),
           s == 200 and b["product"]["status"] == "ON_SALE" and b["product"]["price"] == 99.9)

    s, b = publish(tok, "商品B", 10)
    expect("TC-C02", "商品管理", "已有在售商品时重复发布被拒（唯一在售）", "FR-008",
           "HTTP 409 CONFLICT", "HTTP %s %s" % (s, b.get("error", {}).get("message") if isinstance(b, dict) else b),
           s == 409)

    for label, body, cid in [
        ("名称为纯空格", {"name": "   ", "price": 10}, "TC-C03"),
        ("价格为 0", {"name": "x", "price": 0}, "TC-C04"),
        ("价格为负数", {"name": "x", "price": -5}, "TC-C05"),
        ("价格超上限", {"name": "x", "price": 9999999.01}, "TC-C06"),
        ("缺少名称字段", {"price": 10}, "TC-C07"),
        ("名称超 120 字", {"name": "长" * 121, "price": 10}, "TC-C08"),
        ("描述超 2000 字", {"name": "x", "price": 10, "description": "字" * 2001}, "TC-C09"),
    ]:
        s, b, _ = api("POST", "/api/seller/products", body, token=tok)
        expect(cid, "商品管理", "%s 被校验拒绝" % label, "FR-001",
               "HTTP 422 VALIDATION_ERROR", "HTTP %s" % s, s == 422)

    # 亚分价格用例必须在下架当前商品后执行，否则会先撞上「唯一在售」409 而掩盖真实缺陷
    reset_shop(tok)
    s, b, _ = api("POST", "/api/seller/products",
                  {"name": "亚分价格", "price": 0.001}, token=tok)
    expect("TC-C10", "商品管理", "价格 0.001 元（不足 1 分）应被优雅拒绝而非 500",
           "FR-001 / NFR-009",
           "HTTP 422（参数校验失败）",
           "HTTP %s，响应体=%s" % (s, str(b)[:80]),
           s == 422,
           note="缺陷 BUG-001：Pydantic 仅校验 price>0，换算为分后为 0，"
                "触发 SQLite CHECK(price_cents>0) 未捕获异常 → 500")

    reset_shop(tok)
    publish(tok, "商品A", 99.9, "描述文本")
    pid = current_product()["product"]["id"]

    s, b, _ = api("POST", "/api/seller/products/%s/freeze" % pid, token=tok)
    expect("TC-C11", "商品管理", "手动冻结商品成功", "FR-009",
           "HTTP 200, status=FROZEN",
           "HTTP %s status=%s" % (s, b.get("product", {}).get("status") if isinstance(b, dict) else b),
           s == 200 and b["product"]["status"] == "FROZEN")

    s, b, _ = api("POST", "/api/seller/products/%s/freeze" % pid, token=tok)
    expect("TC-C12", "商品管理", "重复冻结被拒", "FR-009",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, b = submit(pid, "张三")
    expect("TC-C13", "商品管理", "冻结期间不接收新意向", "FR-012/FR-016",
           "HTTP 409 且提示含「交易中」",
           "HTTP %s %s" % (s, b.get("error", {}).get("message") if isinstance(b, dict) else b),
           s == 409 and "交易中" in str(b.get("error", {}).get("message", "")))

    s, b, _ = api("POST", "/api/seller/products/%s/unfreeze" % pid, token=tok)
    expect("TC-C14", "商品管理", "手动解冻恢复在售", "FR-010",
           "HTTP 200, status=ON_SALE",
           "HTTP %s status=%s" % (s, b.get("product", {}).get("status") if isinstance(b, dict) else b),
           s == 200 and b["product"]["status"] == "ON_SALE")

    s, b, _ = api("POST", "/api/seller/products/%s/unfreeze" % pid, token=tok)
    expect("TC-C15", "商品管理", "非冻结状态解冻被拒", "FR-010",
           "HTTP 409", "HTTP %s" % s, s == 409)

    # 冻结 + 存在进行中交易 -> 不允许解冻
    s, it = submit(pid, "李四")
    api("POST", "/api/seller/products/%s/start-transaction" % pid, token=tok)
    s, b, _ = api("POST", "/api/seller/products/%s/unfreeze" % pid, token=tok)
    expect("TC-C16", "商品管理", "存在进行中交易时禁止手动解冻", "FR-010/FR-011",
           "HTTP 409", "HTTP %s" % s, s == 409)

    # 归还队列：标记失败使商品回到在售
    api("POST", "/api/seller/orders/%s/transaction" % it["order"]["order_id"],
        {"result": "FAILED"}, token=tok)

    s, b, _ = api("POST", "/api/seller/products/%s/off-shelf" % pid, token=tok)
    expect("TC-C17", "商品管理", "下架商品成功", "FR-003",
           "HTTP 200, status=OFF_SHELF",
           "HTTP %s status=%s" % (s, b.get("product", {}).get("status") if isinstance(b, dict) else b),
           s == 200 and b["product"]["status"] == "OFF_SHELF")

    s, b, _ = api("POST", "/api/seller/products/%s/off-shelf" % pid, token=tok)
    expect("TC-C18", "商品管理", "重复下架被拒", "FR-003",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, b = submit(pid, "王五")
    expect("TC-C19", "商品管理", "已下架商品不接收新意向", "FR-003",
           "HTTP 409", "HTTP %s %s" % (s, b.get("error", {}).get("message") if isinstance(b, dict) else b),
           s == 409)

    s, b, _ = api("POST", "/api/seller/products/not-exist-id/freeze", token=tok)
    expect("TC-C20", "商品管理", "冻结不存在商品返回 404", "接口设计§1",
           "HTTP 404 NOT_FOUND", "HTTP %s" % s, s == 404)

    patch_status = api("PATCH", "/api/seller/products/%s" % pid, {"name": "改名"}, token=tok)[0]
    expect("TC-C21", "商品管理", "后台不提供商品编辑接口（发布后不可改）", "FR-002",
           "PATCH /api/seller/products/{id} 不存在（HTTP 404/405）",
           "PATCH -> HTTP %s" % patch_status,
           patch_status in (404, 405))


# ==========================================================================
# D. 排队与口令码  FR-013~026
# ==========================================================================
def suite_d(tok):
    print("\n[D] 排队与口令码 (FR-013~026)")
    reset_shop(tok)
    pid = publish(tok, "排队测试商品", 50)[1]["product"]["id"]

    s, b = submit(pid, "张三", "13800001111")
    code1 = b.get("command_code") if isinstance(b, dict) else None
    expect("TC-D01", "排队口令码", "提交意向返回口令码与排队序号 1", "FR-013/FR-014",
           "HTTP 200, queue_position=1, status=QUEUED, command_code 非空",
           "HTTP %s pos=%s status=%s code=%s" % (s, b.get("queue_position"),
                                                 b.get("order", {}).get("status"), bool(code1)),
           s == 200 and b["queue_position"] == 1 and bool(code1))

    expect("TC-D02", "排队口令码", "口令码为 10 位且不含易混淆字符 0/O/1/I/l", "NFR-004",
           "长度 10，字符集为 ABCDEFGHJKMNPQRSTUVWXYZ23456789",
           "code=%s" % code1,
           len(code1) == 10 and not (set(code1) & set("0O1Il")))

    positions = [b["queue_position"]]
    for i in range(2, 6):
        s, b = submit(pid, "买家%d" % i, "1380000000%d" % i)
        positions.append(b["queue_position"])
    expect("TC-D03", "排队口令码", "连续提交按先到先得分配 1..5", "FR-015/NFR-014",
           "序号依次为 [1,2,3,4,5]",
           str(positions), positions == [1, 2, 3, 4, 5])

    s, b, _ = api("GET", "/api/buyer/intents/%s" % code1)
    expect("TC-D04", "排队口令码", "凭口令码查询意向状态与位次", "FR-022",
           "HTTP 200, position=1, can_edit=true, can_cancel=true",
           "HTTP %s pos=%s edit=%s cancel=%s" % (s, b.get("position"), b.get("can_edit"),
                                                 b.get("can_cancel")),
           s == 200 and b["position"] == 1 and b["can_edit"] and b["can_cancel"])

    s, b, _ = api("GET", "/api/buyer/intents/ZZZZZZZZZZ")
    expect("TC-D05", "排队口令码", "无效口令码返回 410", "FR-022/NFR-005",
           "HTTP 410 COMMAND_CODE_INVALID",
           "HTTP %s %s" % (s, b.get("error", {}).get("code") if isinstance(b, dict) else b),
           s == 410)

    s, b, _ = api("PATCH", "/api/buyer/intents",
                  {"code": code1, "buyer_phone": "13900002222"})
    s2, q, _ = api("GET", "/api/buyer/intents/%s" % code1)
    expect("TC-D06", "排队口令码", "修改联系电话后位次不变、未提交字段保持原值", "FR-023",
           "电话=13900002222, 姓名=张三, position 仍为 1",
           "HTTP %s phone=%s name=%s pos=%s" % (s, b.get("buyer_phone"), b.get("buyer_name"),
                                                q.get("position")),
           s == 200 and b["buyer_phone"] == "13900002222"
           and b["buyer_name"] == "张三" and q["position"] == 1)

    s, b, _ = api("PATCH", "/api/buyer/intents", {"code": code1})
    expect("TC-D07", "排队口令码", "修改请求未携带任何可改字段被拒", "FR-023",
           "HTTP 422 VALIDATION_ERROR", "HTTP %s" % s, s == 422)

    # 撤销第 3 位，验证自动补位
    s, third = submit(pid, "待撤销买家", "13800009999")
    s, b, _ = api("POST", "/api/buyer/intents/cancel", {"code": third["command_code"]})
    s2, q2, _ = api("GET", "/api/buyer/intents/%s" % third["command_code"])
    expect("TC-D08", "排队口令码", "撤销排队后口令码立即失效", "FR-024/FR-026",
           "撤销 200；再次查询 410",
           "撤销=%s 再查=%s" % (s, s2), s == 200 and s2 == 410)

    s, lst, _ = api("GET", "/api/seller/products/%s/intents?page_size=100" % pid, token=tok)
    waiting = sorted([i["queue_position"] for i in lst["items"]
                      if i["status"] in ("QUEUED", "REQUEUED")])
    expect("TC-D09", "排队口令码", "撤销后队列序号连续补位（无空洞）", "FR-024",
           "等待中序号应为连续整数 1..n",
           "等待中序号=%s" % waiting,
           waiting == list(range(1, len(waiting) + 1)))

    for label, name, phone, cid in [
        ("姓名为空", "", "13800000000", "TC-D10"),
        ("电话格式非法（字母）", "张三", "abcdefg", "TC-D11"),
        ("电话不足 5 位", "张三", "1234", "TC-D12"),
        ("电话超 20 位", "张三", "1" * 21, "TC-D13"),
        ("姓名超 64 字", "名" * 65, "13800000000", "TC-D14"),
    ]:
        s, b, _ = api("POST", "/api/buyer/intents",
                      {"product_id": pid, "buyer_name": name, "buyer_phone": phone})
        expect(cid, "排队口令码", "%s 被校验拒绝" % label, "FR-013",
               "HTTP 422 VALIDATION_ERROR", "HTTP %s" % s, s == 422)

    s, b, _ = api("POST", "/api/buyer/intents",
                  {"product_id": "no-such-product", "buyer_name": "张三",
                   "buyer_phone": "13800000000"})
    expect("TC-D15", "排队口令码", "向不存在的商品提交意向返回 404", "接口设计§1",
           "HTTP 404 NOT_FOUND", "HTTP %s" % s, s == 404)


# ==========================================================================
# E. 交易流程  FR-027~031
# ==========================================================================
def suite_e(tok):
    print("\n[E] 交易与状态机 (FR-027~031)")
    reset_shop(tok)
    pid = publish(tok, "交易测试商品", 120)[1]["product"]["id"]

    s, b, _ = api("POST", "/api/seller/products/%s/start-transaction" % pid, token=tok)
    expect("TC-E01", "交易流程", "队列为空时进入交易被拒", "FR-021",
           "HTTP 409 且提示「队列为空」",
           "HTTP %s %s" % (s, b.get("error", {}).get("message") if isinstance(b, dict) else b),
           s == 409 and "队列为空" in str(b.get("error", {}).get("message", "")))

    s, a = submit(pid, "买家A", "13800000001")
    s, bb = submit(pid, "买家B", "13800000002")

    s, b, _ = api("POST", "/api/seller/products/%s/start-transaction" % pid, token=tok)
    expect("TC-E02", "交易流程", "进入交易：队首转 IN_TRANSACTION 且商品自动冻结", "FR-011",
           "HTTP 200；order.status=IN_TRANSACTION；product.status=FROZEN；取队首买家A",
           "HTTP %s order=%s product=%s" % (s, b.get("order", {}).get("status"),
                                            b.get("product", {}).get("status")),
           s == 200 and b["order"]["status"] == "IN_TRANSACTION"
           and b["product"]["status"] == "FROZEN"
           and b["order"]["order_id"] == a["order"]["order_id"])

    s, b, _ = api("POST", "/api/seller/products/%s/start-transaction" % pid, token=tok)
    expect("TC-E03", "交易流程", "已有进行中交易时不可重复进入", "FR-011",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, b, _ = api("POST", "/api/buyer/intents/cancel", {"code": a["command_code"]})
    expect("TC-E04", "交易流程", "进入交易后买家不可撤销", "FR-025",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, b, _ = api("PATCH", "/api/buyer/intents",
                  {"code": a["command_code"], "buyer_phone": "13999999999"})
    expect("TC-E05", "交易流程", "进入交易后买家不可修改信息", "FR-023/FR-025",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, q, _ = api("GET", "/api/buyer/intents/%s" % a["command_code"])
    expect("TC-E06", "交易流程", "进入交易后口令码仍可查询且标记不可改不可撤", "FR-022",
           "HTTP 200；position=1；can_edit=false；can_cancel=false",
           "HTTP %s pos=%s edit=%s cancel=%s" % (s, q.get("position"), q.get("can_edit"),
                                                 q.get("can_cancel")),
           s == 200 and q["position"] == 1 and not q["can_edit"] and not q["can_cancel"])

    s, b, _ = api("POST", "/api/seller/orders/%s/transaction" % a["order"]["order_id"],
                  {"result": "FAILED", "note": "买家未到场"}, token=tok)
    expect("TC-E07", "交易流程", "标记交易失败后由队列下一位自动递补并再冻结", "FR-021/FR-029",
           "A=FAILED；B=IN_TRANSACTION；商品保持 FROZEN",
           "A=%s B=%s product=%s" % (b.get("order", {}).get("status"),
                                     api("GET", "/api/buyer/intents/%s" % bb["command_code"])[1]
                                     .get("order", {}).get("status"),
                                     b.get("product", {}).get("status")),
           b["order"]["status"] == "FAILED" and b["product"]["status"] == "FROZEN"
           and api("GET", "/api/buyer/intents/%s" % bb["command_code"])[1]["order"]["status"]
           == "IN_TRANSACTION")

    s, b, _ = api("GET", "/api/buyer/intents/%s" % a["command_code"])
    expect("TC-E08", "交易流程", "交易失败者口令码立即失效", "FR-026",
           "HTTP 410", "HTTP %s" % s, s == 410)

    s, b, _ = api("POST", "/api/seller/orders/%s/void" % bb["order"]["order_id"], token=tok)
    expect("TC-E09", "交易流程", "非交易失败状态不可作废", "FR-030",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, b, _ = api("POST", "/api/seller/orders/%s/void" % a["order"]["order_id"], token=tok)
    expect("TC-E10", "交易流程", "交易失败者可作废", "FR-030",
           "HTTP 200, status=VOIDED",
           "HTTP %s status=%s" % (s, b.get("order", {}).get("status") if isinstance(b, dict) else b),
           s == 200 and b["order"]["status"] == "VOIDED")

    s, b, _ = api("POST", "/api/seller/orders/%s/transaction" % a["order"]["order_id"],
                  {"result": "SUCCESS"}, token=tok)
    expect("TC-E11", "交易流程", "已作废订单不可再标记交易结果", "FR-027",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, b, _ = api("POST", "/api/seller/orders/%s/transaction" % bb["order"]["order_id"],
                  {"result": "SUCCESS"}, token=tok)
    expect("TC-E12", "交易流程", "交易成功：商品下架并进入历史", "FR-028",
           "HTTP 200；order=SUCCESS；product=OFF_SHELF",
           "HTTP %s order=%s product=%s" % (s, b.get("order", {}).get("status"),
                                            b.get("product", {}).get("status")),
           s == 200 and b["order"]["status"] == "SUCCESS"
           and b["product"]["status"] == "OFF_SHELF")

    s, b, _ = api("GET", "/api/buyer/intents/%s" % bb["command_code"])
    expect("TC-E13", "交易流程", "交易成功后关联口令码全部失效", "FR-026",
           "HTTP 410", "HTTP %s" % s, s == 410)

    # 失败且队列为空 -> 恢复在售；重新排队仅 1 次
    reset_shop(tok)
    pid2 = publish(tok, "失败重排商品", 60)[1]["product"]["id"]
    s, x = submit(pid2, "买家X", "13800000009")
    api("POST", "/api/seller/products/%s/start-transaction" % pid2, token=tok)
    s, b, _ = api("POST", "/api/seller/orders/%s/transaction" % x["order"]["order_id"],
                  {"result": "FAILED"}, token=tok)
    expect("TC-E14", "交易流程", "交易失败且队列为空时商品恢复在售", "FR-029",
           "product.status=ON_SALE",
           "product=%s" % b.get("product", {}).get("status"),
           b["product"]["status"] == "ON_SALE")

    s, b, _ = api("POST", "/api/seller/orders/%s/requeue" % x["order"]["order_id"], token=tok)
    ok = s == 200 and b.get("command_code")
    expect("TC-E15", "交易流程", "失败者重新排队：放回队尾并生成新口令码", "FR-030/TBD-03",
           "HTTP 200, status=REQUEUED, queue_position=1, 返回新 command_code",
           "HTTP %s status=%s pos=%s newcode=%s" % (s, b.get("order", {}).get("status"),
                                                    b.get("queue_position"),
                                                    bool(b.get("command_code"))),
           ok and b["order"]["status"] == "REQUEUED")

    s, q, _ = api("GET", "/api/buyer/intents/%s" % b["command_code"])
    expect("TC-E16", "交易流程", "重新排队后新口令码可正常查询", "FR-030",
           "HTTP 200", "HTTP %s" % s, s == 200)

    s, b, _ = api("POST", "/api/seller/orders/%s/requeue" % x["order"]["order_id"], token=tok)
    expect("TC-E17", "交易流程", "同一失败者最多重新排队 1 次", "FR-030",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, b, _ = api("POST", "/api/seller/orders/%s/void" % x["order"]["order_id"], token=tok)
    expect("TC-E18", "交易流程", "已重新排队的订单不可再作废（仅 FAILED 可作废）", "FR-030",
           "HTTP 409", "HTTP %s" % s, s == 409)


# ==========================================================================
# F. 历史、意向列表与日志  FR-004~007 / FR-042
# ==========================================================================
def suite_f(tok):
    print("\n[F] 历史记录·意向列表·操作日志 (FR-004~007/FR-042)")
    reset_shop(tok)

    pid = publish(tok, "历史测试商品", 300)[1]["product"]["id"]
    s, a = submit(pid, "买家甲", "13800000001")
    s, bb = submit(pid, "买家乙", "13800000002")
    api("POST", "/api/seller/products/%s/start-transaction" % pid, token=tok)
    api("POST", "/api/seller/orders/%s/transaction" % a["order"]["order_id"],
        {"result": "FAILED", "note": "第一轮失败"}, token=tok)
    api("POST", "/api/seller/orders/%s/void" % a["order"]["order_id"], token=tok)
    api("POST", "/api/seller/orders/%s/transaction" % bb["order"]["order_id"],
        {"result": "SUCCESS"}, token=tok)

    s, hist, _ = api("GET", "/api/seller/history?page_size=100", token=tok)
    names = [i["name"] for i in hist["items"]]
    expect("TC-F01", "历史记录", "历史列表仅包含已下架商品", "FR-004",
           "列表中包含「历史测试商品」，且全部为 OFF_SHELF",
           "total=%s 名称=%s" % (hist["total"], names[:5]),
           "历史测试商品" in names
           and all(i["status"] == "OFF_SHELF" for i in hist["items"]))

    item = [i for i in hist["items"] if i["name"] == "历史测试商品"][0]
    ts = item["transaction_summary"]
    expect("TC-F02", "历史记录", "历史列表交易概览统计失败轮次与最终结果", "FR-004",
           "failed_rounds=1, final_result=SUCCESS, buyer_count=2",
           json.dumps(ts, ensure_ascii=False),
           ts["failed_rounds"] == 1 and ts["final_result"] == "SUCCESS"
           and ts["buyer_count"] == 2)

    s, det, _ = api("GET", "/api/seller/history/%s" % pid, token=tok)
    results = [t["result"] for t in det["transactions"]]
    expect("TC-F03", "历史记录", "历史详情完整保留多轮交易记录（不可只留最终结果）",
           "FR-031/NFR-015",
           "transactions 为 [FAILED, SUCCESS] 两条",
           "transactions=%s" % results,
           results == ["FAILED", "SUCCESS"])

    phones = [b["buyer_phone"] for b in det["buyers"]]
    expect("TC-F04", "历史记录", "历史商品买家联系电话脱敏展示", "FR-019/NFR-003",
           "电话形如 138****0001（保留前3后4）",
           str(phones),
           all(p and "****" in p for p in phones))

    reset_shop(tok)
    pid2 = publish(tok, "未下架商品", 20)[1]["product"]["id"]
    s, b, _ = api("GET", "/api/seller/history/%s" % pid2, token=tok)
    expect("TC-F05", "历史记录", "未下架商品查看历史详情返回 409", "FR-005",
           "HTTP 409", "HTTP %s" % s, s == 409)

    s, lst, _ = api("GET", "/api/seller/products/%s/intents?page_size=100" % pid2, token=tok)
    s, c = submit(pid2, "未终结买家", "13800007777")
    s, lst, _ = api("GET", "/api/seller/products/%s/intents?page_size=100" % pid2, token=tok)
    row = [i for i in lst["items"] if i["buyer_name"] == "未终结买家"][0]
    expect("TC-F06", "意向列表", "商品在售、订单未终结时卖家可见完整电话", "FR-019",
           "buyer_phone=13800007777（不脱敏）",
           row["buyer_phone"], row["buyer_phone"] == "13800007777")

    s, lst, _ = api("GET", "/api/seller/products/%s/intents?status=QUEUED&page_size=100" % pid2,
                    token=tok)
    expect("TC-F07", "意向列表", "按状态筛选意向列表", "FR-017",
           "全部返回项 status 均为 QUEUED",
           "total=%s 状态集=%s" % (lst["total"], set(i["status"] for i in lst["items"])),
           all(i["status"] == "QUEUED" for i in lst["items"]))

    for q, cid in [("page=0", "TC-F08"), ("page_size=0", "TC-F09"),
                   ("page_size=101", "TC-F10"), ("page=-1", "TC-F11")]:
        s, b, _ = api("GET", "/api/seller/history?%s" % q, token=tok)
        expect(cid, "分页", "分页参数 %s 越界被拒" % q, "接口设计§1",
               "HTTP 422", "HTTP %s" % s, s == 422)

    s, b, _ = api("GET", "/api/seller/logs?page_size=100", token=tok)
    actions = [i["action"] for i in b["items"]]
    expect("TC-F12", "操作日志", "关键操作被记入操作日志", "FR-042",
           "日志含 LOGIN / PUBLISH_PRODUCT / OFF_SHELF_PRODUCT 等",
           str(sorted(set(actions))[:8]),
           "LOGIN" in actions and "PUBLISH_PRODUCT" in actions)

    s, b, _ = api("GET", "/api/seller/logs?page_size=100", token=tok)
    has_detail = all(i.get("detail") for i in b["items"])
    expect("TC-F13", "操作日志", "每条日志含可读中文描述与时间", "FR-042/NFR-010",
           "所有日志项 detail 非空且 created_at 存在",
           "共 %s 条，detail 齐全=%s" % (b["total"], has_detail),
           has_detail and all(i.get("created_at") for i in b["items"]))

    # 关键词搜索
    s, b, _ = api("GET", "/api/seller/history?keyword=%E5%8E%86%E5%8F%B2&page_size=100", token=tok)
    expect("TC-F14", "历史记录", "历史列表支持按名称关键词模糊搜索", "FR-004",
           "搜索「历史」命中「历史测试商品」",
           "total=%s 名称=%s" % (b["total"], [i["name"] for i in b["items"]]),
           any(i["name"] == "历史测试商品" for i in b["items"]))

    s, b, _ = api("GET", "/api/seller/history?keyword=_&page_size=100", token=tok)
    all_total = api("GET", "/api/seller/history?page_size=100", token=tok)[1]["total"]
    expect("TC-F15", "历史记录", "关键词中的 LIKE 通配符应被转义（_ 不应匹配任意字符）",
           "FR-004",
           "搜索「_」应命中 0 条（无商品名含下划线）",
           "搜索「_」命中 %s 条；不带关键词共 %s 条" % (b["total"], all_total),
           b["total"] == 0,
           note="缺陷 BUG-002：keyword 未转义 LIKE 通配符 % 和 _，"
                "导致搜索「_」或「%」返回全部历史商品")


# ==========================================================================
# G. CSV 导出  FR-018
# ==========================================================================
def suite_g(tok):
    print("\n[G] 意向购买人 CSV 导出 (FR-018)")
    reset_shop(tok)
    pid = publish(tok, "导出测试商品", 15)[1]["product"]["id"]
    submit(pid, "导出甲", "13800001111")
    submit(pid, "导出乙", "13800002222")

    s, body, headers = api("GET", "/api/seller/products/%s/intents/export" % pid,
                           token=tok, raw=True)
    ct = headers.get("content-type", "")
    expect("TC-G01", "CSV 导出", "导出接口返回 CSV 且带 UTF-8 BOM", "FR-018",
           "Content-Type 含 text/csv；内容以 BOM 开头",
           "Content-Type=%s BOM=%s" % (ct, body[:3] == b"\xef\xbb\xbf"),
           s == 200 and "text/csv" in ct and body[:3] == b"\xef\xbb\xbf")

    text = body.decode("utf-8-sig")
    lines = [l for l in text.splitlines() if l.strip()]
    expect("TC-G02", "CSV 导出", "CSV 表头与数据行正确", "FR-018",
           "表头=姓名,联系电话,提交时间,排队序号,状态；共 1+2 行",
           "表头=%s 行数=%s" % (lines[0] if lines else "-", len(lines)),
           lines and lines[0].replace("\ufeff", "").startswith("姓名")
           and len(lines) == 3)

    expect("TC-G03", "CSV 导出", "未登录不可导出", "NFR-006",
           "HTTP 401",
           "HTTP %s" % api("GET", "/api/seller/products/%s/intents/export" % pid, raw=True)[0],
           api("GET", "/api/seller/products/%s/intents/export" % pid, raw=True)[0] == 401)


# ==========================================================================
# H. 图片上传  FR-001 / NFR-012
# ==========================================================================
def suite_h(tok):
    print("\n[H] 商品主图上传 (FR-001/NFR-012)")

    s, b = upload("pic.png", png_bytes(2048))
    url = b.get("url") if isinstance(b, dict) else None
    expect("TC-H01", "图片上传", "上传合法 PNG 成功并返回可访问 URL", "FR-001",
           "HTTP 200 且 url 形如 /uploads/xxx.png",
           "HTTP %s url=%s" % (s, url), s == 200 and url and url.startswith("/uploads/"))

    if url:
        try:
            with urllib.request.urlopen(BASE + url, timeout=20) as r:
                ok, code, ct = True, r.status, r.headers.get("Content-Type")
        except Exception as e:
            ok, code, ct = False, "ERR", str(e)
        expect("TC-H02", "图片上传", "已上传图片可经静态路由访问", "FR-001",
               "HTTP 200", "HTTP %s Content-Type=%s" % (code, ct), ok and code == 200)

    s, b = upload("evil.txt", b"not an image")
    expect("TC-H03", "图片上传", "非图片扩展名被拒绝", "NFR-012",
           "HTTP 400 INVALID_IMAGE_FORMAT",
           "HTTP %s %s" % (s, b.get("error", {}).get("code") if isinstance(b, dict) else b),
           s == 400)

    s, b = upload("empty.png", b"")
    expect("TC-H04", "图片上传", "空文件被拒绝", "NFR-012",
           "HTTP 400 EMPTY_FILE",
           "HTTP %s %s" % (s, b.get("error", {}).get("code") if isinstance(b, dict) else b),
           s == 400)

    over = 5 * 1024 * 1024 + 1
    s, b = upload("big.png", png_bytes(1024) + b"\x00" * over)
    expect("TC-H05", "图片上传", "超过 5MB 的图片被拒绝", "NFR-012",
           "HTTP 400 IMAGE_TOO_LARGE",
           "HTTP %s %s" % (s, b.get("error", {}).get("code") if isinstance(b, dict) else b),
           s == 400)

    s, b = upload("UPPER.PNG", png_bytes(1024))
    expect("TC-H06", "图片上传", "大写扩展名按小写规则放行", "NFR-012",
           "HTTP 200", "HTTP %s" % s, s == 200)

    s, b = upload("noext", png_bytes(512))
    expect("TC-H07", "图片上传", "无扩展名文件被拒绝", "NFR-012",
           "HTTP 400", "HTTP %s" % s, s == 400)

    # 内容与扩展名不一致（伪装文件）
    s, b = upload("fake.png", b"<script>alert(1)</script>")
    expect("TC-H08", "图片上传", "仅校验扩展名，不校验真实文件内容", "NFR-012",
           "观察项：扩展名为 .png 即放行，即使内容不是图片",
           "HTTP %s" % s, True,
           note="观察项：接口只做扩展名白名单，未做文件头(Magic Number)校验；"
                "回应 NFR-012「限制图片格式」的强度不足（低风险，静态资源按扩展名下发 Content-Type）")


# ==========================================================================
# I. 并发一致性  NFR-013 / NFR-014
# ==========================================================================
def suite_i(tok):
    print("\n[I] 并发一致性 (NFR-013/NFR-014)")
    reset_shop(tok)
    pid = publish(tok, "并发测试商品", 42)[1]["product"]["id"]

    n = 20
    out = []
    lock = threading.Lock()

    def worker(i):
        s, b = submit(pid, "并发买家%02d" % i, "1390000%04d" % i)
        with lock:
            out.append((s, b))

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(n)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    ok_codes = [b for s, b in out if s == 200]
    positions = sorted(b["queue_position"] for b in ok_codes)
    expect("TC-I01", "并发", "%d 个并发提交意向全部成功且无丢失" % n, "NFR-013",
           "成功 %d 笔" % n, "成功 %d 笔" % len(ok_codes),
           len(ok_codes) == n)

    expect("TC-I02", "并发", "并发下排队序号唯一且连续（无重复、无空洞）", "NFR-013/NFR-014",
           "序号恰为 1..%d 各一次" % n,
           "得到 %s" % (positions[:25] if len(positions) <= 25 else positions[:25] + ["..."]),
           positions == list(range(1, n + 1)))

    s, lst, _ = api("GET", "/api/seller/products/%s/intents?page_size=100" % pid, token=tok)
    heads = [i for i in lst["items"] if i["status"] == "IN_TRANSACTION"]
    expect("TC-I03", "并发", "并发后不存在两个同时进行中的交易（防超卖）", "NFR-013",
           "IN_TRANSACTION 数量为 0（尚未进入交易）",
           "IN_TRANSACTION 数量=%s" % len(heads), len(heads) == 0)

    s, b, _ = api("POST", "/api/seller/products/%s/start-transaction" % pid, token=tok)
    s1 = s
    s, b2, _ = api("POST", "/api/seller/products/%s/start-transaction" % pid, token=tok)
    expect("TC-I04", "并发", "连续进入交易仅第一次成功（先到先得不可干预）", "FR-020/NFR-014",
           "第一次 200，第二次 409",
           "第一次=%s 第二次=%s" % (s1, s), s1 == 200 and s == 409)


# ==========================================================================
# J. 前端路由与联调
# ==========================================================================
def suite_j():
    print("\n[J] 前端页面与前后端联调")

    routes = [
        ("/", "B1 买家商品页"),
        ("/buy", "B2 提交购买意向"),
        ("/success", "B3 提交成功/口令码"),
        ("/query", "B4 口令码查询"),
        ("/intent", "B5 意向详情"),
        ("/seller/login", "S1 卖家登录"),
        ("/seller", "S2 工作台"),
        ("/seller/publish", "S3 发布商品"),
        ("/seller/intents", "S4 意向购买人"),
        ("/seller/history", "S5 历史商品"),
        ("/seller/password", "S7 修改密码"),
        ("/seller/logs", "S8 操作日志"),
    ]
    bad = []
    for path, label in routes:
        s, body, headers = front_get(path)
        if s != 200 or b'id="app"' not in body:
            bad.append("%s(HTTP %s)" % (path, s))
    expect("TC-J01", "前端", "12 个前端路由均可访问（SPA 回退正常）", "FR-051",
           "全部返回 200 且包含挂载点 #app",
           "异常路由: %s" % (bad if bad else "无"), not bad)

    s, body, headers = front_get("/src/main.js")
    ct = headers.get("content-type", "")
    expect("TC-J02", "前端", "Vite 开发服务器正常编译入口模块", "FR-051",
           "HTTP 200 且 Content-Type 为 JavaScript",
           "HTTP %s CT=%s" % (s, ct),
           s == 200 and "javascript" in ct)

    s, body, _ = front_get("/src/App.vue")
    expect("TC-J03", "前端", "Vue 单文件组件被正常编译", "FR-051",
           "HTTP 200", "HTTP %s" % s, s == 200)

    s, body, _ = front_get("/api/products/current")
    ok = s == 200 and b'"state"' in body
    expect("TC-J04", "前端", "Vite 代理 /api 到后端成功（前后端联调打通）", "FR-051",
           "HTTP 200 且返回后端 JSON（含 state 字段）",
           "HTTP %s body=%s" % (s, body[:60]), ok)

    s, body, _ = front_get("/src/views/seller/PublishView.vue")
    expect("TC-J05", "前端", "卖家发布页组件可编译", "FR-051",
           "HTTP 200", "HTTP %s" % s, s == 200)


# ==========================================================================
# K. 安全与健壮性  NFR-005 / NFR-006 / NFR-009
# ==========================================================================
def suite_k(tok):
    print("\n[K] 安全与健壮性 (NFR-005/006/009)")
    reset_shop(tok)
    pid = publish(tok, "安全测试商品", 66)[1]["product"]["id"]
    s, a = submit(pid, "安全买家甲", "13800000001")
    s, bb = submit(pid, "安全买家乙", "13800000002")

    # SQL 注入尝试（keyword 走参数化查询，应被当作普通字符串）
    s, b, _ = api("GET", "/api/seller/history?keyword=%27%20OR%20%271%27%3D%271&page_size=100",
                  token=tok)
    expect("TC-K01", "安全", "历史搜索关键词中的 SQL 注入载荷不生效", "NFR-005",
           "HTTP 200 且返回 0 条（按字面匹配），无数据库错误",
           "HTTP %s total=%s" % (s, b.get("total") if isinstance(b, dict) else b),
           s == 200 and b["total"] == 0)

    # 路径穿越尝试访问后端源码
    s, body, _ = api("GET", "/uploads/../app/config.py", raw=True)
    expect("TC-K02", "安全", "静态资源路径不可穿越到后端源码", "NFR-005",
           "HTTP 404/403，且响应不含 SECRET_KEY 源码内容",
           "HTTP %s 含SECRET_KEY=%s" % (s, b"SELLER_PASSWORD" in body),
           s in (403, 404) and b"SELLER_PASSWORD" not in body)

    # 买家 A 无法通过自己的口令码看到买家 B 的订单
    s, qa, _ = api("GET", "/api/buyer/intents/%s" % a["command_code"])
    s2, qb, _ = api("GET", "/api/buyer/intents/%s" % bb["command_code"])
    expect("TC-K03", "安全", "口令码只能访问自身对应意向，不可越权", "NFR-004",
           "A 的查询结果中 order_id 等于 A，B 的等于 B，两者不同",
           "A=%s B=%s" % (qa["order"]["order_id"][:8], qb["order"]["order_id"][:8]),
           qa["order"]["order_id"] == a["order"]["order_id"]
           and qb["order"]["order_id"] == bb["order"]["order_id"]
           and qa["order"]["order_id"] != qb["order"]["order_id"])

    # 商家名称中的 XSS 载荷按原文存储（前端由 Vue 插值转义，此处验证接口不加工）
    reset_shop(tok)
    payload = '<script>alert(1)</script>'
    s, b = publish(tok, payload, 10)
    expect("TC-K04", "安全", "含 HTML 标签的商品名按原文存储（转义责任在前端）", "NFR-005",
           "接口原样返回，前端 Vue 插值自动转义（`{{ }}`）",
           "存储值=%s" % (b.get("product", {}).get("name") if isinstance(b, dict) else b),
           s == 200 and b["product"]["name"] == payload,
           note="观察项：接口未做 HTML 转义，需确保前端始终用插值而非 v-html 渲染；"
                "已核对前端视图均使用 {{ }} 插值（低风险）")
    reset_shop(tok)

    # 超长 URL / 异常口令码不应导致 500
    s, b, _ = api("GET", "/api/buyer/intents/" + "A" * 500)
    expect("TC-K05", "健壮性", "超长口令码返回 410 而非 500", "NFR-009",
           "HTTP 410", "HTTP %s" % s, s == 410)

    # 请求体缺字段
    s, b, _ = api("POST", "/api/buyer/intents", {}, token=None)
    expect("TC-K06", "健壮性", "缺少必需字段返回 422 而非 500", "NFR-009",
           "HTTP 422", "HTTP %s" % s, s == 422)

    # 非法 JSON
    req = urllib.request.Request(BASE + "/api/buyer/intents", data=b"{not json",
                                 method="POST")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            code = r.status
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception as e:
        code = "ERR:%s" % type(e).__name__
    expect("TC-K07", "健壮性", "请求体为非法 JSON 时返回 422 而非 500", "NFR-009",
           "HTTP 422", "HTTP %s" % code, code == 422)

    # 交易结果取值非法
    reset_shop(tok)
    pid2 = publish(tok, "取值校验商品", 30)[1]["product"]["id"]
    s, x = submit(pid2, "取值买家", "13800000003")
    api("POST", "/api/seller/products/%s/start-transaction" % pid2, token=tok)
    s, b, _ = api("POST", "/api/seller/orders/%s/transaction" % x["order"]["order_id"],
                  {"result": "MAYBE"}, token=tok)
    expect("TC-K08", "健壮性", "交易结果取值非法被拒", "NFR-009",
           "HTTP 422", "HTTP %s" % s, s == 422)


# ==========================================================================
def main():
    print("=" * 78)
    print("《在线购物系统（单卖家版）》黑盒功能测试套件 —— 缪茂锦（测试岗）")
    print("后端 %s   前端 %s" % (BASE, FRONT))
    print("开始时间 %s" % _T0.strftime("%Y-%m-%d %H:%M:%S"))
    print("=" * 78)

    tok = login()
    if not tok:
        print("!! 无法登录后端，测试中止")
        return
    reset_shop(tok)

    for fn in (suite_a, suite_b, suite_c, suite_d, suite_e, suite_f, suite_g, suite_h,
               suite_i, suite_k):
        try:
            fn(tok)
        except Exception as e:
            print("  !! 套件 %s 异常中止: %s: %s" % (fn.__name__, type(e).__name__, e))
    try:
        suite_j()
    except Exception as e:
        print("  !! 前端套件异常: %s: %s" % (type(e).__name__, e))

    passed = sum(1 for r in RESULTS if r["status"] == "PASS")
    failed = len(RESULTS) - passed

    print("\n" + "=" * 78)
    print("汇总：共 %d 条用例，通过 %d，失败 %d，通过率 %.1f%%"
          % (len(RESULTS), passed, failed, 100.0 * passed / max(1, len(RESULTS))))
    print("=" * 78)
    if failed:
        print("失败用例：")
        for r in RESULTS:
            if r["status"] == "FAIL":
                print("  - [%s] %s / %s" % (r["id"], r["module"], r["title"]))
                if r["note"]:
                    print("      %s" % r["note"])

    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump({
            "started_at": _T0.isoformat(sep=" ", timespec="seconds"),
            "finished_at": datetime.now().isoformat(sep=" ", timespec="seconds"),
            "backend": BASE, "frontend": FRONT,
            "total": len(RESULTS), "passed": passed, "failed": failed,
            "results": RESULTS,
        }, fh, ensure_ascii=False, indent=2)
    print("\n明细已写入 %s" % OUT_JSON)


if __name__ == "__main__":
    main()
