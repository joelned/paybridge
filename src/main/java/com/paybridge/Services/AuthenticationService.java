package com.paybridge.Services;

import com.paybridge.Exceptions.EmailNotVerifiedException;
import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthenticationService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationService verificationService;

    public AuthenticationService(AuthenticationManager authenticationManager
            , TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public String login(LoginRequest request) {
        Users user = userRepository.findByEmail(request.getEmail());
        if (user != null && !user.isEmailVerified()) {
            verificationService.resendVerificationCode(user.getEmail());
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword()
        ));

        return tokenService.generateToken(authentication);
    }
    public Merchant getMerchantFromAuthentication(Authentication authentication){
        if (authentication == null) {
            return null;
        }

        String email = authentication.getName();
        Users user = userRepository.findByEmail(email);

        System.out.println("🔍 Found user: " + user);

        if(user == null){
            throw new IllegalArgumentException("User not found");
        }

        Merchant merchant = user.getMerchant();

        if(merchant == null){
            throw new IllegalArgumentException("No merchant associated with user");
        }

        return merchant;
    }

    public Map<String, Object> userData(Merchant merchant){
        Map<String, Object> merchantUserData = new LinkedHashMap<>();
        merchantUserData.put("email", merchant.getEmail());
        merchantUserData.put("businessName", merchant.getBusinessName());
        merchantUserData.put("userType", UserType.MERCHANT);
        return merchantUserData;
    }

}
