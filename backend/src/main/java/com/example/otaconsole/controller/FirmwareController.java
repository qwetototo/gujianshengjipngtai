package com.example.otaconsole.controller;

import com.example.otaconsole.model.FirmwareRecord;
import com.example.otaconsole.model.OperationLog;
import com.example.otaconsole.model.VersionResponse;
import com.example.otaconsole.service.FirmwareService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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

    public FirmwareController(FirmwareService firmwareService) {
        this.firmwareService = firmwareService;
    }

    @GetMapping("/admin/firmware")
    public List<FirmwareRecord> list() {
        return firmwareService.list();
    }

    @PostMapping(value = "/admin/firmware/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("device_model") String deviceModel,
                                      @RequestParam("version") String version,
                                      @RequestParam(value = "description", defaultValue = "") String description,
                                      @RequestParam("uploader") String uploader,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        FirmwareRecord record = firmwareService.upload(deviceModel, version, description, uploader, file);
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

    @PostMapping("/admin/firmware/{id}/rollback")
    public FirmwareRecord rollback(@PathVariable long id,
                                   @RequestParam(defaultValue = "admin") String operator) throws IOException {
        return firmwareService.rollback(id, operator);
    }

    @GetMapping("/logs")
    public List<OperationLog> logs() {
        return firmwareService.logs();
    }

    @GetMapping("/firmware/version")
    public ResponseEntity<?> version(@RequestParam("device_model") String deviceModel,
                                     @RequestParam("current_version") String currentVersion) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        var latest = firmwareService.latestFor(deviceModel, baseUrl);
        if (latest.isPresent() && !latest.get().getVersion().equals(currentVersion)) {
            return ResponseEntity.ok(latest.get());
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", "No new version available");
        return ResponseEntity.ok().body(body);
    }
}
