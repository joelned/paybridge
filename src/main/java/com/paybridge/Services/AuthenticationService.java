package com.paybridge.Services;

import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.DTOs.LoginResponse;
import com.paybridge.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;


    public AuthenticationService(AuthenticationManager authenticationManager
            , TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.getUsername(), request.getPassword()
        ));

        String jwtToken = tokenService.generateToken(authentication);
        return new LoginResponse(jwtToken, request.getUsername(), authentication.getAuthorities().toString(), "1 hour");
    }
}
