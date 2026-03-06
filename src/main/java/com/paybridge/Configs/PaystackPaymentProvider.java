package com.paybridge.Configs;

import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Services.ConnectionTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PaystackPaymentProvider implements PaymentProvider {
    private static final Logger logger = LoggerFactory.getLogger(PaystackPaymentProvider.class);
    private static final String PAYSTACK_VERIFY_KEY_URL = "https://api.paystack.co/transaction?perPage=1&page=1";
    private static final String PAYSTACK_INITIALIZE_URL = "https://api.paystack.co/transaction/initialize";

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ConnectionTestResult testConnection(Map<String, Object> credentials) {
        String secretKey = normalizeSecretKey(credentials.get("secretKey"));
        if (secretKey == null || secretKey.isBlank()) {
            return ConnectionTestResult.failure("Secret key is required");
        }
        if (secretKey.contains(" ")) {
            return ConnectionTestResult.failure("Secret key format is invalid");
        }

        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setBearerAuth(secretKey);
            HttpEntity<Void> httpEntity = new HttpEntity<>(httpHeaders);

            ResponseEntity<Map> restResponse = restTemplate.exchange(
                    PAYSTACK_VERIFY_KEY_URL,
                    HttpMethod.GET,
                    httpEntity,
                    Map.class
            );

            if (!restResponse.getStatusCode().is2xxSuccessful()) {
                return ConnectionTestResult.failure("Unexpected response " + restResponse.getStatusCode());
            }

            Map responseBody = restResponse.getBody();
            if (responseBody == null) {
                return ConnectionTestResult.failure("Empty response from Paystack");
            }

            boolean status = Boolean.TRUE.equals(responseBody.get("status"));
            String message = responseBody.get("message") instanceof String
                    ? (String) responseBody.get("message")
                    : "Unable to verify Paystack key";

            if (!status) {
                return ConnectionTestResult.failure("Paystack connection test failed: " + message);
            }

            return ConnectionTestResult.success("Paystack configuration test successful");
        } catch (HttpStatusCodeException ex) {
            logger.warn("Paystack connection test failed with status {}: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return ConnectionTestResult.failure("Paystack authentication failed (" + ex.getStatusCode() + ")");
        } catch (Exception ex) {
            logger.error("Paystack connection test error", ex);
            return ConnectionTestResult.failure("Paystack connection test failed");
        }
    }

    @Override
    public PaymentProviderResponse CreatePaymentRequest(CreatePaymentRequest request, Map<String, Object> credentials) {
        //get the secret key
        String secretKey = normalizeSecretKey(credentials.get("secretKey"));

        //check if secret key is null or blank (null check)
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Paystack secretKey is required");
        }
        if (secretKey.contains(" ")) {
            throw new IllegalArgumentException("Paystack secretKey format is invalid");
        }

        //check if email is null or blank (null check)
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Customer email is required for Paystack payments");
        }

        //check if amount is empty (null check)
        if(request.getAmount() == null){
            throw new IllegalArgumentException("Amount is not set");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(secretKey);

        String reference = request.getTransactionReference();
        if (reference == null || reference.isBlank()) {
            reference = UUID.randomUUID().toString();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", request.getEmail());
        payload.put("amount", toMinorUnit(request.getAmount()));
        payload.put("reference", reference);
        payload.put("currency", request.getCurrency());

        if (request.getRedirectUrl() != null && !request.getRedirectUrl().isBlank()) {
            payload.put("callback_url", request.getRedirectUrl());
        }
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            payload.put("metadata", request.getMetadata());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    PAYSTACK_INITIALIZE_URL,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map responseBody = responseEntity.getBody();
            if (responseBody == null || !Boolean.TRUE.equals(responseBody.get("status"))) {
                throw new RuntimeException("Paystack initialization failed");
            }

            Object dataObj = responseBody.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                throw new RuntimeException("Unexpected Paystack response structure");
            }

            PaymentProviderResponse response = new PaymentProviderResponse();
            response.setProviderPaymentId(reference);
            response.setStatus("pending");

            Object checkoutUrl = data.get("authorization_url");
            if (checkoutUrl != null) {
                response.setCheckoutUrl(checkoutUrl.toString());
            }

            return response;
        } catch (Exception ex) {
            throw new RuntimeException("Paystack payment creation failed: " + ex.getMessage(), ex);
        }

    }

    @Override
    public String getProviderName() {
        return "paystack";
    }

    private long toMinorUnit(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        return amount.movePointRight(2).longValueExact();
    }

    private String normalizeSecretKey(Object rawSecretKey) {
        if (!(rawSecretKey instanceof String value)) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }

        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            if (normalized.length() > 1) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }

        return normalized;
    }
}
