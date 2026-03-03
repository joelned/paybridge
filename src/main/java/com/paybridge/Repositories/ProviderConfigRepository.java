package com.paybridge.Repositories;

import com.paybridge.Models.Entities.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {
    Optional<ProviderConfig> findByProviderIdAndMerchantId(Long providerId, Long merchantId);
    List<ProviderConfig> findByMerchantIdAndIsEnabledTrue(Long merchantId);
    Optional<ProviderConfig> findByMerchantIdAndProvider_NameIgnoreCaseAndIsEnabledTrue(Long merchantId, String providerName);
    List<ProviderConfig> findByMerchantId(Long merchantId);
}
