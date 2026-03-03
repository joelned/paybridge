package com.paybridge.Configs;

import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Services.ConnectionTestResult;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    /**
     * Test Stripe API connection by sending a request with a secret key
     */
    @Override
    public ConnectionTestResult testConnection(Map<String, Object> credentials) {
        String stripeApiKey= (String) credentials.get("secretKey");

        StripeClient stripeClient = createStripeClient(stripeApiKey);
        CustomerCreateParams params = CustomerCreateParams
                .builder()
                .setDescription("Payment Description")
                .setEmail("test@gmail.comj")
                .build();
        try{
            Customer customer = stripeClient.v1().customers().create(params);
            System.out.println(customer);
            return ConnectionTestResult.success("Stripe test connection successful");
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PaymentProviderResponse CreatePaymentRequest(CreatePaymentRequest request, Map<String, Object> credentials) {
        String stripeApiKey = (String) credentials.get("secretKey");
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new IllegalArgumentException("Stripe secretKey is required");
        }

        String successUrl = normalizeCheckoutUrl(request.getRedirectUrl());
        String cancelUrl = appendQueryParam(successUrl, "payment_status=cancelled");

        StripeClient stripeClient = createStripeClient(stripeApiKey);
        long amountInMinorUnit = toMinorUnit(request.getAmount());

        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(request.getDescription())
                        .build();

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(request.getCurrency().toLowerCase())
                        .setUnitAmount(amountInMinorUnit)
                        .setProductData(productData)
                        .build();

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(priceData)
                                .build()
                );

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            builder.setCustomerEmail(request.getEmail());
        }

        if (request.getTransactionReference() != null && !request.getTransactionReference().isBlank()) {
            builder.setClientReferenceId(request.getTransactionReference());
        }

        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            request.getMetadata().forEach(builder::putMetadata);
        }

        try {
            Session session = stripeClient.v1().checkout().sessions().create(builder.build());

            PaymentProviderResponse response = new PaymentProviderResponse();
            response.setProviderPaymentId(session.getId());
            response.setStatus(session.getStatus() != null ? session.getStatus() : "pending");
            response.setCheckoutUrl(session.getUrl());
            return response;
        } catch (StripeException e) {
            logger.error("Stripe checkout session creation failed", e);
            throw new RuntimeException("Stripe checkout session creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }

    protected StripeClient createStripeClient(String apiKey) {
        return new StripeClient(apiKey);
    }

    private long toMinorUnit(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        return amount.movePointRight(2).longValueExact();
    }

    private String normalizeCheckoutUrl(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new IllegalArgumentException("redirectUrl is required for Stripe checkout session");
        }

        String value = redirectUrl.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }

        return "https://" + value;
    }

    private String appendQueryParam(String baseUrl, String queryParam) {
        return baseUrl.contains("?") ? baseUrl + "&" + queryParam : baseUrl + "?" + queryParam;
    }
}
