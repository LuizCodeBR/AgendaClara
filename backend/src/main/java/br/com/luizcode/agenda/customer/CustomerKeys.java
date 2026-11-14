package br.com.luizcode.agenda.customer;

import br.com.luizcode.agenda.mail.MailPayloadCipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import org.springframework.stereotype.Component;

@Component
public final class CustomerKeys {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final SecretKey key;
    public CustomerKeys(MailPayloadCipher cipher){key=cipher.deriveKey("customer-reservation-access/v1");}
    public boolean available(){return key!=null;}
    public String token(UUID appointment,int generation){
        if(key==null)throw new IllegalStateException("A proteção da instalação não está configurada.");
        try{
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                    (appointment+":"+generation).getBytes(StandardCharsets.US_ASCII)));
        }catch(GeneralSecurityException ex){throw new IllegalStateException("Não foi possível preparar o acesso privado.");}
    }
    public static String random(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static boolean valid(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{43}");}
    public static String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    public static Instant validUntil(Instant end){return end.plus(Duration.ofDays(7));}
    public static boolean matches(String token,String digest){return digest!=null&&MessageDigest.isEqual(
            digest.getBytes(StandardCharsets.US_ASCII),digest(token).getBytes(StandardCharsets.US_ASCII));}
}
