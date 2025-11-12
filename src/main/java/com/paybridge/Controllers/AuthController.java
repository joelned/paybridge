package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.*;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.ApiKeyService;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.VerificationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @PostMapping(value = "/login")
    public ResponseEntity<ApiResponse<String>> login(@RequestBody @Valid LoginRequest loginRequest,
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
        return ResponseEntity.ok(ApiResponse.success("Login Successful"));
    }

    @PostMapping(value = "/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        ApiResponse<String> response = verificationService.verifyEmail(request.getEmail(), request.getCode());
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

        Merchant merchant = merchantRepository.findByEmail(request.getEmail());

        if(merchant == null){
            return ResponseEntity.status(400).body(ApiResponse.error("Merchant does not exist"));
        }
        merchant.setTestMode(true);
        // Persist testMode flag change first to avoid overwriting keys set in a separate transaction
        merchantRepository.save(merchant);
        // Generate both test and live API keys and persist their hashes as part of remediation
        apiKeyService.regenerateApiKey(merchant.getId(), true, true);

        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        try {
            verificationService.resendVerificationCode(request.getEmail());
            return ResponseEntity.ok().body(java.util.Map.of(
                    "message", "Verification code sent successfully",
                    "success", true
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "message", e.getMessage(),
                    "success", false
            ));
        }
    }
}
