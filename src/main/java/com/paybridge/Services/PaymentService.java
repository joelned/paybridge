package com.paybridge.Services;

import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentResponse;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public PaymentResponse createPayment(CreatePaymentRequest paymentRequest){
        PaymentResponse response = new PaymentResponse();

        return response;
    }
}
