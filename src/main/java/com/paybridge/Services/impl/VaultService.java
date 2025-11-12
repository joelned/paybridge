package com.paybridge.Services.impl;

import com.paybridge.Services.CredentialStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.util.HashMap;
import java.util.Map;

@Service
@Profile("vault")
public class VaultService implements CredentialStorageService {

    @Autowired
    private VaultTemplate vaultTemplate;

    private static final String PROVIDER_PATH_PREFIX = "secret/data/paybridge/providers";

    @Override
    public void saveProviderConfig (String providerName, Long merchantId, Map<String, Object> config){
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

    @Override
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

    @Override
    public void removeProviderConfig(String providerName, Long merchantId){
        String path = buildProviderPath(providerName, merchantId);

        try{
            vaultTemplate.delete(path);
        }
        catch(Exception ex){
            throw new RuntimeException("Failed to delete provider configuration from Vault", ex);
        }
    }

    @Override
    public boolean providerConfigExists (String providerName, Long merchantId){
        String path = buildProviderPath(providerName, merchantId);

        try{
            VaultResponseSupport<Map> response = vaultTemplate.read(path, Map.class);
            return response != null && response.getData() != null;
        }
        catch(Exception ex){
            return false;
        }
    }

    @Override
    public void updateProviderConfigProperty (String providerName, Long merchantId,
                                             String fieldName, Object fieldValue) {
        Map<String, Object> currentConfig = getProviderConfig(providerName, merchantId);
        currentConfig.put(fieldName, fieldValue);
        saveProviderConfig(providerName, merchantId, currentConfig);
    }

    private String buildProviderPath(String providerName, Long merchantId){
        return String.format("%s/%s/merchant-%d", PROVIDER_PATH_PREFIX, providerName.toLowerCase(), merchantId);
    }
}
