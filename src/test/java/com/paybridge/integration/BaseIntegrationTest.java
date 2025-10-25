package com.paybridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for integration tests providing common setup and utilities
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestPropertySource(locations = "classpath:application-test.properties")
@Transactional
public abstract class BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected MerchantRepository merchantRepository;

    @Autowired(required = false) // Make Redis optional
    protected RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void baseSetUp() {
        // Clean up database before each test
        cleanDatabase();
        // Clean up Redis if available
        cleanRedis();
    }

    @AfterEach
    void baseTearDown() {
        // Clean up after each test
        cleanDatabase();
        cleanRedis();
    }

    protected void cleanDatabase() {
        // Delete in proper order to respect foreign key constraints
        userRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    protected void cleanRedis() {
        // Clear all Redis keys if Redis is available
        if (redisTemplate != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().flushAll();
                logger.info("Redis cleaned successfully");
            } catch (Exception e) {
                logger.warn("Could not clean Redis: {}. Continuing with tests...", e.getMessage());
                // Continue with tests even if Redis cleanup fails
            }
        }
    }

    /**
     * Helper method to convert object to JSON string
     */
    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * Helper method to parse JSON string to object
     */
    protected <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }
}