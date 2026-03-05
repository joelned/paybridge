package com.paybridge.Services;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.IdempotencyKey;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Repositories.ProviderConfigRepository;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates payment creation across three phases to avoid holding a DB connection
 * open during the (potentially slow) external provider HTTP call.
 *
 * <pre>
 * Phase 1 [short TX]  — validate idempotency key, create customer, lock record
 * Phase 2 [no TX]     — resolve provider config, call external payment API
 * Phase 3 [short TX]  — persist payment, store response, release lock
 * </pre>
 *
 * The {@code @Transactional} boundaries are managed by {@link PaymentTransactionHelper},
 * a separate Spring bean whose proxied methods are called from here. This is required
 * because calling {@code @Transactional} methods on {@code this} bypasses the proxy.
 */
@Service
public class PaymentService {

    private final ProviderConfigRepository providerConfigRepository;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final CredentialStorageService credentialStorageService;
    private final ObjectMapper objectMapper;
    private final PaymentTransactionHelper transactionHelper;

    public PaymentService(ProviderConfigRepository providerConfigRepository,
                          PaymentProviderRegistry paymentProviderRegistry,
                          CredentialStorageService credentialStorageService,
                          ObjectMapper objectMapper,
                          PaymentTransactionHelper transactionHelper) {
        this.providerConfigRepository = providerConfigRepository;
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.credentialStorageService = credentialStorageService;
        this.objectMapper = objectMapper;
        this.transactionHelper = transactionHelper;
    }

    // NOTE: intentionally NOT @Transactional — the three-phase split below
    // ensures no DB connection is held during the external provider HTTP call.
    public PaymentResponse createPayment(CreatePaymentRequest paymentRequest,
                                         Merchant merchant,
                                         String idempotencyKey) {
        if (merchant == null) {
            throw new IllegalArgumentException("Authenticated merchant not found");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }

        String requestHash = buildStableRequestHash(paymentRequest);

        // ── Phase 1: short transaction ────────────────────────────────────────
        // Validates/locks the idempotency key and resolves the customer.
        // DB connection is released as soon as preparePayment() returns.
        PreparePaymentResult preparation =
                transactionHelper.preparePayment(merchant, paymentRequest, idempotencyKey, requestHash);

        if (preparation.hasCachedResponse()) {
            return deserializeResponse(preparation.getCachedResponse());
        }

        IdempotencyKey idempotencyRecord = preparation.getIdempotencyRecord();

        try {
            // ── Phase 2: no transaction ───────────────────────────────────────
            // Provider config lookup is a cheap indexed read — no TX needed.
            // The external API call can take 1–3 s; no DB connection is held.
            ProviderConfig providerConfig =
                    resolveEnabledProviderConfig(merchant.getId(), paymentRequest.getProvider());
            String providerName = providerConfig.getProvider().getName().toLowerCase(Locale.ROOT);

            Map<String, Object> credentials =
                    credentialStorageService.getProviderConfig(providerName, merchant.getId());

            PaymentProviderResponse providerResponse = paymentProviderRegistry
                    .getProvider(providerName)
                    .CreatePaymentRequest(paymentRequest, credentials);

            if (providerResponse == null) {
                throw new RuntimeException("Provider returned empty payment response");
            }

            // ── Phase 3: short transaction ────────────────────────────────────
            // Writes the payment record and releases the idempotency lock.
            return transactionHelper.finalizePayment(
                    merchant, providerConfig, providerResponse,
                    idempotencyRecord, paymentRequest, providerName);

        } catch (RuntimeException ex) {
            // REQUIRES_NEW — commits the failure independently of this call stack.
            transactionHelper.failIdempotencyRecord(idempotencyRecord);
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ProviderConfig resolveEnabledProviderConfig(Long merchantId, String requestedProvider) {
        if (requestedProvider != null && !requestedProvider.isBlank()) {
            String normalizedProvider = requestedProvider.trim().toLowerCase(Locale.ROOT);
            return providerConfigRepository
                    .findByMerchantIdAndProvider_NameIgnoreCaseAndIsEnabledTrue(merchantId, normalizedProvider)
                    .orElseThrow(() -> new IllegalStateException(
                            "Requested provider is not configured/enabled for merchant: " + normalizedProvider));
        }

        List<ProviderConfig> enabledConfigs =
                providerConfigRepository.findByMerchantIdAndIsEnabledTrue(merchantId);
        if (enabledConfigs.isEmpty()) {
            throw new IllegalStateException("No enabled payment provider configuration found for merchant");
        }
        if (enabledConfigs.size() > 1) {
            throw new IllegalStateException(
                    "Multiple providers are enabled. Please specify provider in request body.");
        }
        return enabledConfigs.get(0);
    }

    private PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read cached idempotent response", ex);
        }
    }

    private String buildStableRequestHash(CreatePaymentRequest paymentRequest) {
        try {
            ObjectMapper canonicalMapper = objectMapper.copy()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            byte[] payload = canonicalMapper.writeValueAsBytes(paymentRequest);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compute payment request hash", ex);
        }
    }
}
