package ru.mifi.shortener.service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Фоновая задача, которая периодически удаляет протухшие ссылки. */
public final class ExpiredLinkCleaner implements AutoCloseable {

  private final ScheduledExecutorService executor;

  public ExpiredLinkCleaner(LinkService service, Duration interval) {
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "expired-link-cleaner");
              t.setDaemon(true);
              return t;
            });
    long millis = interval.toMillis();
    executor.scheduleAtFixedRate(
        () -> {
          try {
            service.cleanupExpired();
          } catch (RuntimeException e) {
            System.err.println("Ошибка очистки ссылок: " + e.getMessage());
          }
        },
        millis,
        millis,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
