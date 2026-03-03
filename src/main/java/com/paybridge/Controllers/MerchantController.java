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
}
