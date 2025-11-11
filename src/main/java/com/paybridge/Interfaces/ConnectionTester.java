package com.paybridge.Interfaces;

import com.paybridge.Services.ConnectionTestResult;

import java.util.Map;

public interface ConnectionTester {
    ConnectionTestResult testConnection(Map<String, Object> credentials);
}
