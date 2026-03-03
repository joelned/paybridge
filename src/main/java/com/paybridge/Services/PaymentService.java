package com.paybridge.Services;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.Customer;
import com.paybridge.Models.Entities.IdempotencyKey;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Payment;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Models.Enums.PaymentStatus;
import com.paybridge.Repositories.CustomerRepository;
import com.paybridge.Repositories.IdempotencyKeyRepository;
import com.paybridge.Repositories.PaymentRepository;
import com.paybridge.Repositories.ProviderConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderConfigRepository providerConfigRepository;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final CredentialStorageService credentialStorageService;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(IdempotencyKeyRepository idempotencyKeyRepository,
                          PaymentRepository paymentRepository,
                          ProviderConfigRepository providerConfigRepository,
                          PaymentProviderRegistry paymentProviderRegistry,
                          CredentialStorageService credentialStorageService,
                          CustomerRepository customerRepository,
                          ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.paymentRepository = paymentRepository;
        this.providerConfigRepository = providerConfigRepository;
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.credentialStorageService = credentialStorageService;
        this.customerRepository = customerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest paymentRequest, Merchant merchant, String idempotencyKey) {
        if (merchant == null) {
            throw new IllegalArgumentException("Authenticated merchant not found");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }

        String requestHash = buildStableRequestHash(paymentRequest);
        Optional<IdempotencyKey> existingOpt = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyKey existing = existingOpt.get();

            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                throw new IllegalStateException("Idempotency key already used with a different request payload");
            }

            if (existing.getResponse() != null && !existing.getResponse().isBlank()) {
                try {
                    return objectMapper.readValue(existing.getResponse(), PaymentResponse.class);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to read cached idempotent response", ex);
                }
            }

            if (existing.isLocked()) {
                throw new IllegalStateException("A request with this idempotency key is already being processed");
            }
        }

        Customer customer = resolveCustomer(merchant, paymentRequest);
        IdempotencyKey idempotencyRecord = existingOpt.orElseGet(IdempotencyKey::new);
        if (idempotencyRecord.getId() == null) {
            idempotencyRecord.setIdempotencyKey(idempotencyKey);
            idempotencyRecord.setCreatedAt(LocalDateTime.now());
        }
        idempotencyRecord.setCustomer(customer);
        idempotencyRecord.setRequestHash(requestHash);
        idempotencyRecord.setExpiresAt(LocalDateTime.now().plusDays(1));
        idempotencyRecord.setPaymentStatus(PaymentStatus.PROCESSING);
        idempotencyRecord.setLocked(true);
        idempotencyRecord.setResponse(null);
        idempotencyKeyRepository.save(idempotencyRecord);

        try {
            ProviderConfig providerConfig = resolveEnabledProviderConfig(merchant.getId(), paymentRequest.getProvider());
            String providerName = providerConfig.getProvider().getName().toLowerCase(Locale.ROOT);

            Map<String, Object> credentials = credentialStorageService.getProviderConfig(providerName, merchant.getId());
            PaymentProviderResponse providerResponse = paymentProviderRegistry
                    .getProvider(providerName)
                    .CreatePaymentRequest(paymentRequest, credentials);

            if (providerResponse == null) {
                throw new RuntimeException("Provider returned empty payment response");
            }

            PaymentStatus paymentStatus = mapProviderStatus(providerResponse.getStatus());

            Payment payment = new Payment();
            payment.setMerchant(merchant);
            payment.setProvider(providerConfig.getProvider());
            payment.setAmount(paymentRequest.getAmount());
            payment.setCurrency(paymentRequest.getCurrency());
            payment.setStatus(paymentStatus);
            payment.setProviderReference(providerResponse.getProviderPaymentId());
            Payment savedPayment = paymentRepository.save(payment);

            PaymentResponse response = toPaymentResponse(savedPayment, paymentRequest, providerName, providerResponse);

            idempotencyRecord.setPaymentStatus(paymentStatus);
            idempotencyRecord.setLocked(false);
            idempotencyRecord.setResponse(writeAsString(response));
            idempotencyKeyRepository.save(idempotencyRecord);

            return response;
        } catch (RuntimeException ex) {
            idempotencyRecord.setPaymentStatus(PaymentStatus.FAILED);
            idempotencyRecord.setLocked(false);
            idempotencyKeyRepository.save(idempotencyRecord);
            throw ex;
        }
    }

    private ProviderConfig resolveEnabledProviderConfig(Long merchantId, String requestedProvider) {
        if (requestedProvider != null && !requestedProvider.isBlank()) {
            String normalizedProvider = requestedProvider.trim().toLowerCase(Locale.ROOT);
            return providerConfigRepository
                    .findByMerchantIdAndProvider_NameIgnoreCaseAndIsEnabledTrue(merchantId, normalizedProvider)
                    .orElseThrow(() -> new IllegalStateException(
                            "Requested provider is not configured/enabled for merchant: " + normalizedProvider
                    ));
        }

        List<ProviderConfig> enabledConfigs = providerConfigRepository.findByMerchantIdAndIsEnabledTrue(merchantId);
        if (enabledConfigs.isEmpty()) {
            throw new IllegalStateException("No enabled payment provider configuration found for merchant");
        }

        if (enabledConfigs.size() > 1) {
            throw new IllegalStateException("Multiple providers are enabled. Please specify provider in request body.");
        }

        return enabledConfigs.get(0);
    }

    private Customer resolveCustomer(Merchant merchant, CreatePaymentRequest request) {
        Customer customer = new Customer();
        customer.setMerchant(merchant);
        customer.setEmail(request.getEmail());
        customer.setFullName(request.getCustomerName());
        customer.setPhone(request.getCustomerPhone());

        String referenceSeed = request.getCustomerReference();
        if (referenceSeed == null || referenceSeed.isBlank()) {
            referenceSeed = request.getEmail() != null ? request.getEmail() : UUID.randomUUID().toString();
        }

        int externalId = Math.abs(referenceSeed.hashCode());
        customer.setExternalCustomerId(externalId == 0 ? 1 : externalId);

        return customerRepository.save(customer);
    }

    private PaymentResponse toPaymentResponse(Payment payment,
                                              CreatePaymentRequest request,
                                              String providerName,
                                              PaymentProviderResponse providerResponse) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setStatus(payment.getStatus());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setProvider(providerName);
        response.setProviderReference(providerResponse.getProviderPaymentId());
        response.setCheckoutUrl(providerResponse.getCheckoutUrl());
        response.setClientSecret(providerResponse.getClientSecret());
        response.setMetadata(request.getMetadata());
        response.setMessage("Payment created successfully");

        LocalDateTime createdAt = payment.getCreatedAt() != null ? payment.getCreatedAt() : LocalDateTime.now();
        response.setCreatedAt(createdAt.toInstant(ZoneOffset.UTC));
        response.setExpiresAt(Instant.now().plusSeconds(3600));
        return response;
    }

    private PaymentStatus mapProviderStatus(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return PaymentStatus.PENDING;
        }

        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "succeeded", "success", "successful" -> PaymentStatus.SUCCEEDED;
            case "failed", "failure" -> PaymentStatus.FAILED;
            case "cancelled", "canceled" -> PaymentStatus.CANCELLED;
            case "processing", "pending", "requires_payment_method", "requires_confirmation",
                 "requires_action" -> PaymentStatus.PENDING;
            default -> PaymentStatus.PROCESSING;
        };
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

    private String writeAsString(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize payment response", ex);
        }
    }
}
