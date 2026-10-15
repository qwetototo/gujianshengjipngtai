CREATE TABLE firmware (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_model VARCHAR(120) NOT NULL,
    version VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    size_bytes BIGINT NOT NULL,
    md5 CHAR(32) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(1024) NOT NULL,
    upload_time DATETIME(6) NOT NULL,
    uploader VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_firmware_device_model (device_model),
    INDEX idx_firmware_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE operation_log (
    id CHAR(32) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    action VARCHAR(24) NOT NULL,
    device_model VARCHAR(120) NOT NULL,
    version VARCHAR(255) NOT NULL,
    operator VARCHAR(120) NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_operation_log_time (occurred_at),
    INDEX idx_operation_log_model_action (device_model, action, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
