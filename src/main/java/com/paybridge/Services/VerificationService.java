package com.paybridge.Services;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.ErrorDetail;
import com.paybridge.Models.Enums.ApiErrorCode;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VerificationService {

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final ApiKeyService apiKeyService;

    private final EmailProvider emailProvider;

    public VerificationService(UserRepository userRepository,
                               MerchantRepository merchantRepository,
                               ApiKeyService apiKeyService,
                               EmailProvider emailProvider) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.apiKeyService = apiKeyService;
        this.emailProvider = emailProvider;
    }

    public ApiResponse<String> verifyEmailAndActivateMerchant(String email, String code) {
        Users user = userRepository.findByEmail(email);
        if (user == null) {
            return ApiResponse.error(ErrorDetail.of("Invalid or expired verification code", ApiErrorCode.VERIFICATION_CODE_INVALID));
        }

        if (user.isEmailVerified()) {
            return ApiResponse.error(ErrorDetail.of("Invalid or expired verification code", ApiErrorCode.VERIFICATION_CODE_INVALID));
        }

        if (user.getVerificationAttempts() >= 5) {
            return ApiResponse.error(ErrorDetail.of("Invalid or expired verification code", ApiErrorCode.VERIFICATION_CODE_INVALID));
        }

        // Validate code
        if (!user.isVerificationCodeValid(code)) {
            user.incrementVerificationAttempts();
            userRepository.save(user);

            if (user.getVerificationCodeExpiresAt() != null &&
                    java.time.LocalDateTime.now().isAfter(user.getVerificationCodeExpiresAt())) {
                return ApiResponse.error(ErrorDetail.of("Verification code expired. Please request a new one", ApiErrorCode.VERIFICATION_CODE_EXPIRED));
            }
            return ApiResponse.error(ErrorDetail.of("Invalid verification code", ApiErrorCode.VERIFICATION_CODE_INVALID));
        }

        Merchant merchant = user.getMerchant();
        if (merchant == null) {
            return ApiResponse.error(ErrorDetail.of("Merchant does not exist", ApiErrorCode.ACCOUNT_NOT_FOUND));
        }

        user.markAsVerified();
        userRepository.save(user);
        merchant.setTestMode(true);
        merchantRepository.save(merchant);
        merchant.setStatus(MerchantStatus.PENDING_PROVIDER_SETUP);
        apiKeyService.regenerateApiKey(merchant.getId(), true, true);

        return ApiResponse.success("Email verified successfully");
    }

    public ApiResponse<String> verifyEmail(String email, String code) {
        return verifyEmailAndActivateMerchant(email, code);
    }

    public void resendVerificationCode(String email) {
        Users user = userRepository.findByEmail(email);
        if (user == null) {
            return;
        }

        if (user.isEmailVerified()) {
            return;
        }

        if (!user.canResendVerification()) {
            return;
        }

        String verificationCode = user.generateVerificationCode();
        userRepository.save(user);

        String businessName = user.getMerchant() != null ? user.getMerchant().getBusinessName() : null;
        emailProvider.sendVerificationEmail(user.getEmail(), verificationCode, businessName);
    }
}
