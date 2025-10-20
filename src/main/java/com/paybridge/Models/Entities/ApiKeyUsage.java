package com.paybridge.Models.Entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_key_usage")
public class ApiKeyUsage {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )

    private Integer id;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "response_status")
    private int responseStatus;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
