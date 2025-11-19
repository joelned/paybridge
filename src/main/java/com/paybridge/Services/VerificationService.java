package com.paybridge.Services;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VerificationService {

    private final UserRepository userRepository;

    private final EmailProvider emailProvider;

    public VerificationService(UserRepository userRepository, EmailProvider emailProvider) {
        this.userRepository = userRepository;
        this.emailProvider = emailProvider;
    }

    public ApiResponse<String> verifyEmail(String email, String code) {
        Users user = userRepository.findByEmail(email);
        if (user == null) {
            return ApiResponse.error("No account found with this mail");
        }

        if (user.isEmailVerified()) {
            return ApiResponse.error("Email is already verified");
        }

        if (user.getVerificationAttempts() >= 5) {
            return ApiResponse.error("Too many requests. Please request a new code");
        }

        // Validate code
        if (!user.isVerificationCodeValid(code)) {
            user.incrementVerificationAttempts();
            userRepository.save(user);

            if (user.getVerificationCodeExpiresAt() != null &&
                    java.time.LocalDateTime.now().isAfter(user.getVerificationCodeExpiresAt())) {
                return ApiResponse.error("Verification code expired. Please request a new one");
            }
            return ApiResponse.error("Invalid verification code");
        }

        user.markAsVerified();
        userRepository.save(user);

        return ApiResponse.success("Email verified successfully");
    }

    public void resendVerificationCode(String email) {
        Users user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("No account found with this email");
        }

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified");
        }

        if (!user.canResendVerification()) {
            throw new RuntimeException("Please wait before requesting another verification code");
        }

        user.generateVerificationCode();
        userRepository.save(user);

        String businessName = user.getMerchant() != null ? user.getMerchant().getBusinessName() : null;
        emailProvider.sendVerificationEmail(user.getEmail(), user.getVerificationCode(), businessName);
    }
}