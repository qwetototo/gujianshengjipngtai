package com.example.otaconsole.service;

import io.minio.CopyObjectArgs;
import io.minio.SourceObject;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class ObjectStorageService {
    private final String bucket;
    private final MinioClient client;
    private final String backend;
    private final Path localRoot;

    public ObjectStorageService(@Value("${ota.object-storage.endpoint}") String endpoint,
                               @Value("${ota.object-storage.access-key}") String accessKey,
                               @Value("${ota.object-storage.secret-key}") String secretKey,
                               @Value("${ota.object-storage.bucket}") String bucket,
                               @Value("${ota.object-storage.backend:filesystem}") String backend,
                               @Value("${ota.storage-dir:E:/固件暂存}") String localRoot) {
        this.bucket = bucket;
        this.backend = backend;
        this.localRoot = Path.of(localRoot).toAbsolutePath().normalize();
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @PostConstruct
    void ensureBucket() {
        if ("filesystem".equalsIgnoreCase(backend)) {
            try { Files.createDirectories(localRoot); } catch (IOException e) { throw new IllegalStateException("无法初始化本地固件目录", e); }
            return;
        }
        try {
            if (!client.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化对象存储 bucket", e);
        }
    }

    public void put(String key, InputStream stream, long size) throws IOException {
        if ("filesystem".equalsIgnoreCase(backend)) {
            Path target = localPath(key); Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING); return;
        }
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(stream, size, -1L)
                    .contentType("application/octet-stream").build());
        } catch (Exception e) { throw new IOException("上传固件到对象存储失败", e); }
    }

    public void copy(String source, String destination) throws IOException {
        if ("filesystem".equalsIgnoreCase(backend)) {
            Path target = localPath(destination); Files.createDirectories(target.getParent());
            Files.copy(localPath(source), target, StandardCopyOption.REPLACE_EXISTING); return;
        }
        try {
            client.copyObject(CopyObjectArgs.builder().bucket(bucket).object(destination)
                    .source(SourceObject.builder().bucket(bucket).object(source).build()).build());
        } catch (Exception e) { throw new IOException("对象存储内固件复制失败", e); }
    }

    public boolean exists(String key) throws IOException {
        if ("filesystem".equalsIgnoreCase(backend)) return Files.isRegularFile(localPath(key));
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (io.minio.errors.ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchObject".equals(e.errorResponse().code())) return false;
            throw new IOException("检查对象存储文件失败", e);
        } catch (Exception e) { throw new IOException("检查对象存储文件失败", e); }
    }

    public Resource get(String key) throws IOException {
        if ("filesystem".equalsIgnoreCase(backend)) return new InputStreamResource(Files.newInputStream(localPath(key)));
        try {
            return new InputStreamResource(client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build()));
        } catch (Exception e) { throw new IOException("读取对象存储文件失败", e); }
    }

    public void delete(String key) throws IOException {
        if ("filesystem".equalsIgnoreCase(backend)) { Files.deleteIfExists(localPath(key)); return; }
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception e) { throw new IOException("删除对象存储文件失败", e); }
    }

    private Path localPath(String key) throws IOException {
        Path path = localRoot.resolve(key).normalize();
        if (!path.startsWith(localRoot)) throw new IOException("非法对象键");
        return path;
    }
}
