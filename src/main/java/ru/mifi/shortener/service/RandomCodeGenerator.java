package ru.mifi.shortener.service;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Генерирует случайный код из символов Base62. Код не зависит от исходного URL, поэтому одинаковые
 * URL от разных пользователей получают разные короткие ссылки.
 */
public final class RandomCodeGenerator implements CodeGenerator {

  private static final char[] ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

  private final int length;
  private final Random random;

  public RandomCodeGenerator(int length) {
    this(length, new SecureRandom());
  }

  public RandomCodeGenerator(int length, Random random) {
    if (length <= 0) {
      throw new IllegalArgumentException("Длина кода должна быть положительной");
    }
    this.length = length;
    this.random = random;
  }

  @Override
  public String generate() {
    char[] buf = new char[length];
    for (int i = 0; i < length; i++) {
      buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return new String(buf);
  }
}
