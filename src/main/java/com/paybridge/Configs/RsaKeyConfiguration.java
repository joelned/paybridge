package com.paybridge.Configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class RsaKeyConfiguration {
    @Value("${rsa.private-key-path}")
    private String privateKeyPath;

    @Value("${rsa.public-key-path}")
    private String publicKeyPath;

    @Bean
    public RsaKeyProperties rsaKeyProperties() throws Exception {
        RSAPrivateKey privateKey = loadPrivateKey(privateKeyPath);
        RSAPublicKey publicKey = loadPublicKey(publicKeyPath);
        return new RsaKeyProperties(privateKey, publicKey);
    }

    private RSAPrivateKey loadPrivateKey(String keySource) throws Exception {
        String keyContent = getKeyContent(keySource);

        // Remove headers and whitespace
        String privateKeyPEM = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        // Decode and create key
        byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKey(String keySource) throws Exception {
        String keyContent = getKeyContent(keySource);

        // Remove headers and whitespace
        String publicKeyPEM = keyContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        // Decode and create key
        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    private String getKeyContent(String keySource) throws Exception {
        if (keySource.startsWith("classpath:")) {
            // Load from classpath (local development)
            String resourcePath = keySource.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(resourcePath);
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream()));
        } else {
            // Treat as direct key content (production environment variable)
            return keySource;
        }
    }
}
