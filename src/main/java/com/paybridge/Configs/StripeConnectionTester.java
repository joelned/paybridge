package com.paybridge.Configs;

import com.paybridge.Interfaces.ConnectionTester;
import com.paybridge.Services.ConnectionTestResult;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.Account;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StripeConnectionTester implements ConnectionTester {

    private static final Logger logger = LoggerFactory.getLogger(StripeConnectionTester.class);

    /**
     * Test Stripe API connection by sending request with secret key
     */
    public ConnectionTestResult testConnection(Map<String, Object> credentials) {
        String stripeApiKey= (String) credentials.get("secretKey");

        StripeClient stripeClient = new StripeClient(stripeApiKey);
        CustomerCreateParams params = CustomerCreateParams
                .builder()
                .setDescription("Payment Description")
                .setEmail("test@gmail.comj")
                .build();
        try{
            Customer customer = stripeClient.v1().customers().create(params);
            System.out.println(customer);
            return ConnectionTestResult.success("Stripe test connection successful");
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

}