package br.com.luizcode.agenda.storage;

import br.com.luizcode.agenda.identity.MailSettings;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public class StorageSettings {
    private final String mode;
    public StorageSettings(DataSource source,MailSettings mail,@Value("${app.storage.mode:local}") String mode,
            @Value("${app.mail.encryption-key:}") String configuredKey) throws Exception {
        if(!Set.of("local","ephemeral","persistent").contains(mode))throw new IllegalArgumentException("STORAGE_MODE inválido.");
        if(mode.equals("persistent")) {
            try(var connection=source.getConnection()) {
                if(!connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"))
                    throw new IllegalArgumentException("O modo persistent exige PostgreSQL.");
            }
            if(mail.available()&&configuredKey.isBlank())throw new IllegalArgumentException("O modo persistent exige MAIL_ENCRYPTION_KEY estável no ambiente privado.");
        }
        this.mode=mode;
    }
    public String mode(){return mode;}
}
