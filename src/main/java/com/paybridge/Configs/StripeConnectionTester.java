package com.paybridge.Configs;

import com.paybridge.Services.ConnectionTestResult;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StripeConnectionTester {

    private static final Logger logger = LoggerFactory.getLogger(StripeConnectionTester.class);

    /**
     * Test Stripe API connection by retrieving account balance
     * This is a lightweight operation that verifies API key validity
     */
    public ConnectionTestResult testConnection(Map<String, Object> credentials) {
        String secretKey = (String) credentials.get("secretKey");

        if (secretKey == null || secretKey.trim().isEmpty()) {
            return ConnectionTestResult.failure("Secret key is required");
        }

        // Validate key format
        if (!secretKey.startsWith("sk_")) {
            return ConnectionTestResult.failure("Invalid secret key format. Must start with 'sk_'");
        }

        try {
            // Set the API key
            Stripe.apiKey = secretKey;

            // Make a simple API call to verify credentials
            // Retrieving balance is lightweight and doesn't modify anything
            Balance balance = Balance.retrieve();

            // If we get here, the API key is valid
            logger.info("Stripe connection test successful. Available balance: {} {}",
                    balance.getAvailable().get(0).getAmount(),
                    balance.getAvailable().get(0).getCurrency());

            return ConnectionTestResult.success("Stripe connection successful")
                    .withMetadata("currency", balance.getAvailable().get(0).getCurrency())
                    .withMetadata("environment", secretKey.contains("test") ? "test" : "live");

        } catch (StripeException e) {
            logger.error("Stripe connection test failed: {}", e.getMessage());

            // Handle specific error types
            switch (e.getCode()) {
                case "invalid_api_key":
                    return ConnectionTestResult.failure("Invalid API key");
                case "authentication_required":
                    return ConnectionTestResult.failure("Authentication failed");
                case "rate_limit":
                    return ConnectionTestResult.failure("Rate limit exceeded. Try again later");
                default:
                    return ConnectionTestResult.failure("Connection failed: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error during Stripe connection test", e);
            return ConnectionTestResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Extended test that retrieves account details
     * Use this for initial setup or detailed verification
     */
    public ConnectionTestResult testConnectionWithAccountDetails(Map<String, Object> credentials) {
        String secretKey = (String) credentials.get("secretKey");

        if (secretKey == null || secretKey.trim().isEmpty()) {
            return ConnectionTestResult.failure("Secret key is required");
        }

        try {
            Stripe.apiKey = secretKey;

            // Retrieve account information
            Account account = Account.retrieve();

            logger.info("Stripe account test successful. Account ID: {}, Email: {}",
                    account.getId(), account.getEmail());

            return ConnectionTestResult.success("Stripe account verified")
                    .withMetadata("accountId", account.getId())
                    .withMetadata("email", account.getEmail())
                    .withMetadata("country", account.getCountry())
                    .withMetadata("chargesEnabled", account.getChargesEnabled())
                    .withMetadata("payoutsEnabled", account.getPayoutsEnabled())
                    .withMetadata("environment", secretKey.contains("test") ? "test" : "live");

        } catch (StripeException e) {
            logger.error("Stripe account test failed: {}", e.getMessage());
            return ConnectionTestResult.failure("Failed to retrieve account: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during Stripe account test", e);
            return ConnectionTestResult.failure("Unexpected error: " + e.getMessage());
        }
    }
}