package com.paybridge.Models.Enums;

public enum PaymentEventType {
    CREATED,
    SUCCEEDED,
    PROCESSING,
    FAILED,
    REFUNDED,
    CANCELED,
    PROVIDER_RESPONSE_RECEIVED,
    WEBHOOK_SENT
}
