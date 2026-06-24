<#
.SYNOPSIS
    直播打赏系统 - 一键启动脚本
.DESCRIPTION
    自动化完成 MySQL 启动、环境变量设置、项目构建、服务启动和健康检查。
    基于 2026-06-24 全量验收测试实际经验编写。
.PARAMETER DbPassword
    MySQL root 密码（必填）
.PARAMETER SkipBuild
    跳过 Maven 构建步骤（如果 jar 包已经存在）
.PARAMETER SkipMysqlCheck
    跳过 MySQL 服务状态检查
.EXAMPLE
    .\STARTUP_SCRIPT.ps1 -DbPassword "yourpassword"
.EXAMPLE
    .\STARTUP_SCRIPT.ps1 -DbPassword "yourpassword" -SkipBuild
.NOTES
    需要以管理员权限运行 PowerShell（用于启动 MySQL 服务）
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$DbPassword,

    [switch]$SkipBuild,

    [switch]$SkipMysqlCheck
)

$ErrorActionPreference = "Stop"
$PROJECT_ROOT = "d:\code\javaee-donation-live"

Write-Host ""
Write-Host "═══════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  直播打赏系统 - 一键启动脚本" -ForegroundColor White
Write-Host "═══════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# Step 0: 前置检查
# ============================================================
Write-Host "[0/7] 前置环境检查..." -ForegroundColor Yellow

# 检查 JDK
try {
    $javaVer = & java -version 2>&1 | Select-Object -First 1
    Write-Host "  ✅ JDK: $javaVer" -ForegroundColor Green
} catch {
    Write-Host "  ❌ JDK not found. Please install JDK 17." -ForegroundColor Red
    exit 1
}

# 检查 Maven
try {
    $mvnVer = & mvn -v 2>&1 | Select-String "Apache Maven"
    Write-Host "  ✅ Maven: $($mvnVer.Line.Trim())" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Maven not found." -ForegroundColor Red
    exit 1
}

# 检查项目目录
if (-not (Test-Path $PROJECT_ROOT)) {
    Write-Host "  ❌ Project directory not found: $PROJECT_ROOT" -ForegroundColor Red
    exit 1
}
Write-Host "  ✅ Project: $PROJECT_ROOT" -ForegroundColor Green

# ============================================================
# Step 1: 启动 MySQL
# ============================================================
Write-Host ""
Write-Host "[1/7] 检查 MySQL 服务..." -ForegroundColor Yellow

if (-not $SkipMysqlCheck) {
    try {
        $mysqlService = Get-Service -Name "mysql80" -ErrorAction SilentlyContinue
        if ($mysqlService.Status -ne 'Running') {
            Write-Host "  ⚠️  MySQL service not running, starting..." -ForegroundColor Yellow
            Start-Service -Name "mysql80"
            Start-Sleep 3
            Write-Host "  ✅ MySQL service started" -ForegroundColor Green
        } else {
            Write-Host "  ✅ MySQL service already running" -ForegroundColor Green
        }
    } catch {
        Write-Host "  ⚠️  Cannot manage MySQL service (may need admin rights). Assuming it's running." -ForegroundColor Yellow
    }
}

# 测试 MySQL 连接
try {
    & mysql -u root -p"$DbPassword" -e "SELECT 1;" 2>$null | Out-Null
    Write-Host "  ✅ MySQL connection OK" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Cannot connect to MySQL. Check password and service." -ForegroundColor Red
    exit 1
}

# ============================================================
# Step 2: 检查/初始化数据库
# ============================================================
Write-Host ""
Write-Host "[2/7] 检查数据库..." -ForegroundColor Yellow

$dbExists = & mysql -u root -p"$DbPassword" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='javaee_donation_live';" 2>$null
if ($dbExists -match '0') {
    Write-Host "  ⚠️  Database not initialized. Running schema.sql..." -ForegroundColor Yellow
    & mysql -u root -p"$DbPassword" < "$PROJECT_ROOT\sql\schema.sql" 2>&1 | Out-Null
    Write-Host "  ✅ Database initialized from schema.sql" -ForegroundColor Green
} else {
    # 检查是否缺少关键列
    $colCheck = & mysql -u root -p"$DbPassword" javaee_donation_live -e "SHOW COLUMNS FROM t_reward_event LIKE 'commission_rate';" 2>$null
    if (-not $colCheck) {
        Write-Host "  ⚠️  Missing columns in t_reward_event, adding..." -ForegroundColor Yellow
        & mysql -u root -p"$DbPassword" javaee_donation_live -e @"
ALTER TABLE t_reward_event
  ADD COLUMN commission_rate DECIMAL(8,4) DEFAULT NULL,
  ADD COLUMN commission_amount DECIMAL(18,2) DEFAULT NULL,
  ADD COLUMN withdrawable_amount DECIMAL(18,2) DEFAULT NULL;
"@ 2>$null | Out-Null
        Write-Host "  ✅ Missing columns added" -ForegroundColor Green
    } else {
        Write-Host "  ✅ Database OK (tables + columns verified)" -ForegroundColor Green
    }
}

# ============================================================
# Step 3: 构建项目
# ============================================================
Write-Host ""
Write-Host "[3/7] 构建项目..." -ForegroundColor Yellow

if (-not $SkipBuild) {
    Push-Location $PROJECT_ROOT
    & mvn clean package -DskipTests -q 2>&1
    Pop-Location
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ❌ Build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "  ✅ Build successful" -ForegroundColor Green
} else {
    Write-Host "  ⏭️  Skipped build (using existing jars)" -ForegroundColor Gray
}

