<div align="center">

#  PayBridge

### *Multi-Provider Payment Orchestration Platform*

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.6-6DB33F?style=for-the-badge&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Vault](https://img.shields.io/badge/Vault-000000?style=for-the-badge&logo=vault&logoColor=white)](https://www.vaultproject.io/)

**A production-ready payment gateway orchestrator supporting Stripe and Paystack**

[Features](#-core-features) • [Quick Start](#-quick-start) • [API Guide](#-api-endpoints) • [Architecture](#-system-architecture)

</div>

---

## 🎯 What is PayBridge?

PayBridge is a **RESTful payment orchestration API** built with Spring Boot that provides:
- Unified API across multiple payment providers (Stripe, Paystack)
- Merchant onboarding with email verification
- API key management (test/live modes)
- Idempotent payment processing
- Webhook handling with signature verification
- Real-time payment analytics

**Built for:** Nigerian and global businesses needing reliable payment infrastructure with provider redundancy.

---

## ✨ Core Features

### 🔐 Authentication & Security
- **Dual Authentication**: API keys (for payment requests) + JWT cookies (for dashboard)
- **SHA-256 API Key Hashing**: Keys never stored in plaintext
- **Rate Limiting**: 1,000 req/hr (test), 10,000 req/hr (live) via Redis
- **HashiCorp Vault Integration**: Secure credential storage for provider API keys
- **Email Verification**: 6-digit codes with 10-minute expiration
- **Password Reset**: Secure flow with time-limited reset codes

### 💳 Payment Processing
- **Multi-Provider Support**: Stripe and Paystack (More to come)
- **Idempotency**: SHA-256 request hashing prevents duplicate charges
- **Provider Selection**: Auto-select or explicit provider routing
- **Payment Methods**: Card payments via checkout URLs
- **Status Tracking**: PENDING → PROCESSING → SUCCEEDED/FAILED
- **Async Processing**: External API calls don't block main thread

### 🔔 Webhook Management
- **Signature Verification**:
    - Stripe: Built-in Webhook library validation
    - Paystack: HMAC-SHA512 verification
- **Event Deduplication**: Prevents duplicate webhook processing
- **Status Synchronization**: Auto-updates payment status from provider events
- **Merchant-Specific Secrets**: Per-merchant webhook secret management

### 📊 Merchant Dashboard
- **Registration & Onboarding**: Email verification required
- **Provider Configuration**: Test connections before activation
- **API Key Management**: Generate, rotate, and revoke test/live keys
- **Payment Analytics**:
    - Transaction volume & success rates
    - Provider performance comparison
    - Daily trend analysis (configurable timeframe)
    - Currency breakdown
- **Profile Management**: Business details and settings

### 🛡️ Production-Ready Features
- **Liquibase Migrations**: Version-controlled database schema
- **Connection Pooling**: HikariCP configuration
- **Async Logging**: Redis-backed API usage logs
- **Error Handling**: Global exception handling with structured responses
- **CORS Configuration**: Configurable allowed origins
- **Actuator Endpoints**: Health checks and metrics
- **Test Coverage**: Unit + Integration tests

---

## 🏗️ System Architecture

### Technology Stack

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (Planned)                        │
│                  React/TypeScript Dashboard                  │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS (JWT Cookie)
┌──────────────────────────▼──────────────────────────────────┐
│                     PayBridge API                            │
│                  Spring Boot 3.5.6 (Java 17)                 │
│                                                              │
│  Security Filters                                            │
│  ├─ ApiKeyAuthenticationFilter (x-api-key header)          │
│  └─ CookieAuthenticationFilter (JWT from HttpOnly cookie)  │
│                                                              │
│  Controllers                                                 │
│  ├─ AuthController (login, verify-email, password-reset)   │
│  ├─ MerchantController (profile, analytics, api-keys)      │
│  ├─ PaymentController (create payment)                     │
│  ├─ ProviderController (configure providers)               │
│  └─ WebhookController (stripe, paystack webhooks)          │
│                                                              │
│  Services                                                    │
│  ├─ PaymentService (3-phase payment creation)              │
│  ├─ ProviderService (config management)                    │
│  ├─ WebhookService (signature verification, dedup)         │
│  ├─ ApiKeyService (generation, hashing, rate limiting)     │
│  ├─ AuthenticationService (JWT generation)                 │
│  └─ VerificationService (email verification)               │
│                                                              │
│  Payment Providers                                           │
│  ├─ StripePaymentProvider (Stripe Java SDK)                │
│  └─ PaystackPaymentProvider (RestTemplate)                 │
└──────────┬──────────┬──────────┬───────────────────────────┘
           │          │          │           
    ┌──────▼────┐ ┌──▼─────┐ ┌─▼──────┐ 
    │PostgreSQL │ │ Redis  │ │ Vault  │ 
    │           │ │        │ │        │ 
    │ Merchants │ │ Rate   │ │Provider│
    │ Payments  │ │ Limits │ │  Keys  │ 
    │ Providers │ │ Usage  │ │Secrets │ 
    │ Webhooks  │ │  Logs  │ │        │ 
    └───────────┘ └────────┘ └────────┘ 
```

### Database Schema (PostgreSQL)

```sql
-- Core merchant data
merchants (
  id BIGSERIAL PRIMARY KEY,
  business_name VARCHAR(255),
  email VARCHAR(255) UNIQUE,
  business_type VARCHAR(100),
  business_country VARCHAR(100),
  status VARCHAR(50),  -- PENDING_EMAIL_VERIFICATION, PENDING_PROVIDER_SETUP, ACTIVE, SUSPENDED
  api_key_test_hash VARCHAR(255),  -- SHA-256 hashed
  api_key_live_hash VARCHAR(255),  -- SHA-256 hashed
  test_mode BOOLEAN DEFAULT true,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)

-- User authentication
users (
  id BIGSERIAL PRIMARY KEY,
  merchant_id BIGINT → merchants(id),
  user_type VARCHAR(50),  -- MERCHANT, ADMIN
  email VARCHAR(255) UNIQUE,
  password VARCHAR(255),  -- bcrypt hashed
  email_verified BOOLEAN DEFAULT false,
  enabled BOOLEAN DEFAULT true,
  verification_code VARCHAR(10),
  verification_code_expires_at TIMESTAMP,
  verification_attempts INT DEFAULT 0,
  password_reset_code VARCHAR(10),
  password_reset_code_expires_at TIMESTAMP
)

-- Payment providers (Stripe, Paystack, Flutterwave)
providers (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) UNIQUE,        -- 'stripe', 'paystack'
  display_name VARCHAR(100),       -- 'Stripe', 'Paystack'
  brand_color VARCHAR(7)           -- '#635BFF'
)

-- Merchant-specific provider configurations
provider_configs (
  id BIGSERIAL PRIMARY KEY,
  merchant_id BIGINT → merchants(id),
  provider_id BIGINT → providers(id),
  is_enabled BOOLEAN DEFAULT false,
  vault_path VARCHAR(500),         -- Vault path to encrypted credentials
  last_verified_at TIMESTAMP,
  created_at TIMESTAMP,
  UNIQUE(merchant_id, provider_id)
)

-- Payment transactions
payments (
  id UUID PRIMARY KEY,
  merchant_id BIGINT → merchants(id),
  provider_id BIGINT → providers(id),
  amount DECIMAL(12,2),
  currency VARCHAR(3),              -- USD, NGN, etc.
  status VARCHAR(50),               -- PENDING, SUCCEEDED, FAILED, CANCELLED
  provider_reference VARCHAR(255),  -- Provider's transaction ID
  refunded_amount DECIMAL(12,2) DEFAULT 0,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)

-- Customer data
customers (
  id BIGSERIAL PRIMARY KEY,
  merchant_id BIGINT → merchants(id),
  external_customer_id BIGINT,
  full_name VARCHAR(50),
  email VARCHAR(50),
  phone VARCHAR(18)
)

-- Idempotency protection
idempotency_keys (
  id SERIAL PRIMARY KEY,
  idempotency_key VARCHAR(255) UNIQUE,
  customer_id BIGINT → customers(id),
  request_hash TEXT,                -- SHA-256 of canonicalized request
  response TEXT,                    -- Cached response JSON
  status VARCHAR(20),               -- PROCESSING, SUCCEEDED, FAILED
  locked BOOLEAN DEFAULT false,
  created_at TIMESTAMP,
  expires_at TIMESTAMP
)

-- Webhook event tracking (prevents duplicate processing)
processed_webhook_events (
  id BIGSERIAL PRIMARY KEY,
  provider VARCHAR(50),
  event_id VARCHAR(255),
  created_at TIMESTAMP,
  UNIQUE(provider, event_id)
)

-- API usage tracking (logged from Redis → PostgreSQL)
api_key_usage (
  id SERIAL PRIMARY KEY,
  merchant_id BIGINT,
  endpoint VARCHAR(500),
  method VARCHAR(10),
  ip_address VARCHAR(45),
  user_agent VARCHAR(1000),
  response_status INT,
  time_stamp TIMESTAMP
)
```

### Key Components

| Component | Implementation | Purpose |
|-----------|---------------|---------|
| **ApiKeyAuthenticationFilter** | `@Component` filter | Intercepts requests with `x-api-key` header, validates against SHA-256 hash |
| **CookieAuthenticationFilter** | `@Component` filter | Extracts JWT from HttpOnly cookie, validates signature |
| **PaymentService** | 3-phase transaction | Phase 1: Validate idempotency (TX), Phase 2: Call provider (no TX), Phase 3: Persist payment (TX) |
| **PaymentTransactionHelper** | `@Transactional` methods | Separates DB transactions from external API calls |
| **ApiKeyService** | Async logging + scheduling | Redis → PostgreSQL batch insert every 10 minutes |
| **WebhookService** | Signature verification | Stripe: `Webhook.constructEvent()`, Paystack: HMAC-SHA512 |
| **VaultService** | HashiCorp Vault client | Stores provider API keys at `secret/data/paybridge/providers/{provider}/merchant-{id}` |

---

## 📡 API Endpoints

### Authentication & Registration

#### 1. Register Merchant
```http
POST /api/v1/merchants
Content-Type: application/json

{
  "businessName": "Acme Corp",
  "email": "merchant@acme.com",
  "password": "SecurePass123$",
  "businessType": "ECOMMERCE",
  "businessCountry": "NG",
  "websiteUrl": "https://acme.com"
}
```

**Response:**
```json
{
  "success": true,
  "data": "Registration successful. Please check your email for verification code",
  "timestamp": "2026-03-06T10:00:00"
}
```

#### 2. Verify Email
```http
POST /api/v1/auth/verify-email
Content-Type: application/json

{
  "email": "merchant@acme.com",
  "code": "123456"
}
```

#### 3. Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "merchant@acme.com",
  "password": "SecurePass123$"
}
```

**Response:** Sets `jwt` HttpOnly cookie + returns user data

#### 4. Password Reset Flow
```http
# Request reset code
POST /api/v1/auth/forgot-password
{"email": "merchant@acme.com"}

# Reset with code
POST /api/v1/auth/reset-password
{
  "email": "merchant@acme.com",
  "code": "123456",
  "newPassword": "NewSecurePass123$",
  "confirmPassword": "NewSecurePass123$"
}
```

### Provider Configuration

#### Configure Payment Provider
```http
POST /api/v1/providers/configure?testConnection=true
Cookie: jwt=<your-jwt-token>
Content-Type: application/json

{
  "name": "stripe",
  "config": {
    "secretKey": "sk_test_51..."
  }
}
```

**What happens:**
1. Validates secret key format
2. (If `testConnection=true`) Creates test customer via Stripe API
3. Stores encrypted key in Vault at `secret/data/paybridge/providers/stripe/merchant-{id}`
4. Saves config metadata in `provider_configs` table
5. Merchant status → `ACTIVE`

#### Get Configured Providers
```http
GET /api/v1/providers
Cookie: jwt=<your-jwt-token>
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "configId": 1,
      "providerId": 1,
      "providerName": "Stripe",
      "providerCode": "stripe",
      "enabled": true,
      "lastVerifiedAt": "2026-03-06T09:00:00",
      "createdAt": "2026-03-05T10:00:00"
    }
  ]
}
```

### Payment Processing

#### Create Payment
```http
POST /api/v1/payments
x-api-key: pk_test_abc123...
Idempotency-Key: order-1234-5678
Content-Type: application/json

{
  "amount": 50.00,
  "currency": "USD",
  "description": "Order #1234",
  "email": "customer@example.com",
  "provider": "stripe",
  "metadata": {
    "orderId": "1234",
    "customerId": "cust_abc"
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_...",
    "amount": 50.00,
    "currency": "USD",
    "provider": "stripe",
    "providerReference": "cs_test_...",
    "metadata": {"orderId": "1234", "customerId": "cust_abc"},
    "createdAt": "2026-03-06T10:30:00Z",
    "expiresAt": "2026-03-06T11:30:00Z",
    "message": "Payment created successfully"
  },
  "timestamp": "2026-03-06T10:30:00"
}
```

**Idempotency:** Sending same `Idempotency-Key` returns cached response

### Merchant Management

#### Get Profile
```http
GET /api/v1/merchants/profile
Cookie: jwt=<token>
```

#### Get Analytics
```http
GET /api/v1/merchants/analytics?days=30
Cookie: jwt=<token>
```

**Response:**
```json
{
  "success": true,
  "data": {
    "days": 30,
    "totalTransactions": 245,
    "successfulTransactions": 230,
    "failedTransactions": 12,
    "pendingTransactions": 3,
    "successRate": 93.88,
    "totalProcessedAmount": 12450.00,
    "averageTransactionAmount": 50.82,
    "primaryCurrency": "USD",
    "currenciesUsed": ["USD", "NGN"],
    "providers": [
      {
        "providerCode": "stripe",
        "providerName": "Stripe",
        "transactions": 200,
        "successfulTransactions": 190,
        "failedTransactions": 8,
        "successRate": 95.00,
        "processedAmount": 10000.00
      }
    ],
    "dailyTrend": [
      {
        "date": "2026-02-05",
        "transactions": 10,
        "successfulTransactions": 9,
        "processedAmount": 500.00
      }
    ]
  }
}
```

#### API Key Management

```http
# List API keys
GET /api/v1/merchants/api-keys
Cookie: jwt=<token>

# Response:
{
  "success": true,
  "data": [
    {
      "keyId": "test",
      "mode": "TEST",
      "label": "Test API Key",
      "maskedKey": "pk_test_abc...xyz",
      "active": true,
      "updatedAt": "2026-03-06T09:00:00"
    },
    {
      "keyId": "live",
      "mode": "LIVE",
      "label": "Live API Key",
      "maskedKey": null,
      "active": false,
      "updatedAt": "2026-03-06T09:00:00"
    }
  ]
}

# Create/Rotate API key
POST /api/v1/merchants/api-keys
Cookie: jwt=<token>
{"mode": "TEST"}

# Response includes plaintext key (only shown once!)
{
  "success": true,
  "data": {
    "keyId": "test",
    "mode": "TEST",
    "label": "Test API Key",
    "key": "pk_test_abc123def456...",
    "createdAt": "2026-03-06T10:00:00"
  }
}

# Revoke API key
DELETE /api/v1/merchants/api-keys/test
Cookie: jwt=<token>
```

#### Webhook Secret Management

```http
# Get webhook secret
GET /api/v1/merchants/webhooks/stripe
Cookie: jwt=<token>

# Set/rotate webhook secret
PUT /api/v1/merchants/webhooks/stripe/secret
Cookie: jwt=<token>
{"secret": "whsec_..."}
```

### Webhooks (Providers → PayBridge)

#### Stripe Webhook
```http
POST /api/v1/webhooks/stripe
Stripe-Signature: t=1234567890,v1=abc123...
Content-Type: application/json

{
  "id": "evt_1...",
  "type": "checkout.session.completed",
  "data": {
    "object": {
      "id": "cs_test_...",
      "payment_status": "paid"
    }
  }
}
```

**Verification:** Uses Stripe SDK's `Webhook.constructEvent()` with merchant's webhook secret

#### Paystack Webhook
```http
POST /api/v1/webhooks/paystack
x-paystack-signature: abc123...
Content-Type: application/json

{
  "event": "charge.success",
  "data": {
    "id": "123456",
    "reference": "ref_abc123",
    "status": "success"
  }
}
```

**Verification:** HMAC-SHA512 signature validation

---

## 🚀 Quick Start

### Prerequisites
- **Java 17+**
- **PostgreSQL 14+**
- **Redis 6+**
- **HashiCorp Vault** (optional for local dev)
- **Maven 3.8+**

### 1. Clone Repository
```bash
git clone https://github.com/yourusername/paybridge.git
cd paybridge
```

### 2. Database Setup
```bash
# Start PostgreSQL
docker run -d \
  --name paybridge-postgres \
  -e POSTGRES_DB=paybridge \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=ceiling \
  -p 5432:5432 \
  postgres:14

# Start Redis
docker run -d \
  --name paybridge-redis \
  -p 6379:6379 \
  redis:6

# Start Vault (optional)
docker run -d \
  --name paybridge-vault \
  -p 8200:8200 \
  -e VAULT_DEV_ROOT_TOKEN_ID=dev-token \
  hashicorp/vault:latest
```

### 3. Generate RSA Keys (for JWT signing)
```bash
mkdir -p certs

# Generate private key
openssl genrsa -out certs/private.pem 2048

# Extract public key
openssl rsa -in certs/private.pem -pubout -out certs/public.pem
```

### 4. Configure Environment
```bash
# Required
export DB_USER=admin
export DB_PASSWORD=ceiling
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=paybridge

export REDIS_HOST=localhost
export REDIS_PORT=6379

export RSA_PRIVATE_KEY="$(cat certs/private.pem)"
export RSA_PUBLIC_KEY="$(cat certs/public.pem)"

# Optional (for Vault)
export VAULT_TOKEN=dev-token

# Email (for production)
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
```

### 5. Run Database Migrations
```bash
./mvnw liquibase:update
```

### 6. Start Application
```bash
./mvnw spring-boot:run
```

✅ **API is now running at `http://localhost:8080`**

### 7. Test the API
```bash
# Health check
curl http://localhost:8080/actuator/health

# Register a merchant
curl -X POST http://localhost:8080/api/v1/merchants \
  -H "Content-Type: application/json" \
  -d '{
    "businessName": "Test Corp",
    "email": "test@example.com",
    "password": "SecurePass123$",
    "businessType": "ECOMMERCE",
    "businessCountry": "NG",
    "websiteUrl": "https://test.com"
  }'
```

---

## 🧪 Testing

### Run Tests
```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw verify

# Specific test class
./mvnw test -Dtest=PaymentServiceTest

# With coverage
./mvnw clean test jacoco:report
# Open: target/site/jacoco/index.html
```

---

## 🔒 Security Best Practices

### For Merchants Integrating PayBridge

1. **Never expose API keys in client-side code**
   ```javascript
   // ❌ WRONG
   fetch('https://api.paybridge.dev/payments', {
     headers: {'x-api-key': 'pk_live_...'} // Exposed to users!
   })
   
   // ✅ CORRECT
   // Call your backend, which calls PayBridge
   fetch('https://your-backend.com/create-payment')
   ```

2. **Always use HTTPS in production**

3. **Implement webhook signature verification**
   ```javascript
   // Verify PayBridge webhooks to your server
   const crypto = require('crypto');
   
   function verifyWebhook(payload, signature, secret) {
     const hash = crypto
       .createHmac('sha256', secret)
       .update(payload)
       .digest('hex');
     return hash === signature;
   }
   ```

4. **Use test mode for development**
    - Test keys start with `pk_test_`
    - Live keys start with `pk_live_`

5. **Rotate API keys regularly**

---

## 🗺️ Roadmap

### ✅ Completed
- [x] Merchant registration & email verification
- [x] Stripe integration
- [x] Paystack integration
- [x] API key authentication (test/live)
- [x] JWT cookie authentication
- [x] Rate limiting (Redis)
- [x] Idempotency protection
- [x] Webhook handling & verification
- [x] Vault credential storage
- [x] Payment analytics dashboard API
- [x] N+1 query optimization
- [x] Async payment processing
- [x] Integration test suite (87% coverage)

### 🚧 In Progress
- [ ] Prometheus metrics export

### 📋 Planned
- [ ] Paypal integration
- [ ] Circuit breaker (Resilience4j)
- [ ] Admin dashboard UI (React)

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Make your changes**
4. **Run tests**
   ```bash
   ./mvnw test
   ```
5. **Commit with conventional commits**
   ```bash
   git commit -m "feat: add webhook retry mechanism"
   ```
6. **Push and create PR**
   ```bash
   git push origin feature/amazing-feature
   ```

### Code Standards
- Follow Java naming conventions
- Add unit tests for new services
- Add integration tests for new endpoints

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2026 Ekwegh Joel

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files...
```

---

## 🙏 Acknowledgments

- **Spring Boot Team** — For the excellent framework
- **Stripe & Paystack** — For comprehensive API documentation
- **HashiCorp** — For Vault secrets management
- **Developer Community** — For inspiration and feedback

---

<div align="center">

**Built with ☕ by [Ekwegh Joel](https://github.com/joelned)**

⭐ Star this repo if you find it helpful!

[Report Bug](https://github.com/joelned/paybridge/issues) · [Request Feature](https://github.com/joelned/paybridge/issues) · [Documentation](#)

</div>
