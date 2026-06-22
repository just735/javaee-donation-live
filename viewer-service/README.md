# viewer-service 观众服务

端口：`8081`  
注册中心：Consul（`application-local.yml`）

## 一、接口契约

所有接口统一返回 `ApiResponse<T>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `code` | String | 业务码，如 `SUCCESS`、`PROFILE_DEGRADED` |
| `message` | String | 提示信息 |
| `traceId` | String | 链路 ID（请求头可传入 `traceId`） |
| `data` | T | 业务数据 |

---

### 1. POST /api/viewers/reward — 观众发起打赏

**入参** `RewardRequest`（`donation-common`）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `rewardNo` | String | 是 | 打赏单号，幂等键 |
| `viewerId` | String | 是 | 观众 ID |
| `viewerName` | String | 否 | 观众姓名 |
| `viewerGender` | String | 否 | 性别：`MALE` / `FEMALE` |
| `streamerId` | String | 是 | 主播 ID |
| `streamerName` | String | 否 | 主播姓名 |
| `rewardAmount` | BigDecimal | 是 | 打赏金额，须 > 0 |
| `rewardTime` | String | 否 | 打赏时间，如 `2026-06-17T14:00:00` |

**返回** `ViewerRewardResponse`（本模块 `dto`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `rewardNo` | String | 打赏单号 |
| `settleStatus` | String | 返回状态：通常立即返回 `ACCEPTED`；重复请求可返回 `DUPLICATE` |
| `streamerId` | String | 主播 ID |
| `rewardAmount` | BigDecimal | 打赏金额 |
| `commissionRate` | BigDecimal | 最终结算时使用的提成比例，`ACCEPTED` 阶段通常为空 |
| `commissionAmount` | BigDecimal | 最终结算提成金额，`ACCEPTED` 阶段通常为空 |
| `withdrawableAmount` | BigDecimal | 最终计入主播可提现金额，`ACCEPTED` 阶段通常为空 |
| `settledAt` | LocalDateTime | 最终入账时间，`ACCEPTED` 阶段通常为空 |
| `message` | String | 用户提示，如「打赏请求已接收，正在处理中」 |

**下游**：请求先持久化到 viewer 侧任务表，再由异步任务调用 `finance-service` `POST /api/finance/rewards/settle` 完成最终入账。

---

### 2. GET /api/viewers/{viewerId}/profile — 查询观众画像

**入参**：

| 参数 | 位置 | 必填 | 说明 |
|------|------|------|------|
| `viewerId` | 路径 | 是 | 观众 ID |

**返回** `ViewerProfileResponse`（`donation-common`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `viewerId` | String | 观众 ID |
| `viewerName` | String | 观众姓名 |
| `profileTag` | String | 画像标签：`HIGH`（前 20%）/ `MEDIUM`（20%-80%）/ `LOW`（后 20%）/ `PENDING`（计算中或降级） |
| `profileScore` | BigDecimal | 画像得分（可选） |

**降级**：超时或经营分析异常时 `code=PROFILE_DEGRADED`，`profileTag=PENDING`，`message` 为友好提示。

**下游**：`analytics-service` `GET /api/analytics/viewers/{viewerId}/profile`，**2 秒超时**。

---

### 3. GET /api/viewers/streamers/{streamerId}/top-viewers — 主播 TOP10 打赏观众

**入参**：

| 参数 | 位置 | 必填 | 说明 |
|------|------|------|------|
| `streamerId` | 路径 | 是 | 主播 ID |
| `limit` | Query | 否 | 返回条数，默认 10，最大 10 |

**返回** `List<TopViewerResponse>`（`donation-common`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `viewerId` | String | 观众 ID |
| `viewerName` | String | 观众姓名 |
| `totalRewardAmount` | BigDecimal | 对该主播累计打赏金额 |
| `rewardCount` | Long | 打赏次数 |

**降级**：经营分析异常时 `code=TOP_VIEWERS_DEGRADED`，返回空列表 + 友好 `message`。

**下游**：`analytics-service` 预汇总结果，不直连明细表。

---

## 二、熔断与限流（Sentinel）

### 实现方式

本服务通过 **Alibaba Sentinel**（`spring-cloud-starter-alibaba-sentinel`）对资源名做保护：

- `@SentinelResource` 标注在 `ViewerRewardService`、`FinanceGateway`、`AnalyticsGateway`
- 规则由 `SentinelRuleConfig` 在启动时加载

### 服务级保护 vs 单实例摘除

| 维度 | 本服务实现 | 说明 |
|------|------------|------|
| 保护粒度 | **服务级资源**（如 `analyticsProfile`） | 按调用统计做限流/熔断，不是对某个 IP/实例单独熔断 |
| 单实例故障 | **不**在 Sentinel 层摘除实例 | 依赖 Consul 注册发现 + LoadBalancer 选实例 |
| 多节点效果 | Consul + LoadBalancer + 失败重试/切换 | 某实例不可达时由负载均衡选其他节点 |

答辩时可表述：**Sentinel 负责本服务内的流量控制与熔断降级；实例级故障转移由注册中心与负载均衡配合完成。**

### 资源与规则

| 资源名 | 类型 | 规则摘要 |
|--------|------|----------|
| `viewerReward` | 流控 | 默认 QPS 800/s，可通过 `viewer.reward.qps-limit` 调整 |
| `analyticsProfile` | 熔断 | 异常比例 50%；慢调用 RT > 2s 且慢调用比例 50% |
| `analyticsTopViewers` | 熔断 | 异常比例 50%；慢调用 RT > 2s 且慢调用比例 50% |
| `financeSettle` | 熔断 | 异常比例 50% |

画像查询与 TOP10 查询都配合 `CompletableFuture.orTimeout(2s)` 与 Feign `readTimeout: 2000ms`，确保 2 秒内返回或降级。

---

## 三、加分项

| 功能 | 实现类 | 说明 |
|------|--------|------|
| TOP10 本地缓存 | `TopViewerCacheService` | 热点主播 30s 缓存 |
| 打赏限流 | Sentinel `viewerReward` | 默认 800 QPS，可配置调整 |
| 异步通知 | `RewardNotificationService` | 入账成功后异步打日志，不阻塞主接口 |

---

## 四、构建与测试

### 推荐方式（项目根目录）

依赖 `donation-common`，请在**仓库根目录**执行：

```bash
# 编译并运行 viewer-service 全部测试
mvn test -pl viewer-service -am

