package com.paybridge.Configs;

import com.paybridge.Services.ConnectionTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class PaystackConnectionTester implements ConnectionTester {
    private static final Logger logger = LoggerFactory.getLogger(PaystackConnectionTester.class);
    private static final String PAYSTACK_TOKEN_URL = "https://api.paystack.co/";

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ConnectionTestResult testConnection(Map<String, Object> credentials) {
       String secretKey = (String) credentials.get("secretKey");
       if(secretKey == null || secretKey.isEmpty()){
           return ConnectionTestResult.failure("Secret key is required");
       }
       try{
           HttpHeaders httpHeaders = new HttpHeaders();
           httpHeaders.add("Authorization", "Bearer " + secretKey);

           HttpEntity<String> httpEntity = new HttpEntity<>(httpHeaders);

           ResponseEntity<String> restResponse= restTemplate.exchange(
                   PAYSTACK_TOKEN_URL,
                   HttpMethod.GET,
                   httpEntity,
                   String.class

           );
           if(restResponse.getStatusCode() == HttpStatus.OK && restResponse.getBody() != null){
               return ConnectionTestResult.success("Paystack configuration test successful");
           }
           else{
               return ConnectionTestResult.failure("Unexpected response " + restResponse.getStatusCode());
           }
       }catch(Exception ex){
            return ConnectionTestResult.failure(ex.getMessage());
       }
    }
}
