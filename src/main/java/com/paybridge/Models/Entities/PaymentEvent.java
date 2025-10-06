package com.paybridge.Models.Entities;

import com.paybridge.Models.Enums.PaymentEventType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name="payment_event")
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private PaymentEventType eventType;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String description;
}
