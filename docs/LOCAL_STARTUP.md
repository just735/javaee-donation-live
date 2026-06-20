# 本地启动配置指南

本文档说明如何在本地环境中完整启动 `javaee-donation-live` 项目，包括环境变量配置、数据源修正和启动顺序。

---

## 一、环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 17 | 编译运行 |
| Maven | 3.9+ | 项目构建 |
| MySQL | 8.0 | 数据存储（需提前安装并启动） |
| Redis | 7.x | 缓存（TopViewer 等） |
| Consul | 1.18+ | 服务注册发现（默认 `127.0.0.1:8500`） |

---

## 二、初始化数据库

在 MySQL 中执行建库建表脚本：

```bash
mysql -u root -p < sql/schema.sql
```

脚本会自动创建数据库 `javaee_donation_live` 及以下 6 张表：

- `t_reward_event` — 打赏明细
- `t_streamer_commission_rule` — 主播提成规则
- `t_streamer_balance` — 主播余额
- `t_viewer_profile` — 观众画像
- `t_reward_hourly_stat` — 小时维度统计
- `t_streamer_viewer_summary` — 主播-观众汇总

---

## 三、数据源配置（重要）

项目使用**环境变量**引用数据库连接信息，不在 YAML 中写死明文密码。

### 3.1 配置文件位置

以下两个服务需要数据库连接：

- `analytics-service/src/main/resources/application-local.yml`
- `finance-service/src/main/resources/application-local.yml`

### 3.2 数据源配置格式

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST}/javaee_donation_live?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

### 3.3 注意事项

> **`characterEncoding` 必须使用 `UTF-8`，不能写 `utf8mb4`。**
>
> `utf8mb4` 不是 Java 合法字符集名（`java.nio.charset.StandardCharsets` 中不存在），会导致启动时报 `UnsupportedEncodingException: utf8mb4` 异常。
>
> MySQL 的 utf8mb4 字符集支持通过 JDBC URL 的 `characterEncoding=UTF-8` 参数正确映射，无需在 URL 中显式指定 charset 名称。

---

## 四、设置环境变量

### 4.1 IDEA 运行配置

打开各服务的运行配置（Run Configuration）→ 右上角 **Modify options (M)** → 勾选 **Environment variables**，填入：

```
DB_HOST=localhost:3306;DB_USERNAME=root;DB_PASSWORD=你的密码
```

> 使用英文分号分隔，不能有换行和多余空格。

需要配置环境变量的服务：
- `finance-service` (FinanceServiceApplication)
- `analytics-service` (AnalyticsServiceApplication)

`viewer-service` 和 `simulator-service` 不直接连数据库，不需要 DB 环境变量。

### 4.2 命令行启动

```powershell
# PowerShell 方式
$env:DB_HOST='localhost:3306'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的密码'
java -jar finance-service/target/finance-service-1.0.0-SNAPSHOT.jar

# 或一行方式
$env:DB_HOST='localhost:3306'; $env:DB_USERNAME='root'; $env:DB_PASSWORD='你的密码'; java -jar xxx.jar
```

---

## 五、构建与启动

### 5.1 构建项目

```bash
cd javaee-donation-live
mvn clean package -DskipTests
```

成功后会在各模块 `target/` 下生成 `.jar` 文件。

### 5.2 按顺序启动服务

| 启动顺序 | 服务 | 端口 | 是否需要 DB 环境变量 | 说明 |
|----------|------|------|---------------------|------|
| 1 | finance-service | 8082 | 是 | 财务服务，被 viewer 调用，先启动 |
| 2 | analytics-service | 8083 | 是 | 分析服务，被 viewer 调用 |
| 3 | viewer-service | 8081 | 否 | 核心入口服务 |
| 4 | simulator-service | 8084 | 否 | 压测模拟服务（可选） |

### 5.3 验证启动成功

访问各服务的 Health Check 接口，应返回 `status=200`：

```bash
curl http://localhost:8081/actuator/health   # viewer-service
curl http://localhost:8082/actuator/health   # finance-service
curl http://localhost:8083/actuator/health   # analytics-service
curl http://localhost:8084/actuator/health   # simulator-service
```

如果 health 返回 `503`，通常是 MySQL 未启动或环境变量未生效。

---

## 六、接口测试

### 6.1 打赏接口（POST）

```powershell
$body = @{
    rewardNo = "REQ-001"
    viewerId = "viewer-001"
    viewerName = "张三"
    viewerGender = "MALE"
    streamerId = "streamer-001"
    streamerName = "主播A"
    rewardAmount = 100.00
    rewardTime = "2026-06-20T12:30:00"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/viewers/reward" `
  -Method Post -ContentType "application/json" -Body $body
```

预期返回：`settleStatus=SETTLED`，包含 commissionRate、withdrawableAmount 等字段。

### 6.2 其他 GET 接口（可直接浏览器访问）

| 接口 | 说明 |
|------|------|
| `GET /api/viewers/{viewerId}/profile` | 观众画像 |
| `GET /api/viewers/streamers/{streamerId}/top-viewers` | TOP10 观众 |
| `GET /api/finance/streamers/{streamerId}/balance` | 主播余额 |
| `GET /api/analytics/jobs/rebuild` | 重建分析数据（POST） |
| `GET /api/simulator/templates/default` | 压测模板 |

---

## 七、常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| Health check 503 | MySQL 未启动或连接失败 | 确认 MySQL 运行中，检查环境变量是否生效 |
| `UnsupportedEncodingException: utf8mb4` | JDBC URL 中 characterEncoding 写了非法值 | 改为 `UTF-8` |
| `Request method 'GET' is not supported` | 浏览器地址栏直接访问 POST 接口 | 使用 Postman/curl 发送 POST 请求 |
| `No static resource .` | URL 路径不匹配任何 Controller | 检查路径前缀：viewer 用 `/api/viewers/...`，finance 用 `/api/finance/...` |
| Feign 调用超时/降级 | 下游服务未启动或 Consul 中未注册 | 按顺序启动 finance → analytics → viewer |
