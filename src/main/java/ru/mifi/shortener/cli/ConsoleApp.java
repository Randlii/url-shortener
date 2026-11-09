package ru.mifi.shortener.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import ru.mifi.shortener.domain.ShortLink;
import ru.mifi.shortener.domain.VisitResult;
import ru.mifi.shortener.service.AccessDeniedException;
import ru.mifi.shortener.service.LinkNotFoundException;
import ru.mifi.shortener.service.LinkService;
import ru.mifi.shortener.service.NotificationService;
import ru.mifi.shortener.service.ValidationException;

/** Консольный интерфейс: читает команды в цикле и вызывает сервис. */
public final class ConsoleApp {

  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

  private final LinkService service;
  private final NotificationService notifications;
  private final BrowserOpener browser;
  private final BufferedReader in;
  private final PrintStream out;
  private UUID currentUser;

  public ConsoleApp(
      LinkService service,
      NotificationService notifications,
      BrowserOpener browser,
      BufferedReader in,
      PrintStream out) {
    this.service = service;
    this.notifications = notifications;
    this.browser = browser;
    this.in = in;
    this.out = out;
  }

  public UUID currentUser() {
    return currentUser;
  }

  public void run() throws IOException {
    out.println("Сервис сокращения ссылок. Введите help, чтобы увидеть список команд.");
    while (true) {
      printNotifications();
      out.print("> ");
      out.flush();
      String line = in.readLine();
      if (line == null) {
        break;
      }
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      if (!handle(line)) {
        break;
      }
    }
    out.println("До свидания!");
  }

  /**
   * Обрабатывает одну команду.
   *
   * @return false, если пользователь хочет выйти
   */
  boolean handle(String line) {
    String[] parts = line.split("\\s+");
    String command = parts[0].toLowerCase();
    try {
      switch (command) {
        case "help" -> printHelp();
        case "exit", "quit" -> {
          return false;
        }
        case "whoami" -> whoami();
        case "login" -> login(parts);
        case "logout" -> logout();
        case "create" -> create(parts);
        case "open" -> open(arg(parts, 1, "open <короткая ссылка>"));
        case "list" -> list();
        case "info" -> info(arg(parts, 1, "info <короткая ссылка>"));
        case "limit" -> limit(parts);
        case "delete" -> delete(arg(parts, 1, "delete <короткая ссылка>"));
        default -> {
          if (parts.length == 1 && line.contains("/")) {
            open(line);
          } else {
            out.println("Неизвестная команда: " + parts[0] + ". Введите help.");
          }
        }
      }
    } catch (ValidationException | LinkNotFoundException | AccessDeniedException e) {
      out.println("Ошибка: " + e.getMessage());
    }
    return true;
  }

  private void printHelp() {
    out.println(
        """
        Команды:
          create <url> [лимит]       создать короткую ссылку (лимит переходов необязателен)
          open <короткая ссылка>     перейти по ссылке (можно просто вставить ссылку)
          list                       мои ссылки
          info <короткая ссылка>     параметры ссылки
          limit <ссылка> <число>     изменить лимит переходов (только владелец)
          delete <короткая ссылка>   удалить ссылку (только владелец)
          whoami                     показать мой UUID
          login <uuid>               войти под существующим UUID
          logout                     сбросить текущего пользователя
          help                       эта справка
          exit                       выход
        Примеры:
          create https://www.baeldung.com/java-9-http-client 5
          clck.ru/3DZHeG
          limit clck.ru/3DZHeG 20""");
  }

  private void whoami() {
    if (currentUser == null) {
      out.println("Вы ещё не создавали ссылок. UUID появится после первой команды create.");
    } else {
      out.println("Ваш UUID: " + currentUser);
    }
  }

  private void login(String[] parts) {
    String raw = arg(parts, 1, "login <uuid>");
    try {
      currentUser = UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Некорректный UUID: " + raw);
    }
    out.println("Вы вошли как " + currentUser);
  }

  private void logout() {
    currentUser = null;
    out.println("Текущий пользователь сброшен.");
  }

