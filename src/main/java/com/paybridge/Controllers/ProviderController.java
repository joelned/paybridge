package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.ProviderConfiguration;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.ConnectionTestResult;
import com.paybridge.Services.ProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    private final AuthenticationService authenticationService;

    private final MerchantRepository merchantRepository;

    public ProviderController(ProviderService providerService, AuthenticationService authenticationService, MerchantRepository merchantRepository) {
        this.providerService = providerService;
        this.authenticationService = authenticationService;
        this.merchantRepository = merchantRepository;
    }

    @PostMapping("/configure")
    public ResponseEntity<ApiResponse<Map>> configureProvider(
            @RequestBody @Valid ProviderConfiguration providerConfiguration,
            @RequestParam(defaultValue = "true") boolean testConnection,
            Authentication authentication) {

        try {
            Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);

            ProviderConfig config = providerService.configureProvider(
                    providerConfiguration,
                    merchant.getId(),
                    testConnection
            );

            merchant.setStatus(MerchantStatus.ACTIVE);
            merchantRepository.save(merchant);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "configId", config.getId(),
                    "provider", config.getProvider().getDisplayName(),
                    "tested", testConnection
            )));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            )));

        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.error(Map.of(
                    "status", "error",
                    "message", "Unauthorized"
            )));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(Map.of(
                    "status", "error",
                    "message", "Failed to configure provider"
            )));
        }
    }

    @PostMapping("/test/{configId}")
    public ResponseEntity<ApiResponse<Map>> testProviderConnection(
            @PathVariable Long configId,
            Authentication authentication) {

        try {
            Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);

            ConnectionTestResult result = providerService.testExistingProviderConfig(
                    configId,
                    merchant.getId()
            );

            merchant.setStatus(MerchantStatus.ACTIVE);
            merchantRepository.save(merchant);

            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "message", result.getMessage(),
                    "tested", true,
                    "durationMs", result.getTestDurationMs(),
                    "metadata", result.getMetadata()
            )));

        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ApiResponse.error(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            )));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                    Map.of("status", "error",
                    "message", e.getMessage()
            )));
        }
    }
}