@echo off
chcp 65001 >nul
echo ==========================================
echo        秒杀系统 - 完整测试演示
echo ==========================================
echo.

echo [步骤 1] 登录获取 Token...
echo 请求: POST http://localhost:8080/user/login
echo 账号: admin / 123456
echo.
curl.exe -s -X POST http://localhost:8080/user/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"123456\"}"
echo.
echo.
echo 请复制上面返回的 token 值（data 字段）
echo.
pause
