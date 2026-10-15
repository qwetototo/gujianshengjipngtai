package com.example.otaconsole.model;

public class VersionResponse {
    private String version;
    private String url;
    private long size;
    private String md5;

    public VersionResponse(String version, String url, long size, String md5) {
        this.version = version;
        this.url = url;
        this.size = size;
        this.md5 = md5;
    }

    public String getVersion() { return version; }
    public String getUrl() { return url; }
    public long getSize() { return size; }
    public String getMd5() { return md5; }
}
