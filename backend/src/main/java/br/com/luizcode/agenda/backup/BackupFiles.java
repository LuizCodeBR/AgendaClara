package br.com.luizcode.agenda.backup;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Private local files. The operator account and installed binaries remain trusted. */
final class BackupFiles {
    private BackupFiles() {}
    static Path absolute(Path path){return path.toAbsolutePath().normalize();}
    static void noLinks(Path path) throws IOException {
        for(Path item=absolute(path);item!=null;item=item.getParent()) {
            if(Files.isSymbolicLink(item)||(Files.exists(item,LinkOption.NOFOLLOW_LINKS)&&!item.toRealPath().equals(item)))
                throw new IOException("Não use links simbólicos ou redirecionamentos de pasta.");
        }
    }
    static FileAttribute<?> attributes(Path parent,boolean directory) throws IOException {
        var store=Files.getFileStore(parent);
        if(store.supportsFileAttributeView("posix"))
            return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
        if(!store.supportsFileAttributeView("acl"))throw new IOException("O disco precisa oferecer permissões POSIX ou ACL.");
        var owner=parent.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        var builder=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if(directory)builder.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);
        List<AclEntry> acl=List.of(builder.build());
        return new FileAttribute<List<AclEntry>>() {
            public String name(){return "acl:acl";}
            public List<AclEntry> value(){return acl;}
        };
    }
    static void directory(Path path) throws IOException {
        Path absolute=absolute(path);noLinks(absolute);
        if(Files.exists(absolute,LinkOption.NOFOLLOW_LINKS)) {
            if(!Files.isDirectory(absolute,LinkOption.NOFOLLOW_LINKS))throw new IOException("Pasta inválida.");
            return;
        }
        Path parent=absolute.getParent();if(parent==null)throw new IOException("Pasta inválida.");
        directory(parent);
        try{Files.createDirectory(absolute,attributes(parent,true));}catch(FileAlreadyExistsException ex) {
            if(!Files.isDirectory(absolute,LinkOption.NOFOLLOW_LINKS))throw ex;
        }
        noLinks(absolute);
    }
    static void protect(Path path,boolean directory) throws IOException {
        noLinks(path);
        if(Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));return;
        }
        var view=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(view==null)throw new IOException("O disco precisa oferecer permissões POSIX ou ACL.");
        var builder=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path,LinkOption.NOFOLLOW_LINKS))
            .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if(directory)builder.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);
        view.setAcl(List.of(builder.build()));
    }
    static Path file(Path path) throws IOException {
        Path absolute=absolute(path);noLinks(absolute);
        Files.createFile(absolute,attributes(absolute.getParent(),false));return absolute;
    }
    static Path staging() throws IOException {
        Path parent=absolute(Path.of(System.getProperty("java.io.tmpdir")));directory(parent);noLinks(parent);
        return Files.createTempDirectory(parent,"agenda-backup-",attributes(parent,true));
    }
    static byte[] read(Path path,int maximum) throws IOException {
        noLinks(path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maximum)
            throw new IOException("Arquivo inválido ou maior que o limite de leitura.");
        try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(maximum+1);
            if(bytes.length>maximum){Arrays.fill(bytes,(byte)0);throw new IOException("Arquivo maior que o limite de leitura.");}
            return bytes;
        }
    }
    static void atomic(Path path,byte[] bytes) throws Exception {
        noLinks(path);
        Path partial=path.resolveSibling(path.getFileName()+"."+UUID.randomUUID()+".partial");
        try {
            file(partial);
            try(var channel=FileChannel.open(partial,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(partial,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(partial);}
    }
}
