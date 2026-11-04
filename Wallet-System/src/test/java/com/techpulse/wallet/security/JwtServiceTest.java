package com.techpulse.wallet.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private JwtService jwtService;

    // Base64-encoded 32-byte key used only by this unit test.
    private static final String TEST_SECRET =
            "VGhpc0lzQVRlc3RTZWNyZXRLZXlGb3JXYWxsZXRTeXN0ZW0=";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 60_000L);
    }

    @Test
    void generateToken_shouldCreateTokenWithEmailSubject() {
        String token = jwtService.generateToken("user@example.com", "USER");

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("user@example.com", jwtService.extractEmail(token));
    }

    @Test
    void isTokenValid_shouldReturnTrueForMatchingEmail() {
        String token = jwtService.generateToken("user@example.com", "USER");

        assertTrue(jwtService.isTokenValid(token, "user@example.com"));
    }

    @Test
    void isTokenValid_shouldReturnFalseForDifferentEmail() {
        String token = jwtService.generateToken("user@example.com", "USER");

        assertFalse(jwtService.isTokenValid(token, "another@example.com"));
    }

    @Test
    void extractEmail_shouldRejectMalformedToken() {
        assertThrows(RuntimeException.class,
                () -> jwtService.extractEmail("this-is-not-a-jwt"));
    }
}
