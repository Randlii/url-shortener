package ru.mifi.shortener.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/** Параметры приложения, загружаемые из файла application.properties. */
public final class AppConfig {

  public static final String RESOURCE_NAME = "application.properties";

  private final String baseUrl;
  private final int codeLength;
  private final Duration ttl;
  private final int defaultMaxClicks;
  private final int maxAllowedClicks;
  private final Duration cleanupInterval;

  public AppConfig(
      String baseUrl,
      int codeLength,
      Duration ttl,
      int defaultMaxClicks,
      int maxAllowedClicks,
      Duration cleanupInterval) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("base-url не может быть пустым");
    }
    if (codeLength < 4 || codeLength > 16) {
      throw new IllegalArgumentException("code-length должен быть в диапазоне 4..16");
    }
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl должен быть положительным");
    }
    if (defaultMaxClicks < 1 || maxAllowedClicks < defaultMaxClicks) {
      throw new IllegalArgumentException(
          "Должно выполняться 1 <= default-max-clicks <= max-allowed-clicks");
    }
    if (cleanupInterval.isZero() || cleanupInterval.isNegative()) {
      throw new IllegalArgumentException("cleanup-interval должен быть положительным");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.codeLength = codeLength;
    this.ttl = ttl;
    this.defaultMaxClicks = defaultMaxClicks;
    this.maxAllowedClicks = maxAllowedClicks;
    this.cleanupInterval = cleanupInterval;
  }

  /**
   * Загружает конфигурацию. Если передан путь к внешнему файлу, он используется вместо встроенного
   * ресурса.
   */
  public static AppConfig load(Path externalFile) {
    Properties props = new Properties();
    try {
      if (externalFile != null) {
        try (Reader reader = Files.newBufferedReader(externalFile, StandardCharsets.UTF_8)) {
          props.load(reader);
        }
      } else {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
          if (in == null) {
            throw new IllegalStateException("Не найден ресурс " + RESOURCE_NAME);
          }
          props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Не удалось прочитать конфигурацию: " + e.getMessage(), e);
    }
    return fromProperties(props);
  }

  public static AppConfig fromProperties(Properties p) {
    return new AppConfig(
        p.getProperty("shortener.base-url", "clck.ru/"),
        intProp(p, "shortener.code-length", 6),
        Duration.ofHours(intProp(p, "shortener.ttl-hours", 24)),
        intProp(p, "shortener.default-max-clicks", 10),
        intProp(p, "shortener.max-allowed-clicks", 1000),
        Duration.ofSeconds(intProp(p, "shortener.cleanup-interval-seconds", 60)));
  }

  private static int intProp(Properties p, String key, int def) {
    String value = p.getProperty(key);
    if (value == null || value.isBlank()) {
      return def;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Параметр " + key + " должен быть числом: " + value, e);
    }
  }

  public String baseUrl() {
    return baseUrl;
  }

  public int codeLength() {
    return codeLength;
  }

  public Duration ttl() {
    return ttl;
  }

  public int defaultMaxClicks() {
    return defaultMaxClicks;
  }

  public int maxAllowedClicks() {
    return maxAllowedClicks;
  }

  public Duration cleanupInterval() {
    return cleanupInterval;
  }
}
