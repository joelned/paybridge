package com.paybridge.Services;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.MerchantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MerchantService {

    private final RoleAssignmentService roleAssignmentService;

    private final MerchantRepository merchantRepository;

    private final PasswordEncoder passwordEncoder;


    public MerchantService(RoleAssignmentService roleAssignmentService, MerchantRepository merchantRepository, PasswordEncoder passwordEncoder) {
        this.roleAssignmentService = roleAssignmentService;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
    }
}
