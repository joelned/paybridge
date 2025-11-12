package com.paybridge.unit.Service;

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
import com.paybridge.Services.CredentialStorageService;
import com.paybridge.Services.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.paybridge.Services.ConnectionTestResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private CredentialStorageService credentialStorageService;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private ProviderConfigRepository providerConfigRepository;

    @Mock
    private StripeConnectionTester stripeConnectionTester;

    @Mock
    private FlutterwaveConnectionTester flutterwaveConnectionTester;

    @Mock
    private PaystackConnectionTester paystackConnectionTester;

    @InjectMocks
    private ProviderService providerService;

    private Merchant testMerchant;
    private Provider testProvider;
    private ProviderConfiguration validConfig;
    private static final Long MERCHANT_ID = 1L;
    private static final Long CONFIG_ID = 1L;
    private static final String PROVIDER_NAME = "stripe";

    @BeforeEach
    void setUp() {
        testMerchant = new Merchant();
        testMerchant.setId(MERCHANT_ID);
        testMerchant.setBusinessName("Test Merchant");

        testProvider = new Provider();
        testProvider.setId(1L);
        testProvider.setName(PROVIDER_NAME);
        testProvider.setDisplayName("Stripe");

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("secretKey", "sk_test_123456789");

        validConfig = new ProviderConfiguration();
        validConfig.setName(PROVIDER_NAME);
        validConfig.setConfig(configMap);
    }

    @Test
    void configureProvider_NewStripeConfigWithConnectionTest_Success() {
        // Given
        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(testProvider));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(testMerchant));
        when(providerConfigRepository.findByProviderIdAndMerchantId(testProvider.getId(), MERCHANT_ID))
                .thenReturn(Optional.empty());
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectionTestResult successResult = ConnectionTestResult.success("Stripe test connection successful");
        when(stripeConnectionTester.testConnection(any())).thenReturn(successResult);

        // When
        ProviderConfig result = providerService.configureProvider(validConfig, MERCHANT_ID, true);

        // Then
        assertNotNull(result);
        assertTrue(result.isEnabled());
        assertEquals(testProvider, result.getProvider());
        assertEquals(testMerchant, result.getMerchant());
        assertNotNull(result.getVaultPath());
        assertNotNull(result.getLastVerifiedAt());
        assertTrue(result.getVaultPath().contains("stripe"));
        assertTrue(result.getVaultPath().contains(MERCHANT_ID.toString()));

        verify(credentialStorageService).saveProviderConfig(eq(PROVIDER_NAME), eq(MERCHANT_ID), eq(validConfig.getConfig()));
        verify(providerConfigRepository).save(any(ProviderConfig.class));
        verify(stripeConnectionTester).testConnection(validConfig.getConfig());
    }

    @Test
    void configureProvider_NewFlutterwaveConfig_Success() {
        // Given
        Provider flutterwaveProvider = new Provider();
        flutterwaveProvider.setId(2L);
        flutterwaveProvider.setName("flutterwave");

        Map<String, Object> flutterwaveConfig = new HashMap<>();
        flutterwaveConfig.put("clientId", "client_123");
        flutterwaveConfig.put("clientSecret", "secret_123");
        flutterwaveConfig.put("encryptionKey", "encrypt_123");

        ProviderConfiguration flutterwaveProviderConfig = new ProviderConfiguration();
        flutterwaveProviderConfig.setName("flutterwave");
        flutterwaveProviderConfig.setConfig(flutterwaveConfig);

        when(providerRepository.findByName("flutterwave")).thenReturn(Optional.of(flutterwaveProvider));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(testMerchant));
        when(providerConfigRepository.findByProviderIdAndMerchantId(flutterwaveProvider.getId(), MERCHANT_ID))
                .thenReturn(Optional.empty());
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectionTestResult successResult = ConnectionTestResult.success("Flutterwave credentials are valid");
        when(flutterwaveConnectionTester.testConnection(any())).thenReturn(successResult);

        // When
        ProviderConfig result = providerService.configureProvider(flutterwaveProviderConfig, MERCHANT_ID, true);

        // Then
        assertNotNull(result);
        assertTrue(result.isEnabled());
        assertEquals(flutterwaveProvider, result.getProvider());
        assertTrue(result.getVaultPath().contains("flutterwave"));

        verify(credentialStorageService).saveProviderConfig(eq("flutterwave"), eq(MERCHANT_ID), eq(flutterwaveConfig));
        verify(flutterwaveConnectionTester).testConnection(flutterwaveConfig);
    }

    @Test
    void configureProvider_NewPaystackConfig_Success() {
        // Given
        Provider paystackProvider = new Provider();
        paystackProvider.setId(3L);
        paystackProvider.setName("paystack");

        Map<String, Object> paystackConfig = new HashMap<>();
        paystackConfig.put("secretKey", "sk_test_paystack_123");

        ProviderConfiguration paystackProviderConfig = new ProviderConfiguration();
        paystackProviderConfig.setName("paystack");
        paystackProviderConfig.setConfig(paystackConfig);

        when(providerRepository.findByName("paystack")).thenReturn(Optional.of(paystackProvider));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(testMerchant));
        when(providerConfigRepository.findByProviderIdAndMerchantId(paystackProvider.getId(), MERCHANT_ID))
                .thenReturn(Optional.empty());
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectionTestResult successResult = ConnectionTestResult.success("Paystack configuration test successful");
        when(paystackConnectionTester.testConnection(any())).thenReturn(successResult);

        // When
        ProviderConfig result = providerService.configureProvider(paystackProviderConfig, MERCHANT_ID, true);

        // Then
        assertNotNull(result);
        assertTrue(result.isEnabled());
        assertEquals(paystackProvider, result.getProvider());
        assertTrue(result.getVaultPath().contains("paystack"));

        verify(credentialStorageService).saveProviderConfig(eq("paystack"), eq(MERCHANT_ID), eq(paystackConfig));
        verify(paystackConnectionTester).testConnection(paystackConfig);
    }

    @Test
    void configureProvider_ExistingConfig_Success() {
        // Given
        ProviderConfig existingConfig = new ProviderConfig();
        existingConfig.setId(CONFIG_ID);
        existingConfig.setEnabled(false);
        existingConfig.setProvider(testProvider);
        existingConfig.setMerchant(testMerchant);

        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(testProvider));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(testMerchant));
        when(providerConfigRepository.findByProviderIdAndMerchantId(testProvider.getId(), MERCHANT_ID))
                .thenReturn(Optional.of(existingConfig));
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectionTestResult successResult = ConnectionTestResult.success("Stripe test connection successful");
        when(stripeConnectionTester.testConnection(any())).thenReturn(successResult);

        // When
        ProviderConfig result = providerService.configureProvider(validConfig, MERCHANT_ID, true);

        // Then
        assertNotNull(result);
        assertTrue(result.isEnabled());
        assertEquals(CONFIG_ID, result.getId());
        assertNotNull(result.getLastVerifiedAt());

        verify(credentialStorageService).saveProviderConfig(eq(PROVIDER_NAME), eq(MERCHANT_ID), eq(validConfig.getConfig()));
        verify(providerConfigRepository).save(existingConfig);
    }

    @Test
    void configureProvider_ConnectionTestDisabled_Success() {
        // Given
        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(testProvider));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(testMerchant));
        when(providerConfigRepository.findByProviderIdAndMerchantId(testProvider.getId(), MERCHANT_ID))
                .thenReturn(Optional.empty());
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ProviderConfig result = providerService.configureProvider(validConfig, MERCHANT_ID, false);

        // Then
        assertNotNull(result);
        assertNull(result.getLastVerifiedAt()); // Should not set lastVerified when testConnection = false

        verify(credentialStorageService).saveProviderConfig(eq(PROVIDER_NAME), eq(MERCHANT_ID), eq(validConfig.getConfig()));
        verifyNoInteractions(stripeConnectionTester);
    }

    @Test
    void configureProvider_ConnectionTestFailed_ThrowsException() {
        // Given
        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(testProvider));

        ConnectionTestResult failureResult = ConnectionTestResult.failure("Invalid API key");
        when(stripeConnectionTester.testConnection(any())).thenReturn(failureResult);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> providerService.configureProvider(validConfig, MERCHANT_ID, true));

        assertTrue(exception.getMessage().contains("Provider connection test failed"));
        assertTrue(exception.getMessage().contains("Invalid API key"));
        verify(credentialStorageService, never()).saveProviderConfig(any(), any(), any());
        verify(providerConfigRepository, never()).save(any());
    }

    @Test
    void configureProvider_InvalidConfiguration_ThrowsException() {
        // Given
        ProviderConfiguration invalidConfig = new ProviderConfiguration();
        invalidConfig.setName(""); // Empty name
        invalidConfig.setConfig(new HashMap<>());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> providerService.configureProvider(invalidConfig, MERCHANT_ID, false));

        assertEquals("Invalid provider configuration", exception.getMessage());
    }

    @Test
    void configureProvider_ProviderNotFound_ThrowsException() {
        // Given
        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> providerService.configureProvider(validConfig, MERCHANT_ID, false));

        assertTrue(exception.getMessage().contains("Provider not found: " + PROVIDER_NAME));
    }

    @Test
    void configureProvider_MerchantNotFound_ThrowsException() {
        // Given
        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(testProvider));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> providerService.configureProvider(validConfig, MERCHANT_ID, false));

        assertTrue(exception.getMessage().contains("Merchant not found with ID: " + MERCHANT_ID));
    }

    @Test
    void configureProvider_VaultStorageFailed_ThrowsException() {
        // Given
        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(testProvider));

        ConnectionTestResult successResult = ConnectionTestResult.success("Stripe test connection successful");
        when(stripeConnectionTester.testConnection(any())).thenReturn(successResult);

        doThrow(new RuntimeException("Vault unreachable"))
                .when(credentialStorageService).saveProviderConfig(any(), any(), any());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> providerService.configureProvider(validConfig, MERCHANT_ID, true));

        assertTrue(exception.getMessage().contains("Failed to store credentials in Vault"));
        assertTrue(exception.getMessage().contains("Vault unreachable"));
        verify(providerConfigRepository, never()).save(any());
    }

    @Test
    void configureProvider_StripeMissingSecretKey_ThrowsException() {
        // Given
        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("publishableKey", "pk_test_123"); // Missing secretKey

        ProviderConfiguration providerConfig = new ProviderConfiguration();
        providerConfig.setName("stripe");
        providerConfig.setConfig(invalidConfig);

        when(providerRepository.findByName("stripe")).thenReturn(Optional.of(testProvider));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> providerService.configureProvider(providerConfig, MERCHANT_ID, false));

        assertTrue(exception.getMessage().contains("Missing required field: secretKey"));
    }

    @Test
    void configureProvider_FlutterwaveMissingRequiredFields_ThrowsException() {
        // Given
        Provider flutterwaveProvider = new Provider();
        flutterwaveProvider.setId(2L);
        flutterwaveProvider.setName("flutterwave");

        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("clientId", "client_123");
        // Missing clientSecret and encryptionKey

        ProviderConfiguration providerConfig = new ProviderConfiguration();
        providerConfig.setName("flutterwave");
        providerConfig.setConfig(invalidConfig);

        when(providerRepository.findByName("flutterwave")).thenReturn(Optional.of(flutterwaveProvider));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> providerService.configureProvider(providerConfig, MERCHANT_ID, false));

        assertTrue(exception.getMessage().contains("Missing required field"));
    }

    @Test
    void configureProvider_EmptyFieldValue_ThrowsException() {
        // Given
        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("secretKey", ""); // Empty secretKey

        ProviderConfiguration providerConfig = new ProviderConfiguration();
        providerConfig.setName("stripe");
        providerConfig.setConfig(invalidConfig);

        when(providerRepository.findByName("stripe")).thenReturn(Optional.of(testProvider));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> providerService.configureProvider(providerConfig, MERCHANT_ID, false));

        assertTrue(exception.getMessage().contains("Field cannot be empty: secretKey"));
    }

    @Test
    void configureProvider_UnsupportedProvider_ThrowsException() {
        // Given
        Provider unsupportedProvider = new Provider();
        unsupportedProvider.setId(4L);
        unsupportedProvider.setName("unsupported");

        Map<String, Object> config = new HashMap<>();
        config.put("apiKey", "test_key");

        ProviderConfiguration providerConfig = new ProviderConfiguration();
        providerConfig.setName("unsupported");
        providerConfig.setConfig(config);

        when(providerRepository.findByName("unsupported")).thenReturn(Optional.of(unsupportedProvider));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> providerService.configureProvider(providerConfig, MERCHANT_ID, false));

        assertTrue(exception.getMessage().contains("Unsupported provider: unsupported"));
    }

    @Test
    void testExistingProviderConfig_Success() {
        // Given
        ProviderConfig config = new ProviderConfig();
        config.setId(CONFIG_ID);
        config.setProvider(testProvider);
        config.setMerchant(testMerchant);

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_123456789");

        when(providerConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
        when(credentialStorageService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID)).thenReturn(credentials);

        ConnectionTestResult successResult = ConnectionTestResult.success("Stripe test connection successful");
        when(stripeConnectionTester.testConnection(credentials)).thenReturn(successResult);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(config);

        // When
        ConnectionTestResult result = providerService.testExistingProviderConfig(CONFIG_ID, MERCHANT_ID);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Stripe test connection successful", result.getMessage());

        verify(providerConfigRepository).save(config);
        assertNotNull(config.getLastVerifiedAt());
    }

    @Test
    void testExistingProviderConfig_UnauthorizedAccess_ThrowsException() {
        // Given
        ProviderConfig config = new ProviderConfig();
        config.setId(CONFIG_ID);
        config.setProvider(testProvider);

        Merchant differentMerchant = new Merchant();
        differentMerchant.setId(999L); // Different merchant ID
        config.setMerchant(differentMerchant);

        when(providerConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));

        // When & Then
        SecurityException exception = assertThrows(SecurityException.class,
                () -> providerService.testExistingProviderConfig(CONFIG_ID, MERCHANT_ID));

        assertEquals("Unauthorized access to provider configuration", exception.getMessage());
        verify(credentialStorageService, never()).getProviderConfig(any(), any());
    }

    @Test
    void testExistingProviderConfig_ConnectionTestFailed_DoesNotUpdateTimestamp() {
        // Given
        ProviderConfig config = new ProviderConfig();
        config.setId(CONFIG_ID);
        config.setProvider(testProvider);
        config.setMerchant(testMerchant);

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_123456789");

        when(providerConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
        when(credentialStorageService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID)).thenReturn(credentials);

        ConnectionTestResult failureResult = ConnectionTestResult.failure("Invalid credentials");
        when(stripeConnectionTester.testConnection(credentials)).thenReturn(failureResult);

        // When
        ConnectionTestResult result = providerService.testExistingProviderConfig(CONFIG_ID, MERCHANT_ID);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Invalid credentials", result.getMessage());

        verify(providerConfigRepository, never()).save(any()); // Should not update on failure
        assertNull(config.getLastVerifiedAt()); // Should not update timestamp on failure
    }

    @Test
    void testExistingProviderConfig_ConfigNotFound_ThrowsException() {
        // Given
        when(providerConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> providerService.testExistingProviderConfig(CONFIG_ID, MERCHANT_ID));

        assertTrue(exception.getMessage().contains("Configuration not found"));
    }

    @Test
    void testExistingProviderConfig_VaultConfigNotFound_ThrowsException() {
        // Given
        ProviderConfig config = new ProviderConfig();
        config.setId(CONFIG_ID);
        config.setProvider(testProvider);
        config.setMerchant(testMerchant);

        when(providerConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
        when(credentialStorageService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID))
                .thenThrow(new RuntimeException("Provider configuration not found in Vault"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> providerService.testExistingProviderConfig(CONFIG_ID, MERCHANT_ID));

        assertTrue(exception.getMessage().contains("Provider configuration not found in Vault"));
    }

    @Test
    void testExistingProviderConfig_FlutterwaveProvider_Success() {
        // Given
        Provider flutterwaveProvider = new Provider();
        flutterwaveProvider.setId(2L);
        flutterwaveProvider.setName("flutterwave");

        ProviderConfig config = new ProviderConfig();
        config.setId(CONFIG_ID);
        config.setProvider(flutterwaveProvider);
        config.setMerchant(testMerchant);

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("clientId", "client_123");
        credentials.put("clientSecret", "secret_123");
        credentials.put("encryptionKey", "encrypt_123");

        when(providerConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
        when(credentialStorageService.getProviderConfig("flutterwave", MERCHANT_ID)).thenReturn(credentials);

        ConnectionTestResult successResult = ConnectionTestResult.success("Flutterwave credentials are valid");
        when(flutterwaveConnectionTester.testConnection(credentials)).thenReturn(successResult);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(config);

        // When
        ConnectionTestResult result = providerService.testExistingProviderConfig(CONFIG_ID, MERCHANT_ID);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Flutterwave credentials are valid", result.getMessage());

        verify(providerConfigRepository).save(config);
        verify(flutterwaveConnectionTester).testConnection(credentials);
    }

    @Test
    void testExistingProviderConfig_PaystackProvider_Success() {
        // Given
        Provider paystackProvider = new Provider();
        paystackProvider.setId(3L);
        paystackProvider.setName("paystack");

        ProviderConfig config = new ProviderConfig();
        config.setId(CONFIG_ID);
        config.setProvider(paystackProvider);
        config.setMerchant(testMerchant);

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_paystack_123");

        when(providerConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
        when(credentialStorageService.getProviderConfig("paystack", MERCHANT_ID)).thenReturn(credentials);

        ConnectionTestResult successResult = ConnectionTestResult.success("Paystack configuration test successful");
        when(paystackConnectionTester.testConnection(credentials)).thenReturn(successResult);
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenReturn(config);

        // When
        ConnectionTestResult result = providerService.testExistingProviderConfig(CONFIG_ID, MERCHANT_ID);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Paystack configuration test successful", result.getMessage());

        verify(providerConfigRepository).save(config);
        verify(paystackConnectionTester).testConnection(credentials);
    }

    @Test
    void buildVaultReference_CreatesCorrectPath() {
        // This tests the private method indirectly through configureProvider
        when(providerRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(testProvider));
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(testMerchant));
        when(providerConfigRepository.findByProviderIdAndMerchantId(testProvider.getId(), MERCHANT_ID))
                .thenReturn(Optional.empty());
        when(providerConfigRepository.save(any(ProviderConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ProviderConfig result = providerService.configureProvider(validConfig, MERCHANT_ID, false);

        // Then
        assertNotNull(result.getVaultPath());
        String expectedPath = String.format("vault://providers/%s/merchant-%d",
                PROVIDER_NAME.toLowerCase(), MERCHANT_ID);
        assertEquals(expectedPath, result.getVaultPath());
    }
}