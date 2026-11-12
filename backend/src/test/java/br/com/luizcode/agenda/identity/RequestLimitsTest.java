package br.com.luizcode.agenda.identity;

import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RequestLimitsTest {
    @Test void deniesExcessThenRecoversUsingMonotonicTime() {
        var now = new AtomicLong();
        var limits = new RequestLimits(now::get, 4);
        assertThat(limits.acquire("login", 2)).isZero();
        assertThat(limits.acquire("login", 2)).isZero();
        assertThat(limits.acquire("login", 2)).isEqualTo(60);
        now.set(59_500_000_000L);
        assertThat(limits.acquire("login", 2)).isEqualTo(1);
        now.set(60_000_000_000L);
        assertThat(limits.acquire("login", 2)).isZero();
    }

    @Test void concurrentRequestsCannotExceedAllowance() throws Exception {
        var limits = new RequestLimits(() -> 0, 4);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(20)) {
            var calls = IntStream.range(0,20).mapToObj(i -> executor.submit(() -> {
                start.await(); return limits.acquire("booking", 5);
            })).toList();
            start.countDown();
            int admitted = 0;
            for (var call : calls) if (call.get(5, TimeUnit.SECONDS) == 0) admitted++;
            assertThat(admitted).isEqualTo(5);
        }
    }

    @Test void capacityCannotEvictActiveCountersAndExpiredEntriesAreReclaimed() {
        var now = new AtomicLong();
        var limits = new RequestLimits(now::get, 2);
        assertThat(limits.acquire("a", 1)).isZero();
        assertThat(limits.acquire("b", 1)).isZero();
        assertThat(limits.acquire("c", 1)).isEqualTo(60);
        assertThat(limits.acquire("a", 1)).isEqualTo(60);
        now.set(60_000_000_000L);
        assertThat(limits.acquire("c", 1)).isZero();
        assertThat(limits.acquire("d", 1)).isZero();
    }
}
