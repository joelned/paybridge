package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.*;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.PasswordResetService;
import com.paybridge.Services.VerificationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    private final VerificationService verificationService;

    private final PasswordResetService passwordResetService;

    private final MerchantRepository merchantRepository;

    public AuthController(AuthenticationService authenticationService,
                          VerificationService verificationService,
                          PasswordResetService passwordResetService,
                          MerchantRepository merchantRepository) {
        this.authenticationService = authenticationService;
        this.verificationService = verificationService;
        this.passwordResetService = passwordResetService;
        this.merchantRepository = merchantRepository;
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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(Authentication authentication) {
        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        Map<String, Object> userData = authenticationService.userData(merchant);
        return ResponseEntity.ok(ApiResponse.success(userData));
    }

    @PostMapping(value = "/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        ApiResponse<String> response = verificationService.verifyEmailAndActivateMerchant(request.getEmail(), request.getCode());
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
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

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        ApiResponse<String> response = passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        ApiResponse<String> response = passwordResetService.resetPassword(request);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(HttpServletResponse response) {
        ResponseCookie clearCookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "Logged out successfully",
                "success", true
        )));
    }
}
