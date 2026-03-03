package com.paybridge.Security;

public class SecurityConstants {
    public static final String[] PUBLIC_URLS = {
            "/api/v1/merchants",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/webhooks/stripe",
            "/api/v1/webhooks/paystack"
    };
}
