package com.paybridge.Models.DTOs;

import java.util.Map;

public class ProviderConfiguration {
    private String name;
    private Map<String, Object> config;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    /**
     * Validates if the configuration is valid
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty()
                && config != null && !config.isEmpty();
    }
}