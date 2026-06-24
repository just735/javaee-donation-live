# 快速启动指南

> 基于 2026-06-24 全量验收测试（14/14 PASS + QPS=506.5）的实际经验整理。
> **首次运行请严格按本文档顺序操作，避免踩坑。**

---

## 目录结构总览

```
docs/
├── guide/           # 操作指南（本文件在此）
│   ├── QUICKSTART.md    ← 你在这里
│   └── STARTUP_SCRIPT.ps1  ← 一键启动脚本
├── design/          # 设计文档（架构、数据库、API）
├── plan/            # 计划与规则（开发计划、任务拆解、开发规范）
├── report/          # 报告文档（验收报告、性能优化、完成报告）
└── reference/       # 参考信息（环境配置、本地调试）
```

---

## 一、前置条件检查清单

在开始之前，逐项确认以下条件：

### 1.1 必须已安装的软件

| 软件 | 版本要求 | 检查命令 |
|------|---------|---------|
| JDK | 17 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| MySQL | 8.0 (服务名 `mysql80`) | `mysql --version` |
| Consul | 1.18+ | `consul version` |

### 1.2 硬件要求

- 内存 ≥ **8GB**（4个服务同时运行约需 ~2GB JVM 堆内存）
- 磁盘剩余空间 ≥ **2GB**

### 1.3 端口占用确认

以下端口必须空闲：

| 端口 | 服务 |
|------|------|
| 8081 | viewer-service |
| 8082 | finance-service |
| 8083 | analytics-service |
| 8084 | simulator-service |
| 8500 | Consul |
| 3306 | MySQL |

```powershell
# 检查端口是否被占用（PowerShell）
foreach ($p in @(8081,8082,8083,8084,8500,3306)) {
    $conn = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue
    if ($conn) { Write-Host "⚠️  Port $p is occupied by PID $($conn.OwningProcess)" }
    else { Write-Host "✅ Port $p is free" }
}
```

---

## 二、启动步骤（按顺序执行）

> **重要：必须严格按照以下顺序启动，否则会出现服务间调用失败！**

### Step 1: 启动 MySQL 服务

这是最容易遗漏的一步。MySQL 服务名通常为 `mysql80`。

```powershell
# 以管理员身份运行 PowerShell
net start mysql80
```

验证连接：

```powershell
mysql -u root -p你的密码 -e "SELECT VERSION();"
```

如果报错 `Can't connect to MySQL server`，说明服务未启动。

### Step 2: 初始化数据库

```bash
cd d:\code\javaee-donation-live
mysql -u root -p < sql/schema.sql
```

**关键补充**：如果之前跑过压测但遇到 F9 对账接口报 `Unknown column 'commission_rate'` 错误，
说明建表脚本缺少 3 列。手动补齐：

```sql
ALTER TABLE t_reward_event
  ADD COLUMN commission_rate DECIMAL(8,4) DEFAULT NULL,
  ADD COLUMN commission_amount DECIMAL(18,2) DEFAULT NULL,
  ADD COLUMN withdrawable_amount DECIMAL(18,2) DEFAULT NULL;
```

验证表结构：

```sql
USE javaee_donation_live;
SHOW TABLES;
DESCRIBE t_reward_event;
-- 确认包含: commission_rate, commission_amount, withdrawable_amount
```

### Step 3: 设置环境变量

**以下 3 个 DB 服务都需要此配置**（viewer / finance / analytics）：

```powershell
$env:DB_HOST='localhost:3306'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的MySQL密码'
```

> **注意**：每个终端窗口需要单独设置环境变量！如果在 IDEA 中运行，请在 Run Configuration 的 Environment variables 中设置。

### Step 4: 启动 Consul

```bash
consul agent -dev
```

访问 `http://127.0.0.1:8500` 确认 UI 可打开。

### Step 5: 构建项目

```bash
cd d:\code\javaee-donation-live
mvn clean package -DskipTests
```

构建时间约 1-3 分钟。构建成功后各模块 `target/` 下会有 jar 包。

### Step 6: 按**严格顺序**启动 4 个服务

> **启动顺序不可颠倒！**

| 顺序 | 服务 | 命令 | 依赖 |
|------|------|------|------|
| **①** | finance-service | 见下方 | 仅依赖 MySQL + Consul |
| **②** | analytics-service | 见下方 | 仅依赖 MySQL + Consul |
| **③** | viewer-service | 见下方 | 依赖 ① + ② |
| **④** | simulator-service | 见下方 | 依赖 ③ |

每个服务的启动命令（均在项目根目录下执行）：

