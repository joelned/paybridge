package com.paybridge.Models.Entities;

import com.paybridge.Services.MapToJsonConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "provider_webhooks")
public class ProviderWebHook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_tx_id", nullable = false)
    private ProviderTransactionReference providerTxRef;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload_data", columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, Object> payloadData;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
