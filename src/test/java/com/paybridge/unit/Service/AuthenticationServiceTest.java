package com.paybridge.unit.Service;

import com.paybridge.Exceptions.EmailNotVerifiedException;
import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.TokenService;
import com.paybridge.Services.VerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationService verificationService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void login_Success_ReturnsToken() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");

        Users user = new Users();
        user.setEmail("test@example.com");
        user.setEmailVerified(true);

        Authentication authentication = mock(Authentication.class);

        when(userRepository.findByEmail(request.getEmail())).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenService.generateToken(authentication)).thenReturn("jwt-token");

        // When
        String token = authenticationService.login(request);

        // Then
        assertThat(token).isEqualTo("jwt-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_EmailNotVerified_ThrowsException() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("unverified@example.com");
        request.setPassword("password");

        Users user = new Users();
        user.setEmail("unverified@example.com");
        user.setEmailVerified(false);

        when(userRepository.findByEmail(request.getEmail())).thenReturn(user);

        // When & Then
        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessage("Please verify your email before logging in");

        verify(verificationService).resendVerificationCode(request.getEmail());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_BadCredentials_ThrowsException() {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrong-password");

        Users user = new Users();
        user.setEmail("test@example.com");
        user.setEmailVerified(true);

        when(userRepository.findByEmail(request.getEmail())).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // When & Then
        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void userData_ReturnsCorrectMap() {
        // Given
        Merchant merchant = new Merchant();
        merchant.setEmail("merchant@example.com");
        merchant.setBusinessName("Test Corp");

        // When
        Map<String, Object> data = authenticationService.userData(merchant);

        // Then
        assertThat(data).containsEntry("email", "merchant@example.com");
        assertThat(data).containsEntry("businessName", "Test Corp");
        assertThat(data).containsEntry("userType", UserType.MERCHANT);
    }
}