```powershell
# ① finance-service (终端1)
$env:DB_HOST='localhost:3306'; $env:DB_USERNAME='root'; $env:DB_PASSWORD='你的密码'
java -Xms128m -Xmx256m -jar finance-service/target/finance-service-1.0.0-SNAPSHOT.jar

# ② analytics-service (终端2)
$env:DB_HOST='localhost:3306'; $env:DB_USERNAME='root'; $env:DB_PASSWORD='你的密码'
java -Xms128m -Xmx256m -jar analytics-service/target/analytics-service-1.0.0-SNAPSHOT.jar

# ③ viewer-service (终端3)
$env:DB_HOST='localhost:3306'; $env:DB_USERNAME='root'; $env:DB_PASSWORD='你的密码'
java -Xms128m -Xmx256m -jar viewer-service/target/viewer-service-1.0.0-SNAPSHOT.jar

# ④ simulator-service (终端4)
java -Xms128m -Xmx256m -jar simulator-service/target/simulator-service-1.0.0-SNAPSHOT.jar
```

**等待每个服务完全启动后再启动下一个**（看到 `Started XxxApplication in X seconds` 日志后再继续）。

### Step 7: 健康检查

所有服务启动完成后，逐一验证：

```powershell
foreach ($p in 8081,8082,8083,8084) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:$p/actuator/health" -TimeoutSec 5
        Write-Host "✅ :$p = $($r.status)"
    } catch {
        Write-Host "❌ :$p = DOWN"
    }
}
```

期望输出：

```
✅ :8081 = UP
✅ :8082 = UP
✅ :8083 = UP
✅ :8084 = UP
```

