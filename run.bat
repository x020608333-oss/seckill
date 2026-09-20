@echo off
set JAVA_HOME=D:\Program Files\Java\jdk-1.8
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Users\seckill-projects\seckill
"D:\Program Files\Java\jdk-1.8\bin\java.exe" -Dmaven.multiModuleProjectDirectory=C:\Users\seckill-projects\seckill -cp .mvn\wrapper\maven-wrapper.jar org.apache.maven.wrapper.MavenWrapperMain spring-boot:run
