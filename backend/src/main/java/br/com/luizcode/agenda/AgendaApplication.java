package br.com.luizcode.agenda;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AgendaApplication {
    public static void main(String[] args) {
        if(br.com.luizcode.agenda.backup.OperationsTool.requested(args)) {
            System.exit(br.com.luizcode.agenda.backup.OperationsTool.run(args,System.getenv(),System.out)); return;
        }
        if(br.com.luizcode.agenda.backup.BackupTool.requested(args)) {
            System.exit(br.com.luizcode.agenda.backup.BackupTool.run(args,System.getenv(),System.out)); return;
        }
        SpringApplication.run(AgendaApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
