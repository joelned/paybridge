package com.paybridge.Configs;

import com.paybridge.Configs.StripePaymentProvider;
import com.paybridge.Services.ConnectionTestResult;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.Answers;

@ExtendWith(MockitoExtension.class)
class StripePaymentProviderTest {

    @Spy
    @InjectMocks
    private StripePaymentProvider stripePaymentProvider;

    @Test
    void testConnection_Success() throws StripeException {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_123");

        StripeClient mockClient = mock(StripeClient.class, Answers.RETURNS_DEEP_STUBS);
        Customer mockCustomer = mock(Customer.class);

        // Mock the chain: client.v1().customers().create(...)
        when(mockClient.v1().customers().create(any(CustomerCreateParams.class))).thenReturn(mockCustomer);

        // Mock the protected method to return our mock client
        doReturn(mockClient).when(stripePaymentProvider).createStripeClient("sk_test_123");

        // When
        ConnectionTestResult result = stripePaymentProvider.testConnection(credentials);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successful");
    }

    @Test
    void testConnection_Failure_ThrowsException() throws StripeException {
        // Given
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_invalid");

        StripeClient mockClient = mock(StripeClient.class, Answers.RETURNS_DEEP_STUBS);

        when(mockClient.v1().customers().create(any(CustomerCreateParams.class)))
                .thenThrow(new StripeException("Invalid API Key", "req_123", "invalid_request_error", 401) {});

        doReturn(mockClient).when(stripePaymentProvider).createStripeClient("sk_test_invalid");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            stripePaymentProvider.testConnection(credentials);
        });
        
        assertThat(exception.getCause()).isInstanceOf(StripeException.class);
    }
}
