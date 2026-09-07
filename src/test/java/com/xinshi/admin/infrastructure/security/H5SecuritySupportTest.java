package com.xinshi.admin.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import org.junit.jupiter.api.Test;

class H5SecuritySupportTest {
    private final H5SecuritySupport support = new H5SecuritySupport();

    @Test
    void encryptRoundTripUsesRandomIv() {
        String secret = "test-secret-with-at-least-sixteen-characters";
        byte[] first = support.encrypt(secret, "openid-value");
        byte[] second = support.encrypt(secret, "openid-value");

        assertEquals("openid-value", support.decrypt(secret, first));
        assertFalse(java.util.Arrays.equals(first, second));
    }

    @Test
    void hmacComparisonRejectsDifferentIdentity() {
        String secret = "test-secret-with-at-least-sixteen-characters";
        String expected = support.hmacSha256(secret, "appid:openid-a");

        assertTrue(support.constantTimeEquals(expected, support.hmacSha256(secret, "appid:openid-a")));
        assertFalse(support.constantTimeEquals(expected, support.hmacSha256(secret, "appid:openid-b")));
    }

    @Test
    void normalizationAcceptsChineseMobileAndIdSuffix() {
        assertEquals("张 三", support.normalizeName("  张  三 "));
        assertEquals("13800138000", support.normalizeMobile("138-0013-8000"));
        assertEquals("123X", support.normalizeIdCardLast4("123x"));
    }

    @Test
    void invalidMobileIsRejectedAtBoundary() {
        assertThrows(H5ApiException.class, () -> support.normalizeMobile("12345"));
    }
}
