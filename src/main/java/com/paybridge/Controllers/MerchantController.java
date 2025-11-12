package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Services.MerchantService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    @Autowired
    private MerchantService merchantService;


    @PostMapping
    public ResponseEntity<ApiResponse<String>> registerMerchant(@RequestBody @Valid MerchantRegistrationRequest request){
            merchantService.registerMerchant(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Registration successful. Please check your email for verification code"));
    }
}
