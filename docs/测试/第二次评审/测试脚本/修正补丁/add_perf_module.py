"""为测试套件新增「性能（响应时间）」模块 L —— 覆盖 NFR-001。

NFR-001：商品详情响应小于 2 秒。
本模块对买家端主路径与卖家后台核心读接口做多次采样（含预热），
以客户端往返耗时为准，判定「最慢一次是否小于 2000 ms」。
"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, "_runtime", "functional_suite.py")
t = open(p, encoding="utf-8").read()

# ---------- 1. 引入 time ----------
old_imp = "import threading\nimport urllib.error"
new_imp = "import threading\nimport time\nimport urllib.error"
assert old_imp in t, "未找到 import 锚点"
t = t.replace(old_imp, new_imp, 1)

# ---------- 2. 文件头模块清单补 L ----------
old_roster = "  K  安全与健壮性                NFR-005 / NFR-006 / NFR-009"
new_roster = ("  K  安全与健壮性                NFR-005 / NFR-006 / NFR-009\n"
              "  L  性能（响应时间）            NFR-001")
assert old_roster in t, "未找到模块清单锚点"
t = t.replace(old_roster, new_roster, 1)

# ---------- 3. 插入模块 L ----------
MODULE_L = '''# ==========================================================================
# 测试模块 L —— 性能（响应时间）
# 需求追溯：NFR-001
# 测试内容：
#   1) 商品详情接口（买家端主路径，NFR-001 明示指标）响应时间小于 2 秒
#   2) 在售商品状态接口响应时间小于 2 秒
#   3) 卖家后台核心读接口（历史商品、意向购买人、操作日志）响应时间小于 2 秒
#   4) 凭口令码查询接口响应时间小于 2 秒
# 说明：以客户端往返耗时为准，每次采样本前先预热 1 次，正式采样 5 次，
#       判定标准为「最慢一次的耗时小于 2000 ms」。
# ==========================================================================
def suite_l(tok):
    print("\\n[L] 性能·响应时间 (NFR-001)")
    reset_shop(tok)

    LIMIT_MS = 2000.0
    ROUNDS = 5

    pid = publish(tok, "性能测试商品", 199)[1]["product"]["id"]
    _s, sb, _ = submit(pid, "性能买家甲", "13800000001")
    code = sb.get("command_code") if isinstance(sb, dict) else None

    def timed(fn, rounds=ROUNDS):
        fn()  # 预热一次，排除首次请求的连接与编译开销
        samples = []
        for _ in range(rounds):
            t0 = time.perf_counter()
            fn()
            samples.append((time.perf_counter() - t0) * 1000.0)
        return samples

    def judge(tcid, title, samples, extra=""):
        avg = sum(samples) / len(samples)
        worst = max(samples)
        expect(tcid, "性能", title, "NFR-001",
               "各次响应耗时均小于 %.0f ms" % LIMIT_MS,
               "平均 %.1f ms，最慢 %.1f ms（%d 次采样）%s"
               % (avg, worst, len(samples), extra),
               worst < LIMIT_MS)

    # 1) 商品详情（买家端主路径）
    judge("TC-L01", "商品详情接口响应时间小于 2 秒",
          timed(lambda: api("GET", "/api/products/%s" % pid)))

    # 2) 在售商品状态
    judge("TC-L02", "在售商品状态接口响应时间小于 2 秒",
          timed(lambda: api("GET", "/api/products/current")))

    # 3) 卖家后台核心读接口
    samples = []
    for path in ("/api/seller/history?page_size=100",
                 "/api/seller/products/%s/intents?page_size=100" % pid,
                 "/api/seller/logs?page_size=100"):
        samples += timed(lambda p=path: api("GET", p, token=tok), rounds=3)
    judge("TC-L03", "卖家后台列表接口响应时间小于 2 秒", samples,
          "（历史 / 意向 / 日志，每项 3 次）")

    # 4) 凭口令码查询
    judge("TC-L04", "凭口令码查询接口响应时间小于 2 秒",
          timed(lambda: api("GET", "/api/buyer/intents/%s" % code)),
          "（口令码 %s）" % (code or "无效"))


'''

anchor = "def main():"
assert t.count(anchor) == 1, "main() 锚点不唯一"
t = t.replace(anchor, MODULE_L + anchor, 1)

# ---------- 4. main() 注册模块 L ----------
old_call = "               suite_i, suite_k):"
new_call = "               suite_i, suite_k, suite_l):"
assert old_call in t, "未找到 main() 调用锚点"
t = t.replace(old_call, new_call, 1)

open(p, "w", encoding="utf-8").write(t)
ast.parse(t)
print("已新增模块 L（4 条用例），语法 OK")
