package ru.mifi.shortener.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.mifi.shortener.config.AppConfig;
import ru.mifi.shortener.domain.LinkStatus;
import ru.mifi.shortener.domain.ShortLink;
import ru.mifi.shortener.domain.VisitResult;
import ru.mifi.shortener.repository.LinkRepository;

/** Бизнес-логика сервиса коротких ссылок. */
public final class LinkService {

  private static final int MAX_GENERATION_ATTEMPTS = 20;

  private final LinkRepository repository;
  private final CodeGenerator codeGenerator;
  private final NotificationService notifications;
  private final AppConfig config;
  private final Clock clock;

  public LinkService(
      LinkRepository repository,
      CodeGenerator codeGenerator,
      NotificationService notifications,
      AppConfig config,
      Clock clock) {
    this.repository = repository;
    this.codeGenerator = codeGenerator;
    this.notifications = notifications;
    this.config = config;
    this.clock = clock;
  }

  /**
   * Создаёт короткую ссылку.
   *
   * @param ownerId UUID пользователя
   * @param rawUrl исходный URL
   * @param maxClicks лимит переходов или null, чтобы взять значение из конфигурации
   */
  public ShortLink create(UUID ownerId, String rawUrl, Integer maxClicks) {
    String url = UrlValidator.normalize(rawUrl);
    int limit = maxClicks == null ? config.defaultMaxClicks() : maxClicks;
    validateLimit(limit);
    Instant now = clock.instant();
    Instant expiresAt = now.plus(config.ttl());
    for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
      ShortLink link = new ShortLink(codeGenerator.generate(), url, ownerId, now, expiresAt, limit);
      if (repository.saveIfAbsent(link)) {
        return link;
      }
    }
    throw new IllegalStateException("Не удалось сгенерировать уникальный код, попробуйте ещё раз");
  }

  /** Переход по короткой ссылке: проверяет срок жизни и лимит, засчитывает переход. */
  public VisitResult visit(String codeOrUrl) {
    String code = extractCode(codeOrUrl);
    ShortLink link = repository.findByCode(code).orElse(null);
    if (link == null) {
      return VisitResult.of(LinkStatus.NOT_FOUND);
    }
    if (link.isExpired(clock.instant())) {
      expire(link);
      return VisitResult.of(LinkStatus.EXPIRED);
    }
    if (!link.tryRegisterClick()) {
      return VisitResult.of(LinkStatus.LIMIT_REACHED);
    }
    if (link.isLimitReached()) {
      notifications.notify(
          link.ownerId(),
          "Лимит переходов по ссылке "
              + shortUrl(link)
              + " исчерпан ("
              + link.maxClicks()
              + "). Ссылка больше недоступна, создайте новую.");
    }
    return new VisitResult(LinkStatus.OK, link.originalUrl(), link.remainingClicks());
  }

  public List<ShortLink> listOwn(UUID ownerId) {
    return repository.findByOwner(ownerId);
  }

  public ShortLink get(String codeOrUrl) {
    String code = extractCode(codeOrUrl);
    return repository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
  }

  /** Меняет лимит переходов. Доступно только владельцу. */
  public ShortLink changeLimit(UUID userId, String codeOrUrl, int newLimit) {
    validateLimit(newLimit);
    ShortLink link = requireOwned(userId, codeOrUrl);
    if (newLimit < link.clicks()) {
      throw new ValidationException(
          "Новый лимит ("
              + newLimit
              + ") меньше уже совершённых переходов ("
              + link.clicks()
              + ")");
    }
    link.changeMaxClicks(newLimit);
    return link;
  }

  /** Удаляет ссылку. Доступно только владельцу. */
  public void delete(UUID userId, String codeOrUrl) {
    ShortLink link = requireOwned(userId, codeOrUrl);
    repository.delete(link.code());
  }

  /** Удаляет все протухшие ссылки и уведомляет владельцев. Возвращает число удалённых. */
  public int cleanupExpired() {
    List<ShortLink> removed = repository.removeExpired(clock.instant());
    removed.forEach(this::notifyExpired);
    return removed.size();
  }

  public String shortUrl(ShortLink link) {
    return config.baseUrl() + link.code();
  }

  /** Достаёт код из строки вида "clck.ru/abc123", "https://clck.ru/abc123" или "abc123". */
  public String extractCode(String codeOrUrl) {
    if (codeOrUrl == null || codeOrUrl.isBlank()) {
      throw new ValidationException("Не указана короткая ссылка");
    }
    String value = codeOrUrl.trim();
    int slash = value.lastIndexOf('/');
    if (slash >= 0) {
      value = value.substring(slash + 1);
    }
    if (value.isEmpty()) {
      throw new ValidationException("Не указан код короткой ссылки");
    }
    return value;
  }

  private ShortLink requireOwned(UUID userId, String codeOrUrl) {
    ShortLink link = get(codeOrUrl);
    if (link.isExpired(clock.instant())) {
      expire(link);
      throw new LinkNotFoundException(link.code() + " (срок жизни истёк)");
    }
    if (!link.isOwnedBy(userId)) {
      throw new AccessDeniedException(link.code());
    }
    return link;
  }

  private void expire(ShortLink link) {
    if (repository.delete(link.code())) {
      notifyExpired(link);
    }
  }

  private void notifyExpired(ShortLink link) {
    notifications.notify(
        link.ownerId(),
        "Срок жизни ссылки "
            + shortUrl(link)
            + " истёк, она удалена. Создайте новую ссылку, если она ещё нужна.");
  }

  private void validateLimit(int limit) {
    if (limit < 1) {
      throw new ValidationException("Лимит переходов должен быть не меньше 1");
    }
    if (limit > config.maxAllowedClicks()) {
      throw new ValidationException(
          "Лимит переходов не может превышать " + config.maxAllowedClicks());
    }
  }
}
