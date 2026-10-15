package com.example.otaconsole.service;

import com.example.otaconsole.model.StorageStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class StorageCapacityService {
    private final Path storageRoot;
    private final int warningFreePercent;
    private final long warningFreeBytes;
    private final long uploadReserveBytes;
    private final boolean objectStorage;
    private final ReentrantLock uploadLock = new ReentrantLock();

    public StorageCapacityService(@Value("${ota.storage-dir}") String storageDir,
                                  @Value("${ota.storage.warning-free-percent:15}") int warningFreePercent,
                                  @Value("${ota.storage.warning-free-bytes:10737418240}") long warningFreeBytes,
                                  @Value("${ota.storage.upload-reserve-bytes:2147483648}") long uploadReserveBytes,
                                  @Value("${ota.object-storage.backend:filesystem}") String storageBackend) {
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
        this.warningFreePercent = warningFreePercent;
        this.warningFreeBytes = warningFreeBytes;
        this.uploadReserveBytes = uploadReserveBytes;
        this.objectStorage = "minio".equalsIgnoreCase(storageBackend);
    }

    @PostConstruct
    void initialize() throws IOException {
        if (!objectStorage) Files.createDirectories(storageRoot);
        if (!Files.isDirectory(storageRoot) || (!objectStorage && !Files.isWritable(storageRoot)))
            throw new IllegalStateException("固件存储/监控目录不可用: " + storageRoot);
    }

    public Path storageRoot() {
        return storageRoot;
    }

    public void lockForUpload(long incomingBytes) {
        lockForOperation(incomingBytes, 0);
    }

    public void lockForOperation(long bytesToCopy, long bytesToArchive) {
        uploadLock.lock();
        boolean admitted = false;
        try {
            FileStore store = Files.getFileStore(storageRoot);
            long minimumRequired;
            try {
                minimumRequired = Math.addExact(Math.addExact(bytesToCopy, bytesToArchive), uploadReserveBytes);
            } catch (ArithmeticException overflow) {
                throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "文件过大，无法安全上传");
            }
            if (store.getUsableSpace() < minimumRequired) {
                throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE,
                        "存储空间不足：操作完成后需至少保留 2GB 可用空间");
            }
            admitted = true;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取固件存储空间", exception);
        } finally {
            if (!admitted) {
                uploadLock.unlock();
            }
        }
    }

    public void unlockUpload() {
        if (uploadLock.isHeldByCurrentThread()) {
            uploadLock.unlock();
        }
    }

    public StorageStatus status() throws IOException {
        FileStore store = Files.getFileStore(storageRoot);
        long total = store.getTotalSpace();
        long available = store.getUsableSpace();
        double percent = total == 0 ? 0 : available * 100.0 / total;
        boolean warning = percent < warningFreePercent || available < warningFreeBytes;
        boolean blocked = available < uploadReserveBytes;
        String message = warning
                ? "固件存储空间不足，请尽快扩容或迁移。可用 " + formatBytes(available) + " / " + formatBytes(total)
                : "固件存储空间正常，可用 " + formatBytes(available) + " / " + formatBytes(total);
        return new StorageStatus(total, available, Math.round(percent * 100.0) / 100.0, warning, blocked, message);
    }

    private String formatBytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
