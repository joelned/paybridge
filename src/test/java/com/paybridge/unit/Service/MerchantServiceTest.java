package com.paybridge.unit.Service;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationResponse;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
public class MerchantServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MerchantService merchantService;

    @BeforeEach
        void setUp(){
            merchantService = new MerchantService(userRepository, merchantRepository, passwordEncoder);
        }

    @Test
    void register_nonDuplicateMerchant_Successfully(){
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "businessName1", "business@gmail.com", "password1"
                ,"ecommerce", "NG", "https://website.com"
        );

        when(merchantRepository.existsByEmail(request.getEmail())).thenReturn(false);


        Merchant merchant = createMockMerchant();
        Users users = createMockMerchantUser();

        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant);
        when(userRepository.save(any(Users.class))).thenReturn(users);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");

        MerchantRegistrationResponse response = merchantService.registerMerchant(request);

        assertThat(response).isNotNull();
        assertThat(response.getBusinessName()).isEqualTo("businessName1");
        assertThat(response.getEmail()).isEqualTo("business@gmail.com");
        assertThat(response.getStatus()).isEqualTo(MerchantStatus.PENDING_PROVIDER_SETUP);
        assertThat(users.getUserType()).isEqualTo(UserType.MERCHANT);

        verify(merchantRepository).save(any(Merchant.class));
        verify(userRepository).save(any(Users.class));
        verify(passwordEncoder).encode(request.getPassword());

    }

    Merchant createMockMerchant(){
       Merchant merchant = new Merchant();
       merchant.setStatus(MerchantStatus.PENDING_PROVIDER_SETUP);
       merchant.setBusinessName("businessName1");
       merchant.setEmail("business@gmail.com");
       merchant.setCreatedAt(LocalDateTime.now());
       merchant.setBusinessCountry("NG");
       merchant.setBusinessType("ecommerce");
       merchant.setWebsiteUrl("https://website.com");
       merchant.setUpdatedAt(LocalDateTime.now());
       return merchant;
    }

    Users createMockMerchantUser(){
        Users users = new Users();
        users.setEmail("business@gmail.com");
        users.setUserType(UserType.MERCHANT);
        users.setEnabled(true);
        users.setPassword("password1");
        users.setMerchant(createMockMerchant());

        return users;
    }


}
