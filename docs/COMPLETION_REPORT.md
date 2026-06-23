# JavaEE Donation Live 项目实现报告

## 1. 项目概述

JavaEE Donation Live 是一个面向直播打赏场景的多服务系统。项目围绕“观众打赏、主播分成、财务入账、经营分析、压测模拟”这一完整业务链路展开，采用 Maven 多模块工程组织代码，将不同业务能力拆分为独立服务，便于并行开发、独立部署和后续扩展。

系统主要解决以下问题：

- 观众向主播发起打赏请求。
- 平台根据主播分成规则计算主播实际入账金额。
- 财务服务维护主播余额、提现和对账能力。
- 分析服务基于已结算打赏数据生成观众画像、主播 Top10、小时维度统计。
- 模拟服务生成高并发请求，用于验证系统吞吐量和稳定性。
- 全服务通过 TraceId 串联请求日志，便于联调和故障排查。

## 2. 技术选型

| 技术 | 用途 |
|------|------|
| Java 17 | 项目目标编译版本 |
| Spring Boot 3.4.2 | 服务开发基础框架 |
| Spring Cloud 2024.0.1 | 服务发现、Feign 调用、负载均衡 |
| Spring Cloud OpenFeign | 服务间 HTTP 调用 |
| Spring Cloud LoadBalancer | 客户端负载均衡 |
| Spring Cloud Consul Discovery | 服务注册与发现 |
| Spring Boot Actuator | 健康检查、探针、运行状态暴露 |
| MyBatis-Plus | 数据访问层开发 |
| HikariCP | 数据库连接池 |
| MySQL 8 | 生产/本地业务数据库 |
| H2 | 单元测试和集成测试内存数据库 |
| Sentinel | viewer-service 的限流保护 |
| JUnit 5 / Mockito | 单元测试与集成测试 |
| Maven | 多模块构建和依赖管理 |

## 3. 项目模块结构

项目采用 Maven 聚合工程，包含 5 个核心模块：

| 模块 | 端口 | 作用 |
|------|------|------|
| `donation-common` | - | 公共 DTO、统一响应、TraceId、异常处理等基础能力 |
| `viewer-service` | 8081 | 对外打赏入口、观众画像查询代理、主播 Top10 查询代理 |
| `finance-service` | 8082 | 打赏结算、主播分成规则、主播余额、提现、对账 |
| `analytics-service` | 8083 | 观众画像、Top10、小时维度统计、分析数据重建 |
| `simulator-service` | 8084 | 高并发打赏模拟、压测结果统计、报告输出 |

服务调用关系如下：

1. `simulator-service` 调用 `viewer-service` 发起模拟打赏。
2. `viewer-service` 接收打赏请求并持久化入账任务。
3. `viewer-service` 异步调用 `finance-service` 完成最终结算。
4. `analytics-service` 基于 `finance-service` 已结算数据重建分析结果。
5. `viewer-service` 调用 `analytics-service` 查询画像和 Top10。

## 4. 数据库设计

项目数据库脚本位于 `sql/schema.sql`。核心表包括：

| 表名 | 作用 |
|------|------|
| `t_reward_event` | 打赏事件明细，记录打赏金额、主播、观众、结算状态等 |
| `t_streamer_commission_rule` | 主播分成规则，支持按生效时间维护历史规则 |
| `t_streamer_balance` | 主播余额表，维护累计打赏、平台分成、可提现余额 |
| `t_reward_ingest_task` | viewer-service 入账任务表，保证异步结算可恢复 |
| `t_viewer_profile` | 观众画像结果表 |
| `t_reward_hourly_stat` | 小时维度统计结果表 |
| `t_streamer_viewer_summary` | 主播-观众聚合统计表，用于 Top10 查询 |

数据库设计重点：

- 使用唯一业务单号 `rewardNo` 保证打赏幂等。
- 分成规则按生效时间保存，历史订单按历史规则结算。
- viewer 侧先落任务，再异步结算，避免高并发下请求阻塞或数据丢失。
- analytics 侧使用重建任务生成查询友好的聚合表，降低查询复杂度。

## 5. 核心业务流程

### 5.1 打赏受理流程

