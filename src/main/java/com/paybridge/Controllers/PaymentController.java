package com.paybridge.Controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/payments")
public class PaymentController {

    @PostMapping
    public ResponseEntity<Object> createPayment(){

        return new ResponseEntity<>("Payment Created", HttpStatus.OK);
    }

}

