package com.paybridge.Models.Entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_summaries")
public class JobSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ReconciliationJob job;

    @Column(name = "metric_type", nullable = false)
    private String metricType; // "total_count", "total_volume", "success_rate"

    @Column(name = "metric_value", nullable = false)
    private String metricValue;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
