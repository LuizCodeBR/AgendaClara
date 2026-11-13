package br.com.luizcode.agenda.backup;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** The same OS lock covers first-time initialization, state, backup and report. */
final class OperationsWorkspace implements AutoCloseable {
    private final Path root;
    private final FileChannel channel;
    private final FileLock lock;
    private OperationsWorkspace(Path root,FileChannel channel,FileLock lock){this.root=root;this.channel=channel;this.lock=lock;}
    Path root(){return root;}
    boolean acquired(){return lock!=null;}
    static OperationsWorkspace acquire(Path path) throws Exception {
        Path root=BackupFiles.absolute(path);BackupFiles.noLinks(root);
        if(root.getParent()==null)throw new IOException("Escolha uma pasta operacional exclusiva.");
        BackupFiles.directory(root);
        Path marker=root.resolve(".agenda-operations"),lockPath=root.resolve(".operations.lock");
        BackupFiles.noLinks(marker);BackupFiles.noLinks(lockPath);
        // Do not create a lock or change ACLs in an unrelated nonempty folder.
        if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS)&&!Files.exists(lockPath,LinkOption.NOFOLLOW_LINKS))
            try(var items=Files.list(root)){if(items.findAny().isPresent()&&!Files.exists(lockPath,LinkOption.NOFOLLOW_LINKS))throw new IOException("Escolha uma pasta nova ou vazia.");}
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)&&!Files.exists(lockPath,LinkOption.NOFOLLOW_LINKS)&&
           !new String(BackupFiles.read(marker,64),StandardCharsets.UTF_8).equals(OperationsTool.MARKER))throw new IOException("Pasta sem identificação válida.");
        try{BackupFiles.file(lockPath);}catch(FileAlreadyExistsException ignored){}
        if(!Files.isRegularFile(lockPath,LinkOption.NOFOLLOW_LINKS))throw new IOException("Bloqueio operacional inválido.");
        FileChannel channel=FileChannel.open(lockPath,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        FileLock lock=null;
        try {
            try{lock=channel.tryLock();}catch(OverlappingFileLockException ignored){}
            if(lock==null)return new OperationsWorkspace(root,channel,null);
            if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)) {
                if(!new String(BackupFiles.read(marker,64),StandardCharsets.UTF_8).equals(OperationsTool.MARKER))
                    throw new IOException("Pasta operacional sem identificação válida.");
            } else {
                try(var items=Files.list(root)) {
                    if(items.anyMatch(item->!item.equals(lockPath)))throw new IOException("Pasta não identificada com conteúdo existente.");
                }
                BackupFiles.protect(root,true);
                BackupFiles.atomic(marker,OperationsTool.MARKER.getBytes(StandardCharsets.UTF_8));
            }
            BackupFiles.protect(root,true);BackupFiles.protect(lockPath,false);
            Path backups=root.resolve("backups");BackupFiles.directory(backups);BackupFiles.protect(backups,true);
            return new OperationsWorkspace(root,channel,lock);
        } catch(Exception|Error ex) {
            try{if(lock!=null)lock.release();}finally{channel.close();}
            throw ex;
        }
    }
    public void close() throws IOException {
        try{if(lock!=null&&lock.isValid())lock.release();}finally{channel.close();}
    }
}
