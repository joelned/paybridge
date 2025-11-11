package com.paybridge.Models.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public class CreatePaymentRequest {

    @NotNull private BigDecimal amount;
    @NotNull @Size(min = 3, max = 3) private String currency;
    @Email private String email;
    @NotBlank private String description;
    private Map<String, String> metadata;
}
