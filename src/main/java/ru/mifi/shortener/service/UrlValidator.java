package ru.mifi.shortener.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Проверяет и нормализует URL, который пользователь хочет сократить. */
public final class UrlValidator {

  private static final int MAX_LENGTH = 2048;

  private UrlValidator() {}

  /**
   * Возвращает нормализованный URL. Если схема не указана, добавляется https://.
   *
   * @throws ValidationException если URL некорректен
   */
  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ValidationException("URL не может быть пустым");
    }
    String url = raw.trim();
    if (url.length() > MAX_LENGTH) {
      throw new ValidationException("URL слишком длинный (максимум " + MAX_LENGTH + " символов)");
    }
    if (url.chars().anyMatch(Character::isWhitespace)) {
      throw new ValidationException("URL не должен содержать пробелов");
    }
    if (!url.contains("://")) {
      url = "https://" + url;
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new ValidationException("Некорректный URL: " + e.getReason());
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new ValidationException("Поддерживаются только http и https ссылки");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new ValidationException("В URL не указан домен");
    }
    if (!host.contains(".") && !host.equalsIgnoreCase("localhost")) {
      throw new ValidationException("Домен указан некорректно: " + host);
    }
    return url;
  }
}
