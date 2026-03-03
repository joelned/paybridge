package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Services.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        Map<String, Object> result = webhookService.handleStripeWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/paystack")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handlePaystackWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature
    ) {
        Map<String, Object> result = webhookService.handlePaystackWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
