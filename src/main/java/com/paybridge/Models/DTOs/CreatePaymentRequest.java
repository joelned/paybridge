package com.paybridge.Models.DTOs;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for creating a payment.
 * This represents what the MERCHANT must provide when initiating a payment.
 *
 * MANDATORY FIELDS (what a merchant MUST provide):
 * - amount: How much to charge
 * - currency: What currency (USD, NGN, etc.)
 * - description: What the payment is for
 *
 * OPTIONAL FIELDS (merchant can provide):
 * - provider: Explicit provider route (e.g. "stripe", "paystack")
 * - email: Customer's email (for receipt/notification)
 * - metadata: Custom data merchant wants to store
 * - redirectUrl: Where to send customer after payment
 * - webhookUrl: Override default webhook URL for this payment
 * - customerReference: Merchant's internal customer ID
 */
public class CreatePaymentRequest {

    // ========== MANDATORY FIELDS ==========

    /**
     * Payment amount in the smallest currency unit
     * For USD: $50.00 = 50.00
     * For NGN: ₦5000 = 5000.00
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "9999999.99", message = "Amount exceeds maximum")
    private BigDecimal amount;

    /**
     * ISO 4217 currency code (3 letters)
     * Examples: USD, NGN, GBP, EUR
     */
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters")
    private String currency;

    /**
     * Human-readable description of what the payment is for
     * This appears on customer's bank statement and receipts
     */
    @NotBlank(message = "Description is required")
    @Size(min = 3, max = 500, message = "Description must be between 3 and 500 characters")
    private String description;

    // ========== OPTIONAL FIELDS ==========

    /**
     * Optional explicit provider routing.
     * Examples: stripe, paystack
     */
    @Size(max = 50, message = "Provider value too long")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Provider contains invalid characters")
    private String provider;

    /**
     * Customer's email address
     * Used for sending payment receipts and notifications
     * HIGHLY RECOMMENDED but not mandatory
     */
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Custom metadata the merchant wants to store with this payment
     * This is returned in webhooks and can be used to link to merchant's system
     *
     * Example:
     * {
     *   "orderId": "12345",
     *   "customerId": "cust_abc",
     *   "items": "2x Product A, 1x Product B",
     *   "shippingAddress": "123 Main St"
     * }
     */
    private Map<String, String> metadata;

    /**
     * URL where customer should be redirected after successful payment
     * If not provided, uses merchant's default redirect URL
     */
    @Pattern(
            regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$",
            message = "Invalid redirect URL format"
    )
    private String redirectUrl;

    /**
     * URL where payment success webhook should be sent
     * Overrides merchant's default webhook URL for this specific payment
     */
    @Pattern(
            regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$",
            message = "Invalid webhook URL format"
    )
    private String webhookUrl;

    /**
     * Merchant's internal reference for this customer
     * Helps merchant track which customer made this payment
     */
    @Size(max = 255, message = "Customer reference too long")
    private String customerReference;

    /**
     * Merchant's internal reference for this transaction
     * Must be unique per merchant - used for idempotency
     */
    @Size(max = 255, message = "Transaction reference too long")
    private String transactionReference;

    /**
     * Customer's full name (if known)
     * Some payment providers require this
     */
    @Size(max = 100, message = "Customer name too long")
    private String customerName;

    /**
     * Customer's phone number in E.164 format
     * Example: +2348012345678
     */
    @Pattern(
            regexp = "^\\+[1-9]\\d{1,14}$",
            message = "Phone number must be in E.164 format (e.g., +2348012345678)"
    )
    private String customerPhone;

    // ========== GETTERS AND SETTERS ==========

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public void setCustomerReference(String customerReference) {
        this.customerReference = customerReference;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }
}
