# 💳 PayBridge

> A unified payment gateway aggregator that simplifies payment processing across multiple providers.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg)](https://www.postgresql.org/)

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [License](#license)

## 🎯 Overview

PayBridge is a modern payment gateway aggregator built with Spring Boot that enables businesses to accept payments through multiple payment providers (Stripe, Flutterwave, PayPal) via a single, unified API. It handles payment routing, reconciliation, webhook management, and provides comprehensive analytics for merchants.

### Why PayBridge?

- **Single Integration**: Connect to multiple payment providers through one API
- **Smart Routing**: Automatically route payments based on success rates, costs, and availability
- **Unified Dashboard**: Monitor all transactions across providers in one place
- **Reconciliation**: Automated reconciliation between internal records and provider data
- **Webhook Management**: Reliable webhook delivery with automatic retries
- **Multi-tenancy**: Built for SaaS with complete merchant isolation

## ✨ Features

### Core Features

- 🔐 **Secure Authentication**: JWT-based authentication with RSA encryption
- 💰 **Multi-Provider Support**: Stripe, Flutterwave, PayPal (extensible)
- 🔄 **Smart Payment Routing**: Intelligent provider selection based on rules
- 📊 **Real-time Analytics**: Payment metrics, success rates, and revenue tracking
- 🔔 **Webhook Management**: Receive and forward webhooks with retry logic
- 💸 **Refund Processing**: Full and partial refunds across all providers
- 📝 **Audit Logging**: Complete audit trail for compliance
- 🔍 **Reconciliation**: Automated payment reconciliation with discrepancy detection

### Merchant Features

- Self-service registration and onboarding
- Provider configuration management
- Custom webhook endpoints
- Payment analytics dashboard
- Customer management
- Transaction history

### Admin Features

- System-wide monitoring
- Merchant management
- Provider health checks
- Invite-based admin registration
- Audit log access

## 🛠️ Tech Stack

### Backend
- **Framework**: Spring Boot 3.2+
- **Language**: Java 17+
- **Security**: Spring Security with JWT (OAuth2 Resource Server)
- **Database**: PostgreSQL 15+
- **ORM**: Spring Data JPA with Hibernate
- **Migration**: Liquibase
- **Cache**: Redis
- **Message Queue**: RabbitMQ (optional)

### Key Libraries
- **Nimbus JOSE JWT**: JWT token generation and validation
- **Jackson**: JSON processing
- **Lombok**: Boilerplate reduction
- **MapStruct**: Object mapping
- **AssertJ**: Fluent assertions for testing
- **Mockito**: Mocking framework

### Tools
- **Build Tool**: Maven
- **API Documentation**: OpenAPI/Swagger (planned)
- **Testing**: JUnit 5, Mockito, TestContainers
- **Code Quality**: SonarQube (planned)

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- PostgreSQL 15+
- Redis 7+ (optional, for caching)
- Maven 3.8+

### Installation

 1. **Clone the repository**
```bash
git clone https://github.com/yourusername/paybridge.git
cd paybridge
```

2 **Setup Postgres database**
```bash
CREATE DATABASE paybridge;
GRANT ALL PRIVILEGES ON DATABASE paybridge TO paybridge_user;
```

3 **Generate RSA Keys for JWT**
```bash
openssl genrsa -out src/main/resources/certs/privatekey.pem

openssl rsa -in src/main/resources/certs/privatekey.pem \ -pubout -out src/main/resources/certs/publickey.pem
```
***Make sure to add the keys as environmental variables in your respective IDE.

## 📚Api Documentation

### Authentication

#### Register Merchant

```bash
POST /api/v1/merchants
Content-Type: application/json

{
  "businessName": "Tech Corp",
  "email": "admin@techcorp.com",
  "password": "SecurePass123!",
  "businessType": "SAAS",
  "businessCountry": "NG",
  "websiteUrl": "https://techcorp.com"
}
```
#### Login

```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "admin@techcorp.com",
  "password": "SecurePass123!"
}
}
```

## License 
This project is licensed under the MIT license.



