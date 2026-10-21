package com.ikeu.server.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Narrow Java↔Python HMAC transport contract. No principal field is sourced from LLM input.
 * Wire format v1: base64url("v1|userId|campusId|expiresEpoch|nonce").hex-hmac-sha256.
 * Request MAC covers method, raw path+query, principal, timestamp and per-request nonce.
 * Use TLS/private networking; a shared secret is for trusted services, NOT for browsers.
 */
@Component
public class AgentWireSecurity {
    private final byte[] secret;

    public AgentWireSecurity(@Value("${runningerrands.agent.shared-secret:}") String configuredSecret) {
        this.secret = configuredSecret == null ? new byte[0] :
                configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean configured() {
        return secret.length >= 32;
    }

    public record Principal(long userId, long campusId, long expiresAt) {}

    public String issuePrincipal(long userId, long campusId) {
        requireConfigured();
        if (userId <= 0 || campusId <= 0) throw new IllegalArgumentException("invalid principal");
        String data = "v1|" + userId + "|" + campusId + "|" + (Instant.now().getEpochSecond() + 120)
                + "|" + UUID.randomUUID();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(data.getBytes(StandardCharsets.UTF_8));
        return payload + "." + mac(data);
    }

    public Principal verifyPrincipal(String token) {
        requireConfigured();
        if (token == null || token.length() > 512 || !token.matches("[A-Za-z0-9_-]+[.][a-f0-9]{64}")) {
            throw new SecurityException("invalid principal");
        }
        try {
            String[] parts = token.split("[.]", -1);
            String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\|", -1);
            if (fields.length != 5 || !"v1".equals(fields[0]) ||
                    !fields[4].matches("[0-9a-f-]{36}")) throw new SecurityException("invalid principal");
            if (!constantTimeEqual(parts[1], mac(decoded))) throw new SecurityException("invalid principal");
            long user = Long.parseLong(fields[1]), campus = Long.parseLong(fields[2]);
            long expiration = Long.parseLong(fields[3]), now = Instant.now().getEpochSecond();
            if (user <= 0 || campus <= 0 || expiration <= now || expiration > now + 180)
                throw new SecurityException("expired or invalid principal");
            return new Principal(user, campus, expiration);
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("invalid principal");
        }
    }

    public String signature(String method, String pathWithQuery, String principal, String timestamp, String nonce) {
        requireConfigured();
        return mac(method + "\n" + pathWithQuery + "\n" + principal + "\n" + timestamp + "\n" + nonce);
    }

    public Principal verifyRequest(String method, String rawPathQuery, String principal,
                                   String timestamp, String nonce, String signature) {
        Principal actor = verifyPrincipal(principal);
        if (!"GET".equals(method) || timestamp == null || nonce == null ||
                !nonce.matches("[0-9a-f-]{36}") || signature == null) {
            throw new SecurityException("invalid service request");
        }
        try {
            long epoch = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - epoch) > 90) {
                throw new SecurityException("stale service request");
            }
        } catch (NumberFormatException ex) {
            throw new SecurityException("invalid timestamp");
        }
        if (!constantTimeEqual(signature(method, rawPathQuery, principal, timestamp, nonce), signature)) {
            throw new SecurityException("invalid service signature");
        }
        return actor;
    }

    public static boolean constantTimeEqual(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),
                b.getBytes(StandardCharsets.US_ASCII));
    }

    private String mac(String message) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }

    private void requireConfigured() {
        if (!configured()) throw new SecurityException("Agent shared secret is not configured");
    }
}
