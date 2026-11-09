package ru.mifi.shortener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Часы, которые можно двигать вперёд в тестах. */
public final class MutableClock extends Clock {
  private Instant now;

  public MutableClock(Instant start) {
    this.now = start;
  }

  public void advance(Duration d) {
    now = now.plus(d);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}
