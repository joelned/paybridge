package com.paybridge.Services;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.ErrorDetail;
import com.paybridge.Models.Enums.ApiErrorCode;
import com.paybridge.Models.DTOs.ResetPasswordRequest;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailProvider emailProvider;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(UserRepository userRepository,
                                EmailProvider emailProvider,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailProvider = emailProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public ApiResponse<String> requestPasswordReset(String email) {
        Users user = userRepository.findByEmail(email);

        // Always return generic success to avoid account enumeration.
        if (user == null) {
            return ApiResponse.success("If an account exists, a reset code has been sent to the email address");
        }

        user.generatePasswordResetCode();
        userRepository.save(user);

        String businessName = user.getMerchant() != null ? user.getMerchant().getBusinessName() : null;
        emailProvider.sendPasswordResetEmail(user.getEmail(), user.getPasswordResetCode(), businessName);

        return ApiResponse.success("If an account exists, a reset code has been sent to the email address");
    }

    public ApiResponse<String> resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ApiResponse.error(ErrorDetail.of("Password confirmation does not match", ApiErrorCode.PASSWORD_MISMATCH));
        }

        Users user = userRepository.findByEmail(request.getEmail());
        if (user == null) {
            return ApiResponse.error(ErrorDetail.of("Invalid email or reset code", ApiErrorCode.INVALID_RESET_CODE));
        }

        if (!user.isPasswordResetCodeValid(request.getCode())) {
            return ApiResponse.error(ErrorDetail.of("Invalid or expired reset code", ApiErrorCode.INVALID_RESET_CODE));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.clearPasswordResetCode();
        userRepository.save(user);

        return ApiResponse.success("Password reset successfully. Please login with your new password");
    }
}
