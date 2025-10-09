# PayBridge - Unified Payment Gateway Platform

PayBridge is a comprehensive payment orchestration platform that enables merchants to integrate multiple payment providers through a single, unified API. The system handles payment routing, reconciliation, webhook management, and provides detailed analytics across different payment gateways.

## What is PayBridge?

Think of PayBridge as a universal adapter for payment processing. Instead of integrating with Stripe, PayPal, and Flutterwave separately, merchants integrate once with PayBridge and gain access to all providers. The platform intelligently routes payments, handles failures, manages reconciliation, and provides unified reporting across all payment channels.

## Architecture Overview

PayBridge is built on Spring Boot and follows a multi-tenant architecture where each merchant can configure multiple payment providers. The system uses PostgreSQL for persistent storage, Redis for caching and idempotency, and RabbitMQ for asynchronous processing.

Key components include:
- **Payment Processing Engine**: Handles transaction lifecycle from initiation to completion
- **Smart Routing**: Routes payments to optimal providers based on configurable rules
- **Reconciliation System**: Automatically reconciles transactions with provider records
- **Webhook Management**: Delivers payment events to merchant endpoints with retry logic
- **Audit System**: Comprehensive logging of all system activities

## Project Requirements

### Software Requirements
- **Java 17** or higher
- **Maven 3.8+** for dependency management
- **PostgreSQL 14+** as the primary database
- **Redis 6+** for caching and distributed locking
- **RabbitMQ 3.10+** for message queuing

### Development Tools
- **IntelliJ IDEA** or **Eclipse** (recommended IDEs)
- **Postman** or similar API testing tool
- **Docker** and **Docker Compose** (optional, for containerized dependencies)

## Dependencies

PayBridge uses Spring Boot 3.x with the following major dependencies:

### Core Framework
- `spring-boot-starter-web` - REST API endpoints
- `spring-boot-starter-data-jpa` - Database persistence
- `spring-boot-starter-security` - Authentication and authorization
- `spring-boot-starter-oauth2-resource-server` - JWT token validation

### Database & Persistence
- `postgresql` - PostgreSQL JDBC driver
- `liquibase-core` - Database migration management
- `spring-boot-starter-data-redis` - Redis integration

### Messaging & Events
- `spring-boot-starter-amqp` - RabbitMQ integration

### Security
- `nimbus-jose-jwt` - JWT encoding and decoding
- `bcrypt` - Password hashing (via Spring Security)

### Validation & Utilities
- `spring-boot-starter-validation` - Request validation
- `jackson-databind` - JSON processing

### Testing
- `spring-boot-starter-test` - Testing framework
- `junit-jupiter` - JUnit 5 testing
- `mockito-core` - Mocking framework

## Getting Started

### Database Setup

PayBridge requires a PostgreSQL database. Create the database and user:

```sql
CREATE DATABASE paybridge;
CREATE USER admin WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE paybridge TO admin;
```

### Generate RSA Keys

The application uses RSA keys for JWT signing. Generate a key pair:

```bash
# Generate private key
openssl genrsa -out private.pem 2048

# Generate public key
openssl rsa -in private.pem -pubout -out public.pem
```

### Configuration

Create an `application.properties` file or set environment variables:

```properties
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=paybridge
DB_USER=admin
DB_PASSWORD=your_secure_password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379

# RabbitMQ Configuration
RABBIT_HOST=localhost
RABBIT_PORT=5672

# RSA Keys (base64 encoded or file paths)
RSA_PRIVATE_KEY=classpath:certs/private.pem
RSA_PUBLIC_KEY=classpath:certs/public.pem
```

For production, pass RSA keys as environment variables instead of file paths:

```bash
export RSA_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAo...
-----END PRIVATE KEY-----"
```

### Install Dependencies

Navigate to the project root and run:

```bash
mvn clean install
```

This command downloads all dependencies and compiles the project.

## Running the Application

### Local Development

Start the application using Maven:

```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

### Running with Docker Compose

If you prefer containerized dependencies:

```bash
docker-compose up -d postgres redis rabbitmq
mvn spring-boot:run
```

### Building for Production

Create an executable JAR:

```bash
mvn clean package -DskipTests
java -jar target/paybridge-0.0.1-SNAPSHOT.jar
```

## API Endpoints

### Authentication

#### Login
Authenticate and receive a JWT token for subsequent requests.

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "merchant@example.com",
  "password": "securePassword123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9...",
  "email": "merchant@example.com",
  "userType": "MERCHANT",
  "expiresIn": "1 hour"
}
```

### Merchant Registration

#### Register New Merchant
Create a new merchant account with business details.

```http
POST /api/v1/merchants
Content-Type: application/json

{
  "businessName": "Tech Store",
  "email": "contact@techstore.com",
  "password": "SecurePass123",
  "businessType": "ecommerce",
  "businessCountry": "NG",
  "websiteUrl": "https://techstore.com"
}
```

Response:
```json
{
  "businessName": "Tech Store",
  "email": "contact@techstore.com",
  "status": "PENDING_PROVIDER_SETUP",
  "message": "Merchant Successfully Registered",
  "nextStep": "Please configure payment providers to start receiving payments"
}
```

