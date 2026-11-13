package br.com.luizcode.agenda.identity;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

final class SecretTokens {
    private static final SecureRandom RANDOM=new SecureRandom();
    private SecretTokens() {}
    static String create() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    static boolean wellFormed(String token) { return token!=null && token.matches("[A-Za-z0-9_-]{43}"); }
}
