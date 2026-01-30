package org.AI.panda.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HashUtil {
    private HashUtil() {
    }

    public static String sha256Hex(String value) {
        if (value == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
