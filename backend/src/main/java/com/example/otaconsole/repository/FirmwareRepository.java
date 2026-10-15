package com.example.otaconsole.repository;

import com.example.otaconsole.model.FirmwareRecord;
import com.example.otaconsole.model.FirmwareHistory;
import com.example.otaconsole.model.OperationLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class FirmwareRepository {
    private static final String FIRMWARE_SELECT = """
            SELECT f.*, h.version AS previous_version
              FROM firmware f
              LEFT JOIN firmware_history h ON h.device_model = f.device_model
            """;

    private static final RowMapper<FirmwareRecord> FIRMWARE_ROW = (result, row) -> {
        FirmwareRecord record = new FirmwareRecord();
        record.setId(result.getLong("id"));
        record.setDeviceModel(result.getString("device_model"));
        record.setVersion(result.getString("version"));
        record.setDescription(result.getString("description"));
        record.setFileSize(result.getLong("size_bytes"));
        record.setMd5(result.getString("md5"));
        record.setOriginalFileName(result.getString("original_file_name"));
        record.setStoredFileName(result.getString("stored_file_name"));
        record.setUploadTime(result.getTimestamp("upload_time").toLocalDateTime());
        record.setUploader(result.getString("uploader"));
        record.setPreviousVersion(result.getString("previous_version"));
        return record;
    };

    private static final RowMapper<FirmwareHistory> HISTORY_ROW = (result, row) -> {
        FirmwareHistory history = new FirmwareHistory();
        history.setDeviceModel(result.getString("device_model"));
        history.setVersion(result.getString("version"));
        history.setDescription(result.getString("description"));
        history.setFileSize(result.getLong("size_bytes"));
        history.setMd5(result.getString("md5"));
        history.setOriginalFileName(result.getString("original_file_name"));
        history.setStoredFileName(result.getString("stored_file_name"));
        history.setUploadTime(result.getTimestamp("upload_time").toLocalDateTime());
        history.setUploader(result.getString("uploader"));
        return history;
    };

    private static final RowMapper<OperationLog> LOG_ROW = (result, row) -> {
        OperationLog log = new OperationLog();
        log.setId(result.getString("id"));
        log.setOccurredAt(result.getTimestamp("occurred_at").toLocalDateTime());
        log.setAction(result.getString("action"));
        log.setDeviceModel(result.getString("device_model"));
        log.setVersion(result.getString("version"));
        log.setOperator(result.getString("operator"));
        log.setDetail(result.getString("detail"));
        return log;
    };

    private final JdbcTemplate jdbc;

    public FirmwareRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(FirmwareRecord record) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO firmware
                              (device_model, version, description, size_bytes,
                               md5, original_file_name, stored_file_name, upload_time, uploader)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, record.getDeviceModel());
            statement.setString(2, record.getVersion());
            statement.setString(3, record.getDescription());
            statement.setLong(4, record.getFileSize());
            statement.setString(5, record.getMd5());
            statement.setString(6, record.getOriginalFileName());
            statement.setString(7, record.getStoredFileName());
            statement.setTimestamp(8, Timestamp.valueOf(record.getUploadTime()));
            statement.setString(9, record.getUploader());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回自增主键");
        }
        return key.longValue();
    }

    public void update(FirmwareRecord record) {
        jdbc.update("""
                        UPDATE firmware
                           SET version = ?, description = ?, size_bytes = ?, md5 = ?,
                               original_file_name = ?, stored_file_name = ?, upload_time = ?, uploader = ?
                         WHERE id = ?
                        """,
                record.getVersion(), record.getDescription(), record.getFileSize(),
                record.getMd5(), record.getOriginalFileName(), record.getStoredFileName(),
                Timestamp.valueOf(record.getUploadTime()), record.getUploader(), record.getId());
    }

    public Optional<FirmwareRecord> findByDeviceModel(String deviceModel) {
        return jdbc.query(FIRMWARE_SELECT + " WHERE LOWER(f.device_model) = LOWER(?)",
                FIRMWARE_ROW, deviceModel).stream().findFirst();
    }

    public List<FirmwareRecord> findAll() {
        return jdbc.query(FIRMWARE_SELECT + " ORDER BY f.upload_time DESC, f.id DESC", FIRMWARE_ROW);
    }

    public Optional<FirmwareRecord> findById(long id) {
        return jdbc.query(FIRMWARE_SELECT + " WHERE f.id = ?", FIRMWARE_ROW, id).stream().findFirst();
    }

    public Optional<FirmwareRecord> findLatest(String deviceModel) {
        return jdbc.query(FIRMWARE_SELECT + " WHERE LOWER(f.device_model) = LOWER(?)"
                + " ORDER BY f.upload_time DESC, f.id DESC LIMIT 1", FIRMWARE_ROW, deviceModel)
                .stream().findFirst();
    }

    public Optional<FirmwareHistory> findHistoryByDeviceModel(String deviceModel) {
        return jdbc.query("SELECT * FROM firmware_history WHERE LOWER(device_model) = LOWER(?)",
                HISTORY_ROW, deviceModel).stream().findFirst();
    }

    public void upsertHistory(FirmwareHistory history) {
        jdbc.update("""
                        INSERT INTO firmware_history
                          (device_model, version, description, size_bytes, md5, original_file_name,
                           stored_file_name, upload_time, uploader)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) AS incoming
                        ON DUPLICATE KEY UPDATE
                          version = incoming.version,
                          description = incoming.description,
                          size_bytes = incoming.size_bytes,
                          md5 = incoming.md5,
                          original_file_name = incoming.original_file_name,
                          stored_file_name = incoming.stored_file_name,
                          upload_time = incoming.upload_time,
                          uploader = incoming.uploader
                        """,
                history.getDeviceModel(), history.getVersion(), history.getDescription(), history.getFileSize(),
                history.getMd5(), history.getOriginalFileName(), history.getStoredFileName(),
                Timestamp.valueOf(history.getUploadTime()), history.getUploader());
    }

    public void deleteHistoryByDeviceModel(String deviceModel) {
        jdbc.update("DELETE FROM firmware_history WHERE LOWER(device_model) = LOWER(?)", deviceModel);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM firmware WHERE id = ?", id);
    }

    public void insertLog(OperationLog log) {
        jdbc.update("""
                        INSERT INTO operation_log (id, occurred_at, action, device_model, version, operator, detail)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                log.getId(), Timestamp.valueOf(log.getOccurredAt()), log.getAction(), log.getDeviceModel(),
                log.getVersion(), log.getOperator(), log.getDetail());
    }

    public List<OperationLog> findLogs() {
        return jdbc.query("SELECT * FROM operation_log ORDER BY occurred_at DESC, id DESC", LOG_ROW);
    }
}
