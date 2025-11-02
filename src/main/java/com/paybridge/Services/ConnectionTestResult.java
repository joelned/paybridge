package com.paybridge.Services;

import java.util.HashMap;
import java.util.Map;

public class ConnectionTestResult {
    private boolean success;
    private String message;
    private Map<String, Object> metadata;
    private long testDurationMs;

    private ConnectionTestResult(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.metadata = new HashMap<>();
    }

    public static ConnectionTestResult success(String message) {
        return new ConnectionTestResult(true, message);
    }

    public static ConnectionTestResult failure(String message) {
        return new ConnectionTestResult(false, message);
    }

    public ConnectionTestResult withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public ConnectionTestResult withDuration(long durationMs) {
        this.testDurationMs = durationMs;
        return this;
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public long getTestDurationMs() {
        return testDurationMs;
    }
}