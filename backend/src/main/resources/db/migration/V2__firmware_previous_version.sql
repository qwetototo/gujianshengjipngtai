CREATE TABLE firmware_history (
    device_model VARCHAR(120) NOT NULL,
    version VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    size_bytes BIGINT NOT NULL,
    md5 CHAR(32) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(1024) NOT NULL,
    upload_time DATETIME(6) NOT NULL,
    uploader VARCHAR(120) NOT NULL,
    PRIMARY KEY (device_model),
    CONSTRAINT fk_firmware_history_device_model
        FOREIGN KEY (device_model) REFERENCES firmware (device_model)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