### Protected Endpoints

Include the JWT token in the Authorization header:

```http
GET /protected
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

## Code Examples

### Custom User Details Implementation

PayBridge implements Spring Security's `UserDetails` to manage authentication:

```java
public class CustomUserDetails implements UserDetails {
    private Users users;

    public CustomUserDetails(Users users) {
        this.users = users;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
            new SimpleGrantedAuthority(users.getUserType().name())
        );
    }

    @Override
    public String getUsername() {
        return users.getEmail();
    }

    // Additional methods for account status...
}
```

This implementation maps database users to Spring Security's authentication system, enabling role-based access control.

### JWT Token Generation

The token service creates signed JWT tokens with user claims:

```java
public String generateToken(Authentication authentication) {
    Instant now = Instant.now();
    
    String scope = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.joining(" "));

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuedAt(now)
        .expiresAt(now.plus(1, ChronoUnit.HOURS))
        .claim("scope", scope)
        .subject(authentication.getName())
        .issuer("self")
        .build();

    return this.jwtEncoder.encode(JwtEncoderParameters.from(claims))
        .getTokenValue();
}
```

Tokens expire after one hour and include the user's roles for authorization.

### Merchant Registration Flow

The registration process creates both a merchant entity and associated user:

```java
@Transactional
public MerchantRegistrationResponse registerMerchant(
    MerchantRegistrationRequest request) {
    
    // Validate unique email
    if (merchantRepository.existsByEmail(request.getEmail())) {
        throw new IllegalArgumentException("Email already exists");
    }

    // Create merchant entity
    Merchant merchant = new Merchant();
    merchant.setBusinessName(request.getBusinessName());
    merchant.setEmail(request.getEmail());
    merchant.setStatus(MerchantStatus.PENDING_PROVIDER_SETUP);
    
    // Create associated user
    Users user = new Users();
    user.setMerchant(merchant);
    user.setUserType(UserType.MERCHANT);
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setEnabled(true);

    userRepository.save(user);
    merchantRepository.save(merchant);

    return new MerchantRegistrationResponse(/* ... */);
}
```

The `@Transactional` annotation ensures both entities are saved atomically.

### RSA Key Loading

PayBridge supports loading RSA keys from either classpath or environment variables:

```java
private String getKeyContent(String keySource) throws Exception {
    if (keySource.startsWith("classpath:")) {
        String resourcePath = keySource.substring("classpath:".length());
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return FileCopyUtils.copyToString(
            new InputStreamReader(resource.getInputStream())
        );
    } else {
        // Direct key content from environment variable
        return keySource;
    }
}
```

This flexibility allows secure key management across different environments.

## Database Schema

PayBridge uses Liquibase for database version control. The schema includes:

- **merchants**: Business accounts using the platform
- **users**: Authentication credentials (merchants and admins)
- **providers**: Supported payment gateways (Stripe, PayPal, Flutterwave)
- **provider_configs**: Merchant-specific provider configurations
- **payments**: Transaction records
- **payment_events**: Transaction lifecycle events
- **webhook_deliveries**: Outbound webhook attempt logs
- **reconciliation_jobs**: Automated reconciliation processes

All migrations are defined in `src/main/resources/db/changelog/db.changelog-master.xml`.

## Security Configuration

The application uses JWT-based authentication with RSA signatures:

- Public endpoints: `/api/v1/merchants`, `/api/v1/auth/**`
- Protected endpoints: All others require valid JWT tokens
- Session management: Stateless (no server-side sessions)
- Password encoding: BCrypt with strength 10

CSRF protection is disabled as the API is stateless and token-based.

## Testing

Run the test suite:

```bash
mvn test
```

The project includes unit tests for core services using JUnit 5 and Mockito. Key test classes:

- `AuthenticationServiceTest`: Validates login flows and token generation
- `MerchantServiceTest`: Tests merchant registration and validation

## Project Structure

```
src/
├── main/
│   ├── java/com/paybridge/
│   │   ├── Configs/          # Configuration classes
│   │   ├── Controllers/      # REST API endpoints
│   │   ├── Models/
│   │   │   ├── DTOs/        # Data transfer objects
│   │   │   ├── Entities/    # JPA entities
│   │   │   └── Enums/       # Enumeration types
│   │   ├── Repositories/    # Data access layer
│   │   ├── Security/        # Security configuration
│   │   └── Services/        # Business logic
│   └── resources/
│       ├── application.properties
│       └── db/changelog/    # Database migrations
└── test/                    # Unit and integration tests
```

## Next Steps

After setting up PayBridge, you'll want to:

1. **Configure Payment Providers**: Add API keys for Stripe, PayPal, or Flutterwave
2. **Set Up Webhooks**: Configure webhook endpoints to receive payment notifications
3. **Create Routing Rules**: Define intelligent payment routing logic
4. **Enable Reconciliation**: Schedule automated reconciliation jobs
5. **Monitor Analytics**: Use the dashboard views for transaction insights

## Contributing

We welcome contributions! The project follows standard Spring Boot conventions. When submitting pull requests:

- Write unit tests for new features
- Follow existing code style and naming conventions
- Update documentation for API changes
- Ensure all tests pass before submitting


