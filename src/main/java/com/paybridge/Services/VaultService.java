package com.paybridge.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.util.HashMap;
import java.util.Map;

@Service
public class VaultService {

    @Autowired
    private VaultTemplate vaultTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PROVIDER_PATH_PREFIX = "secret/data/paybridge/providers";

    public void storeProviderConfig(String providerName, Long merchantId, Map<String, Object> config){
        String path = buildProviderPath(providerName, merchantId);

        try{
            Map<String, Object> vaultData = new HashMap<>();
            vaultData.put("data", config);

            vaultTemplate.write(path, vaultData);
        }
        catch(Exception ex){
            throw new RuntimeException("Failed to store provider config in Vault", ex);
        }
    }

    public Map<String, Object> getProviderConfig(String providerName, Long merchantId){
        String path = buildProviderPath(providerName, merchantId);

        try{
            VaultResponseSupport<Map> response = vaultTemplate.read(path, Map.class);
            if(response == null || response.getData() == null){
                throw new RuntimeException("Provider configuration not found in Vault");
            }
            return (Map<String, Object>) response.getData().get("data");

        } catch (Exception ex) {
            throw new RuntimeException("Provider configuration not found in Vault", ex);
        }
    }

    public void deleteProviderConfig(String providerName, Long merchantId){
        String path = buildProviderPath(providerName, merchantId);

        try{
            vaultTemplate.delete(path);
        }
        catch(Exception ex){
            throw new RuntimeException("Failed to delete provider configuration from Vault", ex);
        }
    }

    public boolean configExists(String providerName, Long merchantId){
        String path = buildProviderPath(providerName, merchantId);

        try{
            VaultResponseSupport<Map> response = vaultTemplate.read(path, Map.class);
            return response != null && response.getData() != null;
        }
        catch(Exception ex){
            return false;
        }
    }

    public void updateProviderConfigField(String providerName, Long userId,
                                          String fieldName, Object fieldValue) {
        Map<String, Object> currentConfig = getProviderConfig(providerName, userId);
        currentConfig.put(fieldName, fieldValue);
        storeProviderConfig(providerName, userId, currentConfig);
    }

    private String buildProviderPath(String providerName, Long merchantId){
        return String.format("%s/%s/merchant-%d", PROVIDER_PATH_PREFIX, providerName.toLowerCase(), merchantId);
    }
}
