package com.paybridge.Configs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paybridge.Services.ConnectionTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Component
public class FlutterwaveConnectionTester {

    private static final Logger logger = LoggerFactory.getLogger(FlutterwaveConnectionTester.class);
    private static final String FLUTTERWAVE_TOKEN_URL = "https://idp.flutterwave.com/realms/flutterwave/protocol/openid-connect/token";

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Test Flutterwave API connection using OAuth2 client credentials flow
     */
    public ConnectionTestResult testConnection(Map<String, Object> credentials) {
        String clientId = (String) credentials.get("clientId");
        String clientSecret = (String) credentials.get("clientSecret");

        if (clientId == null || clientId.trim().isEmpty()) {
            return ConnectionTestResult.failure("Client ID is required");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            return ConnectionTestResult.failure("Client secret is required");
        }

        try {
            // Prepare form-urlencoded request body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("grant_type", "client_credentials");

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            // Make token request
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    FLUTTERWAVE_TOKEN_URL,
                    HttpMethod.POST,
                    entity,
                    TokenResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TokenResponse tokenResponse = response.getBody();

                if (tokenResponse.getAccessToken() == null || tokenResponse.getAccessToken().trim().isEmpty()) {
                    logger.warn("Flutterwave returned success but no access token. Response: {}", tokenResponse);
                    return ConnectionTestResult.failure("Authentication succeeded but no access token received");
                }

                logger.info("Flutterwave OAuth2 connection successful. Token expires in: {} seconds",
                        tokenResponse.getExpiresIn());

                return ConnectionTestResult.success("Flutterwave credentials are valid")
                        .withMetadata("accessToken", maskToken(tokenResponse.getAccessToken()))
                        .withMetadata("tokenType", safeToString(tokenResponse.getTokenType()))
                        .withMetadata("expiresIn", String.valueOf(tokenResponse.getExpiresIn()))
                        .withMetadata("scope", safeToString(tokenResponse.getScope()))
                        .withMetadata("environment", inferEnvironment(clientId))
                        .withMetadata("validatedAt", Instant.now().toString());

            } else {
                return ConnectionTestResult.failure("Unexpected response: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            return handleOAuthError(e);
        } catch (Exception e) {
            logger.error("Unexpected error during Flutterwave connection test", e);
            return ConnectionTestResult.failure("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Handle OAuth2 specific errors
     */
    private ConnectionTestResult handleOAuthError(HttpClientErrorException e) {
        String errorMessage = extractOAuthErrorMessage(e);

        logger.error("Flutterwave OAuth2 connection failed: {}", errorMessage);

        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return ConnectionTestResult.failure("Invalid client credentials: Authentication failed");
        } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
            return ConnectionTestResult.failure("Invalid request: Check client_id and client_secret format");
        } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            return ConnectionTestResult.failure("Rate limit exceeded. Try again in a few minutes");
        } else {
            return ConnectionTestResult.failure("Authentication failed: " + e.getStatusCode());
        }
    }

    /**
     * Extract error message from OAuth2 response
     */
    private String extractOAuthErrorMessage(HttpClientErrorException e) {
        try {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null && responseBody.contains("error")) {
                return "OAuth2 authentication error";
            }
        } catch (Exception ex) {
            logger.debug("Could not parse OAuth2 error response", ex);
        }
        return "Authentication failed with status: " + e.getStatusCode();
    }

    /**
     * Infer environment from client ID pattern
     */
    private String inferEnvironment(String clientId) {
        if (clientId == null) return "unknown";

        if (clientId.toLowerCase().contains("test")) {
            return "test";
        } else if (clientId.toLowerCase().contains("live")) {
            return "live";
        } else {
            return "unknown";
        }
    }

    /**
     * Mask token for secure logging (show first and last 4 chars only)
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    /**
     * Safe string conversion to avoid NPE
     */
    private String safeToString(String value) {
        return value != null ? value : "unknown";
    }

    /**
     * Enhanced debugging method to log raw response
     */
    public ConnectionTestResult testConnectionWithDebug(Map<String, Object> credentials) {
        String clientId = (String) credentials.get("clientId");
        String clientSecret = (String) credentials.get("clientSecret");

        try {
            // Prepare request
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("grant_type", "client_credentials");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            // First, get raw response as String to see what's actually returned
            ResponseEntity<String> rawResponse = restTemplate.exchange(
                    FLUTTERWAVE_TOKEN_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            logger.debug("Raw Flutterwave response: {}", rawResponse.getBody());

            // Now parse as TokenResponse
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    FLUTTERWAVE_TOKEN_URL,
                    HttpMethod.POST,
                    entity,
                    TokenResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TokenResponse tokenResponse = response.getBody();

                if (tokenResponse.getAccessToken() == null) {
                    logger.error("Flutterwave response missing access_token. Full response: {}", rawResponse.getBody());
                    return ConnectionTestResult.failure("Authentication succeeded but access token is missing");
                }

                return ConnectionTestResult.success("Flutterwave credentials are valid")
                        .withMetadata("accessToken", maskToken(tokenResponse.getAccessToken()))
                        .withMetadata("tokenType", safeToString(tokenResponse.getTokenType()))
                        .withMetadata("expiresIn", String.valueOf(tokenResponse.getExpiresIn()))
                        .withMetadata("scope", safeToString(tokenResponse.getScope()));

            } else {
                return ConnectionTestResult.failure("Unexpected response: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Debug connection test failed", e);
            return ConnectionTestResult.failure("Debug test failed: " + e.getMessage());
        }
    }

    /**
     * Token Response DTO with better null safety
     */
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private Integer expiresIn;

        @JsonProperty("refresh_expires_in")
        private Integer refreshExpiresIn;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("not-before-policy")
        private Integer notBeforePolicy;

        @JsonProperty("scope")
        private String scope;
        // Getters and setters with null checks
        public String getAccessToken() {
            return accessToken != null ? accessToken : "";
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public int getExpiresIn() {
            return expiresIn != null ? expiresIn : 0;
        }

        public void setExpiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
        }

        public int getRefreshExpiresIn() {
            return refreshExpiresIn != null ? refreshExpiresIn : 0;
        }

        public void setRefreshExpiresIn(Integer refreshExpiresIn) {
            this.refreshExpiresIn = refreshExpiresIn;
        }

        public String getTokenType() {
            return tokenType != null ? tokenType : "unknown";
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public int getNotBeforePolicy() {
            return notBeforePolicy != null ? notBeforePolicy : 0;
        }

        public void setNotBeforePolicy(Integer notBeforePolicy) {
            this.notBeforePolicy = notBeforePolicy;
        }

        public String getScope() {
            return scope != null ? scope : "unknown";
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        @Override
        public String toString() {
            return "TokenResponse{" +
                    "accessToken=" + (accessToken != null ? maskToken(accessToken) : "null") +
                    ", expiresIn=" + expiresIn +
                    ", tokenType='" + tokenType + '\'' +
                    ", scope='" + scope + '\'' +
                    '}';
        }

        private String maskToken(String token) {
            if (token == null || token.length() <= 8) return "***";
            return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
        }
    }
}