# API 接口清单与截图取证指南

## 服务端口总览

| 服务名称 | 端口 | 基础路径 | 状态检查 |
|---------|------|---------|---------|
| **viewer-service** | 8081 | `/api/viewers` | http://localhost:8081/actuator/health |
| **finance-service** | 8082 | `/api/finance` | http://localhost:8082/actuator/health |
| **analytics-service** | 8083 | `/api/analytics` | http://localhost:8083/actuator/health |
| **simulator-service** | 8084 | `/api/simulator` | http://localhost:8084/actuator/health |

---

## 一、核心业务接口（截图取证重点）

### 1. 观众打赏接口 ⭐⭐⭐
**功能**：发起打赏请求，触发完整入账流程

```
POST http://localhost:8081/api/viewers/reward
Content-Type: application/json

{
    "rewardNo": "RW20260623001",
    "viewerId": "V001",
    "viewerName": "张三",
    "viewerGender": "MALE",
    "streamerId": "S001",
    "streamerName": "李四",
    "rewardAmount": 100.00,
    "rewardTime": "2026-06-23T22:00:00"
}
```

**成功响应示例**：
```json
{
    "code": "SUCCESS",
    "traceId": "abc123",
    "data": {
        "rewardNo": "RW20260623001",
        "settleStatus": "ACCEPTED",
        "streamerId": "S001",
        "rewardAmount": 100.00,
        "message": "打赏请求已接收，正在处理中"
    }
}
```

**截图要点**：
- ✅ HTTP状态码：200
- ✅ settleStatus: "ACCEPTED"（异步入账模式）
- ✅ 响应时间 < 50ms（快速返回）

---

### 2. 财务入账接口 ⭐⭐⭐
**功能**：打赏明细落库、计算提成、更新余额

```
POST http://localhost:8082/api/finance/rewards/settle
Content-Type: application/json

{
    "rewardNo": "RW20260623001",
    "viewerId": "V001",
    "streamerId": "S001",
    "rewardAmount": 100.00,
    "rewardTime": "2026-06-23T22:00:00"
}
```

**成功响应示例**：
```json
{
    "code": "SUCCESS",
    "traceId": "def456",
    "data": {
        "rewardNo": "RW20260623001",
        "settleStatus": "ACCEPTED",
        "streamerId": "S001",
        "rewardAmount": 100.00,
        "commissionRate": 0.2000,
        "commissionAmount": 20.00,
        "withdrawableAmount": 80.00
    }
}
```

**截图要点**：
- ✅ commissionRate: 提成比例（默认20%）
- ✅ commissionAmount: 提成金额 = rewardAmount × commissionRate
- ✅ withdrawableAmount: 可提现金额 = rewardAmount - commissionAmount

---

### 3. 查询主播余额 ⭐⭐
**功能**：查询主播累计收入和可提现余额

```
GET http://localhost:8082/api/finance/streamers/S001/balance
```

**响应示例**：
```json
{
    "code": "SUCCESS",
    "data": {
        "streamerId": "S001",
        "totalRewardAmount": 1500.00,
        "totalCommissionAmount": 300.00,
        "withdrawableAmount": 1200.00,
        "version": 15
    }
}
```

**截图要点**：
- ✅ totalRewardAmount: 累计打赏总额
- ✅ totalCommissionAmount: 累计提成
- ✅ withdrawableAmount: 可提现余额
- ✅ version: 乐观锁版本号

---

### 4. 主播提现接口 ⭐⭐
**功能**：主播提现，扣减可提现余额

```
POST http://localhost:8082/api/finance/streamers/S001/withdraw
Content-Type: application/json

{
    "amount": 500.00
}
```

**响应示例**：
```json
{
    "code": "SUCCESS",
    "data": {
        "streamerId": "S001",
        "withdrawAmount": 500.00,
        "beforeBalance": 1200.00,
        "afterBalance": 700.00,
        "status": "SUCCESS"
    }
}
```

**截图要点**：
- ✅ beforeBalance / afterBalance: 提现前后余额变化
- ✅ status: "SUCCESS"

---

### 5. 观众画像查询 ⭐⭐
**功能**：根据消费金额计算观众画像等级

```
GET http://localhost:8081/api/viewers/V001/profile
```

**响应示例**：
```json
{
    "code": "SUCCESS",
    "data": {
        "viewerId": "V001",
        "viewerName": "张三",
        "profileTag": "HIGH",
        "profileScore": 5000.0000
    }
}
```

