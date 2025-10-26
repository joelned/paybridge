package com.paybridge.unit.Service;

import com.paybridge.Models.Entities.ApiKeyUsage;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.ApiKeyUsageRepository;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ListOperations;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    private ListOperations<String, Object> listOperations;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private Merchant testMerchant;
    private final String testApiKey = "pk_test_abcdef123456";

    @BeforeEach
    void setUp() {
        testMerchant = new Merchant();
        testMerchant.setId(1L);
        testMerchant.setEmail("test@example.com");
        testMerchant.setBusinessName("Test Business");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    void generateApiKey_TestMode_ReturnsTestPrefixedKey() {
        // Act
        String apiKey = apiKeyService.generateApiKey(true);

        // Assert
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("pk_test_"));
        assertTrue(apiKey.length() > 40); // Base64 encoded 32 bytes + prefix
    }

    @Test
    void generateApiKey_LiveMode_ReturnsLivePrefixedKey() {
        // Act
        String apiKey = apiKeyService.generateApiKey(false);

        // Assert
        assertNotNull(apiKey);
        assertTrue(apiKey.startsWith("pk_live_"));
        assertTrue(apiKey.length() > 40);
    }

    @Test
    void generateApiKey_MultipleCalls_ReturnsDifferentKeys() {
        // Act
        String key1 = apiKeyService.generateApiKey(true);
        String key2 = apiKeyService.generateApiKey(true);
        String key3 = apiKeyService.generateApiKey(false);

        // Assert
        assertNotEquals(key1, key2);
        assertNotEquals(key2, key3);
        assertNotEquals(key1, key3);
    }

    @Test
    void isTestMode_TestKey_ReturnsTrue() {
        // Arrange
        String testKey = "pk_test_abc123";

        // Act
        boolean result = apiKeyService.isTestMode(testKey);

        // Assert
        assertTrue(result);
    }

    @Test
    void isTestMode_LiveKey_ReturnsFalse() {
        // Arrange
        String liveKey = "pk_live_abc123";

        // Act
        boolean result = apiKeyService.isTestMode(liveKey);

        // Assert
        assertFalse(result);
    }

    @Test
    void isTestMode_NullKey_ReturnsFalse() {
        // Act
        boolean result = apiKeyService.isTestMode(null);

        // Assert
        assertFalse(result);
    }

    @Test
    void regenerateApiKey_RegenerateTest_UpdatesTestKey() {
        // Arrange
        Long merchantId = 1L;
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
        when(merchantRepository.save(any(Merchant.class))).thenReturn(testMerchant);

        String oldTestKey = testMerchant.getApiKeyTest();

        // Act
        apiKeyService.regenerateApiKey(merchantId, true, false);

        // Assert
        verify(merchantRepository).save(testMerchant);
        assertNotEquals(oldTestKey, testMerchant.getApiKeyTest());
        assertTrue(testMerchant.getApiKeyTest().startsWith("pk_test_"));
    }

    @Test
    void regenerateApiKey_RegenerateLive_UpdatesLiveKey() {
        // Arrange
        Long merchantId = 1L;
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
        when(merchantRepository.save(any(Merchant.class))).thenReturn(testMerchant);

        String oldLiveKey = testMerchant.getApiKeyLive();

        // Act
        apiKeyService.regenerateApiKey(merchantId, false, true);

        // Assert
        verify(merchantRepository).save(testMerchant);
        assertNotEquals(oldLiveKey, testMerchant.getApiKeyLive());
        assertTrue(testMerchant.getApiKeyLive().startsWith("pk_live_"));
    }

    @Test
    void regenerateApiKey_RegenerateBoth_UpdatesBothKeys() {
        // Arrange
        Long merchantId = 1L;
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
        when(merchantRepository.save(any(Merchant.class))).thenReturn(testMerchant);

        // Act
        apiKeyService.regenerateApiKey(merchantId, true, true);

        // Assert
        verify(merchantRepository).save(testMerchant);
        assertNotNull(testMerchant.getApiKeyTest());
        assertNotNull(testMerchant.getApiKeyLive());
    }

    @Test
    void regenerateApiKey_MerchantNotFound_ThrowsException() {
        // Arrange
        Long merchantId = 999L;
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> apiKeyService.regenerateApiKey(merchantId, true, false));
    }

    @Test
    void checkRateLimit_WithinLimit_ReturnsTrue() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(100L); // Well within limit

        // Act
        boolean result = apiKeyService.checkRateLimit(testApiKey);

        // Assert
        assertTrue(result);
    }

    @Test
    void checkRateLimit_ExceedsHourlyLimit_ReturnsFalse() {
        // Arrange
        when(valueOperations.get(contains("hourly"))).thenReturn(1001L); // Over hourly limit
        when(valueOperations.get(contains("daily"))).thenReturn(500L);

        // Act
        boolean result = apiKeyService.checkRateLimit(testApiKey);

        // Assert
        assertFalse(result);
    }

    @Test
    void checkRateLimit_ExceedsDailyLimit_ReturnsFalse() {
        // Arrange
        when(valueOperations.get(contains("hourly"))).thenReturn(100L);
        when(valueOperations.get(contains("daily"))).thenReturn(10001L); // Over daily limit

        // Act
        boolean result = apiKeyService.checkRateLimit(testApiKey);

        // Assert
        assertFalse(result);
    }

    @Test
    void checkRateLimit_NoExistingCount_ReturnsTrue() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        boolean result = apiKeyService.checkRateLimit(testApiKey);

        // Assert
        assertTrue(result);
    }

    @Test
    void checkRateLimit_RedisException_ReturnsTrueAsFailsafe() {
        // Arrange
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act
        boolean result = apiKeyService.checkRateLimit(testApiKey);

        // Assert
        assertTrue(result); // Should allow request when Redis fails
    }

    @Test
    void getRealTimeStatistics_Success_ReturnsStats() {
        // Arrange
        when(valueOperations.get(contains("hourly"))).thenReturn(50L);
        when(valueOperations.get(contains("daily"))).thenReturn(500L);

        // Act
        Map<String, Object> stats = apiKeyService.getRealTimeStatistics(testApiKey);

        // Assert
        assertNotNull(stats);
        assertEquals(50L, stats.get("hourlyCount"));
        assertEquals(1000, stats.get("hourlyLimit"));
        assertEquals(950L, stats.get("hourlyRemaining"));
        assertEquals(500L, stats.get("dailyCount"));
        assertEquals(10000, stats.get("dailyLimit"));
        assertEquals(9500L, stats.get("dailyRemaining"));
    }

    @Test
    void getRealTimeStatistics_NoUsage_ReturnsZeroCount() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        Map<String, Object> stats = apiKeyService.getRealTimeStatistics(testApiKey);

        // Assert
        assertNotNull(stats);
        assertEquals(0L, stats.get("hourlyCount"));
        assertEquals(0L, stats.get("dailyCount"));
        assertEquals(1000L, stats.get("hourlyRemaining"));
        assertEquals(10000L, stats.get("dailyRemaining"));
    }

    @Test
    void getRealTimeStatistics_RedisException_ReturnsErrorMessage() {
        // Arrange
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act
        Map<String, Object> stats = apiKeyService.getRealTimeStatistics(testApiKey);

        // Assert
        assertNotNull(stats);
        assertTrue(stats.containsKey("error"));
    }

    @Test
    void logApiKeyUsageToRedis_Success_IncrementsCounters() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/payments");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Act
        apiKeyService.logApiKeyUsageToRedis(testMerchant, testApiKey, request, 200);

        // Assert
        verify(valueOperations, times(2)).increment(anyString());
        verify(redisTemplate, times(2)).expire(anyString(), any(Duration.class));
        verify(listOperations, times(1)).rightPush(anyString(), any());
    }

    @Test
    void getClientIpAddress_WithXForwardedFor_ReturnsFirstIp() {
        // Arrange
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.1");

        // Act
        String ip = apiKeyService.getClientIpAddress(request);

        // Assert
        assertEquals("203.0.113.1", ip);
    }

    @Test
    void getClientIpAddress_WithoutXForwardedFor_ReturnsRemoteAddr() {
        // Arrange
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        // Act
        String ip = apiKeyService.getClientIpAddress(request);

        // Assert
        assertEquals("192.168.1.1", ip);
    }

    @Test
    void getClientIpAddress_EmptyXForwardedFor_ReturnsRemoteAddr() {
        // Arrange
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        // Act
        String ip = apiKeyService.getClientIpAddress(request);

        // Assert
        assertEquals("192.168.1.1", ip);
    }

    @Test
    void persistLogsToDatabase_Success_SavesLogsAndDeletesFromRedis() {
        // Arrange
        Set<String> keys = Set.of("apikey:test:logs");
        when(redisTemplate.keys("apikey:*:logs")).thenReturn(keys);

        Map<String, Object> logData = new HashMap<>();
        logData.put("merchantId", 1L);
        logData.put("endpoint", "/api/v1/payments");
        logData.put("method", "POST");
        logData.put("ipAddress", "127.0.0.1");
        logData.put("userAgent", "TestAgent");
        logData.put("responseStatus", 200);
        logData.put("timestamp", "2025-01-01T12:00:00");

        List<Object> logs = List.of(logData);
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(logs);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));
        when(apiKeyUsageRepository.saveAll(anyList())).thenReturn(List.of());

        // Act
        apiKeyService.persistLogsToDatabase();

        // Assert
        verify(apiKeyUsageRepository, times(1)).saveAll(anyList());
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    void persistLogsToDatabase_NoLogs_DoesNothing() {
        // Arrange
        when(redisTemplate.keys("apikey:*:logs")).thenReturn(null);

        // Act
        apiKeyService.persistLogsToDatabase();

        // Assert
        verify(apiKeyUsageRepository, never()).saveAll(anyList());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void persistLogsToDatabase_MerchantNotFound_SkipsLog() {
        // Arrange
        Set<String> keys = Set.of("apikey:test:logs");
        when(redisTemplate.keys("apikey:*:logs")).thenReturn(keys);

        Map<String, Object> logData = new HashMap<>();
        logData.put("merchantId", 999L); // Non-existent merchant
        logData.put("endpoint", "/api/v1/payments");
        logData.put("method", "POST");
        logData.put("ipAddress", "127.0.0.1");
        logData.put("userAgent", "TestAgent");
        logData.put("responseStatus", 200);

        List<Object> logs = List.of(logData);
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(logs);
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        apiKeyService.persistLogsToDatabase();

        // Assert
        verify(apiKeyUsageRepository, never()).saveAll(anyList());
    }

    @Test
    void convertToLong_IntegerValue_ReturnsLong() {
        // Act
        Long result = apiKeyService.convertToLong(42);

        // Assert
        assertEquals(42L, result);
    }

    @Test
    void convertToLong_LongValue_ReturnsLong() {
        // Act
        Long result = apiKeyService.convertToLong(42L);

        // Asserpublic t
        assertEquals(42L, result);
    }

    @Test
    void convertToLong_NullValue_ReturnsNull() {
        // Act
        Long result = apiKeyService.convertToLong(null);

        // Assert
        assertNull(result);
    }

    @Test
    void convertToLong_NumberValue_ReturnsLong() {
        // Act
        Long result = apiKeyService.convertToLong(42.0);

        // Assert
        assertEquals(42L, result);
    }
}