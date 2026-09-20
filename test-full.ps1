# 完整接口测试
$baseUrl = "http://localhost:8080"

Write-Host "===== 1. 登录 ====="
$loginBody = '{"username":"admin","password":"123456"}'
$loginResponse = curl.exe -s -X POST "$baseUrl/user/login" -H "Content-Type: application/json" -d $loginBody | ConvertFrom-Json
Write-Host "Code: $($loginResponse.code), Token: $($loginResponse.data.Substring(0,50))..."
$token = $loginResponse.data

$headers = @{"Authorization" = "Bearer $token"}

Write-Host "`n===== 2. 商品列表 ====="
$goodsList = curl.exe -s "$baseUrl/goods/list" -H "Authorization: Bearer $token" | ConvertFrom-Json
Write-Host "Code: $($goodsList.code)"
$goodsList.data | ForEach-Object { Write-Host "  - $($_.goodsName): 秒杀价$($_.seckillPrice), 库存$($_.stockCount)" }

Write-Host "`n===== 3. 秒杀下单 ====="
$seckillResponse = curl.exe -s -X POST "$baseUrl/seckill/doSeckill?goodsId=1" -H "Authorization: Bearer $token" | ConvertFrom-Json
Write-Host "Code: $($seckillResponse.code), Message: $($seckillResponse.message)"
if ($seckillResponse.data) {
    Write-Host "  OrderId: $($seckillResponse.data.id), Goods: $($seckillResponse.data.goodsName), Price: $($seckillResponse.data.goodsPrice)"
}

Write-Host "`n===== 4. 重复秒杀(应失败) ====="
$repeatResponse = curl.exe -s -X POST "$baseUrl/seckill/doSeckill?goodsId=1" -H "Authorization: Bearer $token" | ConvertFrom-Json
Write-Host "Code: $($repeatResponse.code), Message: $($repeatResponse.message)"

Write-Host "`n===== 5. 查询秒杀结果 ====="
$result = curl.exe -s "$baseUrl/seckill/result?goodsId=1" -H "Authorization: Bearer $token" | ConvertFrom-Json
Write-Host "Code: $($result.code), OrderId: $($result.data)"

Write-Host "`n===== 测试完成 ====="
