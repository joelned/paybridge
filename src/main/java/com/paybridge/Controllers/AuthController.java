package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.*;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.ApiKeyService;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.VerificationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(path = "/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    private final VerificationService verificationService;

    private final MerchantRepository merchantRepository;

    private final ApiKeyService apiKeyService;

    public AuthController(AuthenticationService authenticationService, VerificationService verificationService, MerchantRepository merchantRepository, ApiKeyService apiKeyService) {
        this.authenticationService = authenticationService;
        this.verificationService = verificationService;
        this.merchantRepository = merchantRepository;
        this.apiKeyService = apiKeyService;
    }

    @PostMapping(value = "/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody @Valid LoginRequest loginRequest,
                                                     HttpServletResponse response){
        String token = authenticationService.login(loginRequest);

        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(3600)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        Merchant merchant = merchantRepository.findByEmail(loginRequest.getEmail()).orElseThrow();
        Map<String, Object> userData = authenticationService.userData(merchant);
        return ResponseEntity.ok(userData);
    }

    @PostMapping(value = "/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        ApiResponse<String> response = verificationService.verifyEmail(request.getEmail(), request.getCode());
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

        Optional<Merchant> merchant = merchantRepository.findByEmail(request.getEmail());

        if(merchant.isEmpty()){
            return ResponseEntity.status(400).body(ApiResponse.error("Merchant does not exist"));
        }
        merchant.get().setTestMode(true);
        // Persist testMode flag change first to avoid overwriting keys set in a separate transaction
        merchantRepository.save(merchant.get());
        // Generate both test and live API keys and persist their hashes as part of remediation
        apiKeyService.regenerateApiKey(merchant.get().getId(), true, true);

        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        try {
            verificationService.resendVerificationCode(request.getEmail());
            return ResponseEntity.ok().body(ApiResponse.success(Map.of(
                    "message", "Verification code sent successfully",
                    "success", true
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
