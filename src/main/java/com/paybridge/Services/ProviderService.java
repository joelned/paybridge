package com.paybridge.Services;

import com.paybridge.Configs.FlutterwaveConnectionTester;
import com.paybridge.Configs.PaystackConnectionTester;
import com.paybridge.Configs.StripeConnectionTester;
import com.paybridge.Models.DTOs.ProviderConfiguration;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Provider;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.ProviderRepository;
import com.paybridge.Repositories.ProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    @Autowired
    private VaultService vaultService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ProviderConfigRepository providerConfigRepository;

    @Autowired
    private StripeConnectionTester stripeConnectionTester;

    @Autowired
    private FlutterwaveConnectionTester flutterwaveConnectionTester;

    @Autowired
    private PaystackConnectionTester paystackConnectionTester;
    /**
     * Configure provider with connection testing
     */
    @Transactional
    public ProviderConfig configureProvider(ProviderConfiguration providerConfiguration,
                                            Long merchantId,
                                            boolean testConnection) {

        // 1. Validate input
        if (!providerConfiguration.isValid()) {
            throw new IllegalArgumentException("Invalid provider configuration");
        }

        // 2. Find provider by name
        Provider provider = providerRepository
                .findByName(providerConfiguration.getName())
                .orElseThrow(() -> new RuntimeException("Provider not found: " +
                        providerConfiguration.getName()));

        // 3. Validate required fields for specific provider
        validateProviderConfig(providerConfiguration.getName(),
                providerConfiguration.getConfig());

        // 4. Test connection if requested
        if (testConnection) {
            ConnectionTestResult testResult = testProviderConnection(
                    providerConfiguration.getName(),
                    providerConfiguration.getConfig()
            );

            if (!testResult.isSuccess()) {
                throw new RuntimeException("Provider connection test failed: " +
                        testResult.getMessage());
            }

        }

        // 5. Store credentials in Vault
        try {
            vaultService.storeProviderConfig(
                    providerConfiguration.getName(),
                    merchantId,
                    providerConfiguration.getConfig()
            );

            log.info("Stored provider config in Vault: {} for merchant {}",
                    providerConfiguration.getName(), merchantId);
        } catch (Exception e) {
            log.error("Failed to store credentials in Vault", e);
            throw new RuntimeException("Failed to store credentials in Vault: " + e.getMessage(), e);
        }

        // 6. Find merchant
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found with ID: " + merchantId));

        // 7. Store metadata in database (NO sensitive data)
        Optional<ProviderConfig> existingConfig = providerConfigRepository
                .findByProviderIdAndMerchantId(provider.getId(), merchantId);

        ProviderConfig config;
        if (existingConfig.isPresent()) {
            config = existingConfig.get();
            config.setEnabled(true);
            log.info("Updated existing provider config: {} for merchant {}",
                    provider.getName(), merchantId);
        } else {
            config = new ProviderConfig();
            config.setProvider(provider);
            config.setMerchant(merchant);
            config.setCreatedAt(LocalDateTime.now());
            config.setEnabled(true);
            log.info("Created new provider config: {} for merchant {}",
                    provider.getName(), merchantId);
        }

        // Set vault path reference
        config.setVaultPath(buildVaultReference(providerConfiguration.getName(), merchantId));

        // Update last verified timestamp if connection was tested
        if (testConnection) {
            config.setLastVerifiedAt(LocalDateTime.now());
        }

        return providerConfigRepository.save(config);
    }

    /**
     * Test existing provider configuration
     */
    @Transactional(readOnly = true)
    public ConnectionTestResult testExistingProviderConfig(Long configId, Long merchantId) {
        ProviderConfig config = providerConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found"));

        // Verify ownership
        if (!config.getMerchant().getId().equals(merchantId)) {
            log.warn("Unauthorized access attempt to config {} by merchant {}", configId, merchantId);
            throw new SecurityException("Unauthorized access to provider configuration");
        }

        // Get credentials from Vault
        Map<String, Object> credentials = vaultService.getProviderConfig(
                config.getProvider().getName(),
                merchantId
        );

        // Test connection
        long startTime = System.currentTimeMillis();
        ConnectionTestResult result = testProviderConnection(
                config.getProvider().getName().toLowerCase(),
                credentials
        );
        long duration = System.currentTimeMillis() - startTime;

        // Update last verified timestamp if successful
        if (result.isSuccess()) {
            config.setLastVerifiedAt(LocalDateTime.now());
            providerConfigRepository.save(config);
            log.info("Provider connection test passed for {} (duration: {}ms)",
                    config.getProvider().getName(), duration);
        } else {
            log.warn("Provider connection test failed for {}: {}",
                    config.getProvider().getName(), result.getMessage());
        }

        return result.withDuration(duration);
    }

    /**
     * Test provider connection based on provider name
     */
    private ConnectionTestResult testProviderConnection(String providerName,
                                                        Map<String, Object> credentials) {
        switch (providerName.toLowerCase()) {
            case "stripe":
                return stripeConnectionTester.testConnection(credentials);
            case "flutterwave":
                return flutterwaveConnectionTester.testConnection(credentials);
            case "paystack":
                return paystackConnectionTester.testConnection(credentials);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }
    }

    /**
     * Validate provider-specific required fields
     */
    private void validateProviderConfig(String providerName, Map<String, Object> config) {
        switch (providerName.toLowerCase()) {
            case "stripe", "paystack":
                requireFields(config, "secretKey");
                break;
            case "flutterwave":
                requireFields(config, "clientSecret", "clientId", "encryptionKey");
                break;
            default:
                throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }
    }

    /**
     * Require specific fields in configuration
     */
    private void requireFields(Map<String, Object> config, String... fields) {
        for (String field : fields) {
            if (!config.containsKey(field) || config.get(field) == null) {
                throw new IllegalArgumentException("Missing required field: " + field);
            }

            // Check if value is empty string
            Object value = config.get(field);
            if (value instanceof String && ((String) value).trim().isEmpty()) {
                throw new IllegalArgumentException("Field cannot be empty: " + field);
            }
        }
    }



    /**
     * Build Vault reference path
     */
    private String buildVaultReference(String providerName, Long merchantId) {
        return String.format("vault://providers/%s/merchant-%d",
                providerName.toLowerCase(),
                merchantId);
    }
}