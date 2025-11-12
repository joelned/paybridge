package com.paybridge.Services;


public interface EmailProvider {
    void sendVerificationEmail(String toEmail, String verificationCode, String businessName);
}
