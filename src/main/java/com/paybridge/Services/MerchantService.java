package com.paybridge.Services;

import com.paybridge.Models.DTOs.MerchantApiKeyCreateResponse;
import com.paybridge.Models.DTOs.MerchantApiKeySummaryResponse;
import com.paybridge.Models.DTOs.MerchantAnalyticsResponse;
import com.paybridge.Models.DTOs.MerchantProfileResponse;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.MerchantWebhookSecretResponse;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Payment;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.ApiKeyMode;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.PaymentStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.PaymentRepository;
import com.paybridge.Repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MerchantService {

    private final UserRepository userRepository;

    private final MerchantRepository merchantRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmailProvider emailProvider;

    private final ApiKeyService apiKeyService;

    private final PaymentRepository paymentRepository;

    private final CredentialStorageService credentialStorageService;

    private static final Set<PaymentStatus> SUCCESS_STATUSES = EnumSet.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.REFUNDED,
            PaymentStatus.PARTIALLY_REFUNDED
    );

    private static final Set<PaymentStatus> FAILED_STATUSES = EnumSet.of(
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED
    );

    public MerchantService(UserRepository userRepository,
                           MerchantRepository merchantRepository,
                           PasswordEncoder passwordEncoder,
                           EmailProvider emailProvider,
                           ApiKeyService apiKeyService,
                           PaymentRepository paymentRepository,
                           CredentialStorageService credentialStorageService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailProvider = emailProvider;
        this.apiKeyService = apiKeyService;
        this.paymentRepository = paymentRepository;
        this.credentialStorageService = credentialStorageService;
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

    public MerchantWebhookSecretResponse getWebhookSecret(Merchant merchant, String provider) {
        String normalizedProvider = normalizeProvider(provider);
        Map<String, Object> config = readProviderConfigOrEmpty(normalizedProvider, merchant.getId());
        String secret = extractWebhookSecretForProvider(normalizedProvider, config);

        MerchantWebhookSecretResponse response = new MerchantWebhookSecretResponse();
        response.setProvider(normalizedProvider);
        response.setConfigured(secret != null && !secret.isBlank());
        response.setMaskedSecret(maskSecret(secret));
        return response;
    }

    public MerchantWebhookSecretResponse upsertWebhookSecret(Merchant merchant, String provider, String secret) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedSecret = normalizeSecret(secret);

        if (!credentialStorageService.providerConfigExists(normalizedProvider, merchant.getId())) {
            throw new IllegalStateException("Configure " + normalizedProvider + " provider first before setting webhook secret");
        }

        credentialStorageService.updateProviderConfigProperty(
                normalizedProvider,
                merchant.getId(),
                "webhookSecret",
                normalizedSecret
        );

        return getWebhookSecret(merchant, normalizedProvider);
    }

    public MerchantAnalyticsResponse getMerchantAnalytics(Merchant merchant, int days) {
        int boundedDays = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        LocalDateTime fromDate = today.minusDays(boundedDays - 1L).atStartOfDay();

        List<Payment> payments = paymentRepository.findByMerchant_IdAndCreatedAtGreaterThanEqual(merchant.getId(), fromDate);

        long totalTransactions = payments.size();
        long successfulTransactions = payments.stream()
                .filter(payment -> SUCCESS_STATUSES.contains(payment.getStatus()))
                .count();
        long failedTransactions = payments.stream()
                .filter(payment -> FAILED_STATUSES.contains(payment.getStatus()))
                .count();
        long pendingTransactions = totalTransactions - successfulTransactions - failedTransactions;

        BigDecimal totalProcessedAmount = payments.stream()
                .filter(payment -> SUCCESS_STATUSES.contains(payment.getStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmountAllStatuses = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal successRate = percentage(successfulTransactions, totalTransactions);
        BigDecimal averageTransactionAmount = totalTransactions == 0
                ? BigDecimal.ZERO
                : totalAmountAllStatuses.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP);

        Map<String, Long> currencyCount = payments.stream()
                .collect(Collectors.groupingBy(Payment::getCurrency, Collectors.counting()));
        List<String> currenciesUsed = currencyCount.keySet().stream().sorted().toList();
        String primaryCurrency = resolvePrimaryCurrency(currencyCount);

        MerchantAnalyticsResponse response = new MerchantAnalyticsResponse();
        response.setDays(boundedDays);
        response.setTotalTransactions(totalTransactions);
        response.setSuccessfulTransactions(successfulTransactions);
        response.setFailedTransactions(failedTransactions);
        response.setPendingTransactions(pendingTransactions);
        response.setSuccessRate(successRate);
        response.setTotalProcessedAmount(totalProcessedAmount);
        response.setAverageTransactionAmount(averageTransactionAmount);
        response.setPrimaryCurrency(primaryCurrency);
        response.setCurrenciesUsed(currenciesUsed);
        response.setProviders(buildProviderAnalytics(payments));
        response.setDailyTrend(buildDailyTrend(payments, today, boundedDays));
        return response;
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

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }

        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("stripe") && !normalized.equals("paystack")) {
            throw new IllegalArgumentException("Unsupported provider. Use 'stripe' or 'paystack'");
        }
        return normalized;
    }

    private String normalizeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Secret is required");
        }
        return secret.trim();
    }

    private Map<String, Object> readProviderConfigOrEmpty(String provider, Long merchantId) {
        if (!credentialStorageService.providerConfigExists(provider, merchantId)) {
            return Map.of();
        }
        return credentialStorageService.getProviderConfig(provider, merchantId);
    }

    private String extractWebhookSecretForProvider(String provider, Map<String, Object> config) {
        String webhookSecret = asNonBlankString(config.get("webhookSecret"));
        if (webhookSecret != null) {
            return webhookSecret;
        }

        if ("paystack".equals(provider)) {
            return asNonBlankString(config.get("secretKey"));
        }
        return null;
    }

    private String asNonBlankString(Object value) {
        if (!(value instanceof String stringValue)) {
            return null;
        }
        String trimmed = stringValue.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private List<MerchantAnalyticsResponse.ProviderAnalytics> buildProviderAnalytics(List<Payment> payments) {
        Map<String, List<Payment>> grouped = payments.stream()
                .collect(Collectors.groupingBy(payment -> payment.getProvider().getName().toLowerCase(Locale.ROOT)));

        List<MerchantAnalyticsResponse.ProviderAnalytics> providerAnalytics = new ArrayList<>();
        for (Map.Entry<String, List<Payment>> entry : grouped.entrySet()) {
            List<Payment> providerPayments = entry.getValue();
            long transactions = providerPayments.size();
            long successful = providerPayments.stream().filter(p -> SUCCESS_STATUSES.contains(p.getStatus())).count();
            long failed = providerPayments.stream().filter(p -> FAILED_STATUSES.contains(p.getStatus())).count();
            BigDecimal processedAmount = providerPayments.stream()
                    .filter(p -> SUCCESS_STATUSES.contains(p.getStatus()))
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Payment sample = providerPayments.get(0);
            MerchantAnalyticsResponse.ProviderAnalytics item = new MerchantAnalyticsResponse.ProviderAnalytics();
            item.setProviderCode(entry.getKey());
            item.setProviderName(sample.getProvider().getDisplayName());
            item.setTransactions(transactions);
            item.setSuccessfulTransactions(successful);
            item.setFailedTransactions(failed);
            item.setSuccessRate(percentage(successful, transactions));
            item.setProcessedAmount(processedAmount);
            providerAnalytics.add(item);
        }

        providerAnalytics.sort(Comparator.comparing(MerchantAnalyticsResponse.ProviderAnalytics::getProcessedAmount).reversed());
        return providerAnalytics;
    }

    private List<MerchantAnalyticsResponse.DailyAnalyticsPoint> buildDailyTrend(List<Payment> payments,
                                                                                 LocalDate today,
                                                                                 int days) {
        Map<LocalDate, MerchantAnalyticsResponse.DailyAnalyticsPoint> byDate = new HashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            MerchantAnalyticsResponse.DailyAnalyticsPoint point = new MerchantAnalyticsResponse.DailyAnalyticsPoint();
            point.setDate(date);
            point.setTransactions(0);
            point.setSuccessfulTransactions(0);
            point.setProcessedAmount(BigDecimal.ZERO);
            byDate.put(date, point);
        }

        for (Payment payment : payments) {
            if (payment.getCreatedAt() == null) {
                continue;
            }
            LocalDate date = payment.getCreatedAt().toLocalDate();
            MerchantAnalyticsResponse.DailyAnalyticsPoint point = byDate.get(date);
            if (point == null) {
                continue;
            }

            point.setTransactions(point.getTransactions() + 1);
            if (SUCCESS_STATUSES.contains(payment.getStatus())) {
                point.setSuccessfulTransactions(point.getSuccessfulTransactions() + 1);
                point.setProcessedAmount(point.getProcessedAmount().add(payment.getAmount()));
            }
        }

        return byDate.values().stream()
                .sorted(Comparator.comparing(MerchantAnalyticsResponse.DailyAnalyticsPoint::getDate))
                .toList();
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String resolvePrimaryCurrency(Map<String, Long> currencyCount) {
        if (currencyCount.isEmpty()) {
            return "N/A";
        }
        if (currencyCount.size() == 1) {
            return currencyCount.keySet().iterator().next();
        }
        return currencyCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("MIXED");
    }
}
