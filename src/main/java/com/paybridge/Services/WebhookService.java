package com.paybridge.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Models.Entities.Payment;
import com.paybridge.Models.Entities.ProcessedWebhookEvent;
import com.paybridge.Models.Enums.PaymentStatus;
import com.paybridge.Repositories.PaymentRepository;
import com.paybridge.Repositories.ProcessedWebhookEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final CredentialStorageService credentialStorageService;
    private final ObjectMapper objectMapper;

    @Value("${webhook.stripe.signing-secret:${STRIPE_WEBHOOK_SECRET:}}")
    private String stripeSigningSecret;

    public WebhookService(PaymentRepository paymentRepository,
                          ProcessedWebhookEventRepository processedWebhookEventRepository,
                          CredentialStorageService credentialStorageService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.credentialStorageService = credentialStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> handleStripeWebhook(String payload, String signatureHeader) {
        if (stripeSigningSecret == null || stripeSigningSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook signing secret is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeSigningSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature");
        }

        String eventId = event.getId();
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Stripe event id is missing");
        }

        if (isAlreadyProcessed("stripe", eventId)) {
            return Map.of("processed", false, "duplicate", true, "eventId", eventId);
        }

        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Session session)) {
            markProcessed("stripe", eventId);
            return Map.of("processed", false, "ignored", true, "reason", "Unsupported Stripe event object", "eventType", event.getType());
        }

        String providerReference = session.getId();
        if (providerReference == null || providerReference.isBlank()) {
            markProcessed("stripe", eventId);
            return Map.of("processed", false, "ignored", true, "reason", "Missing session id");
        }

        Optional<Payment> paymentOpt = paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase(providerReference, "stripe");
        if (paymentOpt.isEmpty()) {
            markProcessed("stripe", eventId);
            return Map.of("processed", false, "ignored", true, "reason", "Payment not found", "providerReference", providerReference);
        }

        Payment payment = paymentOpt.get();
        PaymentStatus nextStatus = mapStripeEventStatus(event.getType());
        if (nextStatus != null) {
            applyStatusTransition(payment, nextStatus);
            paymentRepository.save(payment);
        }

        markProcessed("stripe", eventId);
        return Map.of(
                "processed", true,
                "eventId", eventId,
                "paymentId", payment.getId().toString(),
                "status", payment.getStatus().name(),
                "eventType", event.getType()
        );
    }

    @Transactional
    public Map<String, Object> handlePaystackWebhook(String payload, String signatureHeader) {
        Map<String, Object> eventBody = readAsMap(payload);
        String eventType = asString(eventBody.get("event"));

        Map<String, Object> data = asMap(eventBody.get("data"));
        String providerReference = asString(data.get("reference"));

        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("Paystack webhook missing data.reference");
        }

        Optional<Payment> paymentOpt = paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase(providerReference, "paystack");
        if (paymentOpt.isEmpty()) {
            return Map.of("processed", false, "ignored", true, "reason", "Payment not found", "providerReference", providerReference);
        }

        Payment payment = paymentOpt.get();
        String secretKey = loadMerchantPaystackSecret(payment);
        verifyPaystackSignature(payload, signatureHeader, secretKey);

        String eventId = derivePaystackEventId(eventType, data, providerReference);
        if (isAlreadyProcessed("paystack", eventId)) {
            return Map.of("processed", false, "duplicate", true, "eventId", eventId);
        }

        PaymentStatus nextStatus = mapPaystackEventStatus(eventType, asString(data.get("status")));
        if (nextStatus != null) {
            applyStatusTransition(payment, nextStatus);
            paymentRepository.save(payment);
        }

        markProcessed("paystack", eventId);
        return Map.of(
                "processed", true,
                "eventId", eventId,
                "paymentId", payment.getId().toString(),
                "status", payment.getStatus().name(),
                "eventType", eventType
        );
    }

    private boolean isAlreadyProcessed(String provider, String eventId) {
        return processedWebhookEventRepository.existsByProviderAndEventId(provider, eventId);
    }

    private void markProcessed(String provider, String eventId) {
        ProcessedWebhookEvent event = new ProcessedWebhookEvent();
        event.setProvider(provider);
        event.setEventId(eventId);
        processedWebhookEventRepository.save(event);
    }

    private PaymentStatus mapStripeEventStatus(String eventType) {
        return switch (eventType) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> PaymentStatus.SUCCEEDED;
            case "checkout.session.async_payment_failed" -> PaymentStatus.FAILED;
            case "checkout.session.expired" -> PaymentStatus.CANCELLED;
            default -> null;
        };
    }

    private PaymentStatus mapPaystackEventStatus(String eventType, String providerStatus) {
        if ("charge.success".equalsIgnoreCase(eventType)) {
            return PaymentStatus.SUCCEEDED;
        }
        if ("charge.failed".equalsIgnoreCase(eventType)) {
            return PaymentStatus.FAILED;
        }
        if (providerStatus == null) {
            return null;
        }

        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "success", "successful" -> PaymentStatus.SUCCEEDED;
            case "failed", "abandoned", "reversed" -> PaymentStatus.FAILED;
            default -> null;
        };
    }

    private void applyStatusTransition(Payment payment, PaymentStatus nextStatus) {
        PaymentStatus current = payment.getStatus();

        if (current == PaymentStatus.SUCCEEDED && nextStatus !=
                PaymentStatus.REFUNDED && nextStatus != PaymentStatus.PARTIALLY_REFUNDED) {
            return;
        }

        if (current == PaymentStatus.CANCELLED && nextStatus == PaymentStatus.PENDING) {
            return;
        }

        payment.setStatus(nextStatus);
    }

    private String loadMerchantPaystackSecret(Payment payment) {
        Long merchantId = payment.getMerchant().getId();
        Map<String, Object> credentials = credentialStorageService.getProviderConfig("paystack", merchantId);
        Object secret = credentials.get("secretKey");

        if (!(secret instanceof String secretString) || secretString.isBlank()) {
            throw new IllegalStateException("Paystack secret key not configured for merchant");
        }

        String normalized = secretString.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized;
    }

    private void verifyPaystackSignature(String payload, String signatureHeader, String secretKey) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Missing x-paystack-signature header");
        }

        try {
            Mac sha512Hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            sha512Hmac.init(keySpec);
            byte[] digest = sha512Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);

            if (!expected.equalsIgnoreCase(signatureHeader.trim())) {
                throw new IllegalArgumentException("Invalid Paystack webhook signature");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paystack signature verification failed", e);
            throw new RuntimeException("Failed to verify Paystack signature", e);
        }
    }

    private String derivePaystackEventId(String eventType, Map<String, Object> data, String providerReference) {
        String transactionId = asString(data.get("id"));
        if (transactionId != null && !transactionId.isBlank()) {
            return transactionId;
        }

        String status = asString(data.get("status"));
        return String.format("%s:%s:%s", eventType, providerReference, status != null ? status : "unknown");
    }

    private Map<String, Object> readAsMap(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
