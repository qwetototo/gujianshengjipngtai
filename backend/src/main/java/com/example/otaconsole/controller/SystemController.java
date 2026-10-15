package com.example.otaconsole.controller;

import com.example.otaconsole.model.StorageStatus;
import com.example.otaconsole.service.StorageCapacityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final StorageCapacityService storageCapacityService;

    public SystemController(StorageCapacityService storageCapacityService) {
        this.storageCapacityService = storageCapacityService;
    }

    @GetMapping("/storage")
    public StorageStatus storage() throws IOException {
        return storageCapacityService.status();
    }
}
