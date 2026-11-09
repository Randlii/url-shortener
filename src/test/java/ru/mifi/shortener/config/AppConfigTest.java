package ru.mifi.shortener.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {

  @Test
  void loadsBundledProperties() {
    AppConfig c = AppConfig.load(null);
    assertEquals("clck.ru/", c.baseUrl());
    assertEquals(Duration.ofHours(24), c.ttl());
    assertEquals(10, c.defaultMaxClicks());
  }

  @Test
  void loadsExternalFile(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("custom.properties");
    Files.writeString(file, "shortener.ttl-hours=1\nshortener.base-url=sh.rt\n");
    AppConfig c = AppConfig.load(file);
    assertEquals(Duration.ofHours(1), c.ttl());
    assertEquals("sh.rt/", c.baseUrl());
  }

  @Test
  void rejectsInvalidValues() {
    Properties p = new Properties();
    p.setProperty("shortener.ttl-hours", "abc");
    assertThrows(IllegalArgumentException.class, () -> AppConfig.fromProperties(p));
    p.setProperty("shortener.ttl-hours", "0");
    assertThrows(IllegalArgumentException.class, () -> AppConfig.fromProperties(p));
  }
}
