package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    boolean existsByEmail(String email);

    Optional<String> findByApiKeyLive(String apiKey);
    Optional<String> findByApiKeyTest(String apiKey);
}

