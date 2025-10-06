package com.paybridge.Models.Entities;

import com.paybridge.Services.MapToJsonConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Column(name = "user_id")
    private String userId; // Could be merchant admin user ID

    @Column(nullable = false)
    private String action; // "CREATE", "UPDATE", "DELETE"

    @Column(name = "resource_type", nullable = false)
    private String resourceType; // "payment", "provider_config", etc.

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "old_values", columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, Object> oldValues;

    @Column(name = "new_values", columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, Object> newValues;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
