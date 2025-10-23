package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    boolean existsByEmail(String email);

    Merchant findByEmail(String email);

    Optional<Merchant> findByApiKeyLive(String apiKey);
    Optional<Merchant> findByApiKeyTest(String apiKey);
}

