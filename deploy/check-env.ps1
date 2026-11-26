# 环境连通性自检（在 Docker Desktop 运行后执行）
# 用法：powershell -ExecutionPolicy Bypass -File .\check-env.ps1
$ErrorActionPreference = 'Continue'
$ok = 0; $bad = 0

function Pass($m) { Write-Host "  [OK]   $m" -ForegroundColor Green; $script:ok++ }
function Fail($m) { Write-Host "  [FAIL] $m" -ForegroundColor Red;   $script:bad++ }
function Info($m) { Write-Host "  [--]   $m" -ForegroundColor DarkGray }

Write-Host "`n=== 1. Docker 引擎 ===" -ForegroundColor Cyan
$ver = docker info --format '{{.ServerVersion}}' 2>$null
if ($LASTEXITCODE -eq 0 -and $ver) { Pass "daemon 运行中，ServerVersion=$ver" }
else { Fail "daemon 未运行：请先手动启动 Docker Desktop，等托盘变绿后重试" }

Write-Host "`n=== 2. 容器状态 ===" -ForegroundColor Cyan
$ps = docker compose -f "$PSScriptRoot\docker-compose.yml" ps --format "{{.Name}}`t{{.State}}`t{{.Status}}" 2>$null
if ($ps) { $ps | ForEach-Object { Info $_ } } else { Info "未发现 oes 项目容器（尚未 up）" }

Write-Host "`n=== 3. MySQL 8 ===" -ForegroundColor Cyan
$my = docker exec oes-mysql mysql -uroot -proot123456 -N -e "SELECT VERSION(), @@character_set_server, @@collation_server;" 2>$null
if ($LASTEXITCODE -eq 0 -and $my) { Pass "MySQL: $my" } else { Fail "无法连接 MySQL（容器未就绪或口令不符）" }
$db = docker exec oes-mysql mysql -uroot -proot123456 -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='oes';" 2>$null
if ($db -eq '1') { Pass "数据库 oes 已创建" } else { Fail "数据库 oes 不存在" }

Write-Host "`n=== 4. Redis ===" -ForegroundColor Cyan
$pong = docker exec oes-redis redis-cli -a redis123456 --no-auth-warning ping 2>$null
if ($pong -eq 'PONG') { Pass "Redis PONG" } else { Fail "Redis 无响应" }

Write-Host "`n=== 5. 对象存储（S3 兼容）===" -ForegroundColor Cyan
try {
  $r = Invoke-WebRequest -Uri 'http://localhost:9000/minio/health/live' -UseBasicParsing -TimeoutSec 5
  if ($r.StatusCode -eq 200) { Pass "S3 服务健康检查 = 200" }
} catch { Fail "S3 健康检查失败：$($_.Exception.Message)" }
try {
  $h = Invoke-WebRequest -Uri 'http://localhost:9001/rustfs/console/' -UseBasicParsing -TimeoutSec 5
  Pass "管理控制台可访问（HTTP $($h.StatusCode)）"
} catch { Fail "管理控制台不可访问" }

Write-Host "`n=== 6. 宿主机端口 ===" -ForegroundColor Cyan
foreach ($p in 3306, 6379, 9000, 9001) {
  $c = Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue
  if ($c) { Pass "端口 $p 已监听" } else { Fail "端口 $p 未监听" }
}
Info "PostgreSQL 仍占用 5432，与上述端口互不影响"

Write-Host "`n=== 7. JDK ===" -ForegroundColor Cyan
$jv = (javac -version 2>&1)
Info "当前 javac: $jv（课题要求 17，若需严格对齐请安装 JDK 17 并切换 JAVA_HOME）"

Write-Host "`n----------------------------------------" -ForegroundColor DarkGray
Write-Host "通过 $ok 项，失败 $bad 项" -ForegroundColor $(if ($bad -eq 0) { 'Green' } else { 'Yellow' })
if ($bad -eq 0) { Write-Host "环境就绪，可以开始编码。" -ForegroundColor Green }
