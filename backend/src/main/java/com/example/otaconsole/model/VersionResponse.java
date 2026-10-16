package com.example.otaconsole.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public class VersionResponse {
    private final int schemaVersion = 2;
    private final String product;
    private final String hardwareId;
    private final String partitionId;
    private final String packageType = "full";
    private final String updateScope = "APPIMG";
    private final int minProtocol = 2;
    private final String sdkCompat;
    private final String targetBuild;
    private final String version;
    private final long versionCode;
    private final long size;
    private final String md5;
    private final String sha256;
    private final String packageId;
    private final long workSize;
    private final String url;
    private final String releaseNotes;

    public VersionResponse(FirmwareRecord record, String url) {
        this.product = record.getProduct();
        this.hardwareId = record.getHardwareId();
        this.partitionId = record.getPartitionId();
        this.sdkCompat = record.getSdkCompat();
        this.targetBuild = record.getTargetBuild();
        this.version = record.getVersion();
        this.versionCode = record.getVersionCode();
        this.size = record.getFileSize();
        this.md5 = record.getMd5();
        this.sha256 = record.getSha256();
        this.packageId = record.getMd5();
        this.workSize = record.getWorkSize();
        this.url = url;
        this.releaseNotes = record.getReleaseNotes();
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getProduct() { return product; }
    public String getHardwareId() { return hardwareId; }
    public String getPartitionId() { return partitionId; }
    public String getPackageType() { return packageType; }
    public String getUpdateScope() { return updateScope; }
    public int getMinProtocol() { return minProtocol; }
    public String getSdkCompat() { return sdkCompat; }
    public String getTargetBuild() { return targetBuild; }
    public String getVersion() { return version; }
    public long getVersionCode() { return versionCode; }
    public long getSize() { return size; }
    public String getMd5() { return md5; }
    public String getSha256() { return sha256; }
    public String getPackageId() { return packageId; }
    public long getWorkSize() { return workSize; }
    public String getUrl() { return url; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getReleaseNotes() { return releaseNotes; }
}
