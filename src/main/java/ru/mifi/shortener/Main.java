package ru.mifi.shortener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import ru.mifi.shortener.cli.ConsoleApp;
import ru.mifi.shortener.cli.DesktopBrowserOpener;
import ru.mifi.shortener.config.AppConfig;
import ru.mifi.shortener.repository.InMemoryLinkRepository;
import ru.mifi.shortener.service.ExpiredLinkCleaner;
import ru.mifi.shortener.service.LinkService;
import ru.mifi.shortener.service.NotificationService;
import ru.mifi.shortener.service.RandomCodeGenerator;

/** Точка входа. Необязательный аргумент — путь к внешнему файлу конфигурации. */
public final class Main {

  private Main() {}

  public static void main(String[] args) throws Exception {
    AppConfig config = AppConfig.load(args.length > 0 ? Path.of(args[0]) : null);
    NotificationService notifications = new NotificationService();
    LinkService service =
        new LinkService(
            new InMemoryLinkRepository(),
            new RandomCodeGenerator(config.codeLength()),
            notifications,
            config,
            Clock.systemDefaultZone());
    try (ExpiredLinkCleaner cleaner = new ExpiredLinkCleaner(service, config.cleanupInterval())) {
      new ConsoleApp(
              service,
              notifications,
              new DesktopBrowserOpener(),
              new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
              new PrintStream(System.out, true, StandardCharsets.UTF_8))
          .run();
    }
  }
}
