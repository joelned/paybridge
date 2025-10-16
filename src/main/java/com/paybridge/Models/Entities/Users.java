package com.paybridge.Models.Entities;

import com.paybridge.Models.Enums.UserType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Random;

@Entity
@Table(name = "users")
public class Users {

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

    // --- Verification Logic ---

    public boolean isVerificationCodeValid(String code) {
        return verificationCode != null &&
                verificationCode.equals(code) &&
                verificationCodeExpiresAt != null &&
                LocalDateTime.now().isBefore(verificationCodeExpiresAt);
    }

    public void markAsVerified() {
        this.emailVerified = true;
        this.verificationCode = null;
        this.verificationCodeExpiresAt = null;
        this.verificationAttempts = 0;
    }

    public void generateVerificationCode() {
        Random random = new Random();
        this.verificationCode = String.format("%06d", random.nextInt(1_000_000));
        this.verificationCodeExpiresAt = LocalDateTime.now().plusMinutes(15);
        this.lastVerificationRequestAt = LocalDateTime.now();
        this.verificationAttempts = 0;
    }

    public void incrementVerificationAttempts() {
        this.verificationAttempts++;
    }

    public boolean canResendVerification() {
        return lastVerificationRequestAt == null ||
                LocalDateTime.now().isAfter(lastVerificationRequestAt.plusMinutes(1));
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

    // --- Utility ---
    public boolean isMerchant() { return userType == UserType.MERCHANT; }
    public boolean isAdmin() { return userType == UserType.ADMIN; }
}
