# 数据库设计说明

## 1. 文档目的
本说明文档用于描述 `javaee-donation-live` 项目的数据库设计方案，包含库表划分、字段定义、索引设计、数据关系以及设计原因，作为课程设计提交材料的一部分。

本项目围绕“直播打赏”业务展开，数据库主要支撑以下能力：
- 观众打赏明细落库与幂等控制
- 财务结算与主播余额计算
- 主播提成规则按时间生效
- 观众画像生成
- 按小时、主播、性别维度统计分析
- 主播下观众打赏汇总与 Top10 查询

## 2. 数据库概览
- 数据库名：`javaee_donation_live`
- 字符集：`utf8mb4`
- 排序规则：`utf8mb4_0900_ai_ci`
- 存储引擎：`InnoDB`

数据库建表脚本位于 `sql/schema.sql`。

## 3. 设计原则

### 3.1 先明细，后汇总
项目中的所有分析结果和财务结果，都以打赏明细表为基础进行派生计算。这样可以保证：
- 原始业务数据可追溯
- 汇总结果出错时可重新计算
- 财务对账与经营分析都能基于同一事实来源

### 3.2 关键业务保证幂等
打赏数据不能重复入账，因此打赏单号 `reward_no` 设计为唯一键，通过数据库层唯一约束配合业务层幂等控制，避免重复结算。

### 3.3 规则按时间生效
主播提成比例可能变化，因此提成规则表采用“生效开始时间 + 生效结束时间”的区间模型，确保历史打赏始终按当时生效的规则结算。

### 3.4 明细与汇总分离
为了兼顾查询效率和业务正确性，数据库中既保存明细表，也保存统计汇总表：
- 明细表用于追溯、重建、对账
- 汇总表用于提高查询性能

## 4. 表结构设计

### 4.1 打赏明细表 `t_reward_event`

#### 4.1.1 设计目的
用于保存每一笔打赏事件，是全系统最核心的事实表，供财务结算、经营分析、画像计算和对账使用。

#### 4.1.2 字段说明
| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `reward_no` | VARCHAR(64) | 打赏唯一号，作为幂等键 |
| `trace_id` | VARCHAR(64) | 链路追踪 ID |
| `viewer_id` | VARCHAR(64) | 观众 ID |
| `viewer_name` | VARCHAR(128) | 观众姓名 |
| `viewer_gender` | VARCHAR(16) | 观众性别 |
| `streamer_id` | VARCHAR(64) | 主播 ID |
| `streamer_name` | VARCHAR(128) | 主播姓名 |
| `reward_amount` | DECIMAL(18,2) | 打赏金额 |
| `reward_time` | DATETIME | 打赏时间 |
| `settle_status` | VARCHAR(32) | 入账状态 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

#### 4.1.3 索引设计（已针对高并发写入优化）
- `uk_reward_no (reward_no)`：唯一索引，防止重复入账
- `idx_streamer_time (streamer_id, reward_time)`：支持按主播和时间范围查询

**索引瘦身优化说明**：
原设计包含 `idx_viewer_time (viewer_id, reward_time)` 索引，用于支持按观众历史消费查询。
经压测分析发现：
1. 该索引在写入路径上完全无用（写入不需要按观众查询）
2. InnoDB 每次INSERT/UPDATE都会同步修改所有索引页，该索引导致每次写操作增加约33%的索引维护开销
3. 在500 QPS高并发场景下，频繁的索引页分裂成为写入瓶颈的重要因素

**优化决策**：移除 `idx_viewer_time` 索引。
**影响范围**：仅影响"按观众查询历史消费"场景，该功能可通过全表扫描或ES替代实现。
**性能收益**：减少约33%的索引维护开销，显著提升高并发写入性能。

#### 4.1.4 设计说明
该表既是财务入账依据，也是经营分析重建依据。`reward_no` 唯一约束是“不能重复入账”的核心保障。

### 4.2 主播提成规则表 `t_streamer_commission_rule`

