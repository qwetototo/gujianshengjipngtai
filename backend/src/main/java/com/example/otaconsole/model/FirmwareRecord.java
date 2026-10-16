package com.example.otaconsole.model;

import java.time.LocalDateTime;

public class FirmwareRecord {
    private long id;
    private String product;
    private String version;
    private String description;
    private long fileSize;
    private String md5;
    private String sha256;
    private String originalFileName;
    private String storedFileName;
    private LocalDateTime uploadTime;
    private String uploader;
    private boolean latest;
    private String hardwareId;
    private String partitionId;
    private String sdkCompat;
    private String targetBuild;
    private Long versionCode;
    private Long workSize;
    private String releaseNotes;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
    public String getUploader() { return uploader; }
    public void setUploader(String uploader) { this.uploader = uploader; }
    public boolean isLatest() { return latest; }
    public void setLatest(boolean latest) { this.latest = latest; }
    public String getHardwareId() { return hardwareId; }
    public void setHardwareId(String hardwareId) { this.hardwareId = hardwareId; }
    public String getPartitionId() { return partitionId; }
    public void setPartitionId(String partitionId) { this.partitionId = partitionId; }
    public String getSdkCompat() { return sdkCompat; }
    public void setSdkCompat(String sdkCompat) { this.sdkCompat = sdkCompat; }
    public String getTargetBuild() { return targetBuild; }
    public void setTargetBuild(String targetBuild) { this.targetBuild = targetBuild; }
    public Long getVersionCode() { return versionCode; }
    public void setVersionCode(Long versionCode) { this.versionCode = versionCode; }
    public Long getWorkSize() { return workSize; }
    public void setWorkSize(Long workSize) { this.workSize = workSize; }
    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
}
