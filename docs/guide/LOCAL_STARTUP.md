# 项目整体启动与功能演示教程

本文档面向第一次接手 `javaee-donation-live` 的同学，目标是让你从 0 开始把整个项目启动起来，并完整演示以下能力：

- 配置主播提成规则
- 观众发起打赏
- 财务入账与余额查询
- 观众画像查询
- 主播 Top10 打赏观众查询
- 小时维度经营分析查询
- simulator 压测与 500 QPS 验证

如果你只是想快速联调，也建议至少完整跑一遍本文档，因为它会顺便帮你验证数据库、Consul、服务依赖和接口路径是否都正确。

---

## 1. 项目结构概览

项目是一个 Maven 多模块工程，核心模块如下：

| 模块 | 端口 | 作用 |
|------|------|------|
| `viewer-service` | `8081` | 打赏入口、画像查询代理、Top10 查询代理 |
| `finance-service` | `8082` | 打赏入账、提成规则、余额查询、提现、对账 |
| `analytics-service` | `8083` | 画像、Top10、小时统计、聚合重建 |
| `simulator-service` | `8084` | 打赏压测与压测报告 |
| `donation-common` | - | 公共 DTO、响应体、traceId、异常等 |

服务调用关系：

1. `simulator-service` 调 `viewer-service`
2. `viewer-service` 异步调 `finance-service`
3. `viewer-service` 查画像 / Top10 时调 `analytics-service`
4. `analytics-service` 根据财务侧已结算数据重建画像和统计结果

---

## 2. 环境准备

### 2.1 必备软件

| 软件 | 建议版本 | 用途 |
|------|----------|------|
| JDK | 17 | 编译与运行 |
| Maven | 3.9+ | 构建项目 |
| MySQL | 8.0 | 本地数据库 |
| Consul | 1.18+ | 服务注册发现 |

### 2.2 当前本地启动是否依赖 Redis？

当前仓库代码里，本地启动 **不依赖 Redis**。你可以只准备 MySQL 和 Consul，就能把四个服务启动起来并完成本文档中的全部演示。

### 2.3 推荐机器资源

- 内存至少 8GB
- 若 4 个服务同时启动，建议每个服务限制 JVM 堆内存，避免机器内存被打满

---

## 3. 初始化数据库

在项目根目录执行：

```bash
mysql -u root -p < sql/schema.sql
```

该脚本会创建数据库 `javaee_donation_live`，并初始化以下 7 张表：

- `t_reward_event`：打赏明细
- `t_streamer_commission_rule`：主播提成规则
- `t_streamer_balance`：主播余额
- `t_reward_ingest_task`：viewer 侧持久化入账任务
- `t_viewer_profile`：观众画像
- `t_reward_hourly_stat`：小时维度统计
- `t_streamer_viewer_summary`：主播-观众聚合统计

建议先登录 MySQL 简单确认：

```sql
USE javaee_donation_live;
SHOW TABLES;
```

如果这一步没有建表成功，后面所有功能都会失败。

---

## 4. 配置环境变量

本项目本地 `local` profile 使用环境变量读取数据库账号密码，不在 yml 中写死明文。

### 4.1 需要配置的变量

```bash
DB_HOST=localhost:3306
DB_USERNAME=root
DB_PASSWORD=你的MySQL密码
```

### 4.2 哪些服务需要数据库环境变量

现在以下 3 个服务都需要数据库配置：

- `viewer-service`
- `finance-service`
- `analytics-service`

`simulator-service` 不直接访问数据库，不需要配置这些变量。

### 4.3 Linux / macOS 终端设置示例

```bash
export DB_HOST=localhost:3306
export DB_USERNAME=root
export DB_PASSWORD=你的MySQL密码
```

### 4.4 PowerShell 设置示例

```powershell
$env:DB_HOST='localhost:3306'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的MySQL密码'
```

### 4.5 IDEA 运行配置设置示例

在 Run Configuration 中为 `viewer-service`、`finance-service`、`analytics-service` 分别配置：