**画像等级说明**：
- **HIGH**: 累计消费 ≥ 5000元（高价值用户）
- **MEDIUM**: 累计消费 1000-4999元（中等价值）
- **LOW**: 累计消费 < 1000元（低价值）

**截图要点**：
- ✅ profileTag: HIGH/MEDIUM/LOW
- ✅ profileScore: 累计消费金额

---

### 6. Top10 打赏观众 ⭐⭐
**功能**：查询某主播下打赏最多的观众排行

```
GET http://localhost:8081/api/viewers/streamers/S001/top-viewers?limit=10
```

**响应示例**：
```json
{
    "code": "SUCCESS",
    "data": [
        {
            "viewerId": "V002",
            "viewerName": "王五",
            "totalRewardAmount": 8000.00,
            "rewardCount": 45
        },
        {
            "viewerId": "V001",
            "viewerName": "张三",
            "totalRewardAmount": 5000.00,
            "rewardCount": 30
        },
        ...
    ]
}
```

**截图要点**：
- ✅ 按 totalRewardAmount 降序排列
- ✅ 包含 viewerName, rewardCount 字段

---

## 二、压测相关接口（性能验证重点）⭐⭐⭐

### 7. 启动压测 ⭐⭐⭐
**功能**：发起模拟打赏请求，支持配置QPS、持续时间等参数

```
POST http://localhost:8084/api/simulator/start
Content-Type: application/json

{
    "targetQps": 500,
    "durationSeconds": 5,
    "threadPoolSize": 1024,
    "timeoutMs": 10000,
    "streamerIds": ["S001", "S002"],
    "viewerIds": ["V001", "V002"]
}
```

**响应示例**（压测完成后返回）：
```json
{
    "code": "SUCCESS",
    "data": {
        "requestedCount": 2502,
        "successCount": 2502,
        "failedCount": 0,
        "timeoutCount": 0,
        "blockedCount": 0,
        "successRate": 1.0000,
        "actualQps": 500.4,
        "avgLatencyMs": 2899,
        "p95LatencyMs": 3745,
        "p99LatencyMs": 3905,
        "latencySeries": [
            {"epochSecond": 1782224190, "qps": 199.0, "avgLatencyMs": 1680, "errorCount": 0},
            {"epochSecond": 1782224191, "qps": 503.0, "avgLatencyMs": 2667, "errorCount": 0},
            ...
        ],
        "reportMarkdown": "# 压测报告..."
    }
}
```

**截图要点**：
- ✅ successRate: 1.0000 (100%)
- ✅ actualQps: 500+ (达到目标)
- ✅ avgLatencyMs < 5000ms (可接受范围)

---

### 8. 获取最新压测结果 ⭐⭐
**功能**：查看最近一次压测的详细数据

```
GET http://localhost:8084/api/simulator/results/latest
```

---

### 9. 获取压测报告 ⭐⭐
**功能**：获取 Markdown 格式的压测报告

```
GET http://localhost:8084/api/simulator/report/latest
```

---

### 10. 获取默认压测模板 ⭐
**功能**：获取推荐的压测参数模板

```
GET http://localhost:8084/api/simulator/templates/default
```

**响应示例**：
```json
{
    "code": "SUCCESS",
    "data": {
        "targetQps": 500,
        "durationSeconds": 5,
        "threadPoolSize": 1024,
        "timeoutMs": 10000
    }
}
```

---

## 三、管理接口（系统运维）

### 11. 新增提成规则
```
POST http://localhost:8082/api/finance/commission-rules
Content-Type: application/json

{
    "streamerId": "S001",
    "commissionRate": 0.2500,
    "effectiveFrom": "2026-07-01T00:00:00"
}
```

---

### 12. 手动触发余额预计算
```
POST http://localhost:8082/api/finance/reconciliation/precompute
```

---

### 13. 手动触发对账检查
```
POST http://localhost:8082/api/finance/reconciliation/check?autoCorrect=false
```

---

## 四、健康检查接口（服务状态监控）

### 所有服务的 Actuator 端点

```bash
# Viewer Service
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/health/readiness

# Finance Service
curl http://localhost:8082/actuator/health
curl http://localhost:8082/actuator/health/readiness

# Analytics Service
curl http://localhost:8083/actuator/health
curl http://localhost:8083/actuator/health/readiness

# Simulator Service
curl http://localhost:8084/actuator/health
curl http://localhost:8084/actuator/health/readiness
```

