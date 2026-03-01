package com.paybridge.Configs;

import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Services.ConnectionTestResult;

import java.util.Map;

public interface PaymentProvider {
    ConnectionTestResult testConnection(Map<String, Object> credentials);
    PaymentProviderResponse CreatePaymentRequest(CreatePaymentRequest request, Map<String, Object> credentials);
    String getProviderName();
}