  private void create(String[] parts) {
    String url = arg(parts, 1, "create <url> [лимит]");
    Integer limit = parts.length > 2 ? parseInt(parts[2], "лимит") : null;
    boolean newUser = currentUser == null;
    UUID owner = newUser ? UUID.randomUUID() : currentUser;
    ShortLink link = service.create(owner, url, limit);
    currentUser = owner;
    if (newUser) {
      out.println("Вам присвоен UUID: " + owner + " (сохраните его, чтобы управлять ссылками)");
    }
    out.println("Короткая ссылка: " + service.shortUrl(link));
    out.println(
        "Лимит переходов: "
            + link.maxClicks()
            + ", действует до "
            + TIME_FORMAT.format(link.expiresAt()));
  }

  private void open(String shortUrl) {
    VisitResult result = service.visit(shortUrl);
    switch (result.status()) {
      case OK -> {
        out.println(
            "Переход на "
                + result.originalUrl()
                + " (осталось переходов: "
                + result.remainingClicks()
                + ")");
        try {
          browser.open(result.originalUrl());
        } catch (Exception e) {
          out.println("Не удалось открыть браузер: " + e.getMessage());
        }
      }
      case NOT_FOUND -> out.println("Ссылка не найдена.");
      case EXPIRED ->
          out.println("Срок жизни ссылки истёк, она недоступна. Создайте новую ссылку.");
      case LIMIT_REACHED ->
          out.println("Лимит переходов исчерпан, ссылка недоступна. Создайте новую ссылку.");
      default -> throw new IllegalStateException();
    }
  }

  private void list() {
    UUID user = requireUser();
    List<ShortLink> links = service.listOwn(user);
    if (links.isEmpty()) {
      out.println("У вас нет активных ссылок.");
      return;
    }
    out.printf("%-20s %-12s %-20s %s%n", "Ссылка", "Переходы", "Действует до", "Исходный URL");
    for (ShortLink l : links) {
      out.printf(
          "%-20s %-12s %-20s %s%n",
          service.shortUrl(l),
          l.clicks() + "/" + l.maxClicks(),
          TIME_FORMAT.format(l.expiresAt()),
          l.originalUrl());
    }
  }

  private void info(String shortUrl) {
    ShortLink l = service.get(shortUrl);
    out.println("Ссылка:        " + service.shortUrl(l));
    out.println("Исходный URL:  " + l.originalUrl());
    out.println("Переходы:      " + l.clicks() + " из " + l.maxClicks());
    out.println("Создана:       " + TIME_FORMAT.format(l.createdAt()));
    out.println("Действует до:  " + TIME_FORMAT.format(l.expiresAt()));
    out.println(
        "Вы владелец:   "
            + (l.isOwnedBy(currentUser == null ? new UUID(0, 0) : currentUser) ? "да" : "нет"));
  }

  private void limit(String[] parts) {
    String shortUrl = arg(parts, 1, "limit <короткая ссылка> <число>");
    int value = parseInt(arg(parts, 2, "limit <короткая ссылка> <число>"), "лимит");
    ShortLink link = service.changeLimit(requireUser(), shortUrl, value);
    out.println("Новый лимит для " + service.shortUrl(link) + ": " + link.maxClicks());
  }

  private void delete(String shortUrl) {
    service.delete(requireUser(), shortUrl);
    out.println("Ссылка удалена.");
  }

  private void printNotifications() {
    if (currentUser == null) {
      return;
    }
    for (String message : notifications.drain(currentUser)) {
      out.println("[Уведомление] " + message);
    }
  }

  private UUID requireUser() {
    if (currentUser == null) {
      throw new ValidationException(
          "Пользователь не определён. Создайте ссылку (create) или войдите (login <uuid>).");
    }
    return currentUser;
  }

  private static String arg(String[] parts, int index, String usage) {
    if (parts.length <= index) {
      throw new ValidationException("Не хватает аргументов. Использование: " + usage);
    }
    return parts[index];
  }

  private static int parseInt(String raw, String name) {
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      throw new ValidationException("Параметр «" + name + "» должен быть целым числом: " + raw);
    }
  }
}
