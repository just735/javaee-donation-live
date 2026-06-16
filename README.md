# JavaEE Donation Live

JavaEE 架构与应用课程设计初始化工程，按“观众服务 / 财务服务 / 经营分析服务 / 模拟服务”拆分为多 Maven 模块，适合多人并行开发。

## 模块
- `donation-common`：公共响应、traceId、异常、通用模型
- `viewer-service`：打赏入口、个人标签、主播 Top10
- `finance-service`：打赏入账、分成配置、余额查询
- `analytics-service`：画像计算、维度统计、定时聚合
- `simulator-service`：高并发请求模拟与压测入口

## 文档
- `docs/ARCHITECTURE.md`
- `docs/ENVIRONMENT.md`
- `docs/DEVELOPMENT_RULES.md`
- `docs/DEVELOPMENT_PLAN.md`
- `team/DIVISION.md`
- `sql/schema.sql`

## 快速开始
1. 安装 `JDK 17` 和 `Maven 3.9+`
2. 启动 `MySQL`、`Redis`、`Consul`
3. 在根目录执行 `mvn clean package -DskipTests`
4. 分模块启动对应服务

## 协作原则
- 每个服务单独维护包结构和接口契约
- 公共能力优先沉淀到 `donation-common`
- 先定义接口，再补实现
- 统一使用 `traceId` 贯穿日志、请求和响应