```text
DB_HOST=localhost:3306;DB_USERNAME=root;DB_PASSWORD=你的MySQL密码
```

注意：

- 使用英文分号分隔
- 不要换行
- 这 3 个服务都要配，不是只有 finance / analytics 才需要

---

## 5. 启动 Consul

本地默认地址是 `127.0.0.1:8500`。

如果你已安装 Consul，可以直接开发模式启动：

```bash
consul agent -dev
```

启动后访问：

- `http://127.0.0.1:8500`

确认 Consul UI 能打开。

说明：

- `simulator-service` 本地已显式关闭 Consul
- 但 `viewer-service`、`finance-service`、`analytics-service` 仍启用了 `@EnableDiscoveryClient`
- 因此整个项目按本地默认方式启动时，**Consul 仍然需要先起来**

---

## 6. 构建项目

在仓库根目录执行：

```bash
mvn clean package -DskipTests
```

构建完成后，各模块 `target/` 目录下会出现可运行的 jar。

如果你只想先确认 simulator 相关模块是否能通过测试，也可以使用：

```bash
mvn -pl simulator-service -am test -Dsurefire.failIfNoSpecifiedTests=false
```

---

## 7. 启动顺序

推荐严格按下面顺序启动：

1. `finance-service`
2. `analytics-service`
3. `viewer-service`
4. `simulator-service`

原因：

- `viewer-service` 依赖 `finance-service` 与 `analytics-service`
- `simulator-service` 依赖 `viewer-service`

### 7.1 推荐 JVM 参数

4 个服务同时跑时，建议每个服务都带上：

```bash
-Xms128m -Xmx256m
```

如果你要专门做高 QPS 压测，可把 `simulator-service` 单独提高到：

```bash
-Xms512m -Xmx1g
```

---

## 8. 各服务启动命令

以下命令都在项目根目录执行。

### 8.1 启动 finance-service

```bash
java -Xms128m -Xmx256m -jar finance-service/target/finance-service-1.0.0-SNAPSHOT.jar
```

启动成功后监听端口：`8082`

### 8.2 启动 analytics-service

```bash
java -Xms128m -Xmx256m -jar analytics-service/target/analytics-service-1.0.0-SNAPSHOT.jar
```

启动成功后监听端口：`8083`

### 8.3 启动 viewer-service

```bash
java -Xms128m -Xmx256m -jar viewer-service/target/viewer-service-1.0.0-SNAPSHOT.jar
```

启动成功后监听端口：`8081`

### 8.4 启动 simulator-service

```bash
java -Xms128m -Xmx256m -jar simulator-service/target/simulator-service-1.0.0-SNAPSHOT.jar
```

启动成功后监听端口：`8084`

---

## 9. 健康检查

4 个服务启动完后，依次执行：

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

期望都返回：

```json
{"status":"UP"}
```

如果某个服务不是 `UP`，优先检查：

- MySQL 是否启动
- `DB_HOST / DB_USERNAME / DB_PASSWORD` 是否生效
- Consul 是否启动
- 前置服务是否已按顺序启动

---

## 10. 功能演示总流程

推荐按下面顺序演示，这样每一步都能为下一步提供数据：

1. 先给主播配置提成规则
2. 再发起几笔打赏请求
3. 查询主播余额，确认财务入账
4. 触发 analytics 重建
5. 查询观众画像
6. 查询主播 Top10 观众
7. 查询小时维度统计
8. 最后用 simulator 跑压测

下面每个步骤都附了可直接复制的命令。

---

## 11. 第一步：配置主播提成规则

接口：`POST /api/finance/commission-rules`

示例：给 `streamer-a` 配置 20% 提成规则，自 `2026-06-22 09:00:00` 生效。

```bash
curl -X POST http://localhost:8082/api/finance/commission-rules \
  -H "Content-Type: application/json" \
  -H "traceId: demo-rule-001" \
  -d '{
    "streamerId": "streamer-a",
    "commissionRate": 0.2000,
    "effectiveFrom": "2026-06-22T09:00:00"
  }'
```

