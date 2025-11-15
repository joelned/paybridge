package com.paybridge.Configs;

import com.paybridge.Services.ConnectionTestResult;

import java.util.Map;

public interface PaymentProvider {
    ConnectionTestResult testConnection(Map<String, Object> credentials);
    String getProviderName();
}
