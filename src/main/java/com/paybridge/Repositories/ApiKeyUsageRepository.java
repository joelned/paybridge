package com.paybridge.Repositories;

import com.paybridge.Models.Entities.ApiKeyUsage;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface ApiKeyUsageRepository extends JpaRepository<ApiKeyUsage, Integer> {

    @Query("SELECT COUNT(a) FROM ApiKeyUsage a WHERE a.merchantId = :merchantId AND a.timeStamp >= :since")
    long countByMerchantSince(@Param("merchantId") Long merchantId, @Param("since")LocalDateTime since);
}
