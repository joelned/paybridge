package com.paybridge.unit.Service;

import com.paybridge.Services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private Authentication authentication;

    private TokenService tokenService;

    private final String testUsername = "test@example.com";
    private final String expectedToken = "test.jwt.token";

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(jwtEncoder);
    }

    @Test
    void generateToken_Success_SingleAuthority() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MERCHANT")
        );

        when(authentication.getName()).thenReturn(testUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        Jwt mockJwt = createMockJwt(expectedToken);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // Act
        String result = tokenService.generateToken(authentication);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result);
        verify(jwtEncoder, times(1)).encode(any(JwtEncoderParameters.class));
        verify(authentication, times(1)).getName();
        verify(authentication, times(1)).getAuthorities();
    }

    @Test
    void generateToken_Success_MultipleAuthorities() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_MERCHANT"),
                new SimpleGrantedAuthority("READ_PAYMENTS"),
                new SimpleGrantedAuthority("WRITE_PAYMENTS")
        );

        when(authentication.getName()).thenReturn(testUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        Jwt mockJwt = createMockJwt(expectedToken);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // Act
        String result = tokenService.generateToken(authentication);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result);
        verify(jwtEncoder, times(1)).encode(any(JwtEncoderParameters.class));
    }

    @Test
    void generateToken_Success_NoAuthorities() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = List.of();

        when(authentication.getName()).thenReturn(testUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        Jwt mockJwt = createMockJwt(expectedToken);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // Act
        String result = tokenService.generateToken(authentication);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result);
        verify(jwtEncoder, times(1)).encode(any(JwtEncoderParameters.class));
    }


    @Test
    void generateToken_NullAuthentication_ThrowsException() {
        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> tokenService.generateToken(null));
    }

    @Test
    void generateToken_NullAuthorities_ThrowsException() {

        when(authentication.getAuthorities()).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> tokenService.generateToken(authentication));
    }

    @Test
    void generateToken_NullUsername_ThrowsException() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MERCHANT")
        );

        when(authentication.getName()).thenReturn(null);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        // Act & Assert
        Exception exception = assertThrows(Exception.class,
                () -> tokenService.generateToken(authentication));

        // The actual exception might be NullPointerException or IllegalArgumentException
        assertTrue(exception instanceof NullPointerException ||
                exception instanceof IllegalArgumentException);
    }


    @Test
    void generateToken_JwtEncoderThrowsException_PropagatesException() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MERCHANT")
        );

        when(authentication.getName()).thenReturn(testUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenThrow(new RuntimeException("JWT encoding failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> tokenService.generateToken(authentication));

        assertEquals("JWT encoding failed", exception.getMessage());
        verify(jwtEncoder, times(1)).encode(any(JwtEncoderParameters.class));
    }

    @Test
    void generateToken_AuthorityWithSpaces_HandlesCorrectly() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MERCHANT ADMIN")
        );

        when(authentication.getName()).thenReturn(testUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        Jwt mockJwt = createMockJwt(expectedToken);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // Act
        String result = tokenService.generateToken(authentication);

        // Assert
        assertNotNull(result);
        verify(jwtEncoder).encode(argThat(parameters -> {
            String scope = parameters.getClaims().getClaim("scope");
            assertEquals("ROLE_MERCHANT ADMIN", scope);
            return true;
        }));
    }

    @Test
    void generateToken_SpecialCharactersInUsername_HandlesCorrectly() {
        // Arrange
        String specialUsername = "user+test@example.com";
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MERCHANT")
        );

        when(authentication.getName()).thenReturn(specialUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        Jwt mockJwt = createMockJwt(expectedToken);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // Act
        String result = tokenService.generateToken(authentication);

        // Assert
        assertNotNull(result);
        verify(jwtEncoder).encode(argThat(parameters -> {
            assertEquals(specialUsername, parameters.getClaims().getSubject());
            return true;
        }));
    }

    @Test
    void generateToken_VerifyTokenExpiration() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MERCHANT")
        );

        when(authentication.getName()).thenReturn(testUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        Jwt mockJwt = createMockJwt(expectedToken);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        // Act
        String result = tokenService.generateToken(authentication);

        // Assert
        assertNotNull(result);
        verify(jwtEncoder).encode(argThat(parameters -> {
            Instant issuedAt = parameters.getClaims().getIssuedAt();
            Instant expiresAt = parameters.getClaims().getExpiresAt();

            // Verify expiration is exactly 1 hour after issued at
            assertEquals(issuedAt.plus(1, java.time.temporal.ChronoUnit.HOURS), expiresAt);
            return true;
        }));
    }

    @Test
    void generateToken_MultipleCalls_GeneratesDifferentTokens() {
        // Arrange
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MERCHANT")
        );

        when(authentication.getName()).thenReturn(testUsername);
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        Jwt mockJwt1 = createMockJwt("token1");
        Jwt mockJwt2 = createMockJwt("token2");

        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(mockJwt1)
                .thenReturn(mockJwt2);

        // Act
        String result1 = tokenService.generateToken(authentication);
        String result2 = tokenService.generateToken(authentication);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("token1", result1);
        assertEquals("token2", result2);
        verify(jwtEncoder, times(2)).encode(any(JwtEncoderParameters.class));
    }

    // Helper method to create a mock JWT
    private Jwt createMockJwt(String tokenValue) {
        return new Jwt(
                tokenValue,
                Instant.now(),
                Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS),
                java.util.Map.of("alg", "RS256"),
                java.util.Map.of("sub", testUsername, "scope", "ROLE_MERCHANT")
        );
    }
}