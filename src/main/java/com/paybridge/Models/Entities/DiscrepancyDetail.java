package com.paybridge.Models.Entities;

import jakarta.persistence.*;

@Entity
@Table(name = "discrepancy_details")
public class DiscrepancyDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discrepancy_id", nullable = false)
    private ReconciliationDiscrepancy discrepancy;

    @Column(name = "field_name", nullable = false)
    private String fieldName; // "amount", "status", "currency"

    @Column(name = "paybridge_value")
    private String paybridgeValue;

    @Column(name = "provider_value")
    private String providerValue;

    @Column(name = "difference")
    private String difference;
}
