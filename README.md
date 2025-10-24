# PayBridge - Payment Orchestration Platform

## Introduction

PayBridge is a comprehensive payment orchestration platform designed to simplify payment processing for merchants. Built with Spring Boot and modern Java technologies, it provides a unified API interface to multiple payment providers while handling complex payment workflows, reconciliation, and merchant management.

Think of PayBridge as a "universal translator" for payment systems - it allows merchants to integrate once and connect to multiple payment providers (Stripe, PayPal, Flutterwave, etc.) through a single, consistent API.

## Key Features

### 🔐 Multi-Layer Security
- **JWT Authentication** with RSA key pairs for secure API access
- **API Key Management** with test/live mode support and rate limiting
- **Role-based Access Control** for merchants and administrators

### 💳 Payment Orchestration
- Unified payment processing across multiple providers
- Smart routing and failover capabilities
- Webhook handling for payment status updates
- Comprehensive payment event tracking

### 📊 Advanced Monitoring
- Real-time API usage statistics and rate limiting
- Redis-powered analytics with hourly/daily limits
- Automated reconciliation between PayBridge and provider records
- Detailed audit logging for compliance

### 🚀 Merchant Management
- Self-service merchant onboarding with email verification
- Test and live mode API keys
- Provider configuration management
- Webhook endpoint configuration

## Architecture Overview

```
Client Apps → PayBridge API → Payment Providers
    ↑              ↓              ↓
    └───────── Redis (Caching & Analytics)
                ↓
           PostgreSQL (Persistence)
```

## Technology Stack

- **Backend Framework**: Spring Boot 3.x
- **Security**: Spring Security 6 + JWT + RSA Encryption
- **Database**: PostgreSQL with Hibernate/JPA
- **Caching**: Redis for rate limiting and analytics
- **Messaging**: RabbitMQ for async processing
- **Email**: Spring Mail for verification and notifications


## Project Structure

```
src/main/java/com/paybridge/
├── Configs/           # Configuration classes
├── Controllers/       # REST API endpoints
├── Filters/          # Security filters
├── Models/           # Entities and DTOs
├── Repositories/     # Data access layer
├── Security/         # Security configuration
└── Services/         # Business logic
```

## Getting Started

### Prerequisites

- Java 17 or higher
- PostgreSQL 14+
- Redis 6+
- RabbitMQ 3.8+

### Environment Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd paybridge
   ```

2. **Configure environment variables**
   ```bash
   # Database
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=paybridge
   export DB_USER=admin
   export DB_PASSWORD=your_password

   # Redis
   export REDIS_HOST=localhost
   export REDIS_PORT=6379

   # RSA Keys (generate or provide paths)
   export RSA_PRIVATE_KEY=classpath:private-key.pem
   export RSA_PUBLIC_KEY=classpath:public-key.pem

   # Email (Gmail example)
   export SPRING_MAIL_USERNAME=your-email@gmail.com
   export SPRING_MAIL_PASSWORD=your-app-password
   ```

3. **Generate RSA Key Pair**
   ```bash
   # Generate private key
   openssl genpkey -algorithm RSA -out private-key.pem -pkeyopt rsa_keygen_bits:2048
   
   # Generate public key
   openssl rsa -pubout -in private-key.pem -out public-key.pem
   ```

4. **Database Setup**
   ```sql
   CREATE DATABASE paybridge;
   -- Liquibase will handle schema creation on application startup
   ```

### Running the Application

```bash
# Build the project
./mvnw clean package

# Run the application
java -jar target/paybridge-0.0.1-SNAPSHOT.jar

# Or use Maven Spring Boot plugin
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`

## API Usage Examples

### 1. Merchant Registration

```java
// Register a new merchant
POST /api/v1/merchants
Content-Type: application/json

{
  "businessName": "Acme Corp",
  "email": "merchant@acme.com",
  "password": "SecurePass123",
  "businessType": "E_COMMERCE",
  "businessCountry": "US",
  "websiteUrl": "https://acme.com"
}
```

Response:
```json
{
  "businessName": "Acme Corp",
  "email": "merchant@acme.com",
  "status": "PENDING_VERIFICATION",
  "message": "Registration successful. Please check your email for verification code.",
  "nextStep": "Verify your email to activate your account"
}
```

### 2. Email Verification

```java
// Verify email with code received
POST /api/v1/auth/verify-email
Content-Type: application/json

