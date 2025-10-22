package com.paybridge.Services;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.ApiKeyUsageRepository;
import com.paybridge.Repositories.MerchantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

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

    public Optional<Merchant> validateApiKey(String apiKey){
        if(apiKey == null || apiKey.isEmpty()){
            return Optional.empty();
        }

        if(apiKey.startsWith(TEST_PREFIX)){
            return merchantRepository.findByApiKeyTest(apiKey);
        }
        if(apiKey.startsWith(LIVE_PREFIX)){
            return merchantRepository.findByApiKeyLive(apiKey);
        }

        return Optional.empty();
    }

    public boolean isTestMode(String apiKey){
        return apiKey != null && apiKey.startsWith(TEST_PREFIX);
    }

    @Transactional
    public void regenerateApiKey(Long merchantId, boolean regenerateTest, boolean regenerateLive){
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(()-> new IllegalArgumentException("Merchant not found"));

        if(regenerateTest){
            merchant.setApiKeyTest(generateApiKey(true));
        }

        if(regenerateLive){
            merchant.setApiKeyLive(generateApiKey(false));
        }

        merchantRepository.save(merchant);
    }


}
