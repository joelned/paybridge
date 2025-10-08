package com.paybridge.Models.DTOs;

import com.paybridge.Models.Enums.MerchantStatus;

public class MerchantRegistrationResponse {
    private String businessName;
    private String email;
    private MerchantStatus status;
    private String message;
    private String nextStep;

    public MerchantRegistrationResponse(String businessName, String email, MerchantStatus status, String message, String nextStep) {
        this.businessName = businessName;
        this.email = email;
        this.status = status;
        this.message = message;
        this.nextStep = nextStep;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }
}
