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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest,
                                               HttpServletResponse response){
        LoginResponse loginResponse = authenticationService.login(loginRequest);

        ResponseCookie cookie = ResponseCookie.from("jwt", loginResponse.getToken())
                .httpOnly(true)
                .secure(true) // use HTTPS in production
                .path("/")
                .sameSite("None") // REQUIRED for cross-origin (CORS)
                .maxAge(3600) // 1 hour
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        LoginResponse sanitizedResponse = new LoginResponse(
                null, // don’t expose token
                loginResponse.getEmail(),
                loginResponse.getUserType(),
                loginResponse.getExpiresIn()
        );

        return ResponseEntity.ok(sanitizedResponse);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        VerifyEmailResponse response = verificationService.verifyEmail(request.getEmail(), request.getCode());
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

        Merchant merchant = merchantRepository.findByEmail(request.getEmail());
        merchant.setTestMode(true);
        merchant.setApiKeyTest(apiKeyService.generateApiKey(true));
        merchant.setApiKeyLive(apiKeyService.generateApiKey(false));
        merchantRepository.save(merchant);

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
