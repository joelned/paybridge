package com.paybridge.Services;

import com.paybridge.Repositories.ApiKeyUsageRepository;
import com.paybridge.Repositories.MerchantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyService {

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ApiKeyUsageRepository apiKeyUsageRepository;

    private static final String TEST_PREFIX = "pk_test_";
    private static final String LIVE_PREFIX = "pk_live_";

    public String generateApiKey(boolean isTestMode){
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return isTestMode ? TEST_PREFIX : LIVE_PREFIX + key;
    }
}
