package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.ProviderConfiguration;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.ConnectionTestResult;
import com.paybridge.Services.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private AuthenticationService authenticationService;

    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configureProvider(
            @RequestBody @Valid ProviderConfiguration providerConfiguration,
            @RequestParam(defaultValue = "false") boolean testConnection,
            Authentication authentication) {

        try {
            Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
            ProviderConfig config = providerService.configureProvider(
                    providerConfiguration,
                    merchant.getId(),
                    testConnection
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Provider configured successfully");
            response.put("configId", config.getId());
            response.put("provider", config.getProvider().getDisplayName());
            response.put("tested", testConnection);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to configure provider: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/test/{configId}")
    public ResponseEntity<Map<String, Object>> testProviderConnection(
            @PathVariable Long configId,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            Long userId = getUserIdFromUserDetails(userDetails);

            ConnectionTestResult result = providerService.testExistingProviderConfig(
                    configId,
                    userId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", result.isSuccess() ? "success" : "error");
            response.put("message", result.getMessage());
            response.put("tested", true);
            response.put("durationMs", result.getTestDurationMs());
            response.put("metadata", result.getMetadata());

            return result.isSuccess()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);

        } catch (SecurityException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Unauthorized");
            return ResponseEntity.status(403).body(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Test failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private Long getUserIdFromUserDetails(UserDetails userDetails) {
        // Implement based on your User entity
        return 1L; // Placeholder
    }
}