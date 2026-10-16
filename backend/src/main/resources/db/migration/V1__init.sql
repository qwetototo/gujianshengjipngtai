-- =============================================================
-- ZCode 固件管理数据库 最终版 Schema
-- 合并 V1 ~ V4 所有变更，直接重建
-- =============================================================

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- firmware: 固件主表（以 product 为唯一设备标识）
-- -------------------------------------------------------------
CREATE TABLE firmware (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    product            VARCHAR(63)  NOT NULL,
    hardware_id        VARCHAR(63)  NULL,
    partition_id       VARCHAR(63)  NULL,
    sdk_compat         VARCHAR(63)  NULL,
    target_build       VARCHAR(63)  NULL,
    version            VARCHAR(120) NOT NULL,
    version_code       BIGINT UNSIGNED NULL,
    work_size          INT UNSIGNED NULL,
    description        VARCHAR(1000) NOT NULL,
    release_notes      VARCHAR(1024) NULL,
    size_bytes         BIGINT       NOT NULL,
    md5                CHAR(32)     NOT NULL,
    sha256             CHAR(64)     NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name   VARCHAR(1024) NOT NULL,
    upload_time        DATETIME(6)  NOT NULL,
    uploader           VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_firmware_product_upload (product, upload_time, id),
    INDEX idx_firmware_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------
-- operation_log: 操作日志表
-- -------------------------------------------------------------
CREATE TABLE operation_log (
    id           CHAR(32)     NOT NULL,
    occurred_at  DATETIME(6)  NOT NULL,
    action       VARCHAR(24)  NOT NULL,
    product      VARCHAR(120) NOT NULL,
    version      VARCHAR(255) NOT NULL,
    operator     VARCHAR(120) NOT NULL,
    detail       VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_operation_log_time (occurred_at),
    INDEX idx_operation_log_model_action (product, action, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;