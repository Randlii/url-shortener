package ru.mifi.shortener.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.mifi.shortener.domain.ShortLink;

/** Хранилище коротких ссылок. */
public interface LinkRepository {

  /**
   * Сохраняет ссылку, если код ещё не занят.
   *
   * @return true, если ссылка сохранена; false, если код уже существует
   */
  boolean saveIfAbsent(ShortLink link);

  Optional<ShortLink> findByCode(String code);

  List<ShortLink> findByOwner(UUID ownerId);

  boolean delete(String code);

  /** Удаляет ссылки, срок жизни которых истёк, и возвращает их. */
  List<ShortLink> removeExpired(Instant now);

  int size();
}
