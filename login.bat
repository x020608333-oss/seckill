@echo off
chcp 65001 >nul
echo [1] 登录中...
curl.exe -s -X POST http://localhost:8080/user/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"123456\"}" > C:\Users\seckill-projects\seckill\token.json
type C:\Users\seckill-projects\seckill\token.json