1. 用户或模拟服务请求 `POST /api/viewers/reward`。
2. `viewer-service` 校验打赏参数。
3. 校验通过后写入 `t_reward_ingest_task`。
4. 接口快速返回 `ACCEPTED`。
5. 异步线程调用 `finance-service` 结算接口。
6. 成功后任务标记为完成，失败则进入待重试状态。

该流程的目标是提升入口吞吐量，并保证异常情况下任务可恢复。

### 5.2 财务结算流程

1. `finance-service` 接收 `POST /api/finance/rewards/settle`。
2. 根据 `rewardNo` 判断是否重复结算。
3. 按打赏时间匹配主播当时生效的分成规则。
4. 计算平台分成金额和主播入账金额。
5. 写入打赏事件，并更新主播余额。
6. 返回结算结果。

### 5.3 经营分析流程

1. 调用 `POST /api/analytics/jobs/rebuild` 或由定时任务触发。
2. `analytics-service` 读取已结算打赏事件。
3. 重建观众画像、小时统计、主播观众汇总。
4. 对外提供画像、Top10、小时统计查询接口。

### 5.4 压测模拟流程

1. 调用 `POST /api/simulator/start`。
2. `simulator-service` 根据 QPS、持续时间、主播数量、观众数量生成请求。
3. 通过 Feign 调用 `viewer-service`。
4. 统计成功数、失败数、超时数、实际 QPS 等指标。
5. 可通过结果接口或静态报告页查看压测结果。

## 6. 接口设计

### 6.1 viewer-service

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/viewers/reward` | 发起打赏 |
| GET | `/api/viewers/{viewerId}/profile` | 查询观众画像 |
| GET | `/api/viewers/streamers/{streamerId}/top-viewers` | 查询主播 Top10 打赏观众 |

### 6.2 finance-service

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/finance/rewards/settle` | 打赏结算 |
| POST | `/api/finance/commission-rules` | 配置主播分成规则 |
| GET | `/api/finance/streamers/{streamerId}/balance` | 查询主播余额 |
| POST | `/api/finance/streamers/{streamerId}/withdraw` | 主播提现 |
| POST | `/api/finance/reconciliation/precompute` | 预计算余额 |
| POST | `/api/finance/reconciliation/check` | 执行对账检查 |

### 6.3 analytics-service

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/analytics/viewers/{viewerId}/profile` | 查询观众画像 |
| GET | `/api/analytics/streamers/{streamerId}/top-viewers` | 查询主播 Top10 |
| GET | `/api/analytics/hourly` | 查询小时维度统计 |
| POST | `/api/analytics/jobs/rebuild` | 重建分析数据 |

### 6.4 simulator-service

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/simulator/start` | 发起压测 |
| GET | `/api/simulator/templates/default` | 查看默认压测模板 |
| GET | `/api/simulator/results/latest` | 查看最近一次压测结果 |
| GET | `/api/simulator/report/latest` | 查看最近一次压测报告 |
| GET | `/report.html` | 静态压测报告页面 |

## 7. 非功能实现

### 7.1 链路追踪与统一日志

项目在 `donation-common` 中实现统一 TraceId 能力：

- 所有 HTTP 请求进入服务时经过 `TraceIdFilter`。
- 支持读取 `traceId` 和 `X-Trace-Id` 请求头。
- 请求未携带 TraceId 时自动生成。
- 响应头回写 TraceId。
- TraceId 写入 MDC，日志格式统一打印。
- Feign 调用自动向下游透传 TraceId。
- viewer 异步线程池继承 TraceId，避免异步结算日志断链。

### 7.2 快速启动

项目针对启动速度做了以下优化：

- 关闭 `viewer-service` 的 Sentinel eager 预热。
- HikariCP `minimum-idle` 设置为 `0`，减少启动时预建连接。
- HikariCP `initialization-fail-timeout` 设置为 `-1`，降低数据库短暂不可用对服务启动的影响。

### 7.3 多节点扩缩容和故障处理

项目通过 Consul 和 Actuator 支持多节点部署：

- 每个服务开启 readiness/liveness 探针。
- Consul 健康检查指向 `/actuator/health/readiness`。
- Consul 实例 ID 使用应用名、端口和随机值组合，避免多实例冲突。
- 服务开启 graceful shutdown，滚动发布或下线时尽量处理完正在执行的请求。
- viewer-service 的任务表和恢复调度器支持异步结算失败后的恢复。

## 8. 功能测试与联调结果

