CREATE TABLE IF NOT EXISTS t_reward_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reward_no VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    viewer_id VARCHAR(64) NOT NULL,
    viewer_name VARCHAR(128) NOT NULL,
    viewer_gender VARCHAR(16) NOT NULL,
    streamer_id VARCHAR(64) NOT NULL,
    streamer_name VARCHAR(128) NOT NULL,
    reward_amount DECIMAL(18,2) NOT NULL,
    reward_time DATETIME NOT NULL,
    commission_rate DECIMAL(10,4) DEFAULT NULL,
    commission_amount DECIMAL(18,2) DEFAULT NULL,
    withdrawable_amount DECIMAL(18,2) DEFAULT NULL,
    settle_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reward_no (reward_no),
    KEY idx_streamer_time (streamer_id, reward_time)
);

CREATE TABLE IF NOT EXISTS t_streamer_commission_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    streamer_id VARCHAR(64) NOT NULL,
    commission_rate DECIMAL(10,4) NOT NULL,
    effective_from DATETIME NOT NULL,
    effective_to DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_streamer_effective (streamer_id, effective_from, effective_to)
);

CREATE TABLE IF NOT EXISTS t_streamer_balance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    streamer_id VARCHAR(64) NOT NULL,
    total_reward_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_commission_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    withdrawable_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_streamer_id (streamer_id)
);
