package com.paybridge.Models.Entities;

import com.paybridge.Models.Enums.MerchantStatus;
import com.stripe.model.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="merchants")
public class Merchant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String businessType;

    @Column(nullable = false)
    private String businessCountry;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "webhook_secret")
    private String webhookSecret;

    @Column(name = "website_url")
    private String websiteUrl;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name="api_key_test")
    private String apiKeyTest;

    @Column(name="api_key_live")
    private String apiKeyLive;

    // New hashed-api-key columns (Option B rollout)
    @Column(name = "api_key_test_hash")
    private String apiKeyTestHash;

    @Column(name = "api_key_live_hash")
    private String apiKeyLiveHash;

    @Column(name="test_mode")
    private boolean testMode = true;


    public String getActiveKey(){
        return testMode ? apiKeyTest : apiKeyLive;
    }

    public String getApiKeyTestHash() { return apiKeyTestHash; }
    public void setApiKeyTestHash(String apiKeyTestHash) { this.apiKeyTestHash = apiKeyTestHash; }
    public String getApiKeyLiveHash() { return apiKeyLiveHash; }
    public void setApiKeyLiveHash(String apiKeyLiveHash) { this.apiKeyLiveHash = apiKeyLiveHash; }



    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProviderConfig> providerConfigs = new ArrayList<>();


    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Users> users = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<ProviderConfig> getProviderConfigs() {
        return providerConfigs;
    }


    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public List<Users> getUsers() {
        return users;
    }

    public void setUsers(List<Users> users) {
        this.users = users;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getApiKeyTest() {
        return apiKeyTest;
    }

    public void setApiKeyTest(String apiKeyTest) {
        this.apiKeyTest = apiKeyTest;
    }

    public String getApiKeyLive() {
        return apiKeyLive;
    }

    public void setApiKeyLive(String apiKeyLive) {
        this.apiKeyLive = apiKeyLive;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public String getBusinessCountry() {
        return businessCountry;
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public void setBusinessCountry(String businessCountry) {
        this.businessCountry = businessCountry;
    }
    public String getBusinessName() {
        return businessName;
    }

    public String getEmail() {
        return email;
    }


    public MerchantStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public void setStatus(MerchantStatus status) {
        this.status = status;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }

    public void setProviderConfigs(List<ProviderConfig> providerConfigs) {
        this.providerConfigs = providerConfigs;
    }

    public String getWebHookUrl() {
        return webhookUrl;
    }

    public void setWebHookUrl(String webHookUrl) {
        this.webhookUrl = webHookUrl;
    }
}
