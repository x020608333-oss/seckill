@echo off
echo ===== 1. Login =====
curl.exe -s -X POST http://localhost:8080/user/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"123456\"}" > login_response.txt
type login_response.txt
echo.
echo.
echo ===== 2. Extract Token =====
for /f "tokens=2 delims=:,}" %%a in ('type login_response.txt ^| findstr "data"') do set TOKEN=%%a
set TOKEN=%TOKEN:"=%
echo Token: %TOKEN%
echo.
echo ===== 3. Goods List =====
curl.exe -s http://localhost:8080/goods/list -H "Authorization: Bearer %TOKEN%"
echo.
echo.
echo ===== 4. Do Seckill =====
curl.exe -s -X POST "http://localhost:8080/seckill/doSeckill?goodsId=1" -H "Authorization: Bearer %TOKEN%"
echo.
echo.
echo ===== 5. Repeat Seckill (should fail) =====
curl.exe -s -X POST "http://localhost:8080/seckill/doSeckill?goodsId=1" -H "Authorization: Bearer %TOKEN%"
echo.
echo.
echo ===== 6. Query Result =====
curl.exe -s "http://localhost:8080/seckill/result?goodsId=1" -H "Authorization: Bearer %TOKEN%"
echo.
