package ru.mifi.shortener.domain;

/** Результат перехода: статус и исходный URL (если переход разрешён). */
public record VisitResult(LinkStatus status, String originalUrl, int remainingClicks) {

  public static VisitResult of(LinkStatus status) {
    return new VisitResult(status, null, 0);
  }

  public boolean isOk() {
    return status == LinkStatus.OK;
  }
}
