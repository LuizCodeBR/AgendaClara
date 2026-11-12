package br.com.luizcode.agenda.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded, process-local admission control for this single-instance deployment. */
public final class RequestLimits {
    private record Window(long since, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final LongSupplier ticker;
    private final int capacity;
    private static final long MINUTE = 60_000_000_000L;
    public RequestLimits() { this(System::nanoTime, 2048); }
    public RequestLimits(LongSupplier ticker, int capacity) { this.ticker = ticker; this.capacity = capacity; }

    /** Returns seconds to wait, or zero when admitted. Uses monotonic time. */
    public synchronized int acquire(String key, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Limit must be positive");
        long now = ticker.getAsLong();
        Window window = windows.get(key);
        if (window == null || now - window.since() >= MINUTE) {
            if (window == null && windows.size() >= capacity) {
                windows.entrySet().removeIf(entry -> now - entry.getValue().since() >= MINUTE);
                if (windows.size() >= capacity) return 60;
            }
            windows.put(key, new Window(now, 1)); return 0;
        }
        if (window.count() >= limit)
            return (int) Math.max(1, (MINUTE - (now - window.since()) + 999_999_999L) / 1_000_000_000L);
        windows.put(key, new Window(window.since(), window.count() + 1)); return 0;
    }
}