# ============================================================
# Step 4: 设置全局环境变量
# ============================================================
Write-Host ""
Write-Host "[4/7] 设置环境变量..." -ForegroundColor Yellow

$env:DB_HOST = 'localhost:3306'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = $DbPassword
Write-Host "  ✅ DB_HOST=$($env:DB_HOST)" -ForegroundColor Green
Write-Host "  ✅ DB_USERNAME=$($env:DB_USERNAME)" -ForegroundColor Green
Write-Host "  ✅ DB_PASSWORD=***" -ForegroundColor Green

# ============================================================
# Step 5: 启动 4 个服务
# ============================================================
Write-Host ""
Write-Host "[5/7] 启动服务 (按依赖顺序)..." -ForegroundColor Yellow

$services = @(
    @{ Name="Finance";   Port=8082; Jar="finance-service";       EnvNeeded=$true },
    @{ Name="Analytics", Port=8083; Jar="analytics-service";     EnvNeeded=$true },
    @{ Name="Viewer",    Port=8081; Jar="viewer-service";        EnvNeeded=$true },
    @{ Name="Simulator", Port=8084; Jar="simulator-service";     EnvNeeded=$false }
)

$startedProcesses = @()

foreach ($svc in $services) {
    Write-Host ""
    Write-Host "  Starting $($svc.Name) (:$($svc.Port))..." -ForegroundColor White

    $jarPath = "$PROJECT_ROOT\$($svc.Jar)\target\$($svc.Jar)-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "    ❌ JAR not found: $jarPath" -ForegroundColor Red
        exit 1
    }

    # 清理 Sentinel 锁文件（防止 Viewer 启动失败）
    if ($svc.Jar -eq "viewer-service") {
        Remove-Item -Force "$env:USERPROFILE\logs\csp\sentinel-record.log.*" -ErrorAction SilentlyContinue
    }

    $proc = Start-Process -FilePath "java" -ArgumentList "-Xms128m","-Xmx256m","-jar",$jarPath `
        -PassThru -WindowStyle Normal
    $startedProcesses += $proc
    Write-Host "    ✅ PID: $($proc.Id), waiting for startup..." -ForegroundColor Green

    # 等待服务启动（最多 35 秒）
    $ready = $false
    for ($i = 0; $i -lt 35; $i++) {
        Start-Sleep 1
        try {
            $health = Invoke-RestMethod -Uri "http://localhost:$($svc.Port)/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') {
                $ready = $true
                Write-Host "    ✅ $($svc.Name) is UP (:$($svc.Port)) after ${i}s" -ForegroundColor Green
                break
            }
        } catch {
            # 还没准备好，继续等
        }
        if ($i % 10 -eq 9) {
            Write-Host "    ⏳ Still waiting... (${i+1}s)" -ForegroundColor DarkGray
        }
    }

    if (-not $ready) {
        Write-Host "    ⚠️  $($svc.Name) did not respond to health check within 35s." -ForegroundColor Yellow
        Write-Host "    It may still be starting up. Continuing with next service..." -ForegroundColor Yellow
    }

    # 服务间隔 2 秒
    Start-Sleep 2
}

# ============================================================
# Step 6: 最终健康检查
# ============================================================
Write-Host ""
Write-Host "[6/7] 最终健康检查..." -ForegroundColor Yellow

$allUp = $true
foreach ($svc in $services) {
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:$($svc.Port)/actuator/health" -TimeoutSec 5
        if ($health.status -eq 'UP') {
            Write-Host "  ✅ $($svc.Name.PadRight(12)): $($svc.Port) = UP" -ForegroundColor Green
        } else {
            Write-Host "  ❌ $($svc.Name.PadRight(12)): $($svc.Port) = $($health.status)" -ForegroundColor Red
            $allUp = $false
        }
    } catch {
        Write-Host "  ❌ $($svc.Name.PadRight(12)): $($svc.Port) = UNREACHABLE" -ForegroundColor Red
        $allUp = $false
    }
}

# ============================================================
# Step 7: 完成
# ============================================================
Write-Host ""
Write-Host "[7/7] 完成!" -ForegroundColor Yellow
Write-Host ""

if ($allUp) {
    Write-Host "═══════════════════════════════════════════════" -ForegroundColor Green
    Write-Host "  🎉 所有服务已成功启动！" -ForegroundColor White
    Write-Host "═══════════════════════════════════════════════" -ForegroundColor Green
    Write-Host ""
    Write-Host "  下一步操作:" -ForegroundColor White
    Write-Host "    1. 功能验证: 参见 QUICKSTART.md 第四节" -ForegroundColor Gray
    Write-Host "    2. 压测:      访问 http://localhost:8084/report.html" -ForegroundColor Gray
    Write-Host "    3. 停止:      各终端 Ctrl+C" -ForegroundColor Gray
} else {
    Write-Host "═══════════════════════════════════════════════" -ForegroundColor Yellow
    Write-Host "  ⚠️  部分服务可能未正常启动，请查看上方日志" -ForegroundColor White
    Write-Host "═══════════════════════════════════════════════" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  常见修复方法:" -ForegroundColor White
    Write-Host "    - 检查 MySQL 是否运行: net start mysql80" -ForegroundColor Gray
    Write-Host "    - 检查端口是否冲突: netstat -ano | findstr LISTENING" -ForegroundColor Gray
    Write-Host "    - 查看 QUICKSTART.md 第六节排查指南" -ForegroundColor Gray
}

Write-Host ""
