package com.paybridge.Controllers;

import com.paybridge.Models.DTOs.ErrorResponse;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationResponse;
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
    public ResponseEntity<Object> registerMerchant(@RequestBody @Valid MerchantRegistrationRequest request){
        try{
            MerchantRegistrationResponse response = merchantService.registerMerchant(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex){
            ErrorResponse response = new ErrorResponse
                    (400, ex.getMessage(), "Use a different email", "/api/v1/merchants");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
