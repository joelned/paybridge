package com.paybridge.Models.DTOs;

import com.paybridge.Models.Enums.ApiKeyMode;

import java.time.LocalDateTime;

public class MerchantApiKeySummaryResponse {

    private String keyId;
    private ApiKeyMode mode;
    private String label;
    private String maskedKey;
    private boolean active;
    private LocalDateTime updatedAt;

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

    public String getMaskedKey() {
        return maskedKey;
    }

    public void setMaskedKey(String maskedKey) {
        this.maskedKey = maskedKey;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
