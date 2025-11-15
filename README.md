<div align="center">

# PayBridge

### *Unified Payment Orchestration Platform*

[![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen?style=for-the-badge&logo=spring)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-blue?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-6+-red?style=for-the-badge&logo=redis)](https://redis.io/)

*A payment orchestration platform that provides unified API access to multiple payment providers through secure merchant onboarding, API key management, and provider configuration.*

</div>

---

## 🎯 Overview

PayBridge allows merchants to register, verify their accounts, and configure multiple payment providers (Stripe, Flutterwave, Paystack) through a single API. The platform handles authentication via JWT tokens and API keys, with built-in rate limiting and usage tracking.

## 🛠️ Technology Stack

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 17
- **Database**: PostgreSQL with Liquibase migrations
- **Caching**: Redis for rate limiting and analytics
- **Security**: Spring Security 6 with JWT (RSA encryption)
- **Email**: Spring Mail with SMTP
- **Secret Management**: HashiCorp Vault integration
- **Messaging**: RabbitMQ
- **Testing**: JUnit 5, Testcontainers
- **Build**: Maven

## 📁 Project Structure

```
src/main/java/com/paybridge/
├── Configs/                    # Configuration classes
│   ├── AsyncConfig.java
│   ├── CorsConfig.java
│   ├── FlutterwavePaymentProvider.java
│   ├── GlobalExceptionHandler.java
│   ├── LoggingConfiguration.java
│   ├── PaymentProvider.java
│   ├── PaystackPaymentProvider.java
│   ├── RedisConfig.java
│   ├── RestTemplateConfig.java
│   ├── RsaKeyConfiguration.java
│   ├── RsaKeyProperties.java
│   └── StripePaymentProvider.java
├── Controllers/                # REST API endpoints
│   ├── AuthController.java
│   ├── MerchantController.java
│   └── ProviderController.java
├── Filters/                   # Security filters
│   ├── ApiKeyAuthenticationFilter.java
│   └── CookieAuthenticationFilter.java
├── Models/                    # Data models
│   ├── DTOs/                 # Data Transfer Objects
│   ├── Entities/             # JPA Entities
│   └── Enums/                # Application enums
├── Repositories/             # Data access layer
├── Security/                 # Security configuration
│   └── SecurityConfig.java
├── Services/                 # Business logic
│   ├── impl/
│   │   ├── EmailService.java
│   │   └── VaultService.java
│   ├── ApiKeyService.java
│   ├── AuthenticationService.java
│   ├── ConnectionTestResult.java
│   ├── CredentialStorageService.java
│   ├── CustomUserDetailsService.java
│   ├── EmailProvider.java
│   ├── MerchantService.java
│   ├── PaymentProviderRegistry.java
│   ├── PaymentService.java
│   ├── ProviderService.java
│   ├── TokenService.java
│   └── VerificationService.java
└── PaybridgeApplication.java
```

## 🔗 API Endpoints

### 🌐 Public Endpoints
- `POST /api/v1/merchants` - Merchant registration
- `POST /api/v1/auth/login` - Merchant login
- `POST /api/v1/auth/verify-email` - Email verification
- `POST /api/v1/auth/resend-verification` - Resend verification code

### 🔒 Protected Endpoints (JWT Required)
- `POST /api/v1/providers/configure` - Configure payment provider
- `POST /api/v1/providers/test/{configId}` - Test provider connection

## ✨ Features

### 🔐 Authentication & Security
- **JWT Authentication**: RSA-signed tokens with HTTP-only cookies
- **API Key Management**: Test and live mode API keys with SHA-256 hashing
- **Rate Limiting**: 1,000 requests/hour, 10,000 requests/day per API key
- **CORS Configuration**: Supports multiple frontend origins
- **Password Encryption**: BCrypt hashing

### 👥 Merchant Management
- **Registration**: Business information with email verification
- **Email Verification**: 6-digit codes with 10-minute expiration
- **Account Status**: Pending verification → Active workflow

### 💳 Payment Provider Integration
- **Stripe**: Customer creation testing via Stripe API
- **Flutterwave**: OAuth2 client credentials authentication
- **Paystack**: Bearer token authentication
- **Connection Testing**: Validate provider credentials before saving

### 📊 Monitoring & Analytics
- **Usage Tracking**: Redis-based real-time API usage statistics
- **Scheduled Persistence**: Automatic transfer of Redis logs to PostgreSQL
- **Request Logging**: IP address, endpoint, response status tracking

### 🔑 Secret Management
- **Vault Integration**: HashiCorp Vault for secure credential storage
- **Profile-based**: Vault service activated via `vault` profile

## ⚙️ Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=paybridge
DB_USER=admin
DB_PASSWORD=your_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# RabbitMQ
RABBIT_HOST=localhost
RABBIT_PORT=5672

# RSA Keys
RSA_PRIVATE_KEY=classpath:certs/privatekey.pem
RSA_PUBLIC_KEY=classpath:certs/publickey.pem

# Vault (Optional)
VAULT_TOKEN=your_vault_token

# Email (Gmail SMTP)
spring.mail.username=your_email@gmail.com
spring.mail.password=your_app_password
```

## 🚀 Getting Started

### 📋 Prerequisites
- Java 17+
- PostgreSQL 14+
- Redis 6+
- RabbitMQ 3.8+
- HashiCorp Vault (optional)

### 🔧 Setup

1. **Clone and build**
   ```bash
   git clone <repository-url>
   cd paybridge
   ./mvnw clean package
   ```

2. **Generate RSA Keys**
   ```bash
   mkdir -p src/main/resources/certs
   openssl genpkey -algorithm RSA -out src/main/resources/certs/privatekey.pem -pkeyopt rsa_keygen_bits:2048
   openssl rsa -pubout -in src/main/resources/certs/privatekey.pem -out src/main/resources/certs/publickey.pem
   ```

3. **Database Setup**
   ```sql
   CREATE DATABASE paybridge;
   ```

4. **Run Application**
   ```bash
   ./mvnw spring-boot:run
   ```

🎉 **The application starts on `http://localhost:8080`**

## 💡 Usage Example

### 1. Register Merchant
```bash
POST /api/v1/merchants
Content-Type: application/json

{
  "businessName": "Example Corp",
  "email": "merchant@example.com",
  "password": "SecurePass123$",
  "businessType": "E_COMMERCE",
  "businessCountry": "US",
  "websiteUrl": "https://example.com"
}
```

### 2. Verify Email
```bash
POST /api/v1/auth/verify-email
Content-Type: application/json

{
  "email": "merchant@example.com",
  "code": "123456"
}
```

### 3. Login
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "merchant@example.com",
  "password": "SecurePass123$"
}
```

### 4. Configure Provider
```bash
POST /api/v1/providers/configure
Content-Type: application/json
Cookie: jwt=<jwt-token>

{
  "providerName": "stripe",
  "credentials": {
    "secretKey": "sk_test_..."
  }
}
```

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with Testcontainers
./mvnw verify
```

## ⏱️ Rate Limiting

API keys are automatically rate limited:
- **Hourly**: 1,000 requests
- **Daily**: 10,000 requests

📈 Usage statistics are tracked in Redis and periodically persisted to PostgreSQL.

## 🏷️ Profiles

- `vault` - Enables HashiCorp Vault integration
- `smtp` - Enables email service

Activate profiles:
```bash
java -jar target/paybridge-0.0.1-SNAPSHOT.jar --spring.profiles.active=vault,smtp
```

---

<div align="center">

### 🌟 Built with ❤️ for seamless payment integration

*PayBridge - Bridging the gap between merchants and payment providers*

</div>