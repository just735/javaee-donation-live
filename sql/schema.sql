CREATE DATABASE IF NOT EXISTS javaee_donation_live DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE javaee_donation_live;

CREATE TABLE IF NOT EXISTS t_reward_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reward_no VARCHAR(64) NOT NULL COMMENT '打赏唯一号，幂等键',
    trace_id VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    viewer_id VARCHAR(64) NOT NULL COMMENT '观众ID',
    viewer_name VARCHAR(128) NOT NULL COMMENT '观众姓名',
    viewer_gender VARCHAR(16) NOT NULL COMMENT '观众性别',
    streamer_id VARCHAR(64) NOT NULL COMMENT '主播ID',
    streamer_name VARCHAR(128) NOT NULL COMMENT '主播姓名',
    reward_amount DECIMAL(18,2) NOT NULL COMMENT '打赏金额',
    reward_time DATETIME NOT NULL COMMENT '打赏时间',
    settle_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '入账状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reward_no (reward_no),
    KEY idx_streamer_time (streamer_id, reward_time),
    KEY idx_viewer_time (viewer_id, reward_time)
) ENGINE=InnoDB COMMENT='打赏明细表';

CREATE TABLE IF NOT EXISTS t_streamer_commission_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    streamer_id VARCHAR(64) NOT NULL COMMENT '主播ID',
    commission_rate DECIMAL(10,4) NOT NULL COMMENT '提成比例',
    effective_from DATETIME NOT NULL COMMENT '生效时间',
    effective_to DATETIME DEFAULT NULL COMMENT '失效时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_streamer_effective (streamer_id, effective_from, effective_to)
) ENGINE=InnoDB COMMENT='主播提成规则表';

CREATE TABLE IF NOT EXISTS t_streamer_balance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    streamer_id VARCHAR(64) NOT NULL COMMENT '主播ID',
    total_reward_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计打赏金额',
    total_commission_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计提成金额',
    withdrawable_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '可领取金额',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_streamer_id (streamer_id)
) ENGINE=InnoDB COMMENT='主播余额表';

CREATE TABLE IF NOT EXISTS t_viewer_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    viewer_id VARCHAR(64) NOT NULL COMMENT '观众ID',
    viewer_name VARCHAR(128) NOT NULL COMMENT '观众姓名',
    profile_tag VARCHAR(32) NOT NULL COMMENT '画像标签',
    profile_score DECIMAL(18,4) NOT NULL COMMENT '画像计算分值',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_viewer_id (viewer_id)
) ENGINE=InnoDB COMMENT='观众画像表';

CREATE TABLE IF NOT EXISTS t_reward_hourly_stat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_hour DATETIME NOT NULL COMMENT '小时桶',
    streamer_id VARCHAR(64) NOT NULL COMMENT '主播ID',
    viewer_gender VARCHAR(16) NOT NULL COMMENT '观众性别',
    reward_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '打赏金额',
    reward_count BIGINT NOT NULL DEFAULT 0 COMMENT '打赏次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hour_streamer_gender (stat_hour, streamer_id, viewer_gender)
) ENGINE=InnoDB COMMENT='小时维度打赏统计';

CREATE TABLE IF NOT EXISTS t_streamer_viewer_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    streamer_id VARCHAR(64) NOT NULL COMMENT '主播ID',
    viewer_id VARCHAR(64) NOT NULL COMMENT '观众ID',
    viewer_name VARCHAR(128) NOT NULL COMMENT '观众姓名',
    total_reward_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计打赏金额',
    reward_count BIGINT NOT NULL DEFAULT 0 COMMENT '打赏次数',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_streamer_viewer (streamer_id, viewer_id),
    KEY idx_streamer_amount (streamer_id, total_reward_amount)
) ENGINE=InnoDB COMMENT='主播下观众打赏汇总';
