package com.paybridge.Repositories;

import com.paybridge.Models.Entities.ProviderConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {

    Optional<ProviderConfig> findByProviderIdAndMerchantId(Long providerId, Long merchantId);

    /**
     * Fetches all enabled ProviderConfigs for a merchant, JOIN-fetching the nested
     * {@code provider} in the same query to prevent an N+1 when
     * {@code providerConfig.getProvider()} is accessed later.
     */
    @EntityGraph("ProviderConfig.withProvider")
    List<ProviderConfig> findByMerchantIdAndIsEnabledTrue(Long merchantId);

    /**
     * Fetches a single enabled ProviderConfig by merchant + provider name,
     * JOIN-fetching {@code provider} to avoid a secondary query on access.
     */
    @EntityGraph("ProviderConfig.withProvider")
    Optional<ProviderConfig> findByMerchantIdAndProvider_NameIgnoreCaseAndIsEnabledTrue(Long merchantId, String providerName);

    /**
     * Fetches all ProviderConfigs for a merchant, JOIN-fetching {@code provider}.
     * Replaces the bare {@code findByMerchantId} used in listing endpoints so that
     * iterating over configs and reading {@code .getProvider()} stays at O(1) queries.
     */
    @EntityGraph("ProviderConfig.withProvider")
    List<ProviderConfig> findByMerchantId(Long merchantId);

    /**
     * Loads a single ProviderConfig by PK, eagerly JOIN-fetching both
     * {@code provider} and {@code merchant} in one round-trip.
     * Use this instead of {@code findById} wherever both associations are needed.
     */
    @Query("SELECT pc FROM ProviderConfig pc " +
           "JOIN FETCH pc.provider " +
           "JOIN FETCH pc.merchant " +
           "WHERE pc.id = :id")
    Optional<ProviderConfig> findByIdWithProvider(@Param("id") Long id);
}
