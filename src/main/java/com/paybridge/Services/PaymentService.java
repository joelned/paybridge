package com.paybridge.Services;

import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.IdempotencyKey;
import com.paybridge.Repositories.IdempotencyKeyRepository;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.PaymentRepository;
import com.paybridge.Repositories.ProviderRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    private final PasswordEncoder passwordEncoder;

    private final PaymentRepository paymentRepository;

    private final MerchantRepository merchantRepository;

    private final ProviderRepository providerRepository;
    public PaymentService(IdempotencyKeyRepository idempotencyKeyRepository, PasswordEncoder passwordEncoder, PaymentRepository paymentRepository, MerchantRepository merchantRepository, ProviderRepository providerRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.paymentRepository = paymentRepository;
        this.merchantRepository = merchantRepository;
        this.providerRepository = providerRepository;
    }

}
