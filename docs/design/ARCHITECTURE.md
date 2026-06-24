# 架构说明

## 总体结构
本项目采用 Maven 聚合模式，每个服务独立构建、独立启动、独立部署。

- `viewer-service`：打赏入口与观众侧查询
- `finance-service`：接收打赏入账并计算主播可领取余额
- `analytics-service`：经营分析与画像查询
- `simulator-service`：模拟高并发打赏流量
- `donation-common`：公共 DTO、异常、traceId、统一返回体

## 数据流（高性能异步架构）

### 优化后的数据流（支撑500+ QPS）
```
[Simulator] --HTTP--> [Viewer] --快速返回ACCEPTED--> [客户端]
                      |
                      +--异步线程池(settlementExecutor)
                             |
                             v
                    [Finance.settle()] --入队到内存队列(BlockingQueue)
                             |
                             v
                   [SettlementBatchProcessor] (后台定时线程)
                             |
              +--------------+--------------+
              |              |              |
         攒够50条       超过80ms        队列满时
              |              |              |
              v              v              v
        单次事务批量落库  强制刷新      返回QUEUE_FULL
              |
    +---------+---------+
    |                   |
 批量INSERT明细     聚合UPDATE余额
    (t_reward_event)   (t_streamer_balance)
              |
              v
          [MySQL] (写入TPS从500降至~10)
```

### 原始数据流（同步阻塞，~130 QPS上限）
```
[Simulator] --> [Viewer] --> [Finance] --> [MySQL]
                  |            |             |
              createTask()  INSERT+UPDATE  磁盘刷盘
              (写任务表)    (乐观锁)       (~5ms)
                  |            |
              同步等待~960ms   |
                  |            |
                  +------------+
                       |
                 返回结果(~1s)
```

### 关键性能优化点
1. **Viewer零DB写入**：`reward()` 方法不再调用 `createTask()` 写数据库，通过 `processDirect()` 直连Finance
2. **Finance内存攒批**：请求进入 `BlockingQueue`（容量10000），后台线程每50条或80ms批量落库
3. **连接池扩容**：finance: 20→100, viewer: 20→50
4. **MySQL参数调优**：`innodb_flush_log_at_trx_commit=2`, `sync_binlog=2`
5. **索引瘦身**：移除 `idx_viewer_time` 冗余索引，减少33%索引维护开销

### 详细优化报告
完整的性能优化方案、压测结果和技术细节请参见 [PERFORMANCE_OPTIMIZATION.md](./PERFORMANCE_OPTIMIZATION.md)

## 设计原则
- 重要事件先落库，再异步扩散（高并发场景下改为"先入队后批量落库"）
- 打赏接口保持幂等，避免重复入账（通过 `reward_no` 唯一索引保证）
- 跨服务调用设置超时和熔断（Sentinel 流控与降级）
- 所有 HTTP 请求都携带并记录 `traceId`
- 各服务支持多节点部署和水平扩展
- **新增原则：写入路径极致异步化，读取路径保持一致性**
