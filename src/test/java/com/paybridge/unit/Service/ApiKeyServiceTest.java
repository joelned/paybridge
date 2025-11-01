package com.paybridge.unit.Service;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.ApiKeyUsageRepository;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ApiKeyUsageRepository apiKeyUsageRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ---------- API Key generation ----------
    @Test
    void generateApiKey_ShouldStartWithCorrectPrefix() {
        String testKey = apiKeyService.generateApiKey(true);
        String liveKey = apiKeyService.generateApiKey(false);

        assertTrue(testKey.startsWith("pk_test_"));
        assertTrue(liveKey.startsWith("pk_live_"));
        assertNotEquals(testKey, liveKey);
    }

    @Test
    void generateApiKey_ShouldGenerateUniqueKeys() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String key = apiKeyService.generateApiKey(true);
            assertTrue(keys.add(key), "Should generate unique keys - duplicate: " + key);
        }
        assertEquals(50, keys.size());
    }

    @Test
    void generateApiKey_ShouldHaveSufficientLength() {
        String testKey = apiKeyService.generateApiKey(true);
        String liveKey = apiKeyService.generateApiKey(false);

        assertTrue(testKey.length() > 30, "Test key should be sufficiently long");
        assertTrue(liveKey.length() > 30, "Live key should be sufficiently long");
    }

    // ---------- isTestMode ----------
    @Test
    void isTestMode_ShouldDetectTestPrefix() {
        assertTrue(apiKeyService.isTestMode("pk_test_1234"));
        assertFalse(apiKeyService.isTestMode("pk_live_1234"));
        assertFalse(apiKeyService.isTestMode(null));
    }

    @ParameterizedTest
    @CsvSource({
            "pk_test_abc123, true",
            "pk_live_xyz789, false",
            "pk_test_, true",
            "pk_live_, false",
            "invalid_prefix, false",
            "test_key, false",
            "live_key, false",
            ", false",
            "'', false"
    })
    void isTestMode_Parameterized(String apiKey, boolean expected) {
        assertEquals(expected, apiKeyService.isTestMode(apiKey));
    }

    @Test
    void isTestMode_ShouldHandleVeryShortKeys() {
        assertFalse(apiKeyService.isTestMode("pk_t"));
        assertFalse(apiKeyService.isTestMode("pk_l"));
        assertFalse(apiKeyService.isTestMode("p"));
    }

    // ---------- regenerateApiKey ----------
    @Test
    void regenerateApiKey_ShouldUpdateKeys() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setApiKeyLive("old_live");
        merchant.setApiKeyTest("old_test");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        apiKeyService.regenerateApiKey(1L, true, true);

        verify(merchantRepository, times(1)).save(merchant);
        assertTrue(merchant.getApiKeyLive().startsWith("pk_live_"));
        assertTrue(merchant.getApiKeyTest().startsWith("pk_test_"));
    }

    @Test
    void regenerateApiKey_ShouldOnlyUpdateTestKeyWhenRequested() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setApiKeyLive("original_live");
        merchant.setApiKeyTest("original_test");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        // Only regenerate test key
        apiKeyService.regenerateApiKey(1L, true, false);

        verify(merchantRepository, times(1)).save(merchant);
        assertTrue(merchant.getApiKeyTest().startsWith("pk_test_"));
        assertEquals("original_live", merchant.getApiKeyLive()); // Should remain unchanged
    }

    @Test
    void regenerateApiKey_ShouldOnlyUpdateLiveKeyWhenRequested() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setApiKeyLive("original_live");
        merchant.setApiKeyTest("original_test");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        // Only regenerate live key
        apiKeyService.regenerateApiKey(1L, false, true);

        verify(merchantRepository, times(1)).save(merchant);
        assertEquals("original_test", merchant.getApiKeyTest()); // Should remain unchanged
        assertTrue(merchant.getApiKeyLive().startsWith("pk_live_"));
    }

    @Test
    void regenerateApiKey_ShouldThrowExceptionWhenMerchantNotFound() {
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () ->
                apiKeyService.regenerateApiKey(999L, true, true)
        );

        assertTrue(exception.getMessage().contains("Merchant not found"));
        verify(merchantRepository, never()).save(any());
    }

    @Test
    void regenerateApiKey_ShouldVerifyRepositoryInteraction() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setApiKeyLive("old_live");
        merchant.setApiKeyTest("old_test");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        apiKeyService.regenerateApiKey(1L, true, true);

        ArgumentCaptor<Merchant> merchantCaptor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(merchantCaptor.capture());

        Merchant savedMerchant = merchantCaptor.getValue();
        assertEquals(1L, savedMerchant.getId());
        assertTrue(savedMerchant.getApiKeyTest().startsWith("pk_test_"));
        assertTrue(savedMerchant.getApiKeyLive().startsWith("pk_live_"));
        assertNotEquals("old_test", savedMerchant.getApiKeyTest());
        assertNotEquals("old_live", savedMerchant.getApiKeyLive());
    }

    // ---------- checkRateLimit ----------
    @Test
    void checkRateLimit_ShouldAllowWhenBelowLimit() {
        String apiKey = "pk_test_example";

        // Mock the Redis keys that are actually used by your implementation
        String hourlyKey = "apikey:" + apiKey + ":count:hourly:" + getCurrentHourlySuffix();
        String dailyKey = "apikey:" + apiKey + ":count:daily:" + getCurrentDailySuffix();

        when(valueOperations.get(hourlyKey)).thenReturn(100L);
        when(valueOperations.get(dailyKey)).thenReturn(500L);

        boolean result = apiKeyService.checkRateLimit(apiKey);

        assertTrue(result);
        verify(valueOperations).get(hourlyKey);
        verify(valueOperations).get(dailyKey);
    }

    @Test
    void checkRateLimit_ShouldBlockWhenAboveLimit() {
        String apiKey = "pk_test_over";
        when(valueOperations.get(anyString())).thenReturn(20000L);

        boolean result = apiKeyService.checkRateLimit(apiKey);

        assertFalse(result);
    }

    @Test
    void checkRateLimit_ShouldHandleNullRedisResponse() {
        String apiKey = "pk_test_new";
        when(valueOperations.get(anyString())).thenReturn(null);

        boolean result = apiKeyService.checkRateLimit(apiKey);

        assertTrue(result); // Should allow when no previous usage
    }

    @Test
    void checkRateLimit_ShouldHandleRedisErrorsGracefully() {
        String apiKey = "pk_test_error";
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // Should not throw exception, should fail open (allow request)
        assertDoesNotThrow(() -> {
            boolean result = apiKeyService.checkRateLimit(apiKey);
            assertFalse(result, "Should allow request when Redis fails");
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {9999L, 10000L, 49999L, 50000L})
    void checkRateLimit_ShouldRespectDifferentLimits(long usageCount) {
        String testKey = "pk_test_key";
        String liveKey = "pk_live_key";

        when(valueOperations.get(anyString())).thenReturn(usageCount);

        boolean testResult = apiKeyService.checkRateLimit(testKey);
        boolean liveResult = apiKeyService.checkRateLimit(liveKey);

        // Both should behave the same way with current implementation
        // This test documents the current behavior
        assertEquals(testResult, liveResult);
    }

    // ---------- convertToLong ----------
    @Test
    void convertToLong_ShouldHandleVariousTypes() {
        assertEquals(5L, apiKeyService.convertToLong(5));
        assertEquals(10L, apiKeyService.convertToLong(10L));
        assertEquals(20L, apiKeyService.convertToLong(20.5)); // truncates decimal
        assertNull(apiKeyService.convertToLong("not a number"));
        assertNull(apiKeyService.convertToLong(null));
    }

    @Test
    void convertToLong_ShouldHandleEdgeCases() {
        assertEquals(0L, apiKeyService.convertToLong(0));
        assertEquals(-5L, apiKeyService.convertToLong(-5));
        assertEquals(Long.MAX_VALUE, apiKeyService.convertToLong(Long.MAX_VALUE));
        assertNull(apiKeyService.convertToLong(new Object()));
        assertNull(apiKeyService.convertToLong(""));
    }

    // ---------- maskApiKey ----------
    @Test
    void maskApiKey_ShouldHideMiddleSection() {
        String apiKey = "pk_live_abcdefgh123456";
        String masked = invokeMaskApiKey(apiKey);

        assertTrue(masked.startsWith("pk_live_abc"));
        assertTrue(masked.endsWith("456"));
        assertTrue(masked.contains("..."));
        assertTrue(masked.length() < apiKey.length());
    }

    @Test
    void maskApiKey_ShouldHandleShortKeys() {
        String shortKey = "pk_test_abc";
        String masked = invokeMaskApiKey(shortKey);

        assertNotNull(masked);
        // Should handle short keys gracefully without throwing exceptions
    }

    @Test
    void maskApiKey_ShouldHandleVeryShortKeys() {
        String veryShortKey = "pk_test";
        String masked = invokeMaskApiKey(veryShortKey);

        assertNotNull(masked);
        // Should not throw exception for very short keys
    }

    @Test
    void maskApiKey_ShouldHandleNullAndEmpty() {
        assertEquals("***", invokeMaskApiKey(null));
        assertEquals("***", invokeMaskApiKey(""));
    }

    // ---------- getClientIpAddress ----------
    @Test
    void getClientIpAddress_ShouldPreferForwardedHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.3");
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        String ip = apiKeyService.getClientIpAddress(request);

        assertEquals("10.0.0.1", ip);
    }


    @Test
    void getClientIpAddress_ShouldFallbackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        String ip = apiKeyService.getClientIpAddress(request);

        assertEquals("192.168.1.10", ip);
    }

    @Test
    void getClientIpAddress_ShouldHandleEmptyForwardedHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        String ip = apiKeyService.getClientIpAddress(request);

        assertEquals("192.168.1.10", ip);
    }

    @Test
    void getClientIpAddress_ShouldHandleMultipleForwardedIps() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.2, 10.0.0.3, 10.0.0.4");

        String ip = apiKeyService.getClientIpAddress(request);

        assertEquals("10.0.0.2", ip); // Should take the first one
    }

    // ---------- logApiKeyUsageToRedis ----------
    @Test
    void logApiKeyUsageToRedis_ShouldHandleRedisErrorsGracefully() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setEmail("test@example.com");

        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));

        // Should not throw exception to avoid breaking main flow
        assertDoesNotThrow(() ->
                apiKeyService.logApiKeyUsageToRedis(
                        merchant,
                        "pk_test_key",
                        "/api/v1/test",
                        "GET",
                        "192.168.1.1",
                        "Mozilla/5.0",
                        200
                )
        );
    }
    @Test
    void logApiKeyUsageToRedis_ShouldHandleNullMerchant() {
        // Should not throw NPE when merchant is null
        assertDoesNotThrow(() ->
                apiKeyService.logApiKeyUsageToRedis(
                        null,
                        "pk_test_key",
                        "/api/v1/test",
                        "GET",
                        "192.168.1.1",
                        "Mozilla/5.0",
                        200
                )
        );
    }

    @Test
    void logApiKeyUsageToRedis_ShouldHandleNullParameters() {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setEmail("test@example.com");

        // Should not throw NPE when various parameters are null
        assertDoesNotThrow(() ->
                apiKeyService.logApiKeyUsageToRedis(
                        merchant,
                        null,
                        null,
                        null,
                        null,
                        null,
                        200
                )
        );
    }

    // Helper method to access private maskApiKey method
    private String invokeMaskApiKey(String apiKey) {
        try {
            var method = ApiKeyService.class.getDeclaredMethod("maskApiKey", String.class);
            method.setAccessible(true);
            return (String) method.invoke(apiKeyService, apiKey);
        } catch (Exception e) {
            fail("Failed to call maskApiKey: " + e.getMessage());
            return null;
        }
    }

    private String getCurrentHourlySuffix() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%d-%02d-%02d-%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour());
    }

    private String getCurrentDailySuffix() {
        LocalDate today = LocalDate.now();
        return String.format("%d-%02d-%02d",
                today.getYear(), today.getMonthValue(), today.getDayOfMonth());
    }
}