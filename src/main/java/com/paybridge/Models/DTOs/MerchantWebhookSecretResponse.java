package com.paybridge.Models.DTOs;

public class MerchantWebhookSecretResponse {

    private String provider;
    private boolean configured;
    private String maskedSecret;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getMaskedSecret() {
        return maskedSecret;
    }

    public void setMaskedSecret(String maskedSecret) {
        this.maskedSecret = maskedSecret;
    }
}
