package com.example.otaconsole.repository;

import com.example.otaconsole.model.FirmwareRecord;
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
            SELECT f.*,
                   CASE WHEN f.id = (SELECT f2.id FROM firmware f2
                                      WHERE f2.product = f.product
                                      ORDER BY f2.upload_time DESC, f2.id DESC
                                      LIMIT 1)
                        THEN 1 ELSE 0 END AS version_rank
              FROM firmware f
            """;

    private static final RowMapper<FirmwareRecord> FIRMWARE_ROW = (result, row) -> {
        FirmwareRecord record = new FirmwareRecord();
        record.setId(result.getLong("id"));
        record.setProduct(result.getString("product"));
        record.setVersion(result.getString("version"));
        record.setDescription(result.getString("description"));
        record.setFileSize(result.getLong("size_bytes"));
        record.setMd5(result.getString("md5"));
        record.setSha256(result.getString("sha256"));
        record.setOriginalFileName(result.getString("original_file_name"));
        record.setStoredFileName(result.getString("stored_file_name"));
        record.setUploadTime(result.getTimestamp("upload_time").toLocalDateTime());
        record.setUploader(result.getString("uploader"));
        record.setLatest(result.getInt("version_rank") == 1);
        record.setHardwareId(result.getString("hardware_id"));
        record.setPartitionId(result.getString("partition_id"));
        record.setSdkCompat(result.getString("sdk_compat"));
        record.setTargetBuild(result.getString("target_build"));
        record.setVersionCode(result.getObject("version_code", Long.class));
        record.setWorkSize(result.getObject("work_size", Long.class));
        record.setReleaseNotes(result.getString("release_notes"));
        return record;
    };

    private static final RowMapper<OperationLog> LOG_ROW = (result, row) -> {
        OperationLog log = new OperationLog();
        log.setId(result.getString("id"));
        log.setOccurredAt(result.getTimestamp("occurred_at").toLocalDateTime());
        log.setAction(result.getString("action"));
        log.setProduct(result.getString("product"));
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
                              (product, hardware_id, partition_id, sdk_compat, target_build,
                               version, version_code, work_size, description, release_notes, size_bytes,
                               md5, sha256, original_file_name, stored_file_name, upload_time, uploader)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, record.getProduct());
            statement.setString(2, record.getHardwareId());
            statement.setString(3, record.getPartitionId());
            statement.setString(4, record.getSdkCompat());
            statement.setString(5, record.getTargetBuild());
            statement.setString(6, record.getVersion());
            statement.setObject(7, record.getVersionCode());
            statement.setObject(8, record.getWorkSize());
            statement.setString(9, record.getDescription());
            statement.setString(10, record.getReleaseNotes());
            statement.setLong(11, record.getFileSize());
            statement.setString(12, record.getMd5());
            statement.setString(13, record.getSha256());
            statement.setString(14, record.getOriginalFileName());
            statement.setString(15, record.getStoredFileName());
            statement.setTimestamp(16, Timestamp.valueOf(record.getUploadTime()));
            statement.setString(17, record.getUploader());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回自增主键");
        }
        return key.longValue();
    }

    public List<FirmwareRecord> findAll() {
        return jdbc.query(FIRMWARE_SELECT + " ORDER BY f.upload_time DESC, f.id DESC", FIRMWARE_ROW);
    }

    public Optional<FirmwareRecord> findById(long id) {
        return jdbc.query(FIRMWARE_SELECT + " WHERE f.id = ?", FIRMWARE_ROW, id).stream().findFirst();
    }

    public Optional<FirmwareRecord> findLatest(String product) {
        return jdbc.query(FIRMWARE_SELECT + " WHERE LOWER(f.product) = LOWER(?)"
                + " ORDER BY f.upload_time DESC, f.id DESC LIMIT 1", FIRMWARE_ROW, product)
                .stream().findFirst();
    }

    public List<FirmwareRecord> findExpiredVersions(String product, int retainedHistoricalVersions) {
        return jdbc.query(FIRMWARE_SELECT + " WHERE LOWER(f.product) = LOWER(?)"
                        + " ORDER BY f.upload_time DESC, f.id DESC LIMIT 18446744073709551615 OFFSET ?",
                FIRMWARE_ROW, product, retainedHistoricalVersions + 1);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM firmware WHERE id = ?", id);
    }

    public void insertLog(OperationLog log) {
        jdbc.update("""
                        INSERT INTO operation_log (id, occurred_at, action, product, version, operator, detail)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                log.getId(), Timestamp.valueOf(log.getOccurredAt()), log.getAction(), log.getProduct(),
                log.getVersion(), log.getOperator(), log.getDetail());
    }

    public List<OperationLog> findLogs() {
        return jdbc.query("SELECT * FROM operation_log ORDER BY occurred_at DESC, id DESC", LOG_ROW);
    }
}