**预期响应**：
```json
{
    "status": "UP",
    "components": {
        "db": {"status": "UP"},
        "ping": {"status": "UP"}
    }
}
```

---

## 五、截图取证清单（按优先级排序）

### 必须截图（核心功能证明）

#### 第一组：基础功能验证
1. ✅ **打赏接口调用** - POST `/api/viewers/reward`
   - 截图位置：Postman / 浏览器开发者工具 Network 面板
   - 关键字段：settleStatus="ACCEPTED"

2. ✅ **财务入账确认** - POST `/api/finance/rewards/settle`
   - 关键字段：commissionRate, commissionAmount, withdrawableAmount

3. ✅ **余额查询** - GET `/api/finance/streamers/{id}/balance`
   - 关键字段：totalRewardAmount, withdrawableAmount

#### 第二组：高级功能验证
4. ✅ **观众画像** - GET `/api/viewers/{id}/profile`
   - 关键字段：profileTag (HIGH/MEDIUM/LOW)

5. ✅ **Top10排行** - GET `/api/viewers/streamers/{id}/top-viewers`
   - 关键字段：按 totalRewardAmount 降序排列

6. ✅ **提现操作** - POST `/api/finance/streamers/{id}/withdraw`
   - 关键字段：beforeBalance → afterBalance 变化

#### 第三组：性能压测证明（最重要！）
7. ✅ **压测启动请求** - POST `/api/simulator/start`
   - 参数：targetQps=500, durationSeconds=5

8. ✅ **压测结果响应**
   - 关键指标截图：
     ```
     ✓ 成功率: 100%
     ✓ 实际QPS: 500+
     ✓ 平均延迟: <5s
     ✓ P95延迟: <4s
     ```

9. ✅ **压测曲线图**（使用可视化页面）
   - 访问：http://localhost:8084/simulator-dashboard.html
   - 截图内容：实时QPS曲线、延迟分布图、成功率饼图

---

## 六、快速测试脚本

### PowerShell 一键测试所有接口

```powershell
# 1. 发起打赏
Invoke-RestMethod -Uri "http://localhost:8081/api/viewers/reward" `
  -Method POST -ContentType "application/json" `
  -Body '{"rewardNo":"TEST001","viewerId":"V001","streamerId":"S001","rewardAmount":100}'

# 2. 查询余额
Invoke-RestMethod -Uri "http://localhost:8082/api/finance/streamers/S001/balance"

# 3. 查询画像
Invoke-RestMethod -Uri "http://localhost:8081/api/viewers/V001/profile"

# 4. 查询Top10
Invoke-RestMethod -Uri "http://localhost:8081/api/viewers/streamers/S001/top-viewers?limit=5"

# 5. 启动压测（目标500 QPS，持续5秒）
$body = @{
    targetQps = 500
    durationSeconds = 5
    threadPoolSize = 1024
} | ConvertTo-Json
$r = Invoke-RestMethod -Uri "http://localhost:8084/api/simulator/start" `
  -Method POST -ContentType "application/json" -Body $body
Write-Host "成功率: $($r.data.successRate * 100)%"
Write-Host "实际QPS: $($r.data.actualQps)"
Write-Host "平均延迟: $($r.data.avgLatencyMs)ms"
```

---

## 七、常见问题排查

### 接口返回错误码对照表

| 错误码 | 含义 | 解决方案 |
|-------|------|---------|
| `DUPLICATE` | 重复打赏（rewardNo已存在） | 更换新的 rewardNo |
| `VALIDATION_ERROR` | 参数校验失败 | 检查必填字段 |
| `FINANCE_UNAVAILABLE` | 财务服务不可用 | 检查 finance-service 是否启动 |
| `PROFILE_DEGRADED` | 画像查询降级 | analytics-service 可能未启动 |
| `TOP_VIEWERS_DEGRADED` | Top10查询降级 | analytics-service 可能未启动 |
| `QUEUE_FULL` | 批量处理器队列满 | 降低QPS或增大队列容量 |

### 性能优化前后对比截图建议

**优化前（如有历史数据）**：
- QPS: ~1301
- 成功率: 0%（全部超时）
- 平均延迟: >10s

**优化后（当前版本）**：
- QPS: **500+**
- 成功率: **100%**
- 平均延迟: **~3s**

---

*本文档用于课程答辩时的接口演示和截图取证，请确保所有服务正常运行后进行测试。*
