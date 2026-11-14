package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Replace the old generated status CHECK by metadata, without changing historical migrations. */
public class V10__appointment_outcomes extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        var constraints=new ArrayList<String>();
        try(var statement=connection.createStatement();var rows=statement.executeQuery("""
            select tc.constraint_name,cc.check_clause from information_schema.table_constraints tc
            join information_schema.check_constraints cc on cc.constraint_catalog=tc.constraint_catalog
              and cc.constraint_schema=tc.constraint_schema and cc.constraint_name=tc.constraint_name
            where tc.table_schema=current_schema() and lower(tc.table_name)='appointment'
              and tc.constraint_type='CHECK'
            """)){
            while(rows.next()){String clause=rows.getString(2).toUpperCase(Locale.ROOT);
                if(clause.contains("STATUS")&&clause.contains("'BOOKED'")&&clause.contains("'CANCELLED'"))constraints.add(rows.getString(1));}
        }
        if(constraints.size()!=1)throw new SQLException("Não foi possível identificar a restrição anterior de situação.");
        try(var statement=connection.createStatement()){
            statement.execute("alter table appointment drop constraint \""+constraints.getFirst().replace("\"","\"\"")+"\"");
            statement.execute("alter table appointment add constraint appointment_status_values check(status in ('BOOKED','CANCELLED','COMPLETED','NO_SHOW'))");
            statement.execute("""
                create table appointment_history (
                  id uuid primary key, business_id uuid not null, appointment_id uuid not null,
                  appointment_version integer not null check(appointment_version>=0),
                  action varchar(24) not null check(action in ('CREATED','CANCELLED','RESCHEDULED','OUTCOME','CORRECTION')),
                  from_status varchar(20), to_status varchar(20) not null,
                  from_start timestamp with time zone, from_end timestamp with time zone,
                  to_start timestamp with time zone not null, to_end timestamp with time zone not null,
                  actor_type varchar(12) not null check(actor_type in ('OWNER','CUSTOMER','SYSTEM')),
                  actor_user_id uuid, actor_name varchar(100) not null,
                  reason varchar(500) not null, occurred_at timestamp with time zone not null,
                  request_key uuid, expected_version integer,
                  unique(appointment_id,business_id,appointment_version),
                  unique(business_id,request_key),
                  foreign key(appointment_id,business_id) references appointment(id,business_id) on delete cascade,
                  foreign key(actor_user_id,business_id) references app_user(id,business_id),
                  check((actor_type='OWNER' and actor_user_id is not null) or (actor_type<>'OWNER' and actor_user_id is null)),
                  check(to_status in ('BOOKED','CANCELLED','COMPLETED','NO_SHOW')),
                  check(from_status is null or from_status in ('BOOKED','CANCELLED','COMPLETED','NO_SHOW')),
                  check(to_start<to_end),
                  check(action<>'CORRECTION' or char_length(trim(reason))>=5)
                )
                """);
            statement.execute("create index idx_appointment_history on appointment_history(business_id,appointment_id,appointment_version desc)");
        }
    }
}
