package com.paybridge.Services;

import com.paybridge.Models.Entities.IdempotencyKey;

/**
 * Carries the result of the first transaction phase in payment creation.
 * Either the existing serialized response (idempotent replay), or a locked
 * {@link IdempotencyKey} record ready for the external provider call.
 */
class PreparePaymentResult {

    private final String cachedResponse;
    private final IdempotencyKey idempotencyRecord;

    private PreparePaymentResult(String cachedResponse, IdempotencyKey idempotencyRecord) {
        this.cachedResponse = cachedResponse;
        this.idempotencyRecord = idempotencyRecord;
    }

    static PreparePaymentResult cached(String response) {
        return new PreparePaymentResult(response, null);
    }

    static PreparePaymentResult proceed(IdempotencyKey record) {
        return new PreparePaymentResult(null, record);
    }

    boolean hasCachedResponse() {
        return cachedResponse != null;
    }

    String getCachedResponse() {
        return cachedResponse;
    }

    IdempotencyKey getIdempotencyRecord() {
        return idempotencyRecord;
    }
}
