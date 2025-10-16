package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.*;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.VerificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest){
        LoginResponse response = authenticationService.login(loginRequest);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        VerifyEmailResponse response = verificationService.verifyEmail(request.getEmail(), request.getCode());
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
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
