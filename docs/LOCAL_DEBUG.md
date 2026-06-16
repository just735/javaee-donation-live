# 本地启动与联调说明

## 启动顺序
1. 启动 `finance-service`
2. 启动 `analytics-service`
3. 启动 `viewer-service`
4. 启动 `simulator-service`

## 本地联调链路
- `simulator-service` 调 `viewer-service`
- `viewer-service` 调 `finance-service`
- `viewer-service` 调 `analytics-service`

## 推荐验证顺序
1. 调用 `POST /api/finance/commission-rules` 先配置主播提成
2. 调用 `GET /api/simulator/templates/default` 获取压测模板
3. 调用 `POST /api/simulator/start` 发起一轮模拟打赏
4. 调用 `GET /api/viewers/{viewerId}/profile` 查看画像
5. 调用 `GET /api/viewers/streamers/{streamerId}/top-viewers` 查看 Top10
6. 调用 `GET /api/finance/streamers/{streamerId}/balance` 查看主播余额
7. 调用 `GET /api/analytics/hourly` 查看小时统计

## 示例请求
### 配置提成规则
```json
{
  "streamerId": "streamer-1",
  "commissionRate": 0.25,
  "effectiveFrom": "2026-06-16T00:00:00"
}
```

### 启动模拟
```json
{
  "requestCount": 20,
  "viewerCount": 200,
  "streamerCount": 10,
  "qps": 500,
  "streamerId": "streamer-1"
}
```

## 说明
- 当前联调版主要用于初始化和分工开发，不是最终生产实现
- 财务与经营分析当前使用内存态占位实现，后续由各成员替换成数据库版
- 统一通过 `traceId` 请求头串联日志
