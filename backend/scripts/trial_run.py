"""成员2后端试运行脚本：对运行中的后端服务跑一遍端到端业务流程。

前置：先启动后端服务，再另开终端运行本脚本。

用法：
    # 终端 1：启动服务
    cd backend && python -m uvicorn app.main:app --reload --port 8000
    # 终端 2：运行试运行
    python scripts/trial_run.py                       # 默认 http://127.0.0.1:8000
    python scripts/trial_run.py --port 8020           # 指定端口
    python scripts/trial_run.py --base http://127.0.0.1:8020

退出码：0 表示全部通过，1 表示存在失败。
"""
import argparse
import os
import sys

import httpx

PASSED = 0
FAILED = 0


def check(label: str, cond: bool, extra: str = "") -> None:
    global PASSED, FAILED
    if cond:
        PASSED += 1
        print(f"[PASS] {label}  {extra}")
    else:
        FAILED += 1
        print(f"[FAIL] {label}  {extra}")


def main() -> int:
    parser = argparse.ArgumentParser(description="在线购物系统后端端到端试运行")
    parser.add_argument("--base", default=os.getenv("SHOP_BASE_URL", "http://127.0.0.1:8000"),
                        help="后端服务地址，默认 http://127.0.0.1:8000")
    parser.add_argument("--port", type=int, default=None, help="仅指定端口，等价于 --base http://127.0.0.1:<port>")
    parser.add_argument("--username", default=os.getenv("SHOP_SELLER_USERNAME", "seller"))
    parser.add_argument("--password", default=os.getenv("SHOP_SELLER_PASSWORD", "seller123"))
    args = parser.parse_args()

    base = f"http://127.0.0.1:{args.port}" if args.port else args.base
    print(f"目标服务：{base}\n" + "-" * 60)

    with httpx.Client(base_url=base, timeout=10) as c:
        # 0. 服务信息
        r = c.get("/")
        check("GET / 服务信息", r.status_code == 200 and r.json().get("docs") == "/docs")

        # 1. 初始无商品在售
        r = c.get("/api/products/current")
        check("初始状态可访问（无商品在售时为 NONE）", r.status_code == 200,
              f"-> state={r.json()['state']}")

        # 2. 卖家登录
        r = c.post("/api/seller/login", json={"username": args.username, "password": args.password})
        check("卖家登录", r.status_code == 200 and r.json().get("token"))
        if r.status_code != 200:
            print("登录失败，终止试运行。请确认账号密码与 SHOP_SELLER_USERNAME/PASSWORD 一致。")
            return 1
        headers = {"Authorization": f"Bearer {r.json()['token']}"}

        # 3. 未授权访问被拒
        check("未带令牌访问卖家接口 -> 401", c.get("/api/seller/history").status_code == 401)

        # 4. 发布商品（先清场：若已有在售商品则先下架）
        cur = c.get("/api/products/current").json()
        if cur["state"] != "NONE":
            c.post(f"/api/seller/products/{cur['product']['id']}/off-shelf", headers=headers)
        r = c.post("/api/seller/products", headers=headers,
                   json={"name": "手工陶瓷杯（试运行）", "price": 99.9, "description": "手作限量"})
        check("发布商品", r.status_code == 200, f"-> {r.json().get('message', r.text)}")
        if r.status_code != 200:
            return 1
        product_id = r.json()["product"]["id"]

        # 5. 唯一在售校验
        r = c.post("/api/seller/products", headers=headers, json={"name": "第二件", "price": 10})
        check("重复发布被拒 -> 409", r.status_code == 409, f"-> {r.json()['error']['message']}")

        # 6. 买家匿名浏览
        r = c.get("/api/products/current")
        check("买家看到在售商品", r.json()["state"] == "ON_SALE",
              f"-> {r.json()['product']['name']} ¥{r.json()['product']['price']}")

        # 7. 买家 A、B 提交意向（先到先得）
        ra = c.post("/api/buyer/intents",
                    json={"product_id": product_id, "buyer_name": "张三", "buyer_phone": "13800000001"})
        rb = c.post("/api/buyer/intents",
                    json={"product_id": product_id, "buyer_name": "李四", "buyer_phone": "13800000002"})
        code_a, code_b = ra.json()["command_code"], rb.json()["command_code"]
        order_a, order_b = ra.json()["order"]["order_id"], rb.json()["order"]["order_id"]
        check("买家A提交意向 -> 排队1", ra.json()["queue_position"] == 1, f"-> 口令码 {code_a}")
        check("买家B提交意向 -> 排队2", rb.json()["queue_position"] == 2, f"-> 口令码 {code_b}")

        # 8. 凭口令码查询
        r = c.get(f"/api/buyer/intents/{code_a}")
        check("凭口令码查询A", r.json()["position"] == 1 and r.json()["can_cancel"] is True,
              f"-> {r.json()['order']['status_label']}")

        # 9. 凭口令码改信息（位次不变）
        r = c.patch("/api/buyer/intents", json={"code": code_a, "buyer_phone": "13900000009"})
        check("凭口令码改电话且位次不变", r.status_code == 200 and r.json()["queue_position"] == 1,
              f"-> 新电话 {r.json()['buyer_phone']}")

        # 10. 无效口令码
        check("无效口令码 -> 410", c.get("/api/buyer/intents/ZZZZZZZZZZ").status_code == 410)

        # 11. 卖家与队首进入交易（自动冻结）
        r = c.post(f"/api/seller/products/{product_id}/start-transaction", headers=headers)
        check("进入交易：队首A + 商品自动冻结", r.status_code == 200
              and r.json()["order"]["order_id"] == order_a
              and r.json()["product"]["status"] == "FROZEN", f"-> {r.json()['message']}")

        # 12. 冻结后的约束
        r = c.post("/api/buyer/intents",
                   json={"product_id": product_id, "buyer_name": "王五", "buyer_phone": "13800000003"})
        check("冻结后新意向被拒 -> 409", r.status_code == 409, f"-> {r.json()['error']['message']}")
        r = c.post("/api/buyer/intents/cancel", json={"code": code_a})
        check("进入交易后不可撤销 -> 409", r.status_code == 409, f"-> {r.json()['error']['message']}")
        r = c.get("/api/products/current")
        check("买家端看到“商品交易中”", r.json()["state"] == "FROZEN", f"-> {r.json()['message']}")

        # 13. 标记失败 -> 自动递补
        r = c.post(f"/api/seller/orders/{order_a}/transaction", headers=headers,
                   json={"result": "FAILED", "note": "买家未到场"})
        check("标记交易失败", r.json()["order"]["status"] == "FAILED", f"-> {r.json()['message']}")
        check("失败者口令码失效", c.get(f"/api/buyer/intents/{code_a}").status_code == 410)
        r = c.get(f"/api/buyer/intents/{code_b}")
        check("下一位B自动递补进入交易", r.json()["order"]["status"] == "IN_TRANSACTION",
              f"-> 商品 {r.json()['product_status']}")

        # 14. 失败者作废
        r = c.post(f"/api/seller/orders/{order_a}/void", headers=headers)
        check("作废失败者A", r.json()["order"]["status"] == "VOIDED", f"-> {r.json()['message']}")

        # 15. B 成交 -> 商品下架
        r = c.post(f"/api/seller/orders/{order_b}/transaction", headers=headers, json={"result": "SUCCESS"})
        check("标记B交易成功", r.json()["order"]["status"] == "SUCCESS", f"-> {r.json()['message']}")
        check("商品已下架", r.json()["product"]["status"] == "OFF_SHELF")
        check("成交后B口令码失效", c.get(f"/api/buyer/intents/{code_b}").status_code == 410)

        # 16. 历史记录（含多轮交易）
        r = c.get("/api/seller/history", headers=headers, params={"page_size": 100})
        item = next((x for x in r.json()["items"] if x["id"] == product_id), None)
        check("历史列表含该商品", item is not None
              and item["transaction_summary"]["failed_rounds"] == 1
              and item["transaction_summary"]["final_result"] == "SUCCESS",
              f"-> 失败1轮，最终交易成功")
        r = c.get(f"/api/seller/history/{product_id}", headers=headers)
        detail = r.json()
        results = [t["result"] for t in detail["transactions"]]
        check("历史保留多轮交易记录", results == ["FAILED", "SUCCESS"], f"-> {results}")
        masked = detail["buyers"][0]["buyer_phone"]
        check("历史买家信息脱敏", "****" in masked and masked.startswith("139"),
              f"-> {detail['buyers'][0]['buyer_name']} {masked} {detail['buyers'][0]['status_label']}")

        # 17. 意向购买人列表 + CSV 导出
        r = c.get(f"/api/seller/products/{product_id}/intents", headers=headers)
        check("意向购买人列表", r.json()["total"] == 2, f"-> 共{r.json()['total']}条")
        r = c.get(f"/api/seller/products/{product_id}/intents/export", headers=headers)
        check("导出CSV", r.status_code == 200 and "姓名" in r.content.decode("utf-8-sig"),
              f"-> {len(r.content)} 字节")

        # 18. 操作日志
        r = c.get("/api/seller/logs", headers=headers, params={"page_size": 100})
        actions = [x["action"] for x in r.json()["items"]]
        check("操作日志已留痕", "PUBLISH_PRODUCT" in actions and "TRANSACTION_SUCCESS" in actions,
              f"-> 共{len(actions)}条")

        # 19. 修改密码（旧密码校验）
        r = c.post("/api/seller/password", headers=headers,
                   json={"old_password": "wrong-password", "new_password": "x123456"})
        check("旧密码错误被拒 -> 401", r.status_code == 401)

    print("-" * 60)
    print(f"==== 试运行结果：{PASSED} 通过 / {FAILED} 失败 ====")
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
