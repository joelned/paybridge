package com.paybridge.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Test configuration for integration tests
 */
@TestConfiguration
@Profile("integration-test")
public class IntegrationTestConfig {


    /**
     * Use a mock Redis connection that connects to nothing
     * Tests will run without actual Redis
     */
    @Bean @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        // This will attempt to connect to localhost:6379
        // Make sure Redis is running locally or use option 2/3
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        return factory;
    }
}