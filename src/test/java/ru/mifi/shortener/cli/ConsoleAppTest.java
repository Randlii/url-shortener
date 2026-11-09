package ru.mifi.shortener.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ru.mifi.shortener.MutableClock;
import ru.mifi.shortener.config.AppConfig;
import ru.mifi.shortener.repository.InMemoryLinkRepository;
import ru.mifi.shortener.service.LinkService;
import ru.mifi.shortener.service.NotificationService;
import ru.mifi.shortener.service.RandomCodeGenerator;

/** Интеграционные сценарии через консольный интерфейс. */
class ConsoleAppTest {

  private final List<String> opened = new ArrayList<>();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
  private final NotificationService notifications = new NotificationService();
  private final LinkService service =
      new LinkService(
          new InMemoryLinkRepository(),
          new RandomCodeGenerator(6),
          notifications,
          new AppConfig("clck.ru/", 6, Duration.ofHours(24), 10, 100, Duration.ofSeconds(60)),
          clock);

  private String run(String input) throws Exception {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    new ConsoleApp(
            service,
            notifications,
            opened::add,
            new BufferedReader(new StringReader(input)),
            new PrintStream(buf, true, StandardCharsets.UTF_8))
        .run();
    return buf.toString(StandardCharsets.UTF_8);
  }

  private static String shortLink(String output) {
    Matcher m = Pattern.compile("Короткая ссылка: (\\S+)").matcher(output);
    assertTrue(m.find(), output);
    return m.group(1);
  }

  @Test
  void createOpenAndLimitScenario() throws Exception {
    String out = run("create https://ru.stackoverflow.com 1\nexit\n");
    String link = shortLink(out);
    assertTrue(out.contains("Вам присвоен UUID"));

    String out2 = run(link + "\n" + link + "\nexit\n");
    assertEquals(List.of("https://ru.stackoverflow.com"), opened);
    assertTrue(out2.contains("Лимит переходов исчерпан"));
  }

  @Test
  void ownerGetsNotificationAboutExpiry() throws Exception {
    String out = run("create https://ru.stackoverflow.com 5\nwhoami\nexit\n");
    String uuid = out.replaceAll("(?s).*Ваш UUID: (\\S+).*", "$1");
    clock.advance(Duration.ofDays(1));
    service.cleanupExpired();
    String out2 = run("login " + uuid + "\nlist\nexit\n");
    assertTrue(out2.contains("[Уведомление] Срок жизни ссылки"), out2);
    assertTrue(out2.contains("У вас нет активных ссылок"));
  }

  @Test
  void otherUserCannotDelete() throws Exception {
    String link = shortLink(run("create https://example.com\nexit\n"));
    String out = run("login 00000000-0000-0000-0000-000000000001\ndelete " + link + "\nexit\n");
    assertTrue(out.contains("Нет прав"), out);
  }

  @Test
  void unknownCommandAndBadInputHandled() throws Exception {
    String out = run("foo\ncreate\ncreate https://example.com abc\nlimit\nexit\n");
    assertTrue(out.contains("Неизвестная команда"));
    assertTrue(out.contains("Не хватает аргументов"));
    assertTrue(out.contains("должен быть целым числом"));
  }
}
