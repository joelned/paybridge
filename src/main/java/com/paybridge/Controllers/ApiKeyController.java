package com.paybridge.Controllers;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Services.ApiKeyService;
import com.paybridge.Services.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/get-apikey")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private AuthenticationService authenticationService;


    @GetMapping
    public ResponseEntity<Map<String, Object>> getApiKey(Authentication authentication){
        Map<String, Object> response = new HashMap<>();

        Merchant merchant = authenticationService.getMerchantFromAuthentication(authentication);
        response.put("api_key_test", merchant.getApiKeyTest());
        response.put("api_key_live", merchant.getApiKeyLive());
        response.put("isTestMode", merchant.isTestMode());
        response.put("activeApiKey", merchant.getActiveKey());

        return ResponseEntity.ok(response);

    }
}
