package com.example.otaconsole.service;

import com.example.otaconsole.model.FirmwareHistory;
import com.example.otaconsole.model.FirmwareRecord;
import com.example.otaconsole.model.OperationLog;
import com.example.otaconsole.model.VersionResponse;
import com.example.otaconsole.repository.FirmwareRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class FirmwareService {
    private final StorageCapacityService capacity;
    private final ObjectStorageService storage;
    private final FirmwareRepository repository;
    private final TransactionTemplate transactions;

    public FirmwareService(StorageCapacityService capacity, ObjectStorageService storage,
                           FirmwareRepository repository, PlatformTransactionManager transactionManager) {
        this.capacity = capacity;
        this.storage = storage;
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public FirmwareRecord upload(String deviceModel, String version, String description, String uploader,
                                 MultipartFile file) throws IOException {
        validateRequired(deviceModel, "设备类型", 120);
        validateRequired(version, "版本", 120);
        validateRequired(uploader, "上传人", 120);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("固件文件不能为空");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        if (!original.toLowerCase(Locale.ROOT).endsWith(".pack")) throw new IllegalArgumentException("只允许上传 .pack 固件文件");
        String model = deviceModel.trim();
        String safeName = sanitizeFileName(original);
        capacity.lockForOperation(file.getSize(), 0);
        FirmwareRecord current = null;
        FirmwareHistory oldHistory = null;
        String currentKey = null;
        String historyKey = null;
        try {
            current = repository.findByDeviceModel(model).orElse(null);
            oldHistory = repository.findHistoryByDeviceModel(model).orElse(null);
            if (current != null && !storage.exists(current.getStoredFileName()))
                throw new IllegalStateException("当前固件对象缺失，不能覆盖；请先恢复对象或删除记录");
            if (current != null) capacity.lockForOperation(file.getSize(), current.getFileSize());
            String modelKey = modelDirectory(model);
            currentKey = modelKey + "/current/" + token() + "_" + safeName;
            try (InputStream input = file.getInputStream()) { storage.put(currentKey, input, file.getSize()); }
            if (current != null) {
                historyKey = modelKey + "/history/" + token() + "_" + current.getOriginalFileName();
                storage.copy(current.getStoredFileName(), historyKey);
            }
            FirmwareRecord record = new FirmwareRecord();
            record.setDeviceModel(model); record.setVersion(version.trim());
            record.setDescription(description == null || description.trim().isEmpty() ? "（无简述）" : trimToLength(description.trim(), 1000));
            record.setOriginalFileName(safeName); record.setStoredFileName(currentKey);
            record.setFileSize(file.getSize()); record.setMd5(md5(file)); record.setUploadTime(LocalDateTime.now()); record.setUploader(uploader.trim());
            FirmwareRecord oldCurrent = current;
            String archivedKey = historyKey;
            transactions.executeWithoutResult(status -> {
                if (oldCurrent == null) record.setId(repository.insert(record));
                else { record.setId(oldCurrent.getId()); repository.update(record); }
                if (oldCurrent != null) repository.upsertHistory(toHistory(oldCurrent, archivedKey));
                else repository.deleteHistoryByDeviceModel(model);
                repository.insertLog(new OperationLog(idForLog(), LocalDateTime.now(), "upload", model, record.getVersion(), record.getUploader(),
                        oldCurrent == null ? "上传 " + safeName : "覆盖并保留上一版 " + oldCurrent.getVersion()));
            });
            if (current != null) deleteObject(current.getStoredFileName());
            if (oldHistory != null) deleteObject(oldHistory.getStoredFileName());
            return record;
        } catch (IOException | RuntimeException e) {
            if (currentKey != null) deleteQuietly(currentKey);
            if (historyKey != null) deleteQuietly(historyKey);
            throw e;
        } finally {
            if (current != null) capacity.unlockUpload();
            capacity.unlockUpload();
        }
    }

    public FirmwareRecord rollback(long id, String operator) throws IOException {
        FirmwareRecord current = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("固件不存在"));
        FirmwareHistory history = repository.findHistoryByDeviceModel(current.getDeviceModel())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "没有可回滚的上一版固件"));
        if (!storage.exists(current.getStoredFileName()) || !storage.exists(history.getStoredFileName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前版或上一版固件对象缺失，无法回滚");
        capacity.lockForOperation(current.getFileSize(), history.getFileSize());
        String currentVersion = current.getVersion();
        String currentKey = current.getStoredFileName();
        String historyKey = history.getStoredFileName();
        String newCurrent = modelDirectory(current.getDeviceModel()) + "/current/" + token() + "_" + history.getOriginalFileName();
        String newHistory = modelDirectory(current.getDeviceModel()) + "/history/" + token() + "_" + current.getOriginalFileName();
        try {
            storage.copy(history.getStoredFileName(), newCurrent);
            storage.copy(current.getStoredFileName(), newHistory);
            FirmwareRecord latest = current;
            FirmwareHistory previous = history;
            FirmwareHistory archivedCurrent = toHistory(current, newHistory);
            transactions.executeWithoutResult(status -> {
                latest.setVersion(previous.getVersion()); latest.setDescription(previous.getDescription());
                latest.setFileSize(previous.getFileSize()); latest.setMd5(previous.getMd5());
                latest.setOriginalFileName(previous.getOriginalFileName()); latest.setStoredFileName(newCurrent);
                latest.setUploadTime(LocalDateTime.now()); latest.setUploader(trimToLength(operator, 120));
                repository.update(latest);
                repository.upsertHistory(archivedCurrent);
                repository.insertLog(new OperationLog(idForLog(), LocalDateTime.now(), "rollback", current.getDeviceModel(),
                        previous.getVersion(), trimToLength(operator, 120), "从 " + currentVersion + " 回滚至 " + previous.getVersion()));
            });
            deleteObject(currentKey); deleteObject(historyKey);
            return repository.findById(id).orElseThrow();
        } catch (IOException | RuntimeException e) {
            deleteQuietly(newCurrent); deleteQuietly(newHistory); throw e;
        } finally { capacity.unlockUpload(); }
    }

    public List<FirmwareRecord> list() { return repository.findAll(); }
    public Optional<FirmwareRecord> find(long id) { return repository.findById(id); }
    public Resource file(long id) {
        FirmwareRecord record = find(id).orElseThrow(() -> new IllegalArgumentException("固件不存在"));
        try {
            if (!storage.exists(record.getStoredFileName())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "固件对象不存在");
            return storage.get(record.getStoredFileName());
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取固件对象失败", e); }
    }

    public FirmwareRecord delete(long id, String operator) throws IOException {
        FirmwareRecord record = find(id).orElseThrow(() -> new IllegalArgumentException("固件不存在"));
        FirmwareHistory history = repository.findHistoryByDeviceModel(record.getDeviceModel()).orElse(null);
        transactions.executeWithoutResult(status -> {
            repository.delete(id);
            repository.insertLog(new OperationLog(idForLog(), LocalDateTime.now(), "delete", record.getDeviceModel(), record.getVersion(),
                    trimToLength(operator, 120), "删除 " + record.getOriginalFileName()));
        });
        storage.delete(record.getStoredFileName());
        if (history != null) storage.delete(history.getStoredFileName());
        return record;
    }

    public List<OperationLog> logs() { return repository.findLogs(); }
    public Optional<VersionResponse> latestFor(String model, String contextPath) {
        return repository.findLatest(model).map(r -> new VersionResponse(r.getVersion(),
                contextPath.replaceAll("/+$", "") + "/api/firmware/download/" + r.getId(), r.getFileSize(), r.getMd5()));
    }
    public void recordDownload(FirmwareRecord r, String operator) {
        String who = operator == null || operator.isBlank() ? "device" : trimToLength(operator.trim(), 120);
        repository.insertLog(new OperationLog(idForLog(), LocalDateTime.now(), "download", r.getDeviceModel(), r.getVersion(), who, "下载 " + r.getFileSize() + " 字节"));
    }

    private FirmwareHistory toHistory(FirmwareRecord r, String key) {
        FirmwareHistory h = new FirmwareHistory(); h.setDeviceModel(r.getDeviceModel()); h.setVersion(r.getVersion());
        h.setDescription(r.getDescription()); h.setFileSize(r.getFileSize()); h.setMd5(r.getMd5()); h.setOriginalFileName(r.getOriginalFileName());
        h.setStoredFileName(key); h.setUploadTime(r.getUploadTime()); h.setUploader(r.getUploader()); return h;
    }
    private String modelDirectory(String model) {
        String clean = model.trim().replaceAll("[^\\p{L}\\p{N}._-]", "_").replaceAll("^[.]+|[.]+$", "");
        if (clean.isEmpty()) clean = "model";
        return trimToLength(clean, 70) + "-" + md5Text(model.trim()).substring(0, 8);
    }
    private String sanitizeFileName(String value) {
        String name = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        name = trimToLength(name, 255); return name.isBlank() ? "firmware.pack" : name;
    }
    private String md5(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) { in.transferTo(java.io.OutputStream.nullOutputStream()); }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String md5Text(String value) {
        try { return toHex(MessageDigest.getInstance("MD5").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String toHex(byte[] bytes) { StringBuilder b = new StringBuilder(); for (byte x : bytes) b.append(String.format(Locale.ROOT, "%02x", x)); return b.toString(); }
    private String token() { return UUID.randomUUID().toString().replace("-", ""); }
    private String idForLog() { return token(); }
    private void deleteObject(String key) { try { storage.delete(key); } catch (IOException e) { System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "对象清理失败: {0}", key); } }
    private void deleteQuietly(String key) { if (key != null) deleteObject(key); }
    private void validateRequired(String v, String label, int max) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        if (v.trim().length() > max) throw new IllegalArgumentException(label + "长度不能超过 " + max + " 个字符");
    }
    private String trimToLength(String v, int max) { return v == null || v.length() <= max ? v : v.substring(0, max); }
}
