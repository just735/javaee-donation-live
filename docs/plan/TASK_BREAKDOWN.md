# 五人任务细化清单

## 成员 A：观众服务

### 接口清单
- `POST /api/viewers/reward`：观众发起打赏
- `GET /api/viewers/{viewerId}/profile`：查询观众画像
- `GET /api/viewers/streamers/{streamerId}/top-viewers`：查询主播 Top10 观众

### 类清单
- `ViewerRewardController`
- `ViewerRewardService`
- `FinanceClient`
- `AnalyticsClient`
- `ViewerFallbackService`
- `ViewerTraceFilter`
- `RewardRequest`
- `ViewerProfileResponse`
- `TopViewerResponse`

### 数据表对应
- 主查：`t_streamer_viewer_summary`
- 调财务入账：`t_reward_event`
- 调经营分析画像：`t_viewer_profile`

### 需要完成的事
- 先定义接口入参和返回结构
- 再实现调用财务服务和经营分析服务的 Service 主流程
- 再补超时、熔断、降级
- 最后联调 Top10 和画像查询

## 成员 B：财务服务

### 接口清单
- `POST /api/finance/rewards/settle`：接收并入账打赏数据
- `POST /api/finance/commission-rules`：设置主播提成规则
- `GET /api/finance/streamers/{streamerId}/balance`：查询主播可领取余额

### 类清单
- `FinanceController`
- `FinanceSettlementService`
- `CommissionRuleService`
- `StreamerBalanceService`
- `RewardEventMapper`
- `CommissionRuleMapper`
- `StreamerBalanceMapper`
- `RewardEventEntity`
- `CommissionRuleEntity`
- `StreamerBalanceEntity`

### 数据表对应
- `t_reward_event`
- `t_streamer_commission_rule`
- `t_streamer_balance`
- `t_streamer_viewer_summary`

### 需要完成的事
- 先把入账接口做成幂等
- 再做提成规则配置
- 再做主播余额查询
- 最后补事务、一致性与汇总逻辑

## 成员 C：经营分析服务

### 接口清单
- `GET /api/analytics/viewers/{viewerId}/profile`：查询观众画像
- `GET /api/analytics/hourly`：按小时、主播、性别查询汇总数据
- `POST /api/analytics/jobs/rebuild`：手动触发统计重算

### 类清单
- `AnalyticsController`
- `ViewerProfileService`
- `HourlyStatsService`
- `AnalyticsRebuildJobService`
- `ViewerProfileMapper`
- `RewardHourlyStatMapper`
- `StreamerViewerSummaryMapper`
- `ViewerProfileEntity`
- `RewardHourlyStatEntity`
- `StreamerViewerSummaryEntity`

### 数据表对应
- `t_viewer_profile`
- `t_reward_hourly_stat`
- `t_streamer_viewer_summary`
- 读源数据：`t_reward_event`

### 需要完成的事
- 先做画像查询返回
- 再做画像计算逻辑
- 再做多维度统计查询
- 最后补定时任务和重算入口

## 成员 D：模拟服务

### 接口清单
- `POST /api/simulator/start`：启动一轮模拟压测
- `GET /api/simulator/templates/default`：返回默认压测模板

### 类清单
- `SimulatorController`
- `SimulatorService`
- `ViewerRewardClient`
- `SimulationRequest`
- `SimulationResult`
- `ViewerRewardMockDataFactory`

### 数据表对应
- 不直接操作数据库
- 通过观众服务间接写入 `t_reward_event` 等业务表

### 需要完成的事
- 先完成模拟参数定义
- 再完成调用观众服务的请求逻辑
- 再支持失败统计与结果汇总
- 最后补压测说明和异常场景模拟

## 成员 E：公共能力与全局支撑

### 接口清单
- 不固定暴露业务接口，负责公共模块和联调支撑
- 如有需要可补 `GET /actuator/health`、链路检查、联调辅助接口

### 类清单
- `ApiResponse`
- `TraceContext`
- `GlobalExceptionHandler`
- `TraceIdFilter` 或各服务本地 Filter
- 公共 DTO、错误码枚举、通用常量

### 数据表对应
- 维护 `sql/schema.sql` 中全部表结构
- 重点关注表之间字段一致性

### 需要完成的事
- 统一公共 DTO、traceId、异常处理
- 统一配置、日志、端口、依赖版本
- 组织联调与问题记录
- 维护最终提交文档

## 推荐联调顺序
1. 成员 B 先提供财务入账接口可用版本
2. 成员 A 接通观众服务到财务服务
3. 成员 C 提供画像查询和统计查询可用版本
4. 成员 A 接通观众服务到经营分析服务
5. 成员 D 接入模拟服务发压
6. 成员 E 统一检查日志、配置、接口返回和文档
