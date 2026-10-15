package com.example.otaconsole.model;

public class StorageStatus {
    private final long totalBytes;
    private final long availableBytes;
    private final double availablePercent;
    private final boolean warning;
    private final boolean uploadBlocked;
    private final String message;

    public StorageStatus(long totalBytes, long availableBytes, double availablePercent,
                         boolean warning, boolean uploadBlocked, String message) {
        this.totalBytes = totalBytes;
        this.availableBytes = availableBytes;
        this.availablePercent = availablePercent;
        this.warning = warning;
        this.uploadBlocked = uploadBlocked;
        this.message = message;
    }

    public long getTotalBytes() { return totalBytes; }
    public long getAvailableBytes() { return availableBytes; }
    public double getAvailablePercent() { return availablePercent; }
    public boolean isWarning() { return warning; }
    public boolean isUploadBlocked() { return uploadBlocked; }
    public String getMessage() { return message; }
}
