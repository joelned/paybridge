package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.*;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Enums.ApiKeyMode;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;
    private final AuthenticationService authenticationService;

    public MerchantController(MerchantService merchantService, AuthenticationService authenticationService) {
        this.merchantService = merchantService;
        this.authenticationService = authenticationService;
    }


    @PostMapping
    public ResponseEntity<ApiResponse<String>> registerMerchant(@RequestBody @Valid MerchantRegistrationRequest request){
            merchantService.registerMerchant(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Registration successful. Please check your email for verification code"));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> getMerchantDetails(Authentication authentication){
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        MerchantProfileResponse merchantProfile = merchantService.getMerchantProfile(merchant);

        return ResponseEntity.ok().body(ApiResponse.success(merchantProfile));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<MerchantAnalyticsResponse>> getMerchantAnalytics(Authentication authentication,
                                                                                       @RequestParam(defaultValue = "30") int days) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        MerchantAnalyticsResponse analytics = merchantService.getMerchantAnalytics(merchant, days);
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<List<MerchantApiKeySummaryResponse>>> getMerchantApiKeys(Authentication authentication) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        List<MerchantApiKeySummaryResponse> apiKeys = merchantService.getMerchantApiKeys(merchant);
        return ResponseEntity.ok(ApiResponse.success(apiKeys));
    }

    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<MerchantApiKeyCreateResponse>> createOrRotateApiKey(Authentication authentication,
                                                                                           @RequestBody @Valid ApiKeyModeRequest request) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        MerchantApiKeyCreateResponse response = merchantService.createOrRotateApiKey(merchant, request.getMode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @DeleteMapping("/api-keys/{keyId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeApiKey(Authentication authentication,
                                                                          @PathVariable String keyId) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        ApiKeyMode mode = merchantService.resolveApiKeyModeFromKeyId(keyId);
        merchantService.revokeApiKey(merchant, mode);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "API key revoked successfully",
                "keyId", keyId
        )));
    }

    @GetMapping("/webhooks/{provider}")
    public ResponseEntity<ApiResponse<MerchantWebhookSecretResponse>> getWebhookSecret(Authentication authentication,
                                                                                       @PathVariable String provider) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        MerchantWebhookSecretResponse response = merchantService.getWebhookSecret(merchant, provider);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/webhooks/{provider}/secret")
    public ResponseEntity<ApiResponse<MerchantWebhookSecretResponse>> setWebhookSecret(Authentication authentication,
                                                                                       @PathVariable String provider,
                                                                                       @RequestBody @Valid MerchantWebhookSecretRequest request) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        MerchantWebhookSecretResponse response = merchantService.upsertWebhookSecret(merchant, provider, request.getSecret());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/webhooks/{provider}/rotate")
    public ResponseEntity<ApiResponse<MerchantWebhookSecretResponse>> rotateWebhookSecret(Authentication authentication,
                                                                                          @PathVariable String provider,
                                                                                          @RequestBody @Valid MerchantWebhookSecretRequest request) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        MerchantWebhookSecretResponse response = merchantService.upsertWebhookSecret(merchant, provider, request.getSecret());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
