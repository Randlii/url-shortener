package ru.mifi.shortener.cli;

/** Открывает URL в браузере. Выделено в интерфейс, чтобы подменять в тестах. */
@FunctionalInterface
public interface BrowserOpener {
  void open(String url) throws Exception;
}
