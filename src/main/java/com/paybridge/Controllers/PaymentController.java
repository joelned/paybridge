package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthenticationService authenticationService;

    public PaymentController(PaymentService paymentService, AuthenticationService authenticationService) {
        this.paymentService = paymentService;
        this.authenticationService = authenticationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreatePaymentRequest request,
            Authentication authentication) {

        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        PaymentResponse response = paymentService.createPayment(request, merchant, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
