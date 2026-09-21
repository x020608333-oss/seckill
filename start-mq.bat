@echo off
REM 补齐 ERLANG_HOME\bin: 静默安装时 Install.exe 未执行,
REM RabbitMQ 需要 ERLANG_HOME\bin 下有 erl.exe + *.boot
if not exist "C:\Program Files\Erlang OTP\bin" mkdir "C:\Program Files\Erlang OTP\bin"
if not exist "C:\Program Files\Erlang OTP\bin\start_sasl.boot" (
    copy /y "C:\Program Files\Erlang OTP\erts-14.2.5.2\bin\erl.exe" "C:\Program Files\Erlang OTP\bin\" >nul 2>&1
    copy /y "C:\Program Files\Erlang OTP\erts-14.2.5.2\bin\erlexec.dll" "C:\Program Files\Erlang OTP\bin\" >nul 2>&1
    copy /y "C:\Program Files\Erlang OTP\erts-14.2.5.2\bin\erl_call.exe" "C:\Program Files\Erlang OTP\bin\" >nul 2>&1
    copy /y "C:\Program Files\Erlang OTP\releases\26\*.boot" "C:\Program Files\Erlang OTP\bin\" >nul 2>&1
    copy /y "C:\Program Files\Erlang OTP\releases\26\*.script" "C:\Program Files\Erlang OTP\bin\" >nul 2>&1
    copy /y "C:\Program Files\Erlang OTP\releases\26\*.args" "C:\Program Files\Erlang OTP\bin\" >nul 2>&1
)
set ERLANG_HOME=C:\Program Files\Erlang OTP
set PATH=C:\Program Files\Erlang OTP\bin;C:\Program Files\Erlang OTP\erts-14.2.5.2\bin;C:\Windows\System32;C:\Windows\System32\WindowsPowerShell\v1.0;%PATH%
call "C:\Program Files\RabbitMQ Server\rabbitmq_server-3.13.7\sbin\rabbitmq-server.bat"

