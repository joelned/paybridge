package com.paybridge.Configs;

import com.paybridge.Configs.FlutterwavePaymentProvider;
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
class FlutterwavePaymentProviderTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FlutterwavePaymentProvider flutterwavePaymentProvider;

    @Test
    void testConnection_Success() {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("clientId", "client_123");
        credentials.put("clientSecret", "secret_123");

        FlutterwavePaymentProvider.TokenResponse mockResponse = new FlutterwavePaymentProvider.TokenResponse();
        mockResponse.setAccessToken("access_token_123");
        mockResponse.setExpiresIn(3600);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FlutterwavePaymentProvider.TokenResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        ConnectionTestResult result = flutterwavePaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Flutterwave credentials are valid");
    }

    @Test
    void testConnection_Failure_Unauthorized() {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("clientId", "client_invalid");
        credentials.put("clientSecret", "secret_invalid");

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FlutterwavePaymentProvider.TokenResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        // When
        ConnectionTestResult result = flutterwavePaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Invalid client credentials");
    }

    @Test
    void testConnection_MissingCredentials() {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        // Missing clientId and clientSecret

        // When
        ConnectionTestResult result = flutterwavePaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Client ID is required");
    }
}
