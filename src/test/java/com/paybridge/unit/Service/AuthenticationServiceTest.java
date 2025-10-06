package com.paybridge.unit.Service;

import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.DTOs.LoginResponse;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static org.assertj.core.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                authenticationManager, tokenService
        );
    }

    @Test
    void login_withValidCredentials_shouldReturnJwtToken() {
        // Given
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(tokenService.generateToken(auth)).thenReturn("mock.jwt.token");

        // When
        LoginResponse response = authenticationService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getEmail()).isEqualTo("user@test.com");
        assertThat(response.getExpiresIn()).isEqualTo("1 hour");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_withInvalidCredentials_shouldFail(){
        LoginRequest request = new LoginRequest("abcd@gmail.com", "wrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenThrow(
                new BadCredentialsException("Bad Credentials")
        );

        assertThatThrownBy(
                () -> authenticationService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Bad Credentials");

        verifyNoInteractions(tokenService);
    }

}