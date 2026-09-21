@echo off
chcp 65001 >nul
echo ===== 1. 登录 =====
curl.exe -s -X POST http://localhost:8080/user/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"123456\"}" > token.json
for /f "tokens=4 delims=:," %%a in ('type token.json') do set TOKEN=%%a
set TOKEN=%TOKEN:"=%
echo Token: %TOKEN:~0,50%...

echo.
echo ===== 2. 秒杀 goodsId=1 (应成功) =====
curl.exe -s -X POST "http://localhost:8080/seckill/doSeckill?goodsId=1" -H "Authorization: Bearer %TOKEN%"

echo.
echo ===== 3. 重复秒杀 (应被拦截) =====
curl.exe -s -X POST "http://localhost:8080/seckill/doSeckill?goodsId=1" -H "Authorization: Bearer %TOKEN%"

echo.
echo ===== 4. 查询结果 (应有订单ID) =====
curl.exe -s "http://localhost:8080/seckill/result?goodsId=1" -H "Authorization: Bearer %TOKEN%"

echo.
echo ===== 5. 查看Redis库存和订单Key =====
C:\Users\redis\redis-cli.exe get seckill:stock:1
C:\Users\redis\redis-cli.exe get seckill:order:1:1
