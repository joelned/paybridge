package com.paybridge.Models.DTOs;

import com.paybridge.Models.Enums.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public class PaymentResponse {
   private UUID id;
   private PaymentStatus status;
   private String checkoutUrl;
   private String clientSecret;
   private Instant createdAt;
}
