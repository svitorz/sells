package com.github.svitorz.sells.application.fakes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the tests move by hand. Starts at 2026-01-15 13:00 UTC. */
public final class MutableClock extends Clock {

    private Instant instant = Instant.parse("2026-01-15T13:00:00Z");

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock has a fixed zone");
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
