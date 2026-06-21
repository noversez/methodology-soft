package ru.casebook.dims.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SecurityHash {
    private SecurityHash() {
    }

    public static String sha256(String value) {
        return sha256(("dims:" + value).getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
