package com.paybridge.Configs;

import com.paybridge.Services.ConnectionTestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaystackPaymentProviderTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PaystackPaymentProvider paystackPaymentProvider;

    @Test
    void testConnection_Success() {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_123");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("status", true);
        responseBody.put("message", "Authorization successful");

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        ConnectionTestResult result = paystackPaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Paystack configuration test successful");
    }

    @Test
    void testConnection_Failure_StatusFalseResponse() {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_invalid");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("status", false);
        responseBody.put("message", "Invalid key");

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        ConnectionTestResult result = paystackPaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Invalid key");
    }

    @Test
    void testConnection_Failure_InvalidKeyUnauthorized() {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_invalid");

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        // When
        ConnectionTestResult result = paystackPaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Paystack authentication failed");
    }

    @Test
    void testConnection_MissingSecretKey() {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        // Missing secretKey

        // When
        ConnectionTestResult result = paystackPaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Secret key is required");
    }
}
