package com.paybridge.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the two short-lived DB transactions that bracket the external provider call
 * in {@link PaymentService#createPayment}. Keeping these in a separate Spring bean
 * is required so that the {@code @Transactional} proxy is invoked correctly — calling
 * {@code @Transactional} methods on {@code this} inside the same class bypasses the proxy.
 *
 * <pre>
 * Phase 1 — preparePayment()  : validate idempotency key, create customer, lock record  [short TX]
 * Phase 2 — (PaymentService)  : resolve provider config, call external payment API       [no TX]
 * Phase 3 — finalizePayment() : persist payment, store response, release lock            [short TX]
 * </pre>
 */
@Service
class PaymentTransactionHelper {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    PaymentTransactionHelper(IdempotencyKeyRepository idempotencyKeyRepository,
                             CustomerRepository customerRepository,
                             PaymentRepository paymentRepository,
                             ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Phase 1
    // -------------------------------------------------------------------------

    /**
     * Short transaction: validates the idempotency key, resolves/creates the customer,
     * and locks the key record so no concurrent request can slip through.
     * The DB connection is released as soon as this method returns.
     *
     * @return a {@link PreparePaymentResult} — either a cached response to replay,
     *         or a locked {@link IdempotencyKey} ready for the external call.
     */
    @Transactional
    PreparePaymentResult preparePayment(Merchant merchant,
                                        CreatePaymentRequest request,
                                        String idempotencyKey,
                                        String requestHash) {

        Optional<IdempotencyKey> existingOpt =
                idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyKey existing = existingOpt.get();

            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                throw new IllegalStateException(
                        "Idempotency key already used with a different request payload");
            }
            if (existing.getResponse() != null && !existing.getResponse().isBlank()) {
                return PreparePaymentResult.cached(existing.getResponse());
            }
            if (existing.isLocked()) {
                throw new IllegalStateException(
                        "A request with this idempotency key is already being processed");
            }
        }

        Customer customer = resolveCustomer(merchant, request);

        IdempotencyKey record = existingOpt.orElseGet(IdempotencyKey::new);
        if (record.getId() == null) {
            record.setIdempotencyKey(idempotencyKey);
            record.setCreatedAt(LocalDateTime.now());
        }
        record.setCustomer(customer);
        record.setRequestHash(requestHash);
        record.setExpiresAt(LocalDateTime.now().plusDays(1));
        record.setPaymentStatus(PaymentStatus.PROCESSING);
        record.setLocked(true);
        record.setResponse(null);
        idempotencyKeyRepository.save(record);

        return PreparePaymentResult.proceed(record);
    }

    // -------------------------------------------------------------------------
    // Phase 3
    // -------------------------------------------------------------------------

    /**
     * Short transaction: persists the payment record and updates the idempotency key
     * with the final status and serialised response. The DB connection is only open
     * for these two writes — the expensive external API call has already completed.
     */
    @Transactional
    PaymentResponse finalizePayment(Merchant merchant,
                                    ProviderConfig providerConfig,
                                    PaymentProviderResponse providerResponse,
                                    IdempotencyKey idempotencyRecord,
                                    CreatePaymentRequest request,
                                    String providerName) {

        PaymentStatus paymentStatus = mapProviderStatus(providerResponse.getStatus());

        Payment payment = new Payment();
        payment.setMerchant(merchant);
        payment.setProvider(providerConfig.getProvider());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus(paymentStatus);
        payment.setProviderReference(providerResponse.getProviderPaymentId());
        Payment savedPayment = paymentRepository.save(payment);

        PaymentResponse response = toPaymentResponse(savedPayment, request, providerName, providerResponse);

        idempotencyRecord.setPaymentStatus(paymentStatus);
        idempotencyRecord.setLocked(false);
        idempotencyRecord.setResponse(writeAsString(response));
        idempotencyKeyRepository.save(idempotencyRecord);

        return response;
    }

    /**
     * Marks the idempotency record as failed and releases its lock.
     * Runs in a new independent transaction ({@code REQUIRES_NEW}) so the failure
     * is always committed even when the caller is handling an exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void failIdempotencyRecord(IdempotencyKey record) {
        idempotencyKeyRepository.findByIdempotencyKey(record.getIdempotencyKey())
                .ifPresent(managedRecord -> {
                    managedRecord.setPaymentStatus(PaymentStatus.FAILED);
                    managedRecord.setLocked(false);
                });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Customer resolveCustomer(Merchant merchant, CreatePaymentRequest request) {
        Customer customer = new Customer();
        customer.setMerchant(merchant);
        customer.setEmail(request.getEmail());
        customer.setFullName(request.getCustomerName());
        customer.setPhone(request.getCustomerPhone());

        String referenceSeed = request.getCustomerReference();
        if (referenceSeed == null || referenceSeed.isBlank()) {
            referenceSeed = request.getEmail() != null
                    ? request.getEmail()
                    : UUID.randomUUID().toString();
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

        LocalDateTime createdAt = payment.getCreatedAt() != null
                ? payment.getCreatedAt()
                : LocalDateTime.now();
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
            case "failed", "failure"                  -> PaymentStatus.FAILED;
            case "cancelled", "canceled"              -> PaymentStatus.CANCELLED;
            case "processing", "pending",
                 "requires_payment_method",
                 "requires_confirmation",
                 "requires_action"                    -> PaymentStatus.PENDING;
            default                                   -> PaymentStatus.PROCESSING;
        };
    }

    private String writeAsString(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize payment response", ex);
        }
    }
}
