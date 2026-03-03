package com.paybridge.Services;

public interface EmailProvider {
    void sendVerificationEmail(String toEmail, String verificationCode, String businessName);
    void sendPasswordResetEmail(String toEmail, String resetCode, String businessName);
}
