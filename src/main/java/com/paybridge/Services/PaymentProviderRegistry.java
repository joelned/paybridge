package com.paybridge.Services;

import com.paybridge.Configs.PaymentProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentProviderRegistry {

   private final Map<String, PaymentProvider> paymentProviders;

    public PaymentProviderRegistry(List<PaymentProvider> paymentProviders){
        this.paymentProviders = paymentProviders.stream()
                .collect(Collectors.toMap(PaymentProvider::getProviderName, Function.identity()));
    }

    public PaymentProvider getProvider(String name){
       return Optional.ofNullable(paymentProviders.get(name.toLowerCase()))
               .orElseThrow(() -> new IllegalArgumentException("Unsupported Provider: " + name));
    }

    public List<String> getActiveProviders(){
        return new ArrayList<>(paymentProviders.keySet());
    }
}
