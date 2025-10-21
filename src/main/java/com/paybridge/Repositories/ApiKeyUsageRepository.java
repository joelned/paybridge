package com.paybridge.Repositories;

import com.paybridge.Models.Entities.ApiKeyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyUsageRepository extends JpaRepository<ApiKeyUsage, Integer> {
}
