package com.paybridge.Models.Entities;

import com.paybridge.Models.Enums.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity(name = "idempotency_key")
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @Column(name = "request_hash", nullable = false)
    private String requestHash;
    @Column(name = "response")
    private String response;
    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private PaymentStatus paymentStatus;
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
