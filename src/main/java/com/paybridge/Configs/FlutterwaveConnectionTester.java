package com.paybridge.Configs;

import com.paybridge.Services.ConnectionTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class FlutterwaveConnectionTester {

    private static final Logger logger = LoggerFactory.getLogger(FlutterwaveConnectionTester.class);
    private static final String FLUTTERWAVE_BASE_URL = "https://api.flutterwave.com/v3";

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Test Flutterwave API connection by retrieving account balance
     */
    public ConnectionTestResult testConnection(Map<String, Object> credentials) {
        String secretKey = (String) credentials.get("secretKey");

        if (secretKey == null || secretKey.trim().isEmpty()) {
            return ConnectionTestResult.failure("Secret key is required");
        }

        // Validate key format
        if (!secretKey.startsWith("FLWSECK-") && !secretKey.startsWith("FLWSECK_TEST-")) {
            return ConnectionTestResult.failure("Invalid secret key format");
        }

        try {
            // Create headers with authorization
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + secretKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Make a simple API call to verify credentials
            // Get balance endpoint is lightweight and doesn't modify anything
            String url = FLUTTERWAVE_BASE_URL + "/balances";

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                String status = (String) responseBody.get("status");

                if ("success".equals(status)) {
                    logger.info("Flutterwave connection test successful");

                    // Extract balance data
                    Object dataObj = responseBody.get("data");

                    return ConnectionTestResult.success("Flutterwave connection successful")
                            .withMetadata("environment", secretKey.contains("TEST") ? "test" : "live")
                            .withMetadata("status", status);
                } else {
                    return ConnectionTestResult.failure("Unexpected response status: " + status);
                }
            } else {
                return ConnectionTestResult.failure("Unexpected response code: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            logger.error("Flutterwave connection test failed: {}", e.getMessage());

            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return ConnectionTestResult.failure("Invalid API key or unauthorized");
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                return ConnectionTestResult.failure("Access forbidden. Check API key permissions");
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return ConnectionTestResult.failure("Rate limit exceeded. Try again later");
            } else {
                return ConnectionTestResult.failure("Connection failed: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Unexpected error during Flutterwave connection test", e);
            return ConnectionTestResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Extended test that retrieves account/merchant details
     */
    public ConnectionTestResult testConnectionWithMerchantDetails(Map<String, Object> credentials) {
        String secretKey = (String) credentials.get("secretKey");

        if (secretKey == null || secretKey.trim().isEmpty()) {
            return ConnectionTestResult.failure("Secret key is required");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + secretKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Get merchant profile
            String url = FLUTTERWAVE_BASE_URL + "/profile";

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                String status = (String) responseBody.get("status");

                if ("success".equals(status)) {
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");

                    logger.info("Flutterwave merchant test successful. Business: {}",
                            data.get("business_name"));

                    return ConnectionTestResult.success("Flutterwave merchant verified")
                            .withMetadata("businessName", data.get("business_name"))
                            .withMetadata("businessEmail", data.get("business_email"))
                            .withMetadata("country", data.get("country"))
                            .withMetadata("environment", secretKey.contains("TEST") ? "test" : "live");
                } else {
                    return ConnectionTestResult.failure("Unexpected response status: " + status);
                }
            } else {
                return ConnectionTestResult.failure("Unexpected response code: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            logger.error("Flutterwave merchant test failed: {}", e.getMessage());
            return ConnectionTestResult.failure("Failed to retrieve merchant details: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during Flutterwave merchant test", e);
            return ConnectionTestResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Verify webhook signature (useful for webhook setup validation)
     */
    public boolean verifyWebhookSignature(String payload, String signature, String secretHash) {
        // Flutterwave uses HMAC SHA256 for webhook verification
        // Implementation depends on your webhook setup
        // This is a placeholder for webhook verification logic
        return true;
    }
}