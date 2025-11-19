package com.paybridge.Repositories;

import com.paybridge.Models.Entities.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Integer> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
