package br.com.luizcode.agenda.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.luizcode.agenda.identity.MailSettings;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class StorageSettingsTest {
    MailSettings mail(){return new MailSettings("demo","","","",true,false,false);}
    DataSource source(String product) throws Exception {
        var source=mock(DataSource.class);var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn(product);return source;
    }
    @Test void persistentRequiresPostgresAndStableExplicitKey() throws Exception {
        var pg=source("PostgreSQL");
        assertThat(new StorageSettings(pg,mail(),"persistent","configured-private-key").mode()).isEqualTo("persistent");
        assertThatThrownBy(()->new StorageSettings(pg,mail(),"persistent","")).hasMessageContaining("MAIL_ENCRYPTION_KEY");
        assertThatThrownBy(()->new StorageSettings(source("H2"),mail(),"persistent","configured-private-key")).hasMessageContaining("PostgreSQL");
    }
    @Test void localAndEphemeralModesAreExplicitAndUnknownValuesFail() throws Exception {
        assertThat(new StorageSettings(source("H2"),mail(),"local","").mode()).isEqualTo("local");
        assertThat(new StorageSettings(source("H2"),mail(),"ephemeral","").mode()).isEqualTo("ephemeral");
        assertThatThrownBy(()->new StorageSettings(source("H2"),mail(),"unknown","")).hasMessageContaining("STORAGE_MODE");
    }
}
