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
                       int responseStatus, String method, String userAgent) {
        this.merchantId = merchantId;
        this.endpoint = endpoint;
        this.ipAddress = ipAddress;
        this.responseStatus = responseStatus;
        this.method = method;
        this.userAgent = userAgent;
        this.timeStamp = LocalDateTime.now();
    }

    public ApiKeyUsage() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }
}
