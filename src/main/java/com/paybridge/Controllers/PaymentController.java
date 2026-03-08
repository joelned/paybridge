package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Services.ApiKeyService;
import com.paybridge.Services.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final ApiKeyService apiKeyService;

    public PaymentController(PaymentService paymentService, ApiKeyService apiKeyService) {
        this.paymentService = paymentService;
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @RequestHeader("x-api-key") String apiKey,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreatePaymentRequest request) {

        Merchant merchant = apiKeyService.findMerchantByApiKey(apiKey)
                .orElseThrow(() -> new IllegalArgumentException("Invalid API key"));
        PaymentResponse response = paymentService.createPayment(request, merchant, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
