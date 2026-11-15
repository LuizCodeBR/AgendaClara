package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;

public class V13__professional_access extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var c=context.getConnection();
        try(var s=c.createStatement()){
            s.execute("alter table app_user add column role varchar(12) not null default 'OWNER'");
            s.execute("alter table app_user add column professional_id uuid");
            s.execute("alter table app_user add column enabled boolean not null default true");
            s.execute("alter table app_user add constraint user_professional unique(professional_id)");
            s.execute("alter table app_user add constraint user_professional_company foreign key(professional_id,business_id) references professional(id,business_id)");
            s.execute("alter table app_user add constraint user_role_link check((role='OWNER' and professional_id is null) or (role='PROFESSIONAL' and professional_id is not null))");
            s.execute("""
                create table professional_access (
                  professional_id uuid primary key, business_id uuid not null,
                  version integer not null default 0 check(version>=0),
                  invite_digest varchar(64) unique, invite_email varchar(180), invite_expires_at timestamp with time zone,
                  foreign key(professional_id,business_id) references professional(id,business_id),
                  check((invite_digest is null and invite_email is null and invite_expires_at is null)
                     or (invite_digest is not null and invite_email is not null and invite_expires_at is not null))
                )
                """);
        }
        var names=new ArrayList<String>();
        try(var s=c.createStatement();var r=s.executeQuery("""
            select tc.constraint_name,cc.check_clause from information_schema.table_constraints tc
            join information_schema.check_constraints cc on cc.constraint_catalog=tc.constraint_catalog
              and cc.constraint_schema=tc.constraint_schema and cc.constraint_name=tc.constraint_name
            where tc.table_schema=current_schema() and lower(tc.table_name)='appointment_history' and tc.constraint_type='CHECK'
            """)){
            while(r.next())if(r.getString(2).toUpperCase(Locale.ROOT).contains("ACTOR_TYPE")&&r.getString(2).contains("'OWNER'"))names.add(r.getString(1));
        }
        if(names.size()!=2)throw new SQLException("Restrições anteriores do histórico não identificadas.");
        try(var s=c.createStatement()){
            for(var name:names)s.execute("alter table appointment_history drop constraint \""+name.replace("\"","\"\"")+"\"");
            s.execute("alter table appointment_history add constraint history_actor_type check(actor_type in ('OWNER','PROFESSIONAL','CUSTOMER','SYSTEM'))");
            s.execute("alter table appointment_history add constraint history_actor_user check((actor_type in ('OWNER','PROFESSIONAL') and actor_user_id is not null) or (actor_type in ('CUSTOMER','SYSTEM') and actor_user_id is null))");
        }
    }
}
