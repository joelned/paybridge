package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Merchant;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    boolean existsByEmail(String email);

    Optional<Merchant> findByEmail(String email);

    @Query("SELECT m FROM Merchant m WHERE m.apiKeyTest = :apiKey OR m.apiKeyLive = :apiKey")
    Optional<Merchant> findByApiKeyTestOrApiKeyLive(@Param("apiKey") String apiKey);

    @Query("SELECT m FROM Merchant m WHERE m.apiKeyTestHash = :apiKeyHash OR m.apiKeyLiveHash = :apiKeyHash")
    Optional<Merchant> findByApiKeyHash(@Param("apiKeyHash") String apiKeyHash);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
            "FROM Users u WHERE u.merchant.id = :merchantId " +
            "AND u.userType = com.paybridge.Models.Enums.UserType.MERCHANT " +
            "AND u.enabled = true")
    @Cacheable(cacheNames = "merchantEnabledUser", key = "#merchantId")
    boolean hasMerchantEnabledUser(@Param("merchantId") Long merchantId);
}

