# simulator-service 压测使用说明

模拟服务端口 **8084**，通过 Feign 调用 viewer-service 的 `POST /api/viewers/reward` 接口，用于高并发打赏压测。

## 环境要求

- JDK 17 + Maven 3.9.x
- Spring Boot 3.4.2 / Spring Cloud 2024.0.1
- Consul（`127.0.0.1:8500`）
- 联调时需先启动：finance-service(8082) → analytics-service(8083) → viewer-service(8081) → simulator-service(8084)

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/simulator/templates/default` | 获取默认压测模板 |
| POST | `/api/simulator/start` | 启动压测（同步阻塞，直到结束） |
| GET | `/api/simulator/results/latest` | 最近一次压测结果（含曲线数据） |
| GET | `/api/simulator/report/latest` | 最近一次 Markdown 报告 |
| GET | `/actuator/health` | 健康检查 |
| GET | `/report.html` | 可视化曲线页面 |

## 默认模板

```bash
curl http://localhost:8084/api/simulator/templates/default
```

默认参数：100 主播、300000 观众、500 QPS、10 秒、FIXED 模式。

## 500 QPS 推荐配置

```bash
curl -X POST http://localhost:8084/api/simulator/start \
  -H "Content-Type: application/json" \
  -H "traceId: load-test-001" \
  -d '{
    "streamerCount": 100,
    "viewerCount": 300000,
    "qps": 500,
    "durationSeconds": 10,
    "mode": "FIXED",
    "failureRate": 0,
    "timeoutRate": 0
  }'
```

### JVM 建议

```bash
java -Xms512m -Xmx1g -Dfile.encoding=UTF-8 -jar simulator-service.jar
```

### 验收标准

- **发压侧**：响应中 `actualQps >= 480` 即表示 simulator 单节点发压能力达标
- **全链路**：若 `blockedCount > 0`，说明 viewer-service Sentinel 限流（默认约 200 QPS）触发，需协调成员 A 临时调高限流后再验全链路成功率

## 压测模式

| mode | 说明 |
|------|------|
| `FIXED` | 恒定 QPS 运行 `durationSeconds` 秒 |
| `STEP` | 从 `stepQps` 起步，每 `stepDurationSeconds` 秒增加 `stepQps`，直到达到目标 `qps` |
| `SUSTAINED` | 长时压测，未指定 `durationSeconds` 时默认 60 秒 |

阶梯加压示例：

```json
{
  "qps": 500,
  "durationSeconds": 20,
  "mode": "STEP",
  "stepQps": 100,
  "stepDurationSeconds": 5
}
```

## 异常场景模拟

```json
{
  "qps": 50,
  "durationSeconds": 5,
  "failureRate": 0.1,
  "timeoutRate": 0.05
}
```

- `failureRate`：本地模拟失败，不调用下游
- `timeoutRate`：使用极短 Feign 读超时触发超时

## traceId

- 压测入口请求头 `traceId` 作为本次 run 的父 traceId
- 每个模拟请求生成子 traceId：`sim-{runId}-{序号}`，写入日志并通过 Feign 传给 viewer-service

## 配置项（application-local.yml）

```yaml
simulator:
  default-qps: 500
  default-duration-seconds: 10
  default-streamer-count: 100
  default-viewer-count: 300000
  thread-pool-size: 128        # 并发线程数，500 QPS 建议 >= 128
  max-failure-samples: 50
```

## 联调协调

| 现象 | 处理方式 |
|------|----------|
| `blockedCount` 偏高 | 请成员 A 调高 viewer-service Sentinel `viewerReward` 限流 |
| 成功率低但无限流 | 请成员 B 检查 finance-service 数据库连接池 |
| 超时可忽略 | 检查 viewer/finance 响应时间 |

## 可视化

压测完成后访问：http://localhost:8084/report.html
