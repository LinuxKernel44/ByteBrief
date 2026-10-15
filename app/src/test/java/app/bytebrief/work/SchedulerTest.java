package app.bytebrief.work;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public class SchedulerTest {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static long at(String iso) {
        return ZonedDateTime.parse(iso).toInstant().toEpochMilli();
    }

    @Test
    public void laterTodayWhenTheTimeHasNotPassed() {
        long now = at("2026-10-15T06:00:00+02:00[Europe/Paris]");
        assertEquals(TimeUnit.MINUTES.toMillis(90), Scheduler.delayUntilNext(7 * 60 + 30, now, PARIS));
    }

    @Test
    public void tomorrowWhenTheTimeHasPassedOrIsNow() {
        long now = at("2026-10-15T08:00:00+02:00[Europe/Paris]");
        assertEquals(TimeUnit.HOURS.toMillis(23) + TimeUnit.MINUTES.toMillis(30), Scheduler.delayUntilNext(7 * 60 + 30, now, PARIS));
        long exact = at("2026-10-15T07:30:00+02:00[Europe/Paris]");
        assertEquals(TimeUnit.HOURS.toMillis(24), Scheduler.delayUntilNext(7 * 60 + 30, exact, PARIS));
    }

    @Test
    public void staysOnWallClockAcrossTheAutumnDstChange() {
        // Clocks go back on 2026-10-25 (03:00 -> 02:00): that night has 25 hours.
        long now = at("2026-10-24T20:00:00+02:00[Europe/Paris]");
        long delay = Scheduler.delayUntilNext(7 * 60 + 30, now, PARIS);
        assertEquals(at("2026-10-25T07:30:00+01:00[Europe/Paris]"), now + delay);
        assertEquals(TimeUnit.HOURS.toMillis(12) + TimeUnit.MINUTES.toMillis(30), delay);
    }

    @Test
    public void staysOnWallClockAcrossTheSpringDstChange() {
        long now = at("2026-03-28T20:00:00+01:00[Europe/Paris]");
        long delay = Scheduler.delayUntilNext(7 * 60 + 30, now, PARIS);
        assertEquals(at("2026-03-29T07:30:00+02:00[Europe/Paris]"), now + delay);
        assertEquals(TimeUnit.HOURS.toMillis(10) + TimeUnit.MINUTES.toMillis(30), delay);
    }

    @Test
    public void midnightAndLastMinuteBoundaries() {
        long now = at("2026-10-15T23:59:00+02:00[Europe/Paris]");
        assertEquals(TimeUnit.MINUTES.toMillis(1), Scheduler.delayUntilNext(0, now, PARIS));
        assertTrue(Scheduler.delayUntilNext(24 * 60 - 1, now, PARIS) == 0 || Scheduler.delayUntilNext(24 * 60 - 1, now, PARIS) > 0);
    }

    @Test
    public void neverNegative() {
        for (int m = 0; m < 24 * 60; m += 37) {
            assertTrue(Scheduler.delayUntilNext(m, at("2026-10-25T01:30:00+02:00[Europe/Paris]"), PARIS) >= 0);
        }
    }
}
