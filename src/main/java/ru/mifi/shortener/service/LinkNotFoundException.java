package ru.mifi.shortener.service;

/** Ссылка с указанным кодом не найдена. */
public class LinkNotFoundException extends RuntimeException {
  public LinkNotFoundException(String code) {
    super("Ссылка не найдена: " + code);
  }
}
