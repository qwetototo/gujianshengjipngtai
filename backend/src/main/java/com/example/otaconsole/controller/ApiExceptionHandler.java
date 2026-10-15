package com.example.otaconsole.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 400);
        body.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(body);
    }
}
