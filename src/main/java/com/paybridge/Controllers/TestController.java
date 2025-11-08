package com.paybridge.Controllers;

import com.paybridge.Services.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TestController {

    @Autowired
    private AuthenticationService authenticationService;

    @GetMapping("/test-controller")
    public String TestController() {
       return "This is an authenticated endpoint";
    }
}
