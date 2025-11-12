package com.paybridge.Services;

import java.util.Map;

public interface CredentialStorageService {
    void saveProviderConfig(String providerName, Long merchantId, Map<String, Object> config);
    Map<String, Object> getProviderConfig(String providerName, Long merchantId);
    void removeProviderConfig(String providerName, Long merchantId);
    boolean providerConfigExists(String providerName, Long merchantId);
    void updateProviderConfigProperty(String providerName, Long merchantId, String fieldName, Object fieldValue);

}
