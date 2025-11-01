package com.paybridge.Models.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MerchantRegistrationRequest {

    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 50, message = "Business name must be between 2 and 50 characters")
    private String businessName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,64}$",
            message = "Password must contain uppercase, lowercase, number, special character, and be 8-64 characters")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    private String password;

    @NotBlank(message = "Business type is required")
    private String businessType;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters (e.g., NG, US)")
    private String businessCountry;

    @Pattern(regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$",
            message = "Invalid website URL")
    private String websiteUrl;

    public MerchantRegistrationRequest(String businessName, String email, String password,
                                       String businessType, String country, String websiteUrl) {
        this.businessName = businessName;
        this.email = email;
        this.password = password;
        this.businessType = businessType;
        this.businessCountry = country;
        this.websiteUrl = websiteUrl;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getBusinessCountry() {
        return businessCountry;
    }

    public void setBusinessCountry(String businessCountry) {
        this.businessCountry = businessCountry;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getBusinessName() {
        return businessName;
    }

    public MerchantRegistrationRequest() {
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }


    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
