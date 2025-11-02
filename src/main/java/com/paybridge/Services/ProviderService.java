package com.paybridge.Services;

import com.paybridge.Configs.FlutterwaveConnectionTester;
import com.paybridge.Configs.StripeConnectionTester;
import com.paybridge.Models.DTOs.ProviderConfiguration;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Provider;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.ProviderRepository;
import com.paybridge.Repositories.ProviderConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class ProviderService {

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

    /**
     * Configure provider with connection testing
     */
    @Transactional
    public ProviderConfig configureProvider(ProviderConfiguration providerConfiguration,
                                            Long merchantId,
                                            boolean testConnection) {

        Provider provider = providerRepository
                .findByName(providerConfiguration.getName());

        if(provider == null){
            throw new RuntimeException("Provider does not exist");
        }

        validateProviderConfig(providerConfiguration.getName(),
                providerConfiguration.getConfig());

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
        } catch (Exception e) {
            throw new RuntimeException("Failed to store credentials in Vault", e);
        }

        // 6. Store metadata in database
        Optional<ProviderConfig> existingConfig = providerConfigRepository
                .findByProviderIdAndMerchantId(provider.getId(), merchantId);

        ProviderConfig config = null;
        if (existingConfig.isPresent()) {
            config = existingConfig.get();
            config.setEnabled(true);
        } else {
            Optional<Merchant> merchant = merchantRepository.findById(merchantId);
            if(merchant.isPresent()){
                config = new ProviderConfig();
                config.setProvider(provider);
                config.setMerchant(merchant.get());
                config.setCreatedAt(LocalDateTime.now());
                config.setEnabled(false);
            }
        }


        assert config != null;
        config.setVaultPath(buildVaultReference(providerConfiguration.getName(), merchantId));

        if (testConnection) {
            config.setLastVerifiedAt(LocalDateTime.now());
        }

        return providerConfigRepository.save(config);
    }

    /**
     * Test existing provider configuration
     */
    public ConnectionTestResult testExistingProviderConfig(Long configId, Long merchantId) {
        ProviderConfig config = providerConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found"));

        // Verify ownership
        if (!config.getMerchant().getId().equals(merchantId)) {
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
                config.getProvider().getName(),
                credentials
        );
        long duration = System.currentTimeMillis() - startTime;

        // Update last verified timestamp if successful
        if (result.isSuccess()) {
            config.setLastVerifiedAt(LocalDateTime.now());
            providerConfigRepository.save(config);
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
            case "paypal":
                // Implement PayPal tester similarly
                return ConnectionTestResult.success("PayPal testing not yet implemented");
            default:
                throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }
    }

    private void validateProviderConfig(String providerName, Map<String, Object> config) {
        switch (providerName.toLowerCase()) {
            case "stripe":
                requireFields(config, "secretKey");
                break;
            case "flutterwave":
                requireFields(config, "clientId", "clientSecret", "encryptionKey");
                break;
            default:
                throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }
    }

    private void requireFields(Map<String, Object> config, String... fields) {
        for (String field : fields) {
            if (!config.containsKey(field) || config.get(field) == null) {
                throw new IllegalArgumentException("Missing required field: " + field);
            }
        }
    }

    private String buildVaultReference(String providerName, Long userId) {
        return String.format("vault://providers/%s/user-%d",
                providerName.toLowerCase(),
                userId);
    }
}