package com.paybridge.Models.Entities;

import com.paybridge.Models.Enums.DiscrepancyType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reconciliation_discrepancy")
public class ReconciliationDiscrepancy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ReconciliationJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false)
    private DiscrepancyType discrepancyType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_tx_id")
    private ProviderTransactionReference providerTxRef;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "discrepancy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DiscrepancyDetail> details = new ArrayList<>();
}
