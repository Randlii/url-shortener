package ru.mifi.shortener.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Короткая ссылка с владельцем, лимитом переходов и временем жизни. */
public final class ShortLink {

  private final String code;
  private final String originalUrl;
  private final UUID ownerId;
  private final Instant createdAt;
  private final Instant expiresAt;
  private int maxClicks;
  private int clicks;

  public ShortLink(
      String code,
      String originalUrl,
      UUID ownerId,
      Instant createdAt,
      Instant expiresAt,
      int maxClicks) {
    this.code = Objects.requireNonNull(code);
    this.originalUrl = Objects.requireNonNull(originalUrl);
    this.ownerId = Objects.requireNonNull(ownerId);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.maxClicks = maxClicks;
  }

  public String code() {
    return code;
  }

  public String originalUrl() {
    return originalUrl;
  }

  public UUID ownerId() {
    return ownerId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public synchronized int maxClicks() {
    return maxClicks;
  }

  public synchronized int clicks() {
    return clicks;
  }

  public synchronized int remainingClicks() {
    return Math.max(0, maxClicks - clicks);
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public synchronized boolean isLimitReached() {
    return clicks >= maxClicks;
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerId.equals(userId);
  }

  /**
   * Атомарно засчитывает переход, если лимит ещё не исчерпан.
   *
   * @return true, если переход засчитан
   */
  public synchronized boolean tryRegisterClick() {
    if (clicks >= maxClicks) {
      return false;
    }
    clicks++;
    return true;
  }

  public synchronized void changeMaxClicks(int newMaxClicks) {
    this.maxClicks = newMaxClicks;
  }
}