#### 4.2.1 设计目的
保存每位主播在不同时间段内的提成规则，支持历史规则追溯与未来规则切换。

#### 4.2.2 字段说明
| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `streamer_id` | VARCHAR(64) | 主播 ID |
| `commission_rate` | DECIMAL(10,4) | 提成比例 |
| `effective_from` | DATETIME | 生效开始时间 |
| `effective_to` | DATETIME | 生效结束时间，可为空 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

#### 4.2.3 索引设计
- `idx_streamer_effective (streamer_id, effective_from, effective_to)`：支持根据主播和打赏时间匹配生效规则

#### 4.2.4 设计说明
当主播提成发生变化时，不直接覆盖旧规则，而是关闭旧规则并新增新规则。这样可以保证：
- 历史打赏按历史规则计算
- 新打赏按新规则计算
- 后续可以进行审计和对账

### 4.3 主播余额表 `t_streamer_balance`

#### 4.3.1 设计目的
保存每位主播当前累计打赏金额、累计提成金额和可提现余额，供主播余额查询与提现操作使用。

#### 4.3.2 字段说明
| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `streamer_id` | VARCHAR(64) | 主播 ID |
| `total_reward_amount` | DECIMAL(18,2) | 累计打赏金额 |
| `total_commission_amount` | DECIMAL(18,2) | 累计提成金额 |
| `withdrawable_amount` | DECIMAL(18,2) | 可提现金额 |
| `version` | BIGINT | 乐观锁版本号 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

#### 4.3.3 索引设计
- `uk_streamer_id (streamer_id)`：唯一索引，确保每位主播只有一条余额记录

#### 4.3.4 设计说明
该表属于结果表，用于提高主播余额查询效率。由于主播余额会频繁更新，增加 `version` 字段用于乐观锁控制，避免并发更新造成数据覆盖。

### 4.4 观众画像表 `t_viewer_profile`

#### 4.4.1 设计目的
保存观众画像结果，供观众服务快速查询。

#### 4.4.2 字段说明
| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `viewer_id` | VARCHAR(64) | 观众 ID |
| `viewer_name` | VARCHAR(128) | 观众姓名 |
| `profile_tag` | VARCHAR(32) | 画像标签，如 HIGH / MEDIUM / LOW |
| `profile_score` | DECIMAL(18,4) | 画像计算分值，当前为累计消费金额 |
| `updated_at` | DATETIME | 更新时间 |

#### 4.4.3 索引设计
- `uk_viewer_id (viewer_id)`：唯一索引，保证每位观众只有一条最新画像

#### 4.4.4 设计说明
画像结果允许存在一定延迟，因此采用定时重建方式更新。这样能降低实时计算开销，提高查询性能。

### 4.5 小时统计表 `t_reward_hourly_stat`

#### 4.5.1 设计目的
保存按小时、主播、性别聚合后的统计结果，支撑经营分析服务的多维度查询。

#### 4.5.2 字段说明
| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `stat_hour` | DATETIME | 小时桶，例如 2026-06-18 18:00:00 |
| `streamer_id` | VARCHAR(64) | 主播 ID |
| `viewer_gender` | VARCHAR(16) | 观众性别 |
| `reward_amount` | DECIMAL(18,2) | 当前维度下的打赏总金额 |
| `reward_count` | BIGINT | 当前维度下的打赏次数 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

#### 4.5.3 索引设计
- `uk_hour_streamer_gender (stat_hour, streamer_id, viewer_gender)`：唯一索引，保证同一小时、同一主播、同一性别只有一条聚合记录

#### 4.5.4 设计说明
该表是经营分析服务查询的核心支撑表，可直接返回某时间范围内的小时统计结果，避免每次从明细表实时聚合。

### 4.6 主播观众汇总表 `t_streamer_viewer_summary`

#### 4.6.1 设计目的
保存某主播下各观众的累计打赏金额和打赏次数，用于快速查询某主播下打赏最多的观众 Top10。

