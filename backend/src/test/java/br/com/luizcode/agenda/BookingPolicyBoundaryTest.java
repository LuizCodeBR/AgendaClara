package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.policy.BookingPolicy;
import java.time.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingPolicyBoundaryTest {
    @ParameterizedTest @ValueSource(ints={0,1,60,1440,10080})
    void elapsedTimeIsPreciseAcrossOffsetsAndDaylightSaving(int minutes){
        Instant start=OffsetDateTime.parse("2030-03-10T03:30:00-04:00").toInstant();
        var policy=new BookingPolicy(minutes,minutes,minutes,1);
        Instant deadline=start.minus(Duration.ofMinutes(minutes));
        assertThat(policy.allowsBooking(start,deadline.minusNanos(1))).isTrue();
        assertThat(policy.allowsBooking(start,deadline)).isEqualTo(minutes>0);
        assertThat(policy.allowsCancellation(start,deadline.plusNanos(1))).isFalse();
        assertThat(policy.allowsReschedule(start,start)).isFalse();
        assertThat(policy.cancelUntil(start)).isEqualTo(deadline);
        assertThat(policy.rescheduleUntil(start)).isEqualTo(deadline);
    }
}
