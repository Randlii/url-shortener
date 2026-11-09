package ru.mifi.shortener.service;

/** Генератор кодов коротких ссылок. */
@FunctionalInterface
public interface CodeGenerator {
  String generate();
}
