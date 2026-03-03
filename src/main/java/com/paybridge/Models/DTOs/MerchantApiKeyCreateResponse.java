package com.paybridge.Models.DTOs;

import com.paybridge.Models.Enums.ApiKeyMode;

import java.time.LocalDateTime;

public class MerchantApiKeyCreateResponse {

    private String keyId;
    private ApiKeyMode mode;
    private String label;
    private String key;
    private LocalDateTime createdAt;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public ApiKeyMode getMode() {
        return mode;
    }

    public void setMode(ApiKeyMode mode) {
        this.mode = mode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
