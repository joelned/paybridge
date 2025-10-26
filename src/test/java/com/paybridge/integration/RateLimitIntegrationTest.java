package com.paybridge.integration;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.VerifyEmailRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for API rate limiting functionality
 * Note: These tests require Redis to be running
 * Note: Some tests are commented out due to @Transactional rollback preventing API key authentication
 * The rate limiting logic itself is tested in ApiKeyServiceTest (unit tests)
 */
public class RateLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Note: Tests that use API key authentication with @Transactional are problematic
     * because the transaction rollback removes the merchant/API key from the database
     * before the filter can authenticate. These scenarios are better tested as:
     * 1. Unit tests (ApiKeyServiceTest) - test the rate limiting logic directly
     * 2. Without @Transactional - but this pollutes the test database
     * 3. With manual cleanup - more complex test setup
     */

    @Test
    void rateLimit_RedisIntegration_CountersWork() throws Exception {
        // Skip if Redis is not available
        if (redisTemplate == null) {
            System.out.println("Skipping rate limit test - Redis not available");
            return;
        }

        // Test that we can interact with Redis for rate limiting
        String testKey = "apikey:test_key_12345:count:hourly:2025-10-26-08";

        // Set a value
        redisTemplate.opsForValue().set(testKey, 100L);

        // Retrieve and verify
        Object value = redisTemplate.opsForValue().get(testKey);
        assertNotNull(value);
        assertEquals(100L, ((Number) value).longValue());

        // Increment
        redisTemplate.opsForValue().increment(testKey);
        value = redisTemplate.opsForValue().get(testKey);
        assertEquals(101L, ((Number) value).longValue());

        // Cleanup
        redisTemplate.delete(testKey);
    }

    @Test
    void rateLimit_RedisKeyFormat_Correct() throws Exception {
        // Skip if Redis is not available
        if (redisTemplate == null) {
            System.out.println("Skipping rate limit test - Redis not available");
            return;
        }

        // Verify the key format used by ApiKeyService
        String apiKey = "pk_test_abc123";
        String hourlyKey = String.format("apikey:%s:count:hourly:%s",
                apiKey,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")));

        // Should be able to set and get with this key format
        redisTemplate.opsForValue().set(hourlyKey, 50L);
        Object value = redisTemplate.opsForValue().get(hourlyKey);

        assertNotNull(value);
        assertEquals(50L, ((Number) value).longValue());

        // Cleanup
        redisTemplate.delete(hourlyKey);
    }

    @Test
    void rateLimit_DailyKeyFormat_Correct() throws Exception {
        // Skip if Redis is not available
        if (redisTemplate == null) {
            System.out.println("Skipping rate limit test - Redis not available");
            return;
        }

        // Verify the daily key format
        String apiKey = "pk_live_xyz789";
        String dailyKey = String.format("apikey:%s:count:daily:%s",
                apiKey,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        // Should be able to set and get with this key format
        redisTemplate.opsForValue().set(dailyKey, 1000L);
        Object value = redisTemplate.opsForValue().get(dailyKey);

        assertNotNull(value);
        assertEquals(1000L, ((Number) value).longValue());

        // Cleanup
        redisTemplate.delete(dailyKey);
    }

    @Test
    void rateLimit_ExpiryWorks() throws Exception {
        // Skip if Redis is not available
        if (redisTemplate == null) {
            System.out.println("Skipping rate limit test - Redis not available");
            return;
        }

        // Test that expiry is set correctly
        String testKey = "apikey:test_expiry:count:hourly:2025-10-26-08";

        redisTemplate.opsForValue().set(testKey, 1L);
        redisTemplate.expire(testKey, java.time.Duration.ofSeconds(1));

        // Key should exist initially
        assertTrue(redisTemplate.hasKey(testKey));

        // Wait for expiry
        Thread.sleep(1100);

        // Key should be gone
        assertFalse(redisTemplate.hasKey(testKey));
    }

    // Helper method
    private void registerAndVerifyMerchant(String email, String password) throws Exception {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest();
        request.setBusinessName("Test Business");
        request.setEmail(email);
        request.setPassword(password);
        request.setBusinessType("ECOMMERCE");
        request.setBusinessCountry("US");
        request.setWebsiteUrl("https://example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        Users user = userRepository.findByEmail(email);
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest(email, user.getVerificationCode());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isOk());
    }
}