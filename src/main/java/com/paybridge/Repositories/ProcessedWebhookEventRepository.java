package com.paybridge.Repositories;

import com.paybridge.Models.Entities.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {
    boolean existsByProviderAndEventId(String provider, String eventId);
}
