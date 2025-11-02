package com.paybridge.Repositories;

import com.paybridge.Models.Entities.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Provider findByName(String name);
}
