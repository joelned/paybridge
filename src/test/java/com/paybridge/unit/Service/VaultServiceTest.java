package com.paybridge.unit.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Services.VaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultServiceTest {

    @Mock
    private VaultTemplate vaultTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private VaultService vaultService;

    private static final String PROVIDER_NAME = "stripe";
    private static final Long MERCHANT_ID = 12345L;
    private static final String EXPECTED_PATH = "secret/data/paybridge/providers/stripe/merchant-12345";

    private Map<String, Object> testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new HashMap<>();
        testConfig.put("apiKey", "test-api-key-123");
        testConfig.put("secretKey", "test-secret-key-456");
        testConfig.put("webhookSecret", "whsec_test_789");
    }

    @Test
    void storeProviderConfig_Success() {
        // Given
        Map<String, Object> vaultData = new HashMap<>();
        vaultData.put("data", testConfig);

        // When
        vaultService.storeProviderConfig(PROVIDER_NAME, MERCHANT_ID, testConfig);

        // Then
        verify(vaultTemplate).write(eq(EXPECTED_PATH), eq(vaultData));
    }

    @Test
    void storeProviderConfig_ExceptionThrown() {
        // Given
        Map<String, Object> vaultData = new HashMap<>();
        vaultData.put("data", testConfig);

        doThrow(new RuntimeException("Vault unavailable"))
                .when(vaultTemplate).write(eq(EXPECTED_PATH), eq(vaultData));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vaultService.storeProviderConfig(PROVIDER_NAME, MERCHANT_ID, testConfig));

        assertEquals("Failed to store provider config in Vault", exception.getMessage());
        assertTrue(exception.getCause().getMessage().contains("Vault unavailable"));
    }

    @Test
    void getProviderConfig_Success() {
        // Given
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("data", testConfig);

        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(responseData);
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(mockResponse);

        // When
        Map<String, Object> result = vaultService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID);

        // Then
        assertNotNull(result);
        assertEquals("test-api-key-123", result.get("apiKey"));
        assertEquals("test-secret-key-456", result.get("secretKey"));
        verify(vaultTemplate).read(eq(EXPECTED_PATH), eq(Map.class));
    }

    @Test
    void getProviderConfig_NotFound() {
        // Given
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(null);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vaultService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID));

        assertEquals("Provider configuration not found in Vault", exception.getMessage());
    }

    @Test
    void getProviderConfig_NullData() {
        // Given
        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(null);
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(mockResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vaultService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID));

        assertEquals("Provider configuration not found in Vault", exception.getMessage());
    }

    @Test
    void getProviderConfig_ExceptionThrown() {
        // Given
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vaultService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID));

        assertEquals("Provider configuration not found in Vault", exception.getMessage());
        assertTrue(exception.getCause().getMessage().contains("Connection failed"));
    }

    @Test
    void deleteProviderConfig_Success() {
        // When
        vaultService.deleteProviderConfig(PROVIDER_NAME, MERCHANT_ID);

        // Then
        verify(vaultTemplate).delete(eq(EXPECTED_PATH));
    }

    @Test
    void deleteProviderConfig_ExceptionThrown() {
        // Given
        doThrow(new RuntimeException("Delete failed"))
                .when(vaultTemplate).delete(eq(EXPECTED_PATH));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vaultService.deleteProviderConfig(PROVIDER_NAME, MERCHANT_ID));

        assertEquals("Failed to delete provider configuration from Vault", exception.getMessage());
        assertTrue(exception.getCause().getMessage().contains("Delete failed"));
    }

    @Test
    void configExists_True() {
        // Given
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("data", testConfig);

        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(responseData);
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(mockResponse);

        // When
        boolean exists = vaultService.configExists(PROVIDER_NAME, MERCHANT_ID);

        // Then
        assertTrue(exists);
    }

    @Test
    void configExists_FalseWhenResponseNull() {
        // Given
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(null);

        // When
        boolean exists = vaultService.configExists(PROVIDER_NAME, MERCHANT_ID);

        // Then
        assertFalse(exists);
    }

    @Test
    void configExists_FalseWhenDataNull() {
        // Given
        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(null);
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(mockResponse);

        // When
        boolean exists = vaultService.configExists(PROVIDER_NAME, MERCHANT_ID);

        // Then
        assertFalse(exists);
    }

    @Test
    void configExists_ReturnsFalseOnException() {
        // Given
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class)))
                .thenThrow(new RuntimeException("Any exception"));

        // When
        boolean exists = vaultService.configExists(PROVIDER_NAME, MERCHANT_ID);

        // Then
        assertFalse(exists);
    }

    @Test
    void updateProviderConfigField_Success() {
        // Given
        Map<String, Object> currentConfig = new HashMap<>(testConfig);
        String newFieldName = "newApiKey";
        String newFieldValue = "updated-api-key-999";

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("data", currentConfig);

        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(responseData);
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(mockResponse);

        // When
        vaultService.updateProviderConfigField(PROVIDER_NAME, MERCHANT_ID, newFieldName, newFieldValue);

        // Then
        Map<String, Object> expectedUpdatedConfig = new HashMap<>(testConfig);
        expectedUpdatedConfig.put(newFieldName, newFieldValue);

        Map<String, Object> expectedVaultData = new HashMap<>();
        expectedVaultData.put("data", expectedUpdatedConfig);

        verify(vaultTemplate).write(eq(EXPECTED_PATH), eq(expectedVaultData));
    }

    @Test
    void updateProviderConfigField_OverwritesExistingField() {
        // Given
        Map<String, Object> currentConfig = new HashMap<>(testConfig);
        String existingFieldName = "apiKey";
        String updatedFieldValue = "brand-new-api-key";

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("data", currentConfig);

        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(responseData);
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(mockResponse);

        // When
        vaultService.updateProviderConfigField(PROVIDER_NAME, MERCHANT_ID, existingFieldName, updatedFieldValue);

        // Then
        Map<String, Object> expectedUpdatedConfig = new HashMap<>(testConfig);
        expectedUpdatedConfig.put(existingFieldName, updatedFieldValue);

        Map<String, Object> expectedVaultData = new HashMap<>();
        expectedVaultData.put("data", expectedUpdatedConfig);

        verify(vaultTemplate).write(eq(EXPECTED_PATH), eq(expectedVaultData));
    }

    @Test
    void buildProviderPath_ValidInputs() {
        // This test should verify the path construction, not call the actual method
        // Since buildProviderPath is private, we test it indirectly through the public methods

        // Given
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("data", testConfig);

        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(responseData);
        when(vaultTemplate.read(eq(EXPECTED_PATH), eq(Map.class))).thenReturn(mockResponse);

        // When
        Map<String, Object> result = vaultService.getProviderConfig(PROVIDER_NAME, MERCHANT_ID);

        // Then - verify the path was used correctly through the mock interaction
        assertNotNull(result);
        verify(vaultTemplate).read(eq(EXPECTED_PATH), eq(Map.class));
    }

    @Test
    void buildProviderPath_CaseInsensitiveProviderName() {
        // Given
        String mixedCaseProviderName = "StRiPe";
        String expectedPath = "secret/data/paybridge/providers/stripe/merchant-12345";

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("data", testConfig);

        VaultResponseSupport<Map> mockResponse = mock(VaultResponseSupport.class);
        when(mockResponse.getData()).thenReturn(responseData);
        when(vaultTemplate.read(eq(expectedPath), eq(Map.class))).thenReturn(mockResponse);

        // When
        Map<String, Object> result = vaultService.getProviderConfig(mixedCaseProviderName, MERCHANT_ID);

        // Then
        assertNotNull(result);
        verify(vaultTemplate).read(eq(expectedPath), eq(Map.class));
    }
}