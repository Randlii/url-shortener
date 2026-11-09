package ru.mifi.shortener.service;

/** Ошибка во входных данных пользователя. */
public class ValidationException extends RuntimeException {
  public ValidationException(String message) {
    super(message);
  }
}
