package com.paybridge.unit.Service;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Provider;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Models.Entities.User;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.MerchantService;
import com.paybridge.Services.RoleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

@ExtendWith(MockitoExtension.class)
public class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleAssignmentService roleAssignmentService;
    @Captor
    private ArgumentCaptor<Merchant> merchantCaptor;

    private MerchantService merchantService;

    @BeforeEach
    void setUp(){
        merchantService = new MerchantService(roleAssignmentService,merchantRepository, passwordEncoder);
    }

    @Test
    void registerMerchant_withValidRequest_shouldCreateMerchant(){
       MerchantRegistrationRequest request = createValidRegistrationRequest();

    }

    private MerchantRegistrationRequest createValidRegistrationRequest() {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest();
        request.setEmail("billing@company.com");
        request.setPassword("securePassword123!");
        request.setName("Tech Solutions LLC");
        return request;

    }

    private User createUser() {
        User user = new User();
        user.setEmail("email@test.com");
        user.setPassword("encrypted_password");

        return user;
    }


    private ProviderConfig createMockProviderConfig(){
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setProvider(createMockProvider());

        Map<String, Object> config = new HashMap<>();
        config.put("secret-key", "sienwafdj");
        config.put("private-key", "sdfdsfruwe");

        providerConfig.setConfig(config);
        providerConfig.setEnabled(true);

        return providerConfig;
    }

    private Provider createMockProvider(){
       Provider flutterwave = new Provider();
       flutterwave.setName("Flutterwave");
       flutterwave.setBrandColor("#65355");
       flutterwave.setDisplayName("Flutterwave");

       return flutterwave;
    }
}
