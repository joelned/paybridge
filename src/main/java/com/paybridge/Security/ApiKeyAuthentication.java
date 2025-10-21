package com.paybridge.Security;

import com.paybridge.Models.Entities.Merchant;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;

public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final String apiKey;
    private final boolean isTestMode;
    private final Merchant merchant;


    public ApiKeyAuthentication(Collection<? extends GrantedAuthority> authorities, String apiKey, boolean isTestMode, Merchant merchant) {
        super(Collections.singletonList(new SimpleGrantedAuthority("API_KEY")));
        this.apiKey = apiKey;
        this.isTestMode = isTestMode;
        this.merchant = merchant;
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return merchant.getEmail();
    }

    public boolean isTestMode(){
        return isTestMode;
    }

    public Merchant getMerchant(){
        return merchant;
    }

    public Long getMerchantId(){
        return merchant.getId();
    }
}

