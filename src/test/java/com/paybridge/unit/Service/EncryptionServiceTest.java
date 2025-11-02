package com.paybridge.unit.Service;

import com.paybridge.Configs.EncryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for EncryptionService
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private static final String TEST_KEY = "test-master-key-for-unit-testing-256-bits";

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "masterKeyString", TEST_KEY);
        // Manually call init since @PostConstruct won't run in unit tests
        ReflectionTestUtils.invokeMethod(encryptionService, "init");
    }

    // ========== Basic Encryption/Decryption Tests ==========

    @Test
    void encrypt_ValidPlaintext_ReturnsBase64String() {
        String plaintext = "Hello, World!";

        String ciphertext = encryptionService.encrypt(plaintext);

        assertNotNull(ciphertext);
        assertFalse(ciphertext.isEmpty());
        // Should be Base64 encoded
        assertDoesNotThrow(() -> Base64.getDecoder().decode(ciphertext));
        // Should not equal plaintext
        assertNotEquals(plaintext, ciphertext);
    }

    @Test
    void decrypt_ValidCiphertext_ReturnsOriginalPlaintext() {
        String plaintext = "Sensitive data 123!@#";

        String ciphertext = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(ciphertext);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_RoundTrip_PreservesData() {
        String original = "Test data with special chars: 你好, émojis: 🔒, numbers: 12345";

        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(original, decrypted);
    }

    // ========== Different Data Sizes ==========

    @ParameterizedTest
    @ValueSource(strings = {
            "a",
            "Short text",
            "Medium length text that has multiple words and punctuation.",
            "Very long text that simulates real-world sensitive data like API keys, secrets, " +
                    "and configuration data that might be stored encrypted in the database. This should " +
                    "handle hundreds of characters without any issues whatsoever."
    })
    void encrypt_DifferentLengths_AllSucceed(String plaintext) {
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_LargeData_Succeeds() {
        // Test with ~10KB of data
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeData.append("This is line ").append(i).append("\n");
        }
        String plaintext = largeData.toString();

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    // ========== Unicode and Special Characters ==========

    @Test
    void encrypt_UnicodeCharacters_PreservesEncoding() {
        String plaintext = "日本語 中文 한국어 العربية हिन्दी עברית";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_EmojisAndSymbols_PreservesData() {
        String plaintext = "Emojis: 🔐🔑🛡️💳 Symbols: ©®™€£¥";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_JsonData_PreservesStructure() {
        String jsonData = "{\"apiKey\":\"sk_test_123\",\"secret\":\"very_secret_value\",\"enabled\":true}";

        String encrypted = encryptionService.encrypt(jsonData);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(jsonData, decrypted);
    }

    // ========== IV Uniqueness Tests ==========

    @Test
    void encrypt_SamePlaintext_ProducesDifferentCiphertexts() {
        String plaintext = "Same data every time";

        String ciphertext1 = encryptionService.encrypt(plaintext);
        String ciphertext2 = encryptionService.encrypt(plaintext);

        assertNotEquals(ciphertext1, ciphertext2, "IVs should be random, producing different ciphertexts");

        // But both should decrypt to same plaintext
        assertEquals(plaintext, encryptionService.decrypt(ciphertext1));
        assertEquals(plaintext, encryptionService.decrypt(ciphertext2));
    }

    @Test
    void encrypt_MultipleEncryptions_AllHaveUniqueIVs() {
        String plaintext = "Test";
        int iterations = 100;

        var ciphertexts = new java.util.HashSet<String>();
        for (int i = 0; i < iterations; i++) {
            ciphertexts.add(encryptionService.encrypt(plaintext));
        }

        assertEquals(iterations, ciphertexts.size(), "All ciphertexts should be unique");
    }

    // ========== Error Handling Tests ==========

    @Test
    void encrypt_NullPlaintext_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            encryptionService.encrypt(null);
        });
    }

    @Test
    void encrypt_EmptyPlaintext_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            encryptionService.encrypt("");
        });
    }

    @Test
    void decrypt_NullCiphertext_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            encryptionService.decrypt(null);
        });
    }

    @Test
    void decrypt_EmptyCiphertext_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            encryptionService.decrypt("");
        });
    }

    @Test
    void decrypt_InvalidBase64_ThrowsException() {
        assertThrows(EncryptionException.class, () -> {
            encryptionService.decrypt("not-valid-base64!!!");
        });
    }

    @Test
    void decrypt_TamperedCiphertext_ThrowsException() {
        String plaintext = "Original data";
        String ciphertext = encryptionService.encrypt(plaintext);

        // Tamper with the ciphertext
        byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
        ciphertextBytes[ciphertextBytes.length - 1] ^= 0xFF; // Flip last byte
        String tamperedCiphertext = Base64.getEncoder().encodeToString(ciphertextBytes);

        // Should fail authentication
        assertThrows(EncryptionException.class, () -> {
            encryptionService.decrypt(tamperedCiphertext);
        });
    }

    @Test
    void decrypt_TooShortCiphertext_ThrowsException() {
        // Create ciphertext shorter than IV + auth tag
        String shortCiphertext = Base64.getEncoder().encodeToString(new byte[10]);

        assertThrows(EncryptionException.class, () -> {
            encryptionService.decrypt(shortCiphertext);
        });
    }

    @Test
    void decrypt_CorruptedIV_ThrowsException() {
        String plaintext = "Test data";
        String ciphertext = encryptionService.encrypt(plaintext);

        // Corrupt the IV (first 12 bytes)
        byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
        ciphertextBytes[0] ^= 0xFF;
        ciphertextBytes[5] ^= 0xFF;
        String corruptedCiphertext = Base64.getEncoder().encodeToString(ciphertextBytes);

        assertThrows(EncryptionException.class, () -> {
            encryptionService.decrypt(corruptedCiphertext);
        });
    }

    // ========== Binary Data Tests ==========

    @Test
    void encryptBytes_ValidData_Succeeds() {
        byte[] data = "Binary data 123".getBytes(StandardCharsets.UTF_8);

        String encrypted = encryptionService.encryptBytes(data);
        byte[] decrypted = encryptionService.decryptBytes(encrypted);

        assertArrayEquals(data, decrypted);
    }

    @Test
    void encryptBytes_NullData_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            encryptionService.encryptBytes(null);
        });
    }

    @Test
    void encryptBytes_EmptyData_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            encryptionService.encryptBytes(new byte[0]);
        });
    }

    // ========== Validation Tests ==========

    @Test
    void validateEncryption_ProperlyConfigured_ReturnsTrue() {
        boolean isValid = encryptionService.validateEncryption();

        assertTrue(isValid);
    }

    @Test
    void validateEncryption_MultipleCalls_AlwaysSucceeds() {
        for (int i = 0; i < 10; i++) {
            assertTrue(encryptionService.validateEncryption());
        }
    }

    // ========== Master Key Generation Test ==========

    @Test
    void generateNewMasterKey_CreatesValidKey() {
        String newKey = EncryptionService.generateNewMasterKey();

        assertNotNull(newKey);
        assertFalse(newKey.isEmpty());

        // Should be Base64 encoded
        assertDoesNotThrow(() -> Base64.getDecoder().decode(newKey));

        // Should be 256 bits (32 bytes)
        byte[] keyBytes = Base64.getDecoder().decode(newKey);
        assertEquals(32, keyBytes.length);
    }

    @Test
    void generateNewMasterKey_ProducesUniqueKeys() {
        String key1 = EncryptionService.generateNewMasterKey();
        String key2 = EncryptionService.generateNewMasterKey();

        assertNotEquals(key1, key2);
    }

    // ========== Key Rotation Test ==========

    @Test
    void reEncrypt_WithNewKey_Succeeds() {
        String plaintext = "Data to re-encrypt";

        // Encrypt with old key
        String oldCiphertext = encryptionService.encrypt(plaintext);

        // Create new encryption service with different key
        EncryptionService newEncryptionService = new EncryptionService();
        ReflectionTestUtils.setField(newEncryptionService, "masterKeyString", "new-master-key-different-from-old");
        ReflectionTestUtils.invokeMethod(newEncryptionService, "init");

        // Re-encrypt
        String newCiphertext = encryptionService.reEncrypt(oldCiphertext, newEncryptionService);

        // Verify
        assertNotEquals(oldCiphertext, newCiphertext);
        String decrypted = newEncryptionService.decrypt(newCiphertext);
        assertEquals(plaintext, decrypted);

        // Old key cannot decrypt new ciphertext
        assertThrows(EncryptionException.class, () -> {
            encryptionService.decrypt(newCiphertext);
        });
    }

    // ========== Performance Test ==========

    @Test
    void encrypt_1000Times_CompletesInReasonableTime() {
        String plaintext = "Performance test data";

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            String encrypted = encryptionService.encrypt(plaintext);
            String decrypted = encryptionService.decrypt(encrypted);
            assertEquals(plaintext, decrypted);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Should complete in less than 5 seconds
        assertTrue(duration < 5000, "1000 encrypt/decrypt cycles took " + duration + "ms");
    }

    // ========== Edge Cases ==========

    @Test
    void encrypt_OnlyWhitespace_Succeeds() {
        String plaintext = "   \t\n\r   ";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_VeryLongSingleLine_Succeeds() {
        String plaintext = "A".repeat(100000); // 100K characters

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_BinaryLikeString_Succeeds() {
        String plaintext = "\0\1\2\3\4\5\6\7\b\t\n\u000B\f\r";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }
}