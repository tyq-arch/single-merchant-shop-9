# BUG 记录与汇总

> 本文件为 `03-BUG记录与汇总表.xlsx` 的**在线阅读版**，内容与 Word / Excel 版一致，便于在浏览器中直接查看与检索。


> 编写人：缪茂锦（测试岗）　|　缺陷总数 **2**（中级 1、低级 1），观察项 **10** 项，无阻塞级缺陷。

## 一、缺陷汇总

| 统计维度 | 分类 | 数量 |
| --- | --- | ---: |
| 按严重级别 | 高（Critical/Blocker） | 0 |
| 按严重级别 | 中（Major） | 1 |
| 按严重级别 | 低（Minor） | 1 |
| 按状态 | 待修复 | 2 |
| 按模块 | 商品管理 | 1 |
| 按模块 | 历史记录 | 1 |
| 缺陷密度 | 每百条用例 | 1.8 |

## 二、缺陷明细


### BUG-001 发布商品时价格不足 1 分，接口返回 HTTP 500

| 项目 | 内容 |
| --- | --- |
| 所属模块 | 商品管理 / 发布商品 |
| 需求追溯 | FR-001、NFR-009 |
| 严重级别 / 优先级 | 中（Major） / 高 |
| 缺陷状态 | 待修复 |
| 发现方式 | 黑盒功能测试 TC-C10 |
| 发现日期 | 2026-10-07 |
| 复现概率 | 必现（100%） |
| 测试环境 | 后端 127.0.0.1:8000，SQLite，Python 3.12 + FastAPI 0.142.2 |
| **复现步骤** | 1) POST /api/seller/login，body={"username":"seller","password":"seller123"}，取得 Bearer 令牌；<br>2) 确认当前无在售商品（否则先下架）；<br>3) POST /api/seller/products，Header 带令牌，body={"name":"亚分价格","price":0.001}；<br>4) 观察响应状态码与响应体 |
| **预期结果** | HTTP 422，响应体符合接口文档统一错误结构：{"error":{"code":"VALIDATION_ERROR","message":"..."}} |
| **实际结果** | HTTP 500，响应体为纯文本 "Internal Server Error"（非统一错误结构） |
| **根因分析** | ① schemas.py 中 ProductCreateRequest.price = Field(gt=0, le=9999999)，仅约束大于 0，未约束最小货币单位；<br>② utils.cents_from_price() 以 Decimal ROUND_HALF_UP 换算分，0.001 元 → 0 分；<br>③ schema.sql 中 product.price_cents 定义 CHECK (price_cents > 0)；<br>④ product_service.publish_product() 直接执行 INSERT，未捕获 sqlite3.IntegrityError；<br>⑤ main.py 仅注册了 BusinessError 与 RequestValidationError 处理器，未兜底其他异常，异常穿透 ASGI 后被 Starlette 转为 500。 |
| **证据** | 后端日志堆栈：<br>  File "backend/app/routers/seller.py", line 67, in publish_product<br>  File "backend/app/services/product_service.py", line 29, in publish_product<br>    conn.execute(<br>  sqlite3.IntegrityError: CHECK constraint failed: price_cents > 0 |
| 可达性分析 | 正常 UI 操作不可达：PublishView.vue 使用 <el-input-number :min="0.01" :precision="2">，用户无法通过界面输入 0.001。但该接口为文档公开契约，直接调用 API（自动化脚本、Postman、第三方客户端）可稳定复现。 |
| 影响分析 | ① 接口契约被破坏，违反接口设计文档 §1「错误格式统一」约定；② 服务端出现未处理异常，属稳定性与防御性校验缺失；③ 前端在极端情况下只能显示通用失败提示，无有效错误信息。 |
| **修复建议** | 建议三处同时加固：<br>① 将校验前移到金额语义：ProductCreateRequest 使用 Decimal 并约束 ge=Decimal("0.01")；<br>② 在 publish_product() 中校验 cents_from_price(price) > 0，否则抛 BusinessError（message 如「价格不能低于 0.01 元」，status_code=422）；<br>③ 在 main.py 增加通用 Exception 处理器，将未捕获异常统一转换为 {"error":{"code":"INTERNAL_ERROR",...}} 结构，避免 500 返回纯文本。 |
| 回归验证方法 | 重新执行 TC-C10，期望返回 HTTP 422 且响应体为统一错误结构；同时回归 TC-C01（正常价格发布成功）确保未被误伤。 |


### BUG-002 历史商品搜索未转义 LIKE 通配符，搜索结果不准确

| 项目 | 内容 |
| --- | --- |
| 所属模块 | 历史记录 / 商品搜索 |
| 需求追溯 | FR-004 |
| 严重级别 / 优先级 | 低（Minor） / 中 |
| 缺陷状态 | 待修复 |
| 发现方式 | 黑盒功能测试 TC-F15 |
| 发现日期 | 2026-10-07 |
| 复现概率 | 必现（100%） |
| 测试环境 | 后端 127.0.0.1:8000，SQLite，Python 3.12 + FastAPI 0.142.2 |
| **复现步骤** | 1) 以卖家令牌登录；<br>2) 确认历史中存在若干已下架商品；<br>3) GET /api/seller/history?keyword=_ ；<br>4) 对比 GET /api/seller/history 的 total 值 |
| **预期结果** | keyword 按字面字符串匹配。由于无任何商品名含下划线字符，应返回 total=0 |
| **实际结果** | 返回 total=7，等于不带关键词时的全部历史商品数，即「_」被当作 LIKE 单字符通配符 |
| **根因分析** | product_service.list_history() 中：<br>  where += " AND name LIKE ?"<br>  params.append(f"%{keyword}%")<br>未对用户输入中的 SQL LIKE 元字符 % 与 _ 做转义，也未使用 ESCAPE 子句。（查询本身是参数化的，因此不构成 SQL 注入，仅影响匹配语义。） |
| **证据** | TC-F15 实测：搜索「_」命中 7 条；不带关键词共 7 条；两值相等。 |
| 可达性分析 | 可通过前端历史商品页搜索框直接触发（输入 _ 或 % 后点击查询）。 |
| 影响分析 | 用户以含 % 或 _ 的关键词搜索时结果不准确（_ 匹配任一单字符、% 匹配任意长度）；不含这两个字符的常规关键词不受影响，故影响面有限。 |
| **修复建议** | 对关键词做 LIKE 元字符转义后再拼接，并在 SQL 中显式声明转义符：<br>  safe = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")<br>  where += " AND name LIKE ? ESCAPE '\\'"<br>  params.append(f"%{safe}%") |
| 回归验证方法 | 重新执行 TC-F15，期望 keyword=_ 返回 total=0；回归 TC-F14（keyword=历史 正常命中）确保未被误伤。 |


