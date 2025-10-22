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

    @Column(name = "merchand_id")
    private Long merchantId;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "response_status")
    private int responseStatus;

    @Column(name = "method")
    private String method;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "time_stamp")
    private LocalDateTime timeStamp;


    public ApiKeyUsage(Long merchantId, String endpoint, String ipAddress,
                       int responseStatus, String method, String userAgent, LocalDateTime timeStamp) {
        this.merchantId = merchantId;
        this.endpoint = endpoint;
        this.ipAddress = ipAddress;
        this.responseStatus = responseStatus;
        this.method = method;
        this.userAgent = userAgent;
        this.timeStamp = timeStamp;
    }
}
