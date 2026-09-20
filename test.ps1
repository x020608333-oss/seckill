# 秒杀系统接口测试脚本
$baseUrl = "http://localhost:8080"

Write-Host "===== 1. 登录获取Token ====="
$loginBody = @{username="admin"; password="123456"} | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Uri "$baseUrl/user/login" -Method Post -ContentType "application/json" -Body $loginBody
Write-Host ($loginResponse | ConvertTo-Json)
$token = $loginResponse.data

$headers = @{Authorization = "Bearer $token"}

Write-Host "`n===== 2. 查看秒杀商品列表 ====="
$goodsList = Invoke-RestMethod -Uri "$baseUrl/goods/list" -Method Get -Headers $headers
Write-Host ($goodsList | ConvertTo-Json -Depth 5)

Write-Host "`n===== 3. 发起秒杀(goodsId=1) ====="
$seckillResponse = Invoke-RestMethod -Uri "$baseUrl/seckill/doSeckill?goodsId=1" -Method Post -Headers $headers
Write-Host ($seckillResponse | ConvertTo-Json -Depth 5)

Write-Host "`n===== 4. 重复秒杀(应该被拦截) ====="
$repeatResponse = Invoke-RestMethod -Uri "$baseUrl/seckill/doSeckill?goodsId=1" -Method Post -Headers $headers
Write-Host ($repeatResponse | ConvertTo-Json -Depth 5)

Write-Host "`n===== 5. 查询秒杀结果 ====="
$result = Invoke-RestMethod -Uri "$baseUrl/seckill/result?goodsId=1" -Method Get -Headers $headers
Write-Host ($result | ConvertTo-Json)

Write-Host "`n===== 测试完成 ====="
