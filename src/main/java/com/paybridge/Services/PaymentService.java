package com.paybridge.Services;

import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.IdempotencyKey;
import com.paybridge.Repositories.IdempotencyKeyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    private final PasswordEncoder passwordEncoder;

    public PaymentService(IdempotencyKeyRepository idempotencyKeyRepository, PasswordEncoder passwordEncoder) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PaymentResponse createPayment(CreatePaymentRequest paymentRequest, String idempotencyKey){
        Optional<IdempotencyKey> optionalIdempotencyKey = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if(optionalIdempotencyKey.isEmpty()){
            IdempotencyKey idempotencyKey1 = new IdempotencyKey();
            idempotencyKey1.setCreatedAt(LocalDateTime.now());
            idempotencyKey1.setLocked(true);
            idempotencyKey1.setRequestHash(passwordEncoder.encode(paymentRequest.toString()));
            idempotencyKey1.setExpiresAt(LocalDateTime.now().plusDays(1));

        }
        return null;
    }
}