如果有任何 `❌`，参见 [常见问题排查](#六常见问题排查)。

---

## 三、一键启动脚本

如果你不想每次手动输入命令，可以使用同目录下的 `STARTUP_SCRIPT.ps1` 脚本：

```powershell
# 在 docs/guide 目录下执行
.\STARTUP_SCRIPT.ps1 -DbPassword "你的MySQL密码"
```

脚本会自动完成：
1. 检查并启动 MySQL
2. 设置环境变量
3. 构建项目
4. 按正确顺序启动 4 个服务
5. 执行健康检查

---

## 四、功能验证速查

启动成功后，按以下顺序验证核心功能：

### 4.1 打赏入账 (F1)

```powershell
$body = '{
    "rewardNo": "TEST_001",
    "viewerId": "V001",
    "viewerName": "测试用户",
    "viewerGender": "MALE",
    "streamerId": "S001",
    "streamerName": "主播A",
    "rewardAmount": 100,
    "rewardTime": "2026-06-24T20:00:00"
}'
$r = Invoke-RestMethod -Uri "http://localhost:8081/api/viewers/reward" `
    -Method POST -ContentType "application/json" -Body $body
# 预期: code=SUCCESS, settleStatus=ACCEPTED
```

### 4.2 幂等性 (F2)

用相同的 `rewardNo` 再提交一次 → 应返回 SUCCESS 但不重复入账。

### 4.3 余额查询 (F3)

```powershell
$r = Invoke-RestMethod -Uri "http://localhost:8082/api/finance/streamers/S001/balance"
# 预期: code=SUCCESS
```

### 4.4 提成规则 (F4)

```powershell
$body = '{"streamerId":"S001","commissionRate":0.5,"effectiveFrom":"2026-06-01T00:00:00"}'
$r = Invoke-RestMethod -Uri "http://localhost:8082/api/finance/commission-rules" `
    -Method POST -ContentType "application/json" -Body $body
# 预期: code=SUCCESS, rate=0.5
```

### 4.5 提现操作 (F5)

```powershell
$body = '{"amount":999}'
$r = Invoke-RestMethod -Uri "http://localhost:8082/api/finance/streamers/S001/withdraw" `
    -Method POST -ContentType "application/json" -Body $body
# 预期: code=INSUFFICIENT_BALANCE (余额不足时)
```

> **注意**：提现请求体必须是合法 JSON，key 和 value 都要用双引号包裹！
> 错误写法：`{amount:999}` → 正确写法：`{"amount":999}`

### 4.6 观众画像 (F6)

```powershell
$r = Invoke-RestMethod -Uri "http://localhost:8083/api/analytics/viewers/V001/profile"
# 预期: code=SUCCESS 或 PROFILE_DEGRADED (降级模式)
```

### 4.7 TOP10查询 (F7)

```powershell
$r = Invoke-RestMethod -Uri "http://localhost:8083/api/analytics/streamers/S001/top-viewers"
# 预期: code=SUCCESS
```

### 4.8 多维度统计 (F8)

```powershell
# 支持两种参数格式：
# 格式1: ISO 时间
$startHour = "2026-06-24T18:00"
# 格式2: 纯小时数字 (0-23)，推荐用于日常使用
$r = Invoke-RestMethod -Uri "http://localhost:8083/api/analytics/hourly?startHour=0&endHour=23&streamerId=S001"
# 预期: code=SUCCESS
```

### 4.9 对账检查 (F9)

```powershell
$r = Invoke-RestMethod -Uri "http://localhost:8082/api/finance/reconciliation/check" -Method POST
# 预期: code=SUCCESS, matched=N, mismatched=0
```

### 4.10 500 QPS 压测 (NF5)

```powershell
$body = @{
    targetQps = 500
    durationSeconds = 3
    threadPoolSize = 512
    timeoutMs = 15000
    streamerIds = @("S001","S002")
    viewerIds = @("V001","V002")
} | ConvertTo-Json -Depth 3
$r = Invoke-RestMethod -Uri "http://localhost:8084/api/simulator/start" `
    -Method POST -ContentType "application/json" -Body $body
Write-Host "QPS=$($r.data.actualQps) rate=$([math]::Round($r.data.successRate*100))%"
# 预期: actualQps >= 400, successRate >= 90%
```

---

## 五、停止服务

### 5.1 正常停止

直接在各终端按 `Ctrl+C` 即可优雅关闭对应服务。

### 5.2 强制全部停止（当服务卡死时）

```powershell
# 杀掉所有 Java 进程（谨慎使用！会终止所有 Java 应用）
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
```

### 5.3 清理 Sentinel 锁文件（Viewer 重启失败时）

如果 Viewer 启动时报 `sentinel-record.log.* (Access denied)` 或类似锁文件错误：

```powershell
Remove-Item -Force "$env:USERPROFILE\logs\csp\sentinel-record.log.*" -ErrorAction SilentlyContinue
```

然后重新启动 Viewer 服务即可。

---

## 六、常见问题排查

### 6.1 MySQL 连接失败 (`Can't connect to MySQL server`)

**原因**：MySQL 服务未启动
**解决**：

```powershell
# 管理员 PowerShell
net start mysql80
```

### 6.2 Finance 接口返回 INTERNAL_ERROR (`CannotGetJdbcConnectionException`)

**原因**：高并发后 HikariCP 连接池耗尽，或 MySQL 未启动
**解决**：

1. 确认 MySQL 已启动（Step 1）
2. 重启 Finance 服务释放连接池
3. 如果频繁出现，检查 `application-local.yml` 中 `maximum-pool-size` 配置（建议 ≥50）

### 6.3 Viewer 启动失败 (`sentinel-record.log Access denied`)

**原因**：Sentinel 流控组件的日志锁文件被上一个进程持有
**解决**：

```powershell
# 1. 删除锁文件
Remove-Item -Force "$env:USERPROFILE\logs\csp\sentinel-record.log.*"
# 2. 如果还不行，杀掉残留 Java 进程
Get-Process java | Stop-Process -Force
Start-Sleep 2
# 3. 重新启动
java -Xms128m -Xmx256m -jar viewer-service/target/viewer-service-1.0.0-SNAPSHOT.jar
```

### 6.4 Simulator 启动后自动退出

**原因**：端口被占用或依赖的 Viewer 服务不可达
**解决**：

1. 确认 8084 端口未被占用
2. 确认 Viewer 服务（8081）正常运行且 health 返回 UP
3. 重新启动 Simulator

### 6.5 压测结果全为超时 (`timeoutCount = requestedCount`)

**原因**：Viewer 服务在高并发下崩溃或无响应
**解决**：

1. 检查 Viewer 终端是否有 OOM 或异常退出
2. 重启 Viewer 服务
3. 降低压测参数（如 `durationSeconds=2`, `threadPoolSize=256`）
4. 确保 Viewer 有足够堆内存（`-Xmx256m`）

### 6.6 F8 多维度统计返回 INVALID_STARTHOUR

**原因**：旧代码只支持 ISO 时间格式（如 `2026-06-24T18:00`），不支持纯数字小时
**解决**：使用最新构建的 analytics-service，现已支持两种格式：
- `startHour=18` （纯数字 0-23）
- `startHour=2026-06-24T18:00` （ISO 时间格式）

### 6.7 F9 对账检查返回 INTERNAL_ERROR (`Unknown column`)

**原因**：`t_reward_event` 表缺少 `commission_rate` / `commission_amount` / `withdrawable_amount` 三列
**解决**：执行 [Step 2](#step-2初始化数据库) 中的 ALTER TABLE 补列语句

### 6.8 提现接口返回 INTERNAL_ERROR 而非 INSUFFICIENT_BALANCE

**原因**：旧代码中 `IllegalArgumentException` 被全局异常处理器统一包装
**解决**：使用最新构建的 finance-service，已在 Controller 层增加专门处理

### 6.9 CORS 跨域错误 (`blocked by CORS policy`)

**原因**：前端仪表盘（localhost:8888）访问 Simulator API 时被浏览器拦截
**解决**：Simulator 已内置 CorsConfig，确保使用最新构建版本

### 6.10 服务启动顺序错误导致级联失败

**现象**：Viewer 启动后报错无法连接 Finance/Analytics；Simulator 启动后立即退出
**解决**：严格按 [Step 6](#step-6按严格顺序启动-4-个服务) 的顺序启动，每步等待前一个服务完全就绪

---

## 七、完整演示流程（答辩推荐顺序）

1. 说明四个服务架构和调用关系
2. 展示 Consul 服务注册页面
3. 配置提成规则 → 发两笔不同时间段打赏
4. 查询余额证明提成规则生效
5. 触发 analytics 重建 → 展示画像/Top10/统计
6. 重复提交同一 rewardNo 展示幂等
7. 执行 500 QPS 压测展示性能指标
