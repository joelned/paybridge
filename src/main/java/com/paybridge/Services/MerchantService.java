package com.paybridge.Services;

import com.paybridge.Models.DTOs.MerchantApiKeyCreateResponse;
import com.paybridge.Models.DTOs.MerchantApiKeySummaryResponse;
import com.paybridge.Models.DTOs.MerchantProfileResponse;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.ApiKeyMode;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MerchantService {

    private final UserRepository userRepository;

    private final MerchantRepository merchantRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmailProvider emailProvider;

    private final ApiKeyService apiKeyService;

    public MerchantService(UserRepository userRepository,
                           MerchantRepository merchantRepository,
                           PasswordEncoder passwordEncoder,
                           EmailProvider emailProvider,
                           ApiKeyService apiKeyService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailProvider = emailProvider;
        this.apiKeyService = apiKeyService;
    }


    @Transactional
    public void registerMerchant(MerchantRegistrationRequest request){
        boolean merchantExists = merchantRepository.existsByEmail(request.getEmail());
        if(merchantExists){
            throw new RuntimeException("Merchant already exists");

        }
        Merchant merchant = createMerchant(request);
        Users user = createMerchantUser(merchant, request);

        userRepository.save(user);
        merchantRepository.save(merchant);

       emailProvider.sendVerificationEmail(user.getEmail(), user.getVerificationCode(), user.getMerchant().getBusinessName());
    }

    public Merchant createMerchant(MerchantRegistrationRequest request){

        Merchant merchant = new Merchant();
        merchant.setBusinessType(request.getBusinessType());
        merchant.setBusinessCountry(request.getBusinessCountry());
        merchant.setBusinessName(request.getBusinessName());
        merchant.setWebsiteUrl(request.getWebsiteUrl());
        merchant.setCreatedAt(LocalDateTime.now());
        merchant.setUpdatedAt(LocalDateTime.now());
        merchant.setEmail(request.getEmail());
        merchant.setStatus(MerchantStatus.PENDING_EMAIL_VERIFICATION);
        return merchant;
    }

    public Users createMerchantUser(Merchant merchant, MerchantRegistrationRequest request){

        Users users = new Users();
        users.setMerchant(merchant);
        users.setUserType(UserType.MERCHANT);
        users.setEmail(request.getEmail());
        users.setEnabled(true);
        users.setPassword(passwordEncoder.encode(request.getPassword()));
        users.generateVerificationCode();
        return users;
    }

    public MerchantProfileResponse getMerchantProfile(Merchant merchant){
        MerchantProfileResponse profile = new MerchantProfileResponse();
        profile.setId(merchant.getId());
        profile.setEmail(merchant.getEmail());
        profile.setBusinessName(merchant.getBusinessName());
        profile.setBusinessType(merchant.getBusinessType());
        profile.setBusinessCountry(merchant.getBusinessCountry());
        profile.setWebsiteUrl(merchant.getWebsiteUrl());
        profile.setWebhookUrl(merchant.getWebhookUrl());
        profile.setStatus(merchant.getStatus());
        profile.setTestMode(merchant.isTestMode());
        profile.setCreatedAt(merchant.getCreatedAt());
        profile.setUpdatedAt(merchant.getUpdatedAt());

        return profile;
    }

    public List<MerchantApiKeySummaryResponse> getMerchantApiKeys(Merchant merchant) {
        List<MerchantApiKeySummaryResponse> response = new ArrayList<>();
        response.add(buildApiKeySummary("test", ApiKeyMode.TEST, "Test API Key", merchant.getApiKeyTest(), merchant.getUpdatedAt()));
        response.add(buildApiKeySummary("live", ApiKeyMode.LIVE, "Live API Key", merchant.getApiKeyLive(), merchant.getUpdatedAt()));
        return response;
    }

    public MerchantApiKeyCreateResponse createOrRotateApiKey(Merchant merchant, ApiKeyMode mode) {
        boolean isTestMode = mode == ApiKeyMode.TEST;
        String plainApiKey = apiKeyService.rotateApiKey(merchant.getId(), isTestMode);

        MerchantApiKeyCreateResponse response = new MerchantApiKeyCreateResponse();
        response.setKeyId(isTestMode ? "test" : "live");
        response.setMode(mode);
        response.setLabel(isTestMode ? "Test API Key" : "Live API Key");
        response.setKey(plainApiKey);
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    public void revokeApiKey(Merchant merchant, ApiKeyMode mode) {
        apiKeyService.revokeApiKey(merchant.getId(), mode == ApiKeyMode.TEST);
    }

    public ApiKeyMode resolveApiKeyModeFromKeyId(String keyId) {
        if (keyId == null) {
            throw new IllegalArgumentException("API key id is required");
        }

        String normalized = keyId.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "test" -> ApiKeyMode.TEST;
            case "live" -> ApiKeyMode.LIVE;
            default -> throw new IllegalArgumentException("Unsupported API key id. Use 'test' or 'live'.");
        };
    }

    private MerchantApiKeySummaryResponse buildApiKeySummary(String keyId,
                                                             ApiKeyMode mode,
                                                             String label,
                                                             String plainKey,
                                                             LocalDateTime updatedAt) {
        MerchantApiKeySummaryResponse summary = new MerchantApiKeySummaryResponse();
        summary.setKeyId(keyId);
        summary.setMode(mode);
        summary.setLabel(label);

        boolean active = plainKey != null && !plainKey.isBlank();
        summary.setActive(active);
        summary.setMaskedKey(active ? maskApiKey(plainKey) : null);
        summary.setUpdatedAt(updatedAt);

        return summary;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 16) {
            return "***";
        }
        String prefix = apiKey.substring(0, 11);
        String suffix = apiKey.substring(apiKey.length() - 3);
        return prefix + "..." + suffix;
    }
}
