package ru.mifi.shortener.domain;

/** Результат попытки перехода по короткой ссылке. */
public enum LinkStatus {
  OK,
  NOT_FOUND,
  EXPIRED,
  LIMIT_REACHED
}
