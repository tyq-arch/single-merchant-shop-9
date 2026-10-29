# 后端手机号校验 —— HTTP 请求测试脚本
# 用法：在项目根目录执行
#   pwsh -File "第2次评审测试\脚本-后端手机号校验.ps1"
# 前提：后端已启动（http://127.0.0.1:8000），且店铺里有在售商品

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$BASE = "http://127.0.0.1:8000"

# ---------- 1. 取当前在售商品 id ----------
Write-Host "=== 第 1 步：获取在售商品 ===" -ForegroundColor Cyan
try {
    $cur = Invoke-RestMethod "$BASE/api/products/current" -TimeoutSec 10
} catch {
    Write-Host "  后端不可用，请先启动后端（cd backend; uvicorn app.main:app --port 8000）" -ForegroundColor Red
    exit 1
}

if ($cur.state -ne 'ON_SALE' -or -not $cur.product) {
    Write-Host "  当前没有在售商品（state=$($cur.state)）" -ForegroundColor Yellow
    Write-Host "  请先登录卖家后台发布一个：http://127.0.0.1:5173/seller/publish" -ForegroundColor Yellow
    exit 1
}

$productId = $cur.product.id
Write-Host "  商品 id = $productId   名称 = $($cur.product.name)" -ForegroundColor Green
Write-Host ""

# ---------- 2. 逐个号码发请求 ----------
Write-Host "=== 第 2 步：逐个提交购买意向 ===" -ForegroundColor Cyan
Write-Host ("  {0,-20} {1,-14} {2,-6} {3}" -f "号码", "场景", "HTTP", "后端结论")
Write-Host ("  " + ("-" * 66))

$cases = @(
    @{ phone = '13800000000';    desc = '合法 11 位' }
    @{ phone = '19900000000';    desc = '19 号段' }
    @{ phone = '13800000001';    desc = '合法' }
    @{ phone = '  13800000000  '; desc = '首尾空格' }
    @{ phone = '23800000000';    desc = '2 开头' }
    @{ phone = '12800000000';    desc = '第二位为 2' }
    @{ phone = '10800000000';    desc = '第二位为 0' }
    @{ phone = '1380000000';     desc = '只有 10 位' }
    @{ phone = '138000000000';   desc = '12 位' }
    @{ phone = '12345';          desc = '5 位短号' }
    @{ phone = '+8613800000000'; desc = '带国家码' }
    @{ phone = '138-0000-0000';  desc = '带连字符' }
    @{ phone = '138 0000 0000';  desc = '带空格' }
    @{ phone = '13800000001a';   desc = '含字母' }
    @{ phone = '';               desc = '空值' }
    @{ phone = '   ';            desc = '全空格' }
)

$accept = 0
$reject = 0
foreach ($c in $cases) {
    $body = @{
        product_id  = $productId
        buyer_name  = '手机号测试'
        buyer_phone = $c.phone
    } | ConvertTo-Json

    try {
        $r = Invoke-RestMethod -Method Post -Uri "$BASE/api/buyer/intents" `
             -ContentType "application/json" -Body $body -TimeoutSec 15
        $accept++
        Write-Host ("  {0,-20} {1,-12} {2,-6} {3}" -f $c.phone, $c.desc, 200, "接受（队列位 $($r.queue_position)）")
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if (-not $code) { $code = "ERR" }
        elseif ($code -ne 422) { }
        $reject++
        Write-Host ("  {0,-20} {1,-12} {2,-6} {3}" -f $c.phone, $c.desc, $code, "拒绝") -ForegroundColor Yellow
    }
}

# ---------- 3. 小结 ----------
Write-Host ""
Write-Host "=== 小结 ===" -ForegroundColor Cyan
Write-Host "  后端规则：^[0-9+\-\s]{5,20}$（5~20 位，允许 + - 空格）"
Write-Host "  共 $($cases.Count) 个号码：接受 $accept 个，拒绝 $reject 个"
Write-Host ""
Write-Host "  对照前端规则 ^1[3-9]\d{9}$（严格 11 位）：" -ForegroundColor Gray
Write-Host "  下面这些后端"接受"、前端"拒绝"的号码，就是前后端不一致的证据："
Write-Host "    +8613800000000 / 138-0000-0000 / 138 0000 0000 / 12345 / 23800000000 / 12800000000 / 10800000000 / 1380000000 / 138000000000"
