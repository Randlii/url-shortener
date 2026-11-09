package ru.mifi.shortener.service;

/** Попытка изменить чужую ссылку. */
public class AccessDeniedException extends RuntimeException {
  public AccessDeniedException(String code) {
    super("Нет прав на изменение ссылки " + code + ": её создал другой пользователь");
  }
}