{
  "email": "merchant@acme.com",
  "code": "123456"
}
```

### 3. API Key Authentication

```java
// Make authenticated requests with API key
GET /api/v1/payments
X-API-Key: pk_test_abc123...
```

### 4. Check Rate Limits

```java
// Get current usage statistics
String apiKey = "pk_test_abc123...";
Map<String, Object> stats = apiKeyService.getRealTimeStatistics(apiKey);

// Response example
{
  "hourlyCount": 150,
  "hourlyLimit": 1000,
  "hourlyRemaining": 850,
  "dailyCount": 450,
  "dailyLimit": 10000,
  "dailyRemaining": 9550
}
```

## Core Components Deep Dive

### Security Configuration

PayBridge implements a multi-layered security approach:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/merchants").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(apiKeyAuthenticationFilter, 
                UsernamePasswordAuthenticationFilter.class)
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

### API Key Management

The `ApiKeyService` handles key generation, rate limiting, and usage tracking:

```java
@Service
public class ApiKeyService {
    
    public String generateApiKey(boolean isTestMode) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        
        return (isTestMode ? TEST_PREFIX : LIVE_PREFIX) + key;
    }
    
    public boolean checkRateLimit(String apiKey) {
        // Check hourly and daily limits in Redis
        Long hourlyCount = getHourlyUsage(apiKey);
        Long dailyCount = getDailyUsage(apiKey);
        
        return hourlyCount <= HOURLY_LIMIT && dailyCount <= DAILY_LIMIT;
    }
}
```


## Configuration Details

### Redis Configuration

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        return template;
    }
}
```

### Async Processing

```java
@Configuration
public class AsyncConfig {
    
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setTaskDecorator(new SecurityContextAwareTaskDecorator());
        return executor;
    }
}
```

## Monitoring and Analytics

### API Usage Tracking

PayBridge automatically tracks API usage in Redis with two-level rate limiting:

- **Hourly Limit**: 1,000 requests per hour
- **Daily Limit**: 10,000 requests per day

Usage data is periodically persisted to PostgreSQL for long-term analytics.

### Logging Configuration

Method-level logging with execution timing:

```java
@Around("execution(* com.paybridge.Services.*Service.*(..))")
public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.currentTimeMillis();
    try {
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;
        log.info("{}.{} executed in {} ms", 
            className, methodName, duration);
        return result;
    } catch (Exception e) {
        long duration = System.currentTimeMillis() - start;
        log.error("Error in {}.{} after {} ms", 
            className, methodName, duration, e);
        throw e;
    }
}
```

### Database Migrations

The project uses Liquibase for database migrations. Add new changesets to `db/changelog/db.changelog-master.xml`.

### Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify
```

## Production Considerations

### Security Best Practices

- Rotate RSA key pairs regularly
- Monitor API usage patterns for anomalies
- Implement IP whitelisting for sensitive operations
- Use HTTPS in production environments

### Performance Optimization

- Configure connection pooling for database and Redis
- Implement caching for frequently accessed merchant data
- Use database indexing for query optimization
- Monitor Redis memory usage and configure eviction policies

### Scaling Strategies

- Horizontal scaling with load balancers
- Database read replicas for reporting queries
- Redis cluster for distributed caching
- Message queue partitioning for high throughput

## Support and Contributing

For issues, feature requests, or contributions:

1. Check existing issues and documentation
2. Create detailed bug reports with reproduction steps
3. Follow the code style and testing guidelines
4. Submit pull requests with clear descriptions

## Conclusion

PayBridge provides a robust foundation for payment orchestration with enterprise-grade security, comprehensive monitoring, and flexible provider integration. The modular architecture allows for easy extension while maintaining high performance and reliability.

Whether you're building a new payment integration or modernizing an existing system, PayBridge offers the tools and infrastructure needed to manage complex payment workflows efficiently.

