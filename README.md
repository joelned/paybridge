# PayBridge

Unified payment orchestration backend built with Spring Boot.

PayBridge provides:
- merchant onboarding and email verification
- JWT + API key authentication
- provider configuration and connection testing
- payment initiation with idempotency support
- API usage tracking and rate limiting

Current payment providers:
- Stripe
- Paystack

## Tech Stack
- Java 17
- Spring Boot 3.5.x
- Spring Security (JWT resource server + custom filters)
- PostgreSQL + Liquibase
- Redis
- RabbitMQ
- Vault (profile-based credential storage)
- Maven

## Project Structure
```text
src/main/java/com/paybridge/
├── Configs/
├── Controllers/
├── Filters/
├── Models/
│   ├── DTOs/
│   ├── Entities/
│   └── Enums/
├── Repositories/
├── Security/
├── Services/
│   └── impl/
└── PaybridgeApplication.java
```

## Core Flows

### 1) Merchant onboarding
1. Register merchant
2. Receive verification code by email
3. Verify email
4. Merchant activated and API keys generated

### 2) Provider setup
1. Authenticated merchant submits provider configuration
2. Credentials are validated (optional test call)
3. Credentials are stored through `CredentialStorageService` (Vault profile uses Vault)
4. Provider metadata saved in DB (`provider_configs`)

### 3) Payment creation
1. Merchant sends `POST /api/v1/payments` with `Idempotency-Key`
2. Service enforces idempotency by request hash
3. Enabled provider config is selected
4. Provider payment is created
5. Internal `payments` record is saved
6. Response is cached against idempotency key for safe replay

## API Endpoints

### Public
- `POST /api/v1/merchants`  
  Register merchant

- `POST /api/v1/auth/login`  
  Login and set JWT cookie

- `POST /api/v1/auth/verify-email`  
  Verify email code

- `POST /api/v1/auth/resend-verification`  
  Resend verification code

### Authenticated
- `POST /api/v1/providers/configure`  
  Configure provider credentials for merchant

- `POST /api/v1/providers/test/{configId}`  
  Re-test existing provider config

- `POST /api/v1/payments`  
  Create payment (requires `Idempotency-Key` header)

- `GET /api/v1/test-controller`  
  Test endpoint

## Request Examples

### Register Merchant
```http
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

### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "merchant@example.com",
  "password": "SecurePass123$"
}
```

### Configure Provider (Stripe)
```http
POST /api/v1/providers/configure?testConnection=true
Cookie: jwt=<jwt-token>
Content-Type: application/json

{
  "name": "stripe",
  "config": {
    "secretKey": "sk_test_xxx"
  }
}
```

### Create Payment
```http
POST /api/v1/payments
Cookie: jwt=<jwt-token>
Idempotency-Key: 3c4d91f0-57f8-40fc-aef8-e6fc20626f9f
Content-Type: application/json

{
  "amount": 5000.00,
  "currency": "NGN",
  "description": "Order #1042",
  "email": "customer@example.com",
  "redirectUrl": "https://merchant.example.com/payments/return",
  "transactionReference": "order-1042"
}
```

## Configuration

`src/main/resources/application.properties` expects these values:

### Database
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`

### Redis
- `REDIS_HOST`
- `REDIS_PORT`

### RabbitMQ
- `RABBIT_HOST`
- `RABBIT_PORT`

### RSA Keys
- `RSA_PRIVATE_KEY`
- `RSA_PUBLIC_KEY`

### Vault
- `VAULT_TOKEN`

### Email SMTP
- `spring.mail.username`
- `spring.mail.password`

## Profiles
- `vault`: enables Vault-backed `CredentialStorageService`
- `smtp`: enables SMTP email sender implementation

Default active profiles are set in `application.properties`.

## Running Locally

1. Start dependencies (Postgres, Redis, RabbitMQ; Vault optional but required if using `vault` profile).
2. Set required environment variables.
3. Run:
```bash
./mvnw spring-boot:run
```

## Testing
```bash
./mvnw test
```

If tests fail due Mockito inline agent issues in your local JVM, run compile checks first:
```bash
./mvnw -DskipTests compile
./mvnw -DskipTests test-compile
```

## Notes
- Do not edit previously executed Liquibase changeSets; add new changeSets for schema/data changes.
- Payment idempotency currently uses `idempotency_keys` and cached serialized `PaymentResponse`.
