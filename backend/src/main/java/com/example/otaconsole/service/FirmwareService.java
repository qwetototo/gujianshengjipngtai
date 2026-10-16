package com.example.otaconsole.service;

import com.example.otaconsole.model.FirmwareRecord;
import com.example.otaconsole.model.OperationLog;
import com.example.otaconsole.model.VersionResponse;
import com.example.otaconsole.repository.FirmwareRepository;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String MANIFEST_VALUE = "^[A-Za-z0-9][A-Za-z0-9._-]*$";
    private final StorageCapacityService capacity;
    private final ObjectStorageService storage;
    private final FirmwareRepository repository;
    private final TransactionTemplate transactions;
    private final int retainedHistoricalVersions;

    public FirmwareService(StorageCapacityService capacity, ObjectStorageService storage,
                           FirmwareRepository repository, PlatformTransactionManager transactionManager,
                           @Value("${ota.firmware.history-retain-count:5}") int retainedHistoricalVersions) {
        this.capacity = capacity;
        this.storage = storage;
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.retainedHistoricalVersions = Math.max(0, retainedHistoricalVersions);
    }

    public FirmwareRecord upload(String product, String hardwareId, String partitionId,
                                 String sdkCompat, String targetBuild, String version, Long versionCode,
                                 Long workSize, String description, String releaseNotes, String uploader,
                                 MultipartFile file) throws IOException {
        validateManifestValue(product, "product", 63);
        validateManifestValue(hardwareId, "hardware_id", 63);
        validateManifestValue(partitionId, "partition_id", 63);
        validateManifestValue(sdkCompat, "sdk_compat", 63);
        validateManifestValue(targetBuild, "target_build", 63);
        validateManifestValue(version, "version", 31);
        validateRequired(uploader, "上传人", 120);
        if (versionCode == null || versionCode < 1 || versionCode > 4_294_967_295L)
            throw new IllegalArgumentException("version_code 必须在 1 到 4294967295 之间");
        if (workSize == null || workSize < 0 || workSize > 4_294_967_295L)
            throw new IllegalArgumentException("work_size 必须在 0 到 4294967295 之间");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("固件文件不能为空");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        if (!original.toLowerCase(Locale.ROOT).endsWith(".pack")) throw new IllegalArgumentException("只允许上传 .pack 固件文件");
        String model = product.trim();
        String safeName = sanitizeFileName(original);
        capacity.lockForOperation(file.getSize(), 0);
        String storedKey = modelDirectory(model) + "/releases/" + token() + "_" + safeName;
        try {
            MessageDigest md5Digest = digestOf("MD5");
            MessageDigest sha256Digest = digestOf("SHA-256");
            try (InputStream input = file.getInputStream()) {
                storage.put(storedKey, new DigestInputStream(new DigestInputStream(input, sha256Digest), md5Digest), file.getSize());
            }
            String md5 = toHex(md5Digest.digest());
            String sha256 = toHex(sha256Digest.digest());
            FirmwareRecord record = new FirmwareRecord();
            record.setProduct(product.trim());
            record.setHardwareId(hardwareId.trim());
            record.setPartitionId(partitionId.trim());
            record.setSdkCompat(sdkCompat.trim());
            record.setTargetBuild(targetBuild.trim());
            record.setVersion(version.trim());
            record.setVersionCode(versionCode);
            record.setWorkSize(workSize);
            record.setDescription(description == null || description.trim().isEmpty() ? "（无简述）" : trimToLength(description.trim(), 1000));
            record.setReleaseNotes(releaseNotes == null ? "" : releaseNotes.trim());
            if (record.getReleaseNotes().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024)
                throw new IllegalArgumentException("release_notes 最多 1024 个 UTF-8 字节");
            record.setOriginalFileName(safeName);
            record.setStoredFileName(storedKey);
            record.setFileSize(file.getSize());
            record.setMd5(md5);
            record.setSha256(sha256);
            record.setUploadTime(LocalDateTime.now());
            record.setUploader(uploader.trim());
            record.setLatest(true);

            List<FirmwareRecord> expired = transactions.execute(status -> {
                record.setId(repository.insert(record));
                repository.insertLog(new OperationLog(idForLog(), LocalDateTime.now(), "upload", model, record.getVersion(),
                        record.getUploader(), "发布 " + safeName));
                List<FirmwareRecord> stale = repository.findExpiredVersions(model, retainedHistoricalVersions);
                stale.forEach(item -> repository.delete(item.getId()));
                return stale;
            });
            if (expired != null) expired.forEach(item -> deleteObject(item.getStoredFileName()));
            return record;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(storedKey);
            throw e;
        } finally {
            capacity.unlockUpload();
        }
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
        transactions.executeWithoutResult(status -> {
            repository.delete(id);
            repository.insertLog(new OperationLog(idForLog(), LocalDateTime.now(), "delete", record.getProduct(), record.getVersion(),
                    trimToLength(operator, 120), "删除 " + record.getOriginalFileName()));
        });
        storage.delete(record.getStoredFileName());
        return record;
    }

    public List<OperationLog> logs() { return repository.findLogs(); }
    public Optional<VersionResponse> latestFor(String product, String contextPath) {
        return repository.findLatest(product).map(record -> {
            String url = contextPath.replaceAll("/+$", "") + "/api/firmware/download/" + record.getId();
            if (url.length() > 256 || !url.chars().allMatch(ch -> ch >= 0x20 && ch <= 0x7e))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "下载地址必须是 256 字节以内的 ASCII URL");
            return new VersionResponse(record, url);
        });
    }

    public void recordDownload(FirmwareRecord r, String operator) {
        String who = operator == null || operator.isBlank() ? "device" : trimToLength(operator.trim(), 120);
        repository.insertLog(new OperationLog(idForLog(), LocalDateTime.now(), "download", r.getProduct(), r.getVersion(), who,
                "下载 " + r.getFileSize() + " 字节"));
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
    private MessageDigest digestOf(String algorithm) {
        try { return MessageDigest.getInstance(algorithm); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
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
    private void validateManifestValue(String value, String label, int max) {
        validateRequired(value, label, max);
        if (value.trim().length() > max || !value.trim().matches(MANIFEST_VALUE))
            throw new IllegalArgumentException(label + "仅允许 ASCII 字母、数字、点、下划线和连字符，且须以字母或数字开头");
    }
    private String trimToLength(String v, int max) { return v == null || v.length() <= max ? v : v.substring(0, max); }
}
