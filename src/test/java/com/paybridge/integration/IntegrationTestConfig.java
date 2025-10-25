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


    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        // Return a mock that does nothing for tests
        return new org.springframework.mail.javamail.JavaMailSenderImpl() {
            @Override
            public void send(org.springframework.mail.SimpleMailMessage simpleMessage) {
                // Do nothing - this is a test
            }

            @Override
            public void send(org.springframework.mail.SimpleMailMessage... simpleMessages) {
                // Do nothing - this is a test
            }

            @Override
            public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator) {
                // Do nothing - this is a test
            }

            @Override
            public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators) {
                // Do nothing - this is a test
            }

            @Override
            public void send(jakarta.mail.internet.MimeMessage mimeMessage) {
                // Do nothing - this is a test
            }

            @Override
            public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
                // Do nothing - this is a test
            }
        };
    }

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