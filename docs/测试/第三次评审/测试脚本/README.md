# 测试脚本说明（第三次评审 · 挑战B）

> 全部脚本都可重跑，结果应可复现
> 编写人：缪茂锦（测试岗）　日期：2026-11-11
> 被测版本：`main` = `bc01eaf`

---

## 一、目录

```
测试脚本/
├── 01-红项详细用例-前端.mjs     直接 import validate.js 调函数，拿前端结论
├── 02-红项详细用例-后端.py      发真实 HTTP 请求，拿后端结论
├── 03-生成红项详细用例表.py     汇总两侧结果生成对照表
├── 04-挑战B功能测试.py          ★ 108 条主脚本
├── 05-前端组件编译检查.mjs      27 个 .vue 逐文件编译
├── 红项用例定义.json            31 条输入的用例定义
├── README.md
└── 证据/
    ├── challengeB-all-20261111-204633.json    108 条请求 + 完整响应
    ├── r3-reditem-frontend.json               红项 31 条的前端侧结果
    └── r3-reditem-backend.json                红项 31 条的后端侧结果
```

---

## 二、环境要求

| 项 | 要求 |
| --- | --- |
| 后端 | `backend-java` 已打包并能启动（`java -jar target/shop-backend-1.0.0.jar --server.port=8000`） |
| 数据目录 | **建议干净初始化**（`SHOP_DATA_DIR` 指向空目录），否则种子数据可能已被改动 |
| 卖家密码 | 需为 `seller123`；若之前跑过红项脚本被改成 `Seller123!`，删数据目录重启即可 |
| Python | 3.12（只用到标准库 + openpyxl） |
| Node | 前端脚本需要 `frontend/node_modules` 存在 |

---

## 三、执行顺序（重要）

```bash
# ① 主套件：108 条挑战B 功能测试（先跑这个）
python 04-挑战B功能测试.py

# ② 红项前后端对照：先前端、再后端、最后生成表
node 01-红项详细用例-前端.mjs > ../../_runtime/detail_fe.json
python 02-红项详细用例-后端.py
python 03-生成红项详细用例表.py

# ③ 前端组件编译检查（在 frontend/ 目录下执行）
cd ../../_work/shop9/frontend && node <脚本路径>/05-前端组件编译检查.mjs
```

---

## 四、⚠️ 执行顺序有坑

| 坑 | 说明 |
| --- | --- |
| **红项脚本会改卖家密码** | `02-红项详细用例-后端.py` 每跑完一条成功的改密会复位到 `Seller123!`，跑完后卖家密码是 `Seller123!`，不是 `seller123` |
| **主套件必须在红项之前跑** | 否则卖家密码对不上，主套件登录失败 |
| **带状态接口要复位** | 主脚本每个模块内部已处理购物车状态，但如果你手工插入了数据，可能影响结果 |
| **别在脏库上跑** | 之前跑过的商品/订单会累积，`total` 类断言可能失真 |

---

## 五、各脚本做什么

### 04-挑战B功能测试.py（主套件）

一个脚本跑完挑战B 的 **102 条接口用例**，覆盖 13 个模块：
商品与搜索、收藏、购物车、下单、转收藏、订单 5 态、双向取消、库存模型、
查询台账、游客会话、上限分页、基础校验。

- 自己建类别、8 件商品、3 个客户，**用唯一后缀隔离**，可重复跑；
- 每条用例的请求（方法/路径/请求体/token/guest）**自动记录**并写入结果 JSON；
- 输出：`_runtime/evidence/challengeB-all-<时间戳>.json`。

**当前结果**：102 条，通过 100，失败 2（`L09`/`L10`，LIKE 转义回归）。

### 01 / 02 / 03 红项脚本

针对两个遗留红项（卖家密码、手机号）做**前后端逐条对照**：

- `01` 用 Node 直接 `import` 当前 `validate.js`，调 `validatePhone` / `validateSellerPassword`；
- `02` 发真实 HTTP 请求到后端；
- `03` 合并两侧结果生成 `05-红项详细测试用例.xlsx`。

**当前结果**：31 条，一致 16、不一致 15。

### 05-前端组件编译检查.mjs

用 `@vue/compiler-sfc` 对 `src/**/*.vue` 逐个做 `parse + compileScript + compileTemplate`。

**为什么不用 `vite build`**：沙箱禁止 Node 给子进程开管道，`vite build` 会报 `spawn EPERM`（不是代码问题）。
`compiler-sfc` 是纯 JS 调用，不 spawn 子进程。

**当前结果**：27 个 `.vue`，27 通过，0 失败。

---

## 六、证据文件对照

| 脚本 | 证据文件 |
| --- | --- |
| `01` / `02` 红项详细用例 | `证据/r3-reditem-frontend.json`、`r3-reditem-backend.json` |
| **`04-挑战B功能测试.py`** | **`证据/challengeB-all-*.json`** |
| `05-前端组件编译检查.mjs` | 无（输出即结果） |

---

## 七、预期结果

| 脚本 | 预期 |
| --- | --- |
| `04-挑战B功能测试.py` | 102 条通过 100，`L09`/`L10` 失败（真实缺陷） |
| `01`+`02`+`03` 红项 | 31 条，一致 16 / 不一致 15 |
| `05-前端组件编译检查.mjs` | 27/27 通过 |

> 若与上述不一致，先确认：数据目录是否干净、卖家密码是否为 `seller123`、被测版本是否为 `bc01eaf`。

---

## 八、完整复现步骤

```bash
# 1. 同步代码
git fetch origin && git reset --hard origin/main     # 应为 bc01eaf

# 2. 打包后端
cd backend-java
mvn -s <settings.xml> -Dmaven.test.skip=true package

# 3. 干净启动
SHOP_DATA_DIR=./data-clean java -jar target/shop-backend-1.0.0.jar --server.port=8000

# 4. 跑主套件（另开终端）
cd 第三次评审/测试脚本
python 04-挑战B功能测试.py

# 5. 跑红项对照
node 01-红项详细用例-前端.mjs > ../../_runtime/detail_fe.json
python 02-红项详细用例-后端.py
python 03-生成红项详细用例表.py

# 6. 前端编译检查
cd ../../_work/shop9/frontend
node <脚本路径>/05-前端组件编译检查.mjs
```
