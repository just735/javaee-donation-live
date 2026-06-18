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
    settle_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reward_no (reward_no),
    KEY idx_streamer_time (streamer_id, reward_time),
    KEY idx_viewer_time (viewer_id, reward_time)
);

CREATE TABLE IF NOT EXISTS t_viewer_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    viewer_id VARCHAR(64) NOT NULL,
    viewer_name VARCHAR(128) NOT NULL,
    profile_tag VARCHAR(32) NOT NULL,
    profile_score DECIMAL(18,4) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_viewer_id (viewer_id)
);

CREATE TABLE IF NOT EXISTS t_reward_hourly_stat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_hour DATETIME NOT NULL,
    streamer_id VARCHAR(64) NOT NULL,
    viewer_gender VARCHAR(16) NOT NULL,
    reward_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    reward_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hour_streamer_gender (stat_hour, streamer_id, viewer_gender)
);

CREATE TABLE IF NOT EXISTS t_streamer_viewer_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    streamer_id VARCHAR(64) NOT NULL,
    viewer_id VARCHAR(64) NOT NULL,
    viewer_name VARCHAR(128) NOT NULL,
    total_reward_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    reward_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_streamer_viewer (streamer_id, viewer_id),
    KEY idx_streamer_amount (streamer_id, total_reward_amount)
);
