package br.com.luizcode.agenda.mail;

import br.com.luizcode.agenda.identity.MailSettings;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@DependsOnDatabaseInitialization
public class MailPayloadCipher {
    private final SecretKey key;
    private final String keyId;
    private final ObjectMapper mapper;
    private static final SecureRandom RANDOM=new SecureRandom();
    public MailPayloadCipher(JdbcTemplate jdbc,ObjectMapper mapper,MailSettings settings,Clock clock,
            @Value("${app.mail.encryption-key:}") String configured,
            @Value("${app.mail.key-file:./data/mail-outbox.key}") String keyFile) {
        this.mapper=mapper;
        var existing=jdbc.query("select distinct key_id from mail_outbox where payload is not null and expires_at>?",
                (rs,row)->rs.getString(1),clock.instant().atOffset(ZoneOffset.UTC));
        var identity=jdbc.query("select key_id from mail_key_identity where id=1",(rs,row)->rs.getString(1));
        byte[] bytes=null;
        try {
            Path path=Path.of(keyFile).toAbsolutePath().normalize();
            if(!configured.isBlank()) bytes=Base64.getDecoder().decode(configured.strip());
            else if(settings.demo()) bytes=localDemoKey(path,!existing.isEmpty()||!identity.isEmpty());
            else if(Files.exists(path)) bytes=Base64.getDecoder().decode(Files.readString(path).strip());
            else if(!existing.isEmpty()||!identity.isEmpty()) throw new IllegalStateException("Restaure a chave de criptografia da fila antes de iniciar.");
            else if(settings.available()) throw new IllegalStateException("Configure MAIL_ENCRYPTION_KEY com 32 bytes em Base64 para preservar a fila.");
            if(bytes!=null && bytes.length!=32) throw new IllegalArgumentException("A chave da fila precisa ter 32 bytes em Base64.");
            key=bytes==null?null:new SecretKeySpec(bytes,"AES");
            keyId=bytes==null?null:HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(keyId!=null && identity.isEmpty()) {
                try { jdbc.update("insert into mail_key_identity(id,key_id) values(1,?)",keyId); }
                catch(org.springframework.dao.DuplicateKeyException race) { /* Other process registered the installation key. */ }
                identity=jdbc.query("select key_id from mail_key_identity where id=1",(rs,row)->rs.getString(1));
            }
            if(!identity.isEmpty()&&!Objects.equals(identity.getFirst(),keyId))
                throw new IllegalStateException("A chave configurada não corresponde à instalação. Restaure a chave original.");
            if(existing.stream().anyMatch(id->!Objects.equals(id,keyId)))
                throw new IllegalStateException("A chave configurada não corresponde aos e-mails pendentes. Restaure a chave original.");
        } catch(IllegalStateException|IllegalArgumentException ex) { throw ex; }
        catch(Exception ex) { throw new IllegalStateException("Não foi possível carregar a chave de criptografia da fila."); }
    }
    private static void restrict(Path path) throws java.io.IOException {
        if(Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));
        else {
            var view=Files.getFileAttributeView(path,AclFileAttributeView.class);
            if(view!=null) view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
    }
    public String keyId() { return keyId; }

    /** RFC 5869 HKDF-SHA256, one expansion block; separates application purposes from AES. */
    public SecretKey deriveKey(String purpose) {
        if (key == null) return null;
        byte[] original=key.getEncoded(), prk=null, expanded=null;
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32],"HmacSHA256"));
            prk=mac.doFinal(original);
            mac.init(new SecretKeySpec(prk,"HmacSHA256"));
            mac.update(("AgendaClara/"+purpose).getBytes(StandardCharsets.UTF_8));
            expanded=mac.doFinal(new byte[]{1});
            return new SecretKeySpec(expanded,"HmacSHA256");
        } catch(GeneralSecurityException ex) { throw new IllegalStateException("Não foi possível preparar a proteção de acesso."); }
        finally { Arrays.fill(original,(byte)0);if(prk!=null)Arrays.fill(prk,(byte)0);if(expanded!=null)Arrays.fill(expanded,(byte)0); }
    }
    public String encrypt(String aad,MailQueueModels.Payload payload) {
        try {
            if(key==null) throw new IllegalStateException();
            byte[] nonce=new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(mapper.writeValueAsBytes(payload));
            byte[] result=new byte[nonce.length+encrypted.length]; System.arraycopy(nonce,0,result,0,12); System.arraycopy(encrypted,0,result,12,encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch(Exception ex) { throw new IllegalStateException("Não foi possível proteger o conteúdo do e-mail."); }
    }
    public MailQueueModels.Payload decrypt(String aad,String value) {
        try {
            byte[] bytes=Base64.getDecoder().decode(value); if(bytes.length<29) throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return mapper.readValue(cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length)),MailQueueModels.Payload.class);
        } catch(Exception ex) { throw new IllegalStateException("O conteúdo protegido do e-mail não pôde ser validado."); }
    }

    private static byte[] localDemoKey(Path path,boolean restoreRequired) throws java.io.IOException {
        // JVM coordination avoids overlapping Java locks; the file lock coordinates other processes.
        synchronized(MailPayloadCipher.class) {
            if(!Files.exists(path)&&restoreRequired)
                throw new IllegalStateException("Restaure a chave de criptografia da fila antes de iniciar.");
            Files.createDirectories(path.getParent());
            Path guard=path.resolveSibling(path.getFileName()+".lock");
            try(var channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                    var ignored=channel.lock()) {
                if(Files.exists(path)) return Base64.getDecoder().decode(Files.readString(path).strip());
                if(restoreRequired) throw new IllegalStateException("Restaure a chave de criptografia da fila antes de iniciar.");
                byte[] generated=new byte[32]; RANDOM.nextBytes(generated);
                Files.createFile(path);
                restrict(path); // No secret is written until owner-only permissions are in place.
                try(var output=FileChannel.open(path,StandardOpenOption.WRITE)) {
                    ByteBuffer bytes=StandardCharsets.UTF_8.encode(Base64.getEncoder().encodeToString(generated));
                    while(bytes.hasRemaining()) output.write(bytes);
                    output.force(true);
                }
                return generated;
            }
        }
    }

}
