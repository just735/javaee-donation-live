-- ============================================================
-- MySQL 写入性能调优（本地开发/课程演示用）
-- 执行方式: mysql -u root -p < mysql-tuning.sql
-- ============================================================

-- 1. 删除冗余索引（写入时减少索引页维护开销）
-- idx_viewer_time 仅用于 analytics 读路径，写入路径不需要
ALTER TABLE javaee_donation_live.t_reward_event DROP INDEX IF EXISTS idx_viewer_time;

-- 2. InnoDB 写入专项参数（需有 SUPER 权限或 INNODB_* 权限）
-- 注意：以下参数重启 MySQL 后失效，永久修改需写入 my.ini

-- 2a. redo日志刷盘策略：2 = 每秒刷一次（折中方案）
--   1(默认): 每次事务提交刷盘，最安全但最慢
--   2: 每秒刷一次，崩溃最多丢1s数据，性能提升5-10倍
--   0: 交由OS控制，最快但可能丢更多数据
SET GLOBAL innodb_flush_log_at_trx_commit = 2;

-- 2b. binlog刷盘策略：2 = 每秒刷一次
SET GLOBAL sync_binlog = 2;

-- 2c. 增大InnoDB日志缓冲区（减少磁盘IO次数）
SET GLOBAL innodb_log_buffer_size = 64 * 1024 * 1024;  -- 64MB

-- 2d. 缓冲池大小设为物理内存的50%（8GB内存 → 4GB）
-- 注意：如果机器内存较小请适当降低此值
SET GLOBAL innodb_buffer_pool_size = 1024 * 1024 * 1024;  -- 1GB (适合开发机)

-- 3. 验证当前设置
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';
SHOW VARIABLES LIKE 'sync_binlog';
SHOW VARIABLES LIKE 'innodb_log_buffer_size';
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';

-- 4. 确认 t_reward_event 当前索引状态
SHOW INDEX FROM javaee_donation_live.t_reward_event;
