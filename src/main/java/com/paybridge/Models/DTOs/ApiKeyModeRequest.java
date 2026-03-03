package com.paybridge.Models.DTOs;

import com.paybridge.Models.Enums.ApiKeyMode;
import jakarta.validation.constraints.NotNull;

public class ApiKeyModeRequest {

    @NotNull(message = "API key mode is required")
    private ApiKeyMode mode;

    public ApiKeyMode getMode() {
        return mode;
    }

    public void setMode(ApiKeyMode mode) {
        this.mode = mode;
    }
}
