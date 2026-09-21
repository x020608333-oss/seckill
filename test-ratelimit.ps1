# v4 rate limit test: 10 rapid requests, print every response
$login = Invoke-RestMethod -Uri "http://localhost:8080/user/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"123456"}'
$token = $login.data
$headers = @{ Authorization = "Bearer $token" }

$sw = [System.Diagnostics.Stopwatch]::StartNew()
1..10 | ForEach-Object {
    $r = Invoke-RestMethod -Uri "http://localhost:8080/seckill/doSeckill?goodsId=1" -Method Post -Headers $headers
    Write-Host ("[$_]`tcode=" + $r.code + "`tmessage=" + $r.message)
}
$sw.Stop()
Write-Host ("elapsed: " + $sw.ElapsedMilliseconds + " ms")
