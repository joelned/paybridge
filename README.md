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

- **Backend Framework**: Spring Boot 3.5.6
- **Security**: Spring Security 6 + JWT + RSA Encryption
- **Database**: PostgreSQL with Hibernate/JPA + Liquibase migrations
- **Caching**: Redis for rate limiting and analytics
- **Messaging**: RabbitMQ for async processing
- **Email**: Spring Mail for verification and notifications
- **Payment Providers**: Stripe, Flutterwave integration
- **Secret Management**: HashiCorp Vault integration
- **Testing**: JUnit 5, Testcontainers, Spring Boot Test
- **Build Tool**: Maven 3.x


## Project Structure

```
src/main/java/com/paybridge/
├── Configs/           # Configuration classes (Redis, CORS, Async, etc.)
├── Controllers/       # REST API endpoints
│   ├── AuthController.java
│   ├── MerchantController.java
│   └── ProviderController.java
├── Filters/          # Security filters
│   ├── ApiKeyAuthenticationFilter.java
│   └── CookieAuthenticationFilter.java
├── Models/           # Entities and DTOs
│   ├── DTOs/         # Data Transfer Objects
│   ├── Entities/     # JPA Entities
│   └── Enums/        # Application enums
├── Repositories/     # Data access layer
├── Security/         # Security configuration
└── Services/         # Business logic
    ├── ApiKeyService.java
    ├── AuthenticationService.java
    ├── EmailService.java
    ├── MerchantService.java
    ├── ProviderService.java
    ├── TokenService.java
    ├── VaultService.java
    └── VerificationService.java

src/main/resources/
├── certs/            # RSA key pairs
├── db/changelog/     # Liquibase migrations
└── application.properties

src/test/
├── java/com/paybridge/
│   ├── integration/  # Integration tests
│   └── unit/         # Unit tests
└── resources/
    ├── test-keys/    # Test RSA keys
    └── application-test.properties
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

### 3. Login

```bash
# Login with verified credentials
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "merchant@acme.com",
  "password": "SecurePass123"
}
```

Response sets secure HTTP-only cookie with JWT token.

### 4. Get API Keys

```bash
# Get API keys (requires authentication)
GET /api/v1/get-apikey
Cookie: jwt=<jwt-token>
```

### 5. API Key Authentication

```bash
# Make authenticated requests with API key
GET /api/v1/payments
X-API-Key: pk_test_abc123...
```

### 6. Check Rate Limits

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

PayBridge includes comprehensive test coverage with both unit and integration tests:

#### Test Structure
- **Unit Tests**: Service layer testing with mocked dependencies
- **Integration Tests**: Full application context testing with Testcontainers
- **Security Tests**: Authentication and authorization testing
- **Rate Limiting Tests**: Redis-based rate limiting validation

#### Running Tests

```bash
# Run all tests
./mvnw test

# Run only unit tests
./mvnw test -Dtest="**/unit/**"

# Run only integration tests
./mvnw test -Dtest="**/integration/**"

# Run with coverage
./mvnw test jacoco:report

# Run integration tests with Testcontainers
./mvnw verify
```

#### Test Configuration

Tests use H2 in-memory database and embedded Redis for fast execution:

```properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.redis.host=localhost
spring.redis.port=6370
```

## Additional Features

### 🔒 Vault Integration

Secure secret management with HashiCorp Vault:

```java
@Service
public class VaultService {
    public void storeProviderCredentials(String merchantId, String provider, Map<String, String> credentials) {
        String path = String.format("secret/merchants/%s/providers/%s", merchantId, provider);
        vaultTemplate.write(path, credentials);
    }
}
```

### 📧 Email Verification System

- Automated email verification with 6-digit codes
- Code expiration and rate limiting
- Resend verification functionality
- HTML email templates

### 🔄 Provider Integration

- **Stripe**: Full payment processing integration
- **Flutterwave**: African payment gateway support
- Connection testing and validation
- Provider-specific configuration management

### 📊 Comprehensive Logging

- Method execution timing with AOP
- Request/response logging
- Security event auditing
- Performance monitoring

## Production Considerations

### Security Best Practices

- Rotate RSA key pairs regularly (stored in `src/main/resources/certs/`)
- Monitor API usage patterns for anomalies
- Implement IP whitelisting for sensitive operations
- Use HTTPS in production environments
- Secure cookie configuration (HttpOnly, Secure, SameSite)
- Vault integration for sensitive data storage

### Performance Optimization

- Connection pooling configured for PostgreSQL and Redis
- Async processing with ThreadPoolTaskExecutor
- Redis caching for frequently accessed data
- Database indexing for query optimization
- Method-level performance monitoring

### Scaling Strategies

- Horizontal scaling with load balancers
- Database read replicas for reporting queries
- Redis cluster for distributed caching
- Message queue partitioning for high throughput
- Testcontainers for consistent testing environments

## API Endpoints

### Public Endpoints
- `POST /api/v1/merchants` - Merchant registration
- `POST /api/v1/auth/verify-email` - Email verification
- `POST /api/v1/auth/resend-verification` - Resend verification code
- `POST /api/v1/auth/login` - Merchant login

### Protected Endpoints (JWT Required)
- `GET /api/v1/get-apikey` - Get API keys
- `POST /api/v1/providers/test-connection` - Test provider connection
- `GET /api/v1/merchants/profile` - Get merchant profile

### API Key Protected Endpoints
- `GET /api/v1/payments` - List payments
- `POST /api/v1/payments` - Create payment
- `GET /api/v1/usage-stats` - Get usage statistics

## Environment Variables

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=paybridge
DB_USER=admin
DB_PASSWORD=your_password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379

# RSA Keys
RSA_PRIVATE_KEY=classpath:certs/privatekey.pem
RSA_PUBLIC_KEY=classpath:certs/publickey.pem

# Email Configuration
SPRING_MAIL_USERNAME=your-email@gmail.com
SPRING_MAIL_PASSWORD=your-app-password

# Vault Configuration (Optional)
SPRING_CLOUD_VAULT_HOST=localhost
SPRING_CLOUD_VAULT_PORT=8200
SPRING_CLOUD_VAULT_TOKEN=your-vault-token

# Provider API Keys
STRIPE_SECRET_KEY=sk_test_...
FLUTTERWAVE_SECRET_KEY=FLWSECK_TEST-...
```

## Support and Contributing

For issues, feature requests, or contributions:

1. Check existing issues and documentation
2. Create detailed bug reports with reproduction steps
3. Follow the code style and testing guidelines
4. Ensure all tests pass before submitting PRs
5. Submit pull requests with clear descriptions

## Conclusion

PayBridge provides a robust foundation for payment orchestration with enterprise-grade security, comprehensive monitoring, and flexible provider integration. The modular architecture allows for easy extension while maintaining high performance and reliability.

Whether you're building a new payment integration or modernizing an existing system, PayBridge offers the tools and infrastructure needed to manage complex payment workflows efficiently.