# 打包（跳过测试）
mvn clean package -DskipTests -pl viewer-service -am
```

`-am` 会同时构建依赖模块 `donation-common`，避免子模块单独测试找不到 common 依赖。

### 不推荐（易失败）

在 `viewer-service` 子目录直接执行 `mvn test` 时，若本地未安装 `donation-common`，会报依赖找不到。可先执行：

```bash
mvn -pl donation-common install
```

再进入子模块测试。

### 启动

```bash
mvn -pl viewer-service spring-boot:run
```

需先启动 Consul、finance-service(8082)、analytics-service(8083)。

---

## 五、请求示例

```bash
# 打赏
curl -X POST http://localhost:8081/api/viewers/reward \
  -H "Content-Type: application/json" \
  -H "traceId: demo-001" \
  -d '{"rewardNo":"r-001","viewerId":"v-1","viewerName":"观众1","viewerGender":"MALE","streamerId":"s-1","streamerName":"主播1","rewardAmount":10.00,"rewardTime":"2026-06-17T14:00:00"}'

# 画像
curl http://localhost:8081/api/viewers/v-1/profile -H "traceId: demo-002"

# TOP10
curl "http://localhost:8081/api/viewers/streamers/s-1/top-viewers?limit=10" -H "traceId: demo-003"
```
