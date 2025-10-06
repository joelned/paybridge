package com.paybridge.Models.Entities;


import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "provider_tx_refs")
public class ProviderTransactionReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "reference_id", nullable = false)
    private String referenceId; // Provider's payment ID

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private String status; // Provider's status representation

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "providerTxRef", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProviderWebHook> providerWebhooks = new ArrayList<>();
}
