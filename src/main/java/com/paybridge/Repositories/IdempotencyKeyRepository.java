package com.paybridge.Repositories;

import com.paybridge.Models.Entities.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Integer> {
}
