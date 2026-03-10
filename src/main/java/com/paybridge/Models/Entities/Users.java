package com.paybridge.Models.Entities;

import com.paybridge.Models.Enums.UserType;
import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.security.MessageDigest;

@Entity
@Table(name = "users")
public class Users {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant; // null if ADMIN

    private boolean enabled = true;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verification_code_expires_at")
    private LocalDateTime verificationCodeExpiresAt;

    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts = 0;

    @Column(name = "last_verification_request_at")
    private LocalDateTime lastVerificationRequestAt;

    @Column(name = "password_reset_code")
    private String passwordResetCode;

    @Column(name = "password_reset_code_expires_at")
    private LocalDateTime passwordResetCodeExpiresAt;

    // --- Verification Logic ---

    public boolean isVerificationCodeValid(String code) {
        if (verificationCode == null || code == null || verificationCodeExpiresAt == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(verificationCodeExpiresAt)) {
            return false;
        }

        if (looksLikeHash(verificationCode)) {
            String candidateHash = sha256Hex(code);
            return MessageDigest.isEqual(
                    verificationCode.getBytes(StandardCharsets.UTF_8),
                    candidateHash.getBytes(StandardCharsets.UTF_8)
            );
        }

        // Backward compatibility for existing plaintext codes.
        return MessageDigest.isEqual(
                verificationCode.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8)
        );
    }

    public void markAsVerified() {
        this.emailVerified = true;
        this.verificationCode = null;
        this.verificationCodeExpiresAt = null;
        this.verificationAttempts = 0;
    }

    public String generateVerificationCode() {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        this.verificationCode = sha256Hex(code);
        this.verificationCodeExpiresAt = LocalDateTime.now().plusMinutes(10);
        this.lastVerificationRequestAt = LocalDateTime.now();
        this.verificationAttempts = 0;
        return code;
    }

    public void incrementVerificationAttempts() {
        this.verificationAttempts++;
    }

    public boolean canResendVerification() {
        return lastVerificationRequestAt == null ||
                LocalDateTime.now().isAfter(lastVerificationRequestAt.plusMinutes(5));
    }

    public String generatePasswordResetCode() {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        this.passwordResetCode = sha256Hex(code);
        this.passwordResetCodeExpiresAt = LocalDateTime.now().plusMinutes(15);
        return code;
    }

    public boolean isPasswordResetCodeValid(String code) {
        if (passwordResetCode == null || code == null) {
            return false;
        }

        if (passwordResetCodeExpiresAt == null || LocalDateTime.now().isAfter(passwordResetCodeExpiresAt)) {
            return false;
        }

        if (looksLikeHash(passwordResetCode)) {
            String candidateHash = sha256Hex(code);
            return MessageDigest.isEqual(
                    passwordResetCode.getBytes(StandardCharsets.UTF_8),
                    candidateHash.getBytes(StandardCharsets.UTF_8)
            );
        }

        // Backward compatibility for existing plaintext codes.
        return MessageDigest.isEqual(
                passwordResetCode.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8)
        );
    }

    public void clearPasswordResetCode() {
        this.passwordResetCode = null;
        this.passwordResetCodeExpiresAt = null;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static boolean looksLikeHash(String value) {
        return value != null && value.length() == 64 && value.matches("^[a-fA-F0-9]{64}$");
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserType getUserType() { return userType; }
    public void setUserType(UserType userType) { this.userType = userType; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public LocalDateTime getVerificationCodeExpiresAt() { return verificationCodeExpiresAt; }
    public void setVerificationCodeExpiresAt(LocalDateTime verificationCodeExpiresAt) { this.verificationCodeExpiresAt = verificationCodeExpiresAt; }

    public int getVerificationAttempts() { return verificationAttempts; }
    public void setVerificationAttempts(int verificationAttempts) { this.verificationAttempts = verificationAttempts; }

    public LocalDateTime getLastVerificationRequestAt() { return lastVerificationRequestAt; }
    public void setLastVerificationRequestAt(LocalDateTime lastVerificationRequestAt) { this.lastVerificationRequestAt = lastVerificationRequestAt; }

    public String getPasswordResetCode() {
        return passwordResetCode;
    }

    public void setPasswordResetCode(String passwordResetCode) {
        this.passwordResetCode = passwordResetCode;
    }

    public LocalDateTime getPasswordResetCodeExpiresAt() {
        return passwordResetCodeExpiresAt;
    }

    public void setPasswordResetCodeExpiresAt(LocalDateTime passwordResetCodeExpiresAt) {
        this.passwordResetCodeExpiresAt = passwordResetCodeExpiresAt;
    }

    // --- Utility ---
    public boolean isMerchant() { return userType == UserType.MERCHANT; }
    public boolean isAdmin() { return userType == UserType.ADMIN; }
}
