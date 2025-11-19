package com.paybridge.integration;

import com.paybridge.Services.CredentialStorageService;
import com.paybridge.Services.impl.EmailService;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @MockitoBean
    protected CredentialStorageService credentialStorageService;

    @MockitoBean
    protected EmailService emailService;

    @MockitoBean
    protected RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    protected org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

}
