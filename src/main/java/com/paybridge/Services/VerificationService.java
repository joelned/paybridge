package com.paybridge.Services;

import com.paybridge.Models.DTOs.VerifyEmailResponse;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VerificationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TokenService tokenService;

    public VerifyEmailResponse verifyEmail(String email, String code) {
        Users user = userRepository.findByEmail(email);
        if (user == null) {
            return new VerifyEmailResponse("No account found with this email", false);
        }

        if (user.isEmailVerified()) {
            return new VerifyEmailResponse("Email is already verified", false);
        }

        // Check verification attempts
        if (user.getVerificationAttempts() >= 5) {
            return new VerifyEmailResponse("Too many verification attempts. Please request a new code.", false);
        }

        // Validate code
        if (!user.isVerificationCodeValid(code)) {
            user.incrementVerificationAttempts();
            userRepository.save(user);

            if (user.getVerificationCodeExpiresAt() != null &&
                    java.time.LocalDateTime.now().isAfter(user.getVerificationCodeExpiresAt())) {
                return new VerifyEmailResponse("Verification code has expired. Please request a new one.", false);
            }
            return new VerifyEmailResponse("Invalid verification code", false);
        }

        // Mark as verified
        user.markAsVerified();
        userRepository.save(user);

        return new VerifyEmailResponse("Email verified successfully", true);
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

        // Generate new verification code
        user.generateVerificationCode();
        userRepository.save(user);

        // Send new verification email
        String businessName = user.getMerchant() != null ? user.getMerchant().getBusinessName() : null;
        emailService.sendVerificationEmail(user.getEmail(), user.getVerificationCode(), businessName);
    }
}