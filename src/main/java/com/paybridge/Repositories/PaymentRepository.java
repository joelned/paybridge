package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByProviderReferenceAndProvider_NameIgnoreCase(String providerReference, String providerName);
}
