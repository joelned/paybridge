package com.paybridge.Models.DTOs;

import java.util.Map;

public class ProviderConfiguration {
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String name;
   private Map<String, Object> config;

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}
