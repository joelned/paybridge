package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @EntityGraph(attributePaths = {"provider", "merchant"})
    Optional<Payment> findByProviderReferenceAndProvider_NameIgnoreCase(String providerReference, String providerName);

    @EntityGraph(attributePaths = {"provider"})
    List<Payment> findByMerchant_IdAndCreatedAtGreaterThanEqual(Long merchantId, LocalDateTime fromDate);
}
