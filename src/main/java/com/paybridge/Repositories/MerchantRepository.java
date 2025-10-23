package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Merchant;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    boolean existsByEmail(String email);

    Merchant findByEmail(String email);

    @Query("SELECT m FROM Merchant m WHERE m.apiKeyTest = :apiKey OR m.apiKeyLive = :apiKey")
    Optional<Merchant> findByApiKeyTestOrApiKeyLive(@Param("apiKey") String apiKey);
}