如果你想演示“提成规则会变，但历史订单按旧规则结算”，可以再补一条新规则：

```bash
curl -X POST http://localhost:8082/api/finance/commission-rules \
  -H "Content-Type: application/json" \
  -H "traceId: demo-rule-002" \
  -d '{
    "streamerId": "streamer-a",
    "commissionRate": 0.3000,
    "effectiveFrom": "2026-06-22T10:00:00"
  }'
```

说明：

- 后配的新规则不会覆盖历史打赏的结算事实
- 老订单按老规则，新订单按新规则

---

## 12. 第二步：发起打赏请求

接口：`POST /api/viewers/reward`

### 12.1 示例一：旧规则时间段打赏

```bash
curl -X POST http://localhost:8081/api/viewers/reward \
  -H "Content-Type: application/json" \
  -H "traceId: demo-reward-001" \
  -d '{
    "rewardNo": "demo-reward-old-001",
    "viewerId": "viewer-1",
    "viewerName": "观众1",
    "viewerGender": "MALE",
    "streamerId": "streamer-a",
    "streamerName": "主播A",
    "rewardAmount": 100.00,
    "rewardTime": "2026-06-22T09:30:00"
  }'
```

### 12.2 示例二：新规则时间段打赏

```bash
curl -X POST http://localhost:8081/api/viewers/reward \
  -H "Content-Type: application/json" \
  -H "traceId: demo-reward-002" \
  -d '{
    "rewardNo": "demo-reward-new-001",
    "viewerId": "viewer-2",
    "viewerName": "观众2",
    "viewerGender": "FEMALE",
    "streamerId": "streamer-a",
    "streamerName": "主播A",
    "rewardAmount": 200.00,
    "rewardTime": "2026-06-22T10:30:00"
  }'
```

### 12.3 预期现象

`viewer-service` 一般会立即返回：

- `settleStatus = ACCEPTED`

这表示：

- 请求已经被 viewer 接收
- viewer 已持久化入账任务
- 后续由异步任务去调用 finance 完成最终结算

这不是同步 `SETTLED` 设计，而是当前项目为了满足“高吞吐 + 不丢账 + 可恢复”采用的异步受理模式。

---

## 13. 第三步：查询主播余额

接口：`GET /api/finance/streamers/{streamerId}/balance`

```bash
curl http://localhost:8082/api/finance/streamers/streamer-a/balance \
  -H "traceId: demo-balance-001"
```

如果前面两笔打赏都已异步入账成功，结果中应能看到：

- 总打赏金额增加
- 总提成金额增加
- 可提现余额增加

按上面的演示数据：

- 第一笔 100 元按 20% 提成，主播到账 80
- 第二笔 200 元按 30% 提成，主播到账 140
- 两笔总打赏 300
- 总提成 80
- 可提现余额 220

这一步是演示“财务正确结算”和“历史规则冻结”的关键证据。

---

## 14. 第四步：触发 analytics 重建

接口：`POST /api/analytics/jobs/rebuild`

```bash
curl -X POST http://localhost:8083/api/analytics/jobs/rebuild \
  -H "traceId: demo-rebuild-001"
```

这个接口会根据财务侧已经 `SETTLED` 的打赏明细，重建：

- `t_viewer_profile`
- `t_reward_hourly_stat`
- `t_streamer_viewer_summary`

如果你刚打完赏就立即查画像 / Top10 / 小时统计，可能还看不到最新结果；先调用一次重建接口最稳妥。

---

## 15. 第五步：查询观众画像

接口：`GET /api/viewers/{viewerId}/profile`

```bash
curl http://localhost:8081/api/viewers/viewer-1/profile \
  -H "traceId: demo-profile-001"
```

```bash
curl http://localhost:8081/api/viewers/viewer-2/profile \
  -H "traceId: demo-profile-002"
```

结果中主要关注：

- `profileTag`
- `profileScore`

常见标签：

- `HIGH`
- `MEDIUM`
- `LOW`
- 降级场景下可能出现 `PENDING`

说明：

