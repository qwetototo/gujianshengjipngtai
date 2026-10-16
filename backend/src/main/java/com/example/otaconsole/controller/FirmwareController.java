package com.example.otaconsole.controller;

import com.example.otaconsole.model.FirmwareRecord;
import com.example.otaconsole.model.OperationLog;
import com.example.otaconsole.model.VersionResponse;
import com.example.otaconsole.service.FirmwareService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FirmwareController {
    private final FirmwareService firmwareService;
    private final ObjectMapper objectMapper;

    public FirmwareController(FirmwareService firmwareService, ObjectMapper objectMapper) {
        this.firmwareService = firmwareService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/admin/firmware")
    public List<FirmwareRecord> list() {
        return firmwareService.list();
    }

    @PostMapping(value = "/admin/firmware/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("product") String product,
                                      @RequestParam("hardware_id") String hardwareId,
                                      @RequestParam("partition_id") String partitionId,
                                      @RequestParam("sdk_compat") String sdkCompat,
                                      @RequestParam("target_build") String targetBuild,
                                      @RequestParam("version") String version,
                                      @RequestParam("version_code") Long versionCode,
                                      @RequestParam("work_size") Long workSize,
                                      @RequestParam(value = "description", defaultValue = "") String description,
                                      @RequestParam(value = "release_notes", defaultValue = "") String releaseNotes,
                                      @RequestParam("uploader") String uploader,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        FirmwareRecord record = firmwareService.upload(product, hardwareId, partitionId, sdkCompat,
                targetBuild, version, versionCode, workSize, description, releaseNotes, uploader, file);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "上传成功");
        body.put("data", record);
        return body;
    }

    @GetMapping("/admin/firmware/{id}")
    public ResponseEntity<FirmwareRecord> detail(@PathVariable long id) {
        return firmwareService.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/firmware/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable long id,
                                             @RequestParam(defaultValue = "device") String operator) {
        FirmwareRecord record = firmwareService.find(id).orElseThrow(() -> new IllegalArgumentException("固件不存在"));
        firmwareService.recordDownload(record, operator);
        Resource resource = firmwareService.file(id);
        String encoded = URLEncoder.encode(record.getOriginalFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(record.getFileSize())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/admin/firmware/{id}")
    public FirmwareRecord delete(@PathVariable long id,
                                 @RequestParam(defaultValue = "admin") String operator) throws IOException {
        return firmwareService.delete(id, operator);
    }

    @GetMapping("/logs")
    public List<OperationLog> logs() {
        return firmwareService.logs();
    }

    @GetMapping("/firmware/version")
    public ResponseEntity<?> version(@RequestParam("product") String product) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        VersionResponse manifest = firmwareService.latestFor(product, baseUrl).orElse(null);
        if (manifest == null) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("message", "未找到该 product 的已发布固件");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        // 契约约束：完整清单正文不超过 2048 字节；同版本时也必须返回完整清单，由 App 判断是否升级。
        try {
            byte[] payload = objectMapper.writeValueAsBytes(manifest);
            if (payload.length > 2048) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "清单超过 2048 字节（当前 " + payload.length + "），请精简 release_notes"));
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(payload);
        } catch (JacksonException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "清单序列化失败"));
        }
    }
}
