package com.paybridge.Models.DTOs;

public class LoginResponse {
    String token;
    String email;
    String userType;
    String expiresIn;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getExpiresIn() {
        return expiresIn;
    }

    public LoginResponse(String token, String email, String userType, String expiresIn) {
        this.token = token;
        this.email = email;
        this.userType = userType;
        this.expiresIn = expiresIn;
    }

    public void setExpiresIn(String expiresIn) {
        this.expiresIn = expiresIn;
    }
}