- 该接口由 `viewer-service` 对外提供
- 实际数据来自 `analytics-service`
- 当前实现中画像查询带 2 秒超时，超时后会友好降级

---

## 16. 第六步：查询主播 Top10 打赏观众

接口：`GET /api/viewers/streamers/{streamerId}/top-viewers`

```bash
curl "http://localhost:8081/api/viewers/streamers/streamer-a/top-viewers?limit=10" \
  -H "traceId: demo-top-001"
```

结果中关注：

- `viewerId`
- `viewerName`
- `totalRewardAmount`
- `rewardCount`

按前面的示例数据，`viewer-2` 应排在 `viewer-1` 前面，因为它给 `streamer-a` 的累计打赏更高。

---

## 17. 第七步：查询小时维度分析数据

接口：`GET /api/analytics/hourly`

```bash
curl "http://localhost:8083/api/analytics/hourly?startHour=2026-06-22T09:00:00&endHour=2026-06-22T10:00:00&gender=MALE&streamerId=streamer-a" \
  -H "traceId: demo-hourly-001"
```

结果中关注：

- `statHour`
- `streamerId`
- `viewerGender`
- `rewardAmount`
- `rewardCount`

这一步对应课程设计中的“按小时 / 主播 / 性别进行统计分析”。

---

## 18. 第八步：验证幂等

用完全相同的 `rewardNo` 再提交一次：

```bash
curl -X POST http://localhost:8081/api/viewers/reward \
  -H "Content-Type: application/json" \
  -H "traceId: demo-dup-001" \
  -d '{
    "rewardNo": "demo-reward-old-001",
    "viewerId": "viewer-1",
    "viewerName": "观众1",
    "viewerGender": "MALE",
    "streamerId": "streamer-a",
    "streamerName": "主播A",
    "rewardAmount": 100.00,
    "rewardTime": "2026-06-22T09:30:00"
  }'
```

预期现象：

- 返回 `settleStatus = DUPLICATE`
- 不会重复记账
- 主播余额不应再次增加

这一步用于证明“不能重复”的要求。

---

## 19. 第九步：运行 simulator 做压测

### 19.1 查看默认模板

```bash
curl http://localhost:8084/api/simulator/templates/default
```

当前默认模板的核心参数是：

- `streamerCount = 100`
- `viewerCount = 300000`
- `qps = 500`
- `durationSeconds = 10`
- `mode = FIXED`

### 19.2 发起一次 500 QPS 压测

```bash
curl -X POST http://localhost:8084/api/simulator/start \
  -H "Content-Type: application/json" \
  -H "traceId: demo-sim-500" \
  -d '{
    "streamerCount": 100,
    "viewerCount": 300000,
    "qps": 500,
    "durationSeconds": 3,
    "mode": "FIXED",
    "failureRate": 0.0,
    "timeoutRate": 0.0
  }'
```

### 19.3 结果怎么看

响应里重点关注：

- `requestedCount`
- `successCount`
- `acceptedCount`
- `failedCount`
- `timeoutCount`
- `blockedCount`
- `actualQps`

当前项目已经验证通过的目标结果是：

- `actualQps >= 500`
- `failedCount = 0`
- `timeoutCount = 0`
- `blockedCount = 0`

如果你想查看最近一次压测结果，也可以调用：

```bash
curl http://localhost:8084/api/simulator/results/latest
```

或者看报告：

```bash
curl http://localhost:8084/api/simulator/report/latest
```

静态可视化页面：

- `http://localhost:8084/report.html`

---

## 20. 常见问题排查

### 20.1 `viewer-service` 启动失败，报数据库连接错误

原因通常是：

- 忘了给 `viewer-service` 配 `DB_HOST / DB_USERNAME / DB_PASSWORD`
- MySQL 没启动
- `javaee_donation_live` 数据库没初始化

### 20.2 health 接口不是 `UP`

优先检查：

1. MySQL 是否可连
2. Consul 是否可访问 `127.0.0.1:8500`
3. 相关前置服务是否已启动
4. 端口是否被占用

