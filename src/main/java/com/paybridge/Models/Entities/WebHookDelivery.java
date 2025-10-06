package com.paybridge.Models.Entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_deliveries")
public class WebHookDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_payload_id", nullable = false)
    private WebHookPayload webhookPayload;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;


}
