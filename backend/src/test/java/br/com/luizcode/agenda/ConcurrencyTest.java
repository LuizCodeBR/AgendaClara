package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class ConcurrencyTest extends ApiTestBase {
    @Test void eightSimultaneousCustomersProduceOneBooking() throws Exception {
        var outcomes = race(8, index -> {
            try { schedule.book(catalog.business(BUSINESS), request("09:00")); return "created"; }
            catch (BusinessException ex) { assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT); return "conflict"; }
        });
        assertThat(outcomes).filteredOn("created"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("conflict"::equals).hasSize(7);
        assertThat(jdbc.queryForObject("select count(*) from appointment where status = 'BOOKED'", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentRetriesOfSameRequestReturnOneReference() throws Exception {
        var request = request("09:00");
        var outcomes = race(8, index -> schedule.book(catalog.business(BUSINESS), request).receipt().reference());
        assertThat(outcomes.stream().distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentBlockAndBookingCannotBothWin() throws Exception {
        var outcomes = race(2, index -> {
            try {
                if (index == 0) schedule.book(catalog.business(BUSINESS), request("09:00"));
                else schedule.block(catalog.business(BUSINESS), new BlockRequest(PROFESSIONAL, at("09:00"), at("10:00"), "Reunião"));
                return "created";
            } catch (BusinessException ex) { assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT); return "conflict"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("created", "conflict");
        Integer total = jdbc.queryForObject("select (select count(*) from appointment) + (select count(*) from time_block)", Integer.class);
        assertThat(total).isEqualTo(1);
    }
    @Test void databaseRejectsCrossBusinessRelationships() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into time_block(id, business_id, professional_id, starts_at, ends_at, reason)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), BUSINESS, OTHER_PROFESSIONAL, at("09:00"), at("10:00"), "Inválido"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    private <T> List<T> race(int participants, java.util.function.IntFunction<T> action) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(participants);
        CountDownLatch ready = new CountDownLatch(participants);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < participants; index++) {
                final int current = index;
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    return action.apply(current);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown(); workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
