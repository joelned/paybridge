# PayBridge Backend

> A merchant-first payment orchestration API that lets ecommerce teams integrate once and route payments across providers.

PayBridge is the backend engine behind the PayBridge merchant dashboard. It handles onboarding, auth, provider configuration, payment creation, idempotency, API keys, and webhook-driven status updates.

## Why This Project Stands Out

- Real multi-provider payment orchestration (`stripe`, `paystack`)
- Two auth models in one system: JWT cookie (dashboard) + API key (merchant server-to-server)
- Idempotent payment creation with request hashing and replay-safe responses
- Provider credentials stored via pluggable `CredentialStorageService` (Vault profile ready)
- Webhook signature verification + duplicate event protection
- Production-oriented validation and merchant isolation rules

## Current Capabilities

### Merchant and Auth
- Merchant registration: `POST /api/v1/merchants`
- Login with JWT cookie: `POST /api/v1/auth/login`
- Session bootstrap for frontend: `GET /api/v1/auth/me`
- Logout (cookie clear): `POST /api/v1/auth/logout`
- Email verification + resend
- Forgot/reset password flows

### Merchant Settings
- Merchant profile retrieval: `GET /api/v1/merchants/profile`
- API key lifecycle (create/rotate/list/revoke)

### Provider Management
- Configure provider credentials per merchant
- Test provider connection
- Fetch merchant-configured providers for UI display

### Payments
- Create payment via `POST /api/v1/payments`
- Idempotency via required `Idempotency-Key` header
- Explicit provider routing supported (`provider` in request)
- Safe fallback behavior when provider is omitted

### Webhooks
- Stripe webhook endpoint with signature verification
- Paystack webhook endpoint with HMAC signature verification
- Idempotent webhook processing via persisted processed event IDs

## Supported Providers

- Stripe
- Paystack

## Tech Stack

- Java 17
- Spring Boot 3.5.x
- Spring Security (JWT resource server + custom auth filters)
- PostgreSQL + Liquibase
- Redis
- RabbitMQ
- HashiCorp Vault (profile-based credential storage)
- Maven

## Auth Model (Important)

PayBridge uses two authentication paths:

1. Dashboard/merchant management endpoints:
- `jwt` HttpOnly cookie

2. Merchant ecommerce backend payment calls:
- `x-api-key` header (server-to-server)

In practice, payment creation should happen from the merchant backend, not directly from browser UI clients.

## API Overview

### Public endpoints
- `POST /api/v1/merchants`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/resend-verification`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `POST /api/v1/webhooks/stripe`
- `POST /api/v1/webhooks/paystack`

### Authenticated endpoints
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `GET /api/v1/merchants/profile`
- `GET /api/v1/merchants/api-keys`
- `POST /api/v1/merchants/api-keys`
- `DELETE /api/v1/merchants/api-keys/{keyId}`
- `GET /api/v1/providers`
- `POST /api/v1/providers/configure`
- `POST /api/v1/providers/test/{configId}`
- `POST /api/v1/payments`

## Example: Create Payment (Server-to-Server)

```http
POST /api/v1/payments
x-api-key: pk_test_xxxxxxxxx
Idempotency-Key: 8b8e062c-8d0d-4cc8-b2e9-1be2a2647f2d
Content-Type: application/json

{
  "amount": 1000,
  "currency": "NGN",
  "description": "Order #1001",
  "email": "customer@example.com",
  "provider": "paystack"
}
```

## Environment Configuration

`src/main/resources/application.properties` expects these environment variables:

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

### Email SMTP
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`

### Vault
- `VAULT_TOKEN`

### Webhooks
- `STRIPE_WEBHOOK_SECRET`

## Local Run

```bash
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```

Test coverage includes:
- webhook controller integration behavior
- Stripe and Paystack webhook service flows
- provider routing on payment API
- API key lifecycle endpoints

## Local Webhook Testing

Use [ngrok](https://ngrok.com/) to expose localhost:

```bash
ngrok http 8080
```

Then set provider webhook URLs to:
- `https://<ngrok-domain>/api/v1/webhooks/stripe`
- `https://<ngrok-domain>/api/v1/webhooks/paystack`

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

## Notes

- Add new Liquibase changeSets for schema changes; do not edit executed changeSets.
- Keep API key plaintext display one-time only in client UX.
- If multiple providers are enabled and no provider is specified in payment request, API returns a clear validation error.
