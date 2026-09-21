# v3 MQ异步秒杀全链路测试(Invoke-RestMethod版, 避免curl引号问题)
$login = Invoke-RestMethod -Uri "http://localhost:8080/user/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"123456"}'
$token = $login.data
Write-Host "Token: $($token.Substring(0,40))..."
$headers = @{ Authorization = "Bearer $token" }

# 重置测试数据
& 'C:\Users\redis\redis-cli.exe' set seckill:stock:1 100 | Out-Null
& 'C:\Users\redis\redis-cli.exe' del seckill:order:1:1 | Out-Null
& 'D:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' -uroot -p1qazmlp0 -e "USE seckill; DELETE FROM seckill_order WHERE user_id=1 AND goods_id=1; DELETE FROM order_info WHERE user_id=1 AND goods_id=1; UPDATE seckill_goods SET stock_count=100 WHERE goods_id=1;" 2>$null | Out-Null
& 'C:\Users\redis\redis-cli.exe' set seckill:stock:1 100 | Out-Null

Write-Host "`n===== 1. 秒杀(应返回排队中) ====="
$r = Invoke-RestMethod -Uri "http://localhost:8080/seckill/doSeckill?goodsId=1" -Method Post -Headers $headers
$r | ConvertTo-Json -Depth 5

Write-Host "`n===== 2. 立即查结果(应为0=排队中) ====="
$r = Invoke-RestMethod -Uri "http://localhost:8080/seckill/result?goodsId=1" -Method Get -Headers $headers
$r | ConvertTo-Json

Start-Sleep 3

Write-Host "`n===== 3. 等3秒后再查(应为订单ID) ====="
$r = Invoke-RestMethod -Uri "http://localhost:8080/seckill/result?goodsId=1" -Method Get -Headers $headers
$r | ConvertTo-Json

Write-Host "`n===== 4. 重复秒杀(应被拦截) ====="
try {
    $r = Invoke-RestMethod -Uri "http://localhost:8080/seckill/doSeckill?goodsId=1" -Method Post -Headers $headers
    $r | ConvertTo-Json
} catch {
    Write-Host $_.ErrorDetails.Message
}

Write-Host "`n===== 5. 数据验证 ====="
Write-Host "Redis库存(应为99):"
& 'C:\Users\redis\redis-cli.exe' get seckill:stock:1
Write-Host "Redis订单Key(应为订单ID):"
& 'C:\Users\redis\redis-cli.exe' get seckill:order:1:1
Write-Host "DB验证(库存99 + 秒杀订单1条):"
& 'D:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' -uroot -p1qazmlp0 -e "USE seckill; SELECT stock_count FROM seckill_goods WHERE goods_id=1; SELECT id,order_id FROM seckill_order WHERE user_id=1;" 2>$null