### 20.3 `UnsupportedEncodingException: utf8mb4`

JDBC URL 中 `characterEncoding` 必须写 `UTF-8`，不要写 `utf8mb4`。

### 20.4 打赏后立刻查不到画像 / Top10 / 小时统计

这是正常现象，因为：

- 打赏是异步入账
- analytics 结果来自聚合表

解决方式：

1. 先确认 finance 已入账
2. 再调用 `POST /api/analytics/jobs/rebuild`
3. 然后再查画像 / Top10 / 小时统计

### 20.5 simulator 压测达不到 500 QPS

先检查：

- 是否使用了最新代码构建出的 `simulator-service`
- `viewer.reward.qps-limit` 是否至少大于等于 500
- `simulator-service` 是否给了足够 JVM 内存
- 本机 CPU / 内存是否太紧张

当前仓库代码已经把单节点 `actualQps` 调整到了 `501.0`，如果你本地明显低于这个数，优先怀疑本机环境而不是业务逻辑。

---

## 21. 一次完整演示的最短命令清单

如果你已经完成环境准备，只想快速跑一遍最关键流程，可以按下面顺序：

1. 初始化数据库

```bash
mysql -u root -p < sql/schema.sql
```

2. 启动 Consul

```bash
consul agent -dev
```

3. 构建项目

```bash
mvn clean package -DskipTests
```

4. 启动四个服务

```bash
java -Xms128m -Xmx256m -jar finance-service/target/finance-service-1.0.0-SNAPSHOT.jar
java -Xms128m -Xmx256m -jar analytics-service/target/analytics-service-1.0.0-SNAPSHOT.jar
java -Xms128m -Xmx256m -jar viewer-service/target/viewer-service-1.0.0-SNAPSHOT.jar
java -Xms128m -Xmx256m -jar simulator-service/target/simulator-service-1.0.0-SNAPSHOT.jar
```

5. 配规则

```bash
curl -X POST http://localhost:8082/api/finance/commission-rules -H "Content-Type: application/json" -d '{"streamerId":"streamer-a","commissionRate":0.2,"effectiveFrom":"2026-06-22T09:00:00"}'
```

6. 发打赏

```bash
curl -X POST http://localhost:8081/api/viewers/reward -H "Content-Type: application/json" -d '{"rewardNo":"demo-1","viewerId":"viewer-1","viewerName":"观众1","viewerGender":"MALE","streamerId":"streamer-a","streamerName":"主播A","rewardAmount":100.00,"rewardTime":"2026-06-22T09:30:00"}'
```

7. 重建 analytics

```bash
curl -X POST http://localhost:8083/api/analytics/jobs/rebuild
```

8. 查余额、画像、Top10、小时统计

```bash
curl http://localhost:8082/api/finance/streamers/streamer-a/balance
curl http://localhost:8081/api/viewers/viewer-1/profile
curl "http://localhost:8081/api/viewers/streamers/streamer-a/top-viewers?limit=10"
curl "http://localhost:8083/api/analytics/hourly?startHour=2026-06-22T09:00:00&endHour=2026-06-22T10:00:00&gender=MALE&streamerId=streamer-a"
```

9. 跑一次 500 QPS 压测

```bash
curl -X POST http://localhost:8084/api/simulator/start -H "Content-Type: application/json" -d '{"streamerCount":100,"viewerCount":300000,"qps":500,"durationSeconds":3,"mode":"FIXED","failureRate":0.0,"timeoutRate":0.0}'
```

---

## 22. 建议答辩演示顺序

如果你是为了课程答辩，建议按这个顺序展示：

1. 说明四个服务与调用关系
2. 展示 Consul 中的服务注册
3. 配置主播提成规则
4. 发两笔不同时间段打赏
5. 展示余额结果，证明旧规则 / 新规则分别生效
6. 触发 analytics 重建
7. 展示画像、Top10、小时统计
8. 重复提交同一 `rewardNo`，展示幂等
9. 跑 simulator，展示单节点 `500+ QPS`

这样能把功能要求和非功能要求都覆盖到。
