package com.paybridge.Repositories;

import com.paybridge.Models.Entities.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {
    Optional<ProviderConfig> findByProviderIdAndMerchantId(Long providerId, Long merchantId);
}
