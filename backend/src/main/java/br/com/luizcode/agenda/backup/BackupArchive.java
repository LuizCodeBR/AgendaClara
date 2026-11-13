package br.com.luizcode.agenda.backup;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import tools.jackson.databind.ObjectMapper;

/** Operator-only encrypted archive; never exposed through a tenant HTTP endpoint. */
public final class BackupArchive {
    public static final String VERSION="0.8.1";
    static final int LIMIT=64*1024*1024;
    private static final byte[] MAGIC="ACBK0001".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final ObjectMapper JSON=new ObjectMapper();
    public record Manifest(int format,String appVersion,String engine,String engineVersion,String schema,String createdAt,String keyId,Map<String,String> files) {}
    public record Bundle(Manifest manifest,Map<String,byte[]> files) implements AutoCloseable {
        public void close(){ files.values().forEach(bytes->Arrays.fill(bytes,(byte)0)); }
        @Override public String toString(){ return "Bundle[protected]"; }
    }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException ex){ throw new IllegalStateException(ex); }
    }
    static void protect(Path path,boolean directory) throws IOException { BackupFiles.protect(path,directory); }
    static Path privateFile(Path path) throws IOException { return BackupFiles.file(path); }
    static Path staging() throws IOException { return BackupFiles.staging(); }
    static void clearStage(Path stage) throws IOException {
        Path root=stage.toAbsolutePath().normalize();BackupFiles.noLinks(root);
        if(!root.getFileName().toString().startsWith("agenda-backup-")) throw new IOException("Diretório temporário inesperado.");
        try(var paths=Files.list(root)) {
            for(Path path:paths.toList()) {
                if(!path.toAbsolutePath().normalize().getParent().equals(root)||Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Conteúdo temporário inesperado.");
                Files.deleteIfExists(path);
            }
        }
        Files.deleteIfExists(root);
    }
    static byte[] readBounded(Path path) throws IOException { return BackupFiles.read(path,LIMIT); }
    static void checkFiles(String engine,Map<String,byte[]> files) throws IOException {
        String database=switch(engine){case "h2"->"agenda.mv.db";case "postgres"->"database.dump";default->throw new IOException("Tipo de banco inválido.");};
        if(!files.containsKey(database)||files.size()>2||files.keySet().stream().anyMatch(name->!name.equals(database)&&!name.equals("mail-outbox.key")))
            throw new IOException("Conteúdo do backup inválido.");
        long total=files.values().stream().mapToLong(bytes->bytes.length).sum();
        if(total>LIMIT) throw new IOException("O backup excede o limite de 64 MiB.");
    }
    static byte[] zip(Manifest manifest,Map<String,byte[]> files) throws IOException {
        var buffer=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(JSON.writeValueAsBytes(manifest)); zip.closeEntry();
            for(var file:files.entrySet()) { zip.putNextEntry(new ZipEntry(file.getKey())); zip.write(file.getValue()); zip.closeEntry(); }
        }
        return buffer.toByteArray();
    }
    static byte[] crypt(int mode,byte[] input,char[] password,byte[] salt,byte[] nonce) throws GeneralSecurityException {
        var specification=new PBEKeySpec(password,salt,600000,256);
        byte[] raw=null;
        try {
            raw=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(raw,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(MAGIC); cipher.updateAAD(salt); cipher.updateAAD(nonce);
            return cipher.doFinal(input);
        } finally { specification.clearPassword(); if(raw!=null)Arrays.fill(raw,(byte)0); }
    }
    public static void write(Path output,String engine,String engineVersion,String schema,String keyId,Map<String,byte[]> files,char[] password) throws Exception {
        if(Character.codePointCount(password,0,password.length)<15||password.length>256) throw new IOException("Use uma senha de backup de 15 a 256 caracteres.");
        checkFiles(engine,files);
        var sums=new LinkedHashMap<String,String>(); files.forEach((name,value)->sums.put(name,digest(value)));
        var manifest=new Manifest(1,VERSION,engine,engineVersion,schema,Instant.now().toString(),keyId,sums);
        byte[] payload=zip(manifest,files);
        if(payload.length>LIMIT) throw new IOException("O backup excede o limite de 64 MiB.");
        byte[] salt=new byte[16],nonce=new byte[12]; RANDOM.nextBytes(salt); RANDOM.nextBytes(nonce);
        Path absolute=output.toAbsolutePath().normalize(),partial=null;
        try {
            if(Files.exists(absolute,LinkOption.NOFOLLOW_LINKS)) throw new IOException("O arquivo de destino já existe.");
            BackupFiles.directory(absolute.getParent());
            partial=absolute.resolveSibling("."+absolute.getFileName()+"."+UUID.randomUUID()+".partial");
            privateFile(partial);
            try(var stream=Files.newOutputStream(partial)) {
                stream.write(MAGIC); stream.write(salt); stream.write(nonce);
                stream.write(crypt(Cipher.ENCRYPT_MODE,payload,password,salt,nonce));
            }
            try(var channel=java.nio.channels.FileChannel.open(partial,StandardOpenOption.WRITE)){channel.force(true);}
            // No REPLACE_EXISTING: a pre-existing backup must never be overwritten.
            Files.move(partial,absolute);
        } finally { Arrays.fill(payload,(byte)0); if(partial!=null)Files.deleteIfExists(partial); }
    }
    public static Bundle read(Path input,char[] password) throws Exception {
        byte[] encrypted=BackupFiles.read(input,LIMIT+52),plain=null;
        try {
            if(encrypted.length<52||!Arrays.equals(MAGIC,Arrays.copyOf(encrypted,8))) throw new IOException("Formato de backup inválido.");
            plain=crypt(Cipher.DECRYPT_MODE,Arrays.copyOfRange(encrypted,36,encrypted.length),password,
                    Arrays.copyOfRange(encrypted,8,24),Arrays.copyOfRange(encrypted,24,36));
            var files=new LinkedHashMap<String,byte[]>(); byte[] metadata=null; long total=0;
            try(var zip=new ZipInputStream(new ByteArrayInputStream(plain))) {
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    String name=entry.getName();
                    if(entry.isDirectory()||!Set.of("manifest.json","agenda.mv.db","database.dump","mail-outbox.key").contains(name)
                            ||files.containsKey(name)||(name.equals("manifest.json")&&metadata!=null))
                        throw new IOException("Arquivo inesperado ou duplicado no backup.");
                    int maximum=name.equals("manifest.json")?16384:LIMIT;
                    byte[] value=zip.readNBytes(maximum+1); total+=value.length;
                    if(value.length>maximum||total>LIMIT+16384L) throw new IOException("O conteúdo excede o limite de leitura.");
                    if(name.equals("manifest.json"))metadata=value;else files.put(name,value);
                }
            }
            if(metadata==null)throw new IOException("Manifesto ausente.");
            Manifest manifest=JSON.readValue(metadata,Manifest.class);
            if(manifest.format()!=1||manifest.files()==null||!manifest.files().keySet().equals(files.keySet()))throw new IOException("Manifesto incompatível.");
            checkFiles(manifest.engine(),files);
            for(var file:files.entrySet())if(!digest(file.getValue()).equals(manifest.files().get(file.getKey())))throw new IOException("Integridade do backup inválida.");
            byte[] key=files.get("mail-outbox.key");
            if(key!=null) {
                byte[] raw=Base64.getDecoder().decode(new String(key,java.nio.charset.StandardCharsets.US_ASCII).strip());
                try { if(raw.length!=32||!digest(raw).equals(manifest.keyId()))throw new IOException("Chave incompatível com o backup."); }
                finally{Arrays.fill(raw,(byte)0);}
            } else if(manifest.keyId()!=null)throw new IOException("Chave ausente no backup.");
            return new Bundle(manifest,files);
        } finally { Arrays.fill(encrypted,(byte)0); if(plain!=null)Arrays.fill(plain,(byte)0); }
    }
}
