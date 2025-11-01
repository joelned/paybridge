package com.paybridge.Services;

import com.paybridge.Configs.EncryptionException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM Encryption Service for securing sensitive data
 *
 * Security Features:
 * - AES-256-GCM authenticated encryption
 * - Random IV (96 bits) per encryption
 * - Authentication tag (128 bits)
 * - Key derivation from environment variable using SHA-256
 *
 * Data Format: [IV (12 bytes)][Ciphertext + Auth Tag]
 *
 * @author PayBridge Team
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    // AES-GCM Constants
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits (recommended for GCM)
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag
    private static final int AES_KEY_SIZE = 256; // 256 bits

    @Value("${encryption.secret.key}")
    private String masterKeyString;

    private SecretKey masterKey;

    /**
     * Initialize the master key from configuration on bean creation
     * Uses SHA-256 to derive a 256-bit key from the environment variable
     */
    @PostConstruct
    private void init() {
        try {
            this.masterKey = deriveKeyFromString(masterKeyString);
            log.info("Encryption service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize encryption service", e);
            throw new RuntimeException("Encryption service initialization failed", e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM
     *
     * Process:
     * 1. Generate random 96-bit IV
     * 2. Encrypt plaintext with AES-GCM
     * 3. Prepend IV to ciphertext
     * 4. Base64 encode the result
     *
     * @param plaintext The text to encrypt
     * @return Base64 encoded string containing IV + ciphertext + auth tag
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }

        if (plaintext.isEmpty()) {
            throw new IllegalArgumentException("Plaintext cannot be empty");
        }

        try {
            // Generate random IV
            byte[] iv = generateIV();

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);

            // Encrypt
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            // Combine IV + ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            // Base64 encode
            String encoded = Base64.getEncoder().encodeToString(combined);

            log.debug("Successfully encrypted data (length: {} bytes)", plaintext.length());
            return encoded;

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts ciphertext using AES-256-GCM
     *
     * Process:
     * 1. Base64 decode
     * 2. Extract IV from first 12 bytes
     * 3. Extract ciphertext + auth tag from remaining bytes
     * 4. Decrypt and verify authentication tag
     *
     * @param ciphertext Base64 encoded string containing IV + ciphertext + auth tag
     * @return Decrypted plaintext
     * @throws EncryptionException if decryption or authentication fails
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("Ciphertext cannot be null");
        }

        if (ciphertext.isEmpty()) {
            throw new IllegalArgumentException("Ciphertext cannot be empty");
        }

        try {
            // Base64 decode
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Validate minimum length (IV + at least 1 byte of ciphertext + auth tag)
            if (combined.length < GCM_IV_LENGTH + GCM_TAG_LENGTH / 8 + 1) {
                throw new EncryptionException("Invalid ciphertext: too short");
            }

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            // Extract ciphertext + auth tag
            byte[] ciphertextBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertextBytes, 0, ciphertextBytes.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);

            // Decrypt and verify
            byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);

            String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

            log.debug("Successfully decrypted data (length: {} bytes)", plaintext.length());
            return plaintext;

        } catch (javax.crypto.AEADBadTagException e) {
            log.error("Authentication tag verification failed - data may have been tampered with", e);
            throw new EncryptionException("Decryption failed: authentication tag mismatch", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Base64 encoding", e);
            throw new EncryptionException("Invalid ciphertext format", e);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Generates a cryptographically secure random IV
     *
     * @return 12-byte (96-bit) random IV
     */
    private byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    /**
     * Derives a 256-bit AES key from a string using SHA-256
     *
     * This allows using arbitrary-length passwords/secrets from environment variables
     *
     * @param keyString The string to derive key from
     * @return SecretKey for AES-256
     * @throws Exception if key derivation fails
     */
    private SecretKey deriveKeyFromString(String keyString) throws Exception {
        if (keyString == null || keyString.isEmpty()) {
            throw new IllegalArgumentException("Key string cannot be null or empty");
        }

        // Use SHA-256 to create a 256-bit key from the string
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(keyString.getBytes(StandardCharsets.UTF_8));

        // Verify we got 256 bits (32 bytes)
        if (keyBytes.length != 32) {
            throw new IllegalStateException("Key derivation produced invalid length: " + keyBytes.length);
        }

        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Validates that the encryption service is properly configured
     * Useful for health checks
     *
     * @return true if encryption/decryption works correctly
     */
    public boolean validateEncryption() {
        try {
            String testData = "test-encryption-validation-" + System.currentTimeMillis();
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);
            return testData.equals(decrypted);
        } catch (Exception e) {
            log.error("Encryption validation failed", e);
            return false;
        }
    }

    /**
     * Generates a new cryptographically secure AES-256 key
     * Useful for generating new master keys
     *
     * @return Base64 encoded 256-bit key
     */
    public static String generateNewMasterKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate master key", e);
        }
    }

    /**
     * Re-encrypts data with a new key (for key rotation)
     *
     * @param oldCiphertext Ciphertext encrypted with current key
     * @param newEncryptionService EncryptionService with new key
     * @return Ciphertext encrypted with new key
     */
    public String reEncrypt(String oldCiphertext, EncryptionService newEncryptionService) {
        try {
            // Decrypt with old key
            String plaintext = this.decrypt(oldCiphertext);

            // Encrypt with new key
            return newEncryptionService.encrypt(plaintext);
        } catch (Exception e) {
            log.error("Re-encryption failed", e);
            throw new EncryptionException("Failed to re-encrypt data", e);
        }
    }

    /**
     * Encrypts binary data
     *
     * @param data Binary data to encrypt
     * @return Base64 encoded encrypted data
     */
    public String encryptBytes(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        try {
            // Generate random IV
            byte[] iv = generateIV();

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(data);

            // Combine IV + ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            // Base64 encode
            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("Binary encryption failed", e);
            throw new EncryptionException("Failed to encrypt binary data", e);
        }
    }

    /**
     * Decrypts binary data
     *
     * @param ciphertext Base64 encoded encrypted data
     * @return Decrypted binary data
     */
    public byte[] decryptBytes(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            throw new IllegalArgumentException("Ciphertext cannot be null or empty");
        }

        try {
            // Base64 decode
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Validate length
            if (combined.length < GCM_IV_LENGTH + GCM_TAG_LENGTH / 8 + 1) {
                throw new EncryptionException("Invalid ciphertext: too short");
            }

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            // Extract ciphertext
            byte[] ciphertextBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertextBytes, 0, ciphertextBytes.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);

            // Decrypt
            return cipher.doFinal(ciphertextBytes);

        } catch (Exception e) {
            log.error("Binary decryption failed", e);
            throw new EncryptionException("Failed to decrypt binary data", e);
        }
    }
}