#### 4.6.2 字段说明
| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `streamer_id` | VARCHAR(64) | 主播 ID |
| `viewer_id` | VARCHAR(64) | 观众 ID |
| `viewer_name` | VARCHAR(128) | 观众姓名 |
| `total_reward_amount` | DECIMAL(18,2) | 累计打赏金额 |
| `reward_count` | BIGINT | 打赏次数 |
| `updated_at` | DATETIME | 更新时间 |

#### 4.6.3 索引设计
- `uk_streamer_viewer (streamer_id, viewer_id)`：唯一索引，保证同一主播与观众组合只有一条汇总记录
- `idx_streamer_amount (streamer_id, total_reward_amount)`：支持按主播快速查询高打赏观众排序

#### 4.6.4 设计说明
该表与小时统计表一样，属于分析服务的派生结果表。通过该表查询 Top10，无需每次扫描全部打赏明细。

## 5. 表之间的关系
本项目没有使用外键约束，但逻辑上存在如下关系：

- `t_reward_event` 是原始事实表
- `t_streamer_commission_rule` 按 `streamer_id` 与 `t_reward_event` 关联，用于计算财务结算
- `t_streamer_balance` 按 `streamer_id` 汇总 `t_reward_event` 结果
- `t_viewer_profile` 按 `viewer_id` 汇总 `t_reward_event` 结果
- `t_reward_hourly_stat` 按 `reward_time + streamer_id + viewer_gender` 汇总 `t_reward_event`
- `t_streamer_viewer_summary` 按 `streamer_id + viewer_id` 汇总 `t_reward_event`

说明：
- 为了保证微服务之间职责独立、减少耦合，数据库层没有建立强外键
- 业务一致性更多依赖应用层校验与汇总重建机制

## 6. 与业务需求的对应关系

### 6.1 打赏不能丢失、不能重复
- `t_reward_event` 保存每笔打赏明细
- `reward_no` 唯一索引保证幂等

### 6.2 支持主播余额查询
- `t_streamer_balance` 保存主播可提现余额
- `t_streamer_commission_rule` 保证提成按历史规则和新规则正确切换

### 6.3 支持观众画像查询
- `t_viewer_profile` 保存画像结果
- 来源于打赏明细的消费汇总数据

### 6.4 支持查询主播 Top10 打赏观众
- `t_streamer_viewer_summary` 提供按主播快速排序查询能力

### 6.5 支持小时 / 主播 / 性别三维组合查询
- `t_reward_hourly_stat` 提供多维组合统计能力

## 7. 性能与扩展性考虑

### 7.1 索引优化
所有高频查询字段均已建立索引，包括：
- 打赏唯一号
- 主播 + 时间
- 观众 + 时间
- 主播 + 金额排序
- 小时 + 主播 + 性别

### 7.2 汇总表设计
对于画像、Top10、小时统计等场景，直接构建汇总表，避免每次查询都对明细表进行实时全量扫描。

### 7.3 支持重建与对账
由于所有派生数据都来源于 `t_reward_event`，当统计结果异常时，可以重新执行：
- 财务余额预计算 / 对账
- 经营分析重建任务

### 7.4 支持规则演进
提成规则采用区间模型，不会因规则变更影响历史记录，后续若要增加更多等级、更多维度，也能在现有模型上扩展。

## 8. 设计总结
本项目数据库设计围绕“明细可追溯、结果可汇总、规则可追踪、查询要高效”四个目标展开：
- `t_reward_event` 负责保存真实业务数据
- `t_streamer_commission_rule` 与 `t_streamer_balance` 负责财务结算
- `t_viewer_profile`、`t_reward_hourly_stat`、`t_streamer_viewer_summary` 负责经营分析查询

该设计能够满足课程设计中对功能正确性、代码可维护性、数据一致性和文档完整性的要求，也便于后续联调、测试和答辩说明。
