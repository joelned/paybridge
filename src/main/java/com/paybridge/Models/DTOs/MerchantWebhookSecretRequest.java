package com.paybridge.Models.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MerchantWebhookSecretRequest {

    @NotBlank(message = "Secret is required")
    @Size(max = 255, message = "Secret is too long")
    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