本项目覆盖了以下测试内容：

- 参数校验测试。
- 打赏受理和幂等测试。
- 入账任务持久化与恢复测试。
- 财务结算测试。
- 主播分成规则测试。
- 余额、提现、对账测试。
- 观众画像、Top10、小时统计测试。
- QPS 限速器测试。
- 模拟压测指标统计测试。
- 模拟数据生成测试。

已执行完整 Maven 测试：

```bash
mvn test
```

执行结果：

```text
Reactor Summary:
javaee-donation-live  SUCCESS
donation-common       SUCCESS
viewer-service        SUCCESS
finance-service       SUCCESS
analytics-service     SUCCESS
simulator-service     SUCCESS
BUILD SUCCESS
```

## 9. BUG 与问题修复汇总

| 问题 | 影响 | 修复方式 |
|------|------|----------|
| 日志没有统一 MDC TraceId | 跨服务排查困难 | TraceId 写入 MDC，四个服务统一日志 pattern |
| TraceId 只支持单一请求头 | 外部系统使用 `X-Trace-Id` 时无法串联 | 同时支持 `traceId` 和 `X-Trace-Id` |
| Feign 调用 TraceId 可能丢失 | 下游日志无法关联上游请求 | Feign 拦截器统一透传 TraceId |
| 异步结算线程 TraceId 丢失 | viewer 异步入账日志断链 | 使用 `TraceContext.wrap` 包装线程池任务 |
| viewer 存在显式 eager 预热 | 启动变慢，不利于扩缩容 | 关闭 Sentinel eager |
| 数据库连接池启动时预建连接 | 多服务同时启动会增加数据库瞬时压力 | `minimum-idle=0` |
| Consul 实例标识不适合多节点 | 多实例可能冲突或不易定位 | 使用应用名、端口、随机值生成实例 ID |
| 健康检查未区分存活和就绪 | 故障摘除不够精确 | 开启 liveness/readiness 探针 |
| Lombok 注解处理不稳定 | Maven 编译找不到 `builder()` | 父 POM 显式配置 annotation processor，并升级 Lombok |
| JDK 25 下 Mockito/Byte Buddy 测试不兼容 | 测试无法正常执行 | Surefire 增加 `-Dnet.bytebuddy.experimental=true` |

## 10. 部署与运行说明

### 10.1 环境准备

- JDK 17 或更高版本。
- Maven 3.9+。
- MySQL 8。
- Consul。

### 10.2 数据库初始化

```bash
mysql -u root -p < sql/schema.sql
```

### 10.3 环境变量

```bash
DB_HOST=localhost:3306
DB_USERNAME=root
DB_PASSWORD=your_password
```

PowerShell 示例：

```powershell
$env:DB_HOST='localhost:3306'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='your_password'
```

### 10.4 启动顺序

推荐启动顺序：

1. Consul
2. `finance-service`
3. `analytics-service`
4. `viewer-service`
5. `simulator-service`

启动命令示例：

```bash
mvn clean package -DskipTests
java -jar finance-service/target/finance-service-1.0.0-SNAPSHOT.jar
java -jar analytics-service/target/analytics-service-1.0.0-SNAPSHOT.jar
java -jar viewer-service/target/viewer-service-1.0.0-SNAPSHOT.jar
java -jar simulator-service/target/simulator-service-1.0.0-SNAPSHOT.jar
```

### 10.5 健康检查

```bash
curl http://localhost:8081/actuator/health/readiness
curl http://localhost:8082/actuator/health/readiness
curl http://localhost:8083/actuator/health/readiness
curl http://localhost:8084/actuator/health/readiness
```

## 11. 项目总结

本项目完成了直播打赏场景下从入口受理、异步结算、财务入账、余额查询、数据分析到高并发压测的完整闭环。系统采用多服务架构，模块边界清晰，支持服务注册发现、跨服务调用、统一 TraceId 日志、健康探针、优雅停机和多节点部署。

功能层面，项目实现了打赏幂等、主播分成规则、余额维护、提现、对账、观众画像、主播 Top10、小时统计和压测模拟。非功能层面，项目补充了链路追踪、统一日志、快速启动、多节点注册、故障摘除和完整测试验证。当前代码已通过完整 `mvn test`，具备课程设计演示、答辩和后续扩展的基础。
