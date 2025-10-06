package com.paybridge.Models.Entities;

import com.paybridge.Services.MapToJsonConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name="webhook_payloads")
public class WebHookPayload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "payload_type", nullable = false)
    private String payloadType;

    @Column(name = "payload_data", columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, Object> payloadData;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Relationships
    @OneToMany(mappedBy = "webhookPayload", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WebHookDelivery> webhookDeliveries = new ArrayList<>();
}