## 三、观察项与改进建议

| 编号 | 级别 | 模块 | 标题 | 说明 | 建议 |
| --- | --- | --- | --- | --- | --- |
| OBS-001 | 中 | 文件上传 | POST /api/files/images 上传接口未做鉴权 | 任意调用者无需令牌即可上传文件（单个最大 5MB），文件落盘后经 /uploads 公开可访问，存在被匿名大量上传占用磁盘的风险。 | 为该接口增加卖家令牌校验（Depends(get_current_seller)），并同步更新接口文档；如需保留公开能力，建议增加频率限制与总量配额。 |
| OBS-002 | 低 | 文件上传 | 上传仅校验扩展名，未校验文件真实类型 | 服务端只检查文件扩展名是否在白名单内，不读取文件头（Magic Number），可将任意内容命名为 .png 上传。 | 增加文件头魔数校验（PNG/JPEG/GIF/WEBP）；当前风险较低，因为静态资源按扩展名下发 Content-Type: image/png，浏览器不会按脚本执行。 |
| OBS-003 | 低 | 商品管理 / 前端渲染 | 商品名按原文存储，接口不做 HTML 转义 | 商品名称可包含 HTML 标签并原样存储与返回，转义责任完全在前端渲染层。 | 保持「接口存原文、前端插值转义」的现有约定，并把它写入前端代码评审检查项；后续若引入富文本渲染需重新评估。 |
| OBS-004 | 提示 | 商品状态机 | 手动冻结与交易自动冻结在状态上不可区分 | 两种冻结原因在 product.status 上同为 FROZEN，接口未提供区分字段；前端只能通过「是否存在 IN_TRANSACTION 订单」间接推断应显示「手动解冻」还是「标记交易成功/失败」。 | 属实现与需求的表达力问题，当前功能正确（TC-C11~TC-C16 均通过）。建议在需求或接口上补充冻结来源字段，降低前端推断耦合。 |
| OBS-005 | 提示 | 口令码 | 口令码有效期规则未在需求/接口文档中明确 | 数据库 command_code.expires_at 字段存在，配置默认 30 天（SHOP_COMMAND_CODE_TTL_DAYS），但需求分析文档将其列入 TBD，接口设计文档也未描述过期行为。 | 需求侧补充确认口令码有效期时长与到期提示文案，接口文档补充 410 的过期场景说明。当前实现可用（TC-D05、TC-E08、TC-E13 均通过），属文档缺口。 |
| OBS-006 | 提示 | 隐私脱敏 | 交易失败买家电话立即脱敏，与卖家处置需求存在张力 | 订单进入 FAILED（属终结状态）后买家电话立即按 FR-019/NFR-003 脱敏，但卖家恰需据此决定「作废」还是「重新排队」；重新排队后（REQUEUED）才恢复明文。 | 建议明确「失败待处置订单」的展示规则（例如处置前保持明文、处置后脱敏），在需求文档中补充说明。 |
| OBS-007 | 文档 | 需求文档一致性 | 需求分析文档存在重复条目与图文不一致 | ① FR-001 与 FR-036（发布商品）、FR-035 与 FR-041（修改密码）内容完全重复；② 订单状态机图缺少「排队中→已作废」边，而状态表列出了该迁移；③ 商品状态机图触发条件为「交易失败且队列为空」，状态表为「手动解冻或交易失败」；④ 术语「再售/在售」混用。 | 由设计岗（周育民）统一去重并修正图与表的一致性，避免用例追溯出现一对多歧义。 |
| OBS-008 | 文档 | 接口文档完整性 | 接口文档章节覆盖不完整 | 接口设计文档 §2 总览表列出 24 条接口，但 §3~§6 仅详细展开 #2~#24，GET /（服务信息）没有详细章节；§5.14「作废」未写明成功响应体结构。 | 补齐 GET / 的响应结构说明与作废接口的成功响应示例。 |
| OBS-009 | 文档 | 页面原型文档一致性 | 侧边栏项数描述与实际不符 | 页面原型设计文档称卖家后台侧边栏「固定 5 项」，但同处及示意图均列出 6 项（工作台、发布商品、意向购买人、历史商品、修改密码、操作日志）。 | 修正文档表述为 6 项。 |
| OBS-010 | 提示 | 分页 | 分页页码超出总页数时返回空列表且无提示 | GET /api/seller/history?page=999 返回 HTTP 200 与空 items，未做友好提示。 | 属可接受行为（REST 常规语义）。建议前端在结果为空且 total>0 时提示用户返回首页。 |

> 说明：观察项不构成功能缺陷，但影响系统稳健性、安全性或文档一致性，建议纳入后续迭代。
