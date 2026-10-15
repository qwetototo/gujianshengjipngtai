package com.example.otaconsole.service;

import com.example.otaconsole.model.OperationLog;
import com.example.otaconsole.model.StorageStatus;
import com.example.otaconsole.repository.FirmwareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class StorageAlertMonitor {
    private static final Logger log = LoggerFactory.getLogger(StorageAlertMonitor.class);

    private final StorageCapacityService storage;
    private final FirmwareRepository repository;
    private Boolean lastWarning;

    public StorageAlertMonitor(StorageCapacityService storage, FirmwareRepository repository) {
        this.storage = storage;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${ota.storage.monitor-interval:60000}",
            initialDelayString = "${ota.storage.monitor-initial-delay:5000}")
    public synchronized void checkStorage() {
        try {
            StorageStatus status = storage.status();
            if (lastWarning == null) {
                lastWarning = false;
            }
            if (status.isWarning() == lastWarning) {
                return;
            }

            String action = status.isWarning() ? "storage_warning" : "storage_recovered";
            String message = status.getMessage();
            OperationLog entry = new OperationLog(UUID.randomUUID().toString().replace("-", ""),
                    LocalDateTime.now(), action, "系统", "存储空间", "system", message);
            repository.insertLog(entry);
            if (status.isWarning()) {
                log.warn("Firmware storage warning: {}", message);
            } else {
                log.info("Firmware storage recovered: {}", message);
            }
            lastWarning = status.isWarning();
        } catch (IOException exception) {
            log.error("Unable to inspect firmware storage", exception);
        } catch (RuntimeException exception) {
            log.error("Unable to persist firmware storage alert", exception);
        }
    }
}
