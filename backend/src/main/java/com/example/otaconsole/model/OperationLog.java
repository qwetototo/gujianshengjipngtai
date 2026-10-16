package com.example.otaconsole.model;

import java.time.LocalDateTime;

public class OperationLog {
    private String id;
    private LocalDateTime occurredAt;
    private String action;
    private String product;
    private String version;
    private String operator;
    private String detail;

    public OperationLog() {}

    public OperationLog(String id, LocalDateTime occurredAt, String action, String product, String version, String operator, String detail) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.action = action;
        this.product = product;
        this.version = version;
        this.operator = operator;
        this.detail = detail;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
