package com.paybridge.Services;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationResponse;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MerchantService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;


    public MerchantService(UserRepository userRepository, MerchantRepository merchantRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public MerchantRegistrationResponse registerMerchant(MerchantRegistrationRequest request){
        boolean merchantExists = merchantRepository.existsByEmail(request.getEmail());
        if(merchantExists){
            throw new RuntimeException("Merchant already exists");
        }
        Merchant merchant = createMerchant(request);
        Users user = createMerchantUser(merchant, request);

        userRepository.save(user);
        merchantRepository.save(merchant);

       emailService.sendVerificationEmail(user.getEmail(), user.getVerificationCode(), user.getMerchant().getBusinessName());

        return new MerchantRegistrationResponse(
                merchant.getBusinessName(), merchant.getEmail(), merchant.getStatus(),
                "Registration successful. Please check your email for verification code.",
                "Verify your email to activate your account"
        );
    }

    public Merchant createMerchant(MerchantRegistrationRequest request){

        Merchant merchant = new Merchant();
        merchant.setBusinessType(request.getBusinessType());
        merchant.setBusinessCountry(request.getBusinessCountry());
        merchant.setBusinessName(request.getBusinessName());
        merchant.setWebsiteUrl(request.getWebsiteUrl());
        merchant.setCreatedAt(LocalDateTime.now());
        merchant.setUpdatedAt(LocalDateTime.now());
        merchant.setEmail(request.getEmail());
        merchant.setStatus(MerchantStatus.PENDING_PROVIDER_SETUP);
        return merchant;
    }

    public Users createMerchantUser(Merchant merchant, MerchantRegistrationRequest request){

        Users users = new Users();
        users.setMerchant(merchant);
        users.setUserType(UserType.MERCHANT);
        users.setEmail(request.getEmail());
        users.setEnabled(true);
        users.setPassword(passwordEncoder.encode(request.getPassword()));
        users.generateVerificationCode();
        return users;
    }

}
