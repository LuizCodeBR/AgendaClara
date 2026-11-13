package br.com.luizcode.agenda.mail;

import java.time.Instant;
import java.util.*;

public final class MailQueueModels {
    private MailQueueModels() {}
    public record Payload(String from,String to,String subject,String text,String actionUrl) {
        @Override public String toString() { return "Payload[redacted]"; }
    }
    public record Job(UUID id,UUID businessId,UUID userId,String purpose,String mode,String status,String payload,
                      int attempts,int manualRetries,Instant createdAt,Instant expiresAt,Instant sendDeadline,
                      Instant nextAttemptAt,Instant leaseUntil,UUID leaseToken,Instant acceptedAt,String lastError) {
        public String aad() { return id+":"+businessId+":"+userId+":"+purpose+":"+mode; }
        @Override public String toString() { return "Job[id="+id+",status="+status+"]"; }
    }
    public record Delivery(boolean accepted,boolean retryable,String code,int retryAfterSeconds) {
        public static Delivery ok() { return new Delivery(true,false,null,0); }
        public static Delivery failed(String code,boolean retryable,int after) { return new Delivery(false,retryable,code,after); }
    }
    public record Item(UUID id,String purpose,String mode,String status,int attempts,int manualRetries,Instant createdAt,
                       Instant expiresAt,Instant nextAttemptAt,Instant acceptedAt,String lastError,boolean canRetry) {}
    public record Monitor(String mode,Map<String,Long> counts,Instant oldestPendingAt,Instant lastCheckAt,
                          String health,List<Item> messages) {}
}
