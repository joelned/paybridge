package com.paybridge.Services;

import com.paybridge.Models.Entities.PaymentEvent;
import com.paybridge.Repositories.PaymentRepository;
import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.RestController;

@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final ProviderOrchestrationService providerOrchestrationService;


    public PaymentService(PaymentRepository paymentRepository, IdempotencyService idempotencyService, ProviderOrchestrationService providerOrchestrationService) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.providerOrchestrationService = providerOrchestrationService;
    }


}
