package com.xinshi.admin.infrastructure.security;

import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Cryptographic and normalization helpers for the H5 boundary.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Component
public class H5SecuritySupport {
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String randomToken(int byteLength) {
        byte[] value = new byte[byteLength];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public String sha256(String value) {
        return hex(digest("SHA-256", value.getBytes(StandardCharsets.UTF_8)));
    }

    public String hmacSha256(String secret, String value) {
        requireSecret(secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC initialization failed", exception);
        }
    }

    public byte[] encrypt(String secret, String plaintext) {
        requireSecret(secret);
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(secret), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + encrypted.length);
            buffer.put((byte) 1).put(iv).put(encrypted);
            return buffer.array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("H5 data encryption failed", exception);
        }
    }

    public String decrypt(String secret, byte[] ciphertext) {
        requireSecret(secret);
        if (ciphertext == null || ciphertext.length <= 1 + GCM_IV_BYTES) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            byte version = buffer.get();
            if (version != 1) {
                throw new IllegalArgumentException("Unsupported ciphertext version");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(secret), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("H5 data decryption failed", exception);
        }
    }

    public String normalizeName(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC);
        return normalized.replaceAll("\\s+", " ").trim();
    }

    public String normalizeMobile(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\s-]", "");
        if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "手机号格式错误");
        }
        return normalized;
    }

    public String normalizeIdCardLast4(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[0-9]{3}[0-9X]$")) {
            throw new H5ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "身份证后四位格式错误");
        }
        return normalized;
    }

    public boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = (left == null ? "" : left).getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = (right == null ? "" : right).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    public String maskName(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.substring(0, 1) + "*";
    }

    public String maskMobile(String mobile) {
        if (mobile == null || mobile.length() != 11) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private SecretKeySpec aesKey(String secret) {
        return new SecretKeySpec(digest("SHA-256", secret.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private byte[] digest(String algorithm, byte[] value) {
        try {
            return MessageDigest.getInstance(algorithm).digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Digest initialization failed", exception);
        }
    }

    private void requireSecret(String secret) {
        if (secret == null || secret.trim().length() < 16) {
            throw new IllegalStateException("H5 secret must contain at least 16 characters");
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
