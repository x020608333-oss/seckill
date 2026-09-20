@echo off
set JAVA_HOME=D:\Program Files\Java\jdk-1.8
set PATH=%JAVA_HOME%\bin;%PATH%
REM 本地开发数据库密码(推送GitHub时已脱敏, 本地通过环境变量注入)
if "%DB_PASSWORD%"=="" set DB_PASSWORD=1qazmlp0
if "%JWT_SECRET%"=="" set JWT_SECRET=seckill-interview-project-secret-key-2024-very-long-enough
cd /d C:\Users\seckill-projects\seckill
"D:\Program Files\Java\jdk-1.8\bin\java.exe" -Dmaven.multiModuleProjectDirectory=C:\Users\seckill-projects\seckill -cp .mvn\wrapper\maven-wrapper.jar org.apache.maven.wrapper.MavenWrapperMain spring-boot:run
