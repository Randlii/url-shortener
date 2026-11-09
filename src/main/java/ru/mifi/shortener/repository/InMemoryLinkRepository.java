package ru.mifi.shortener.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import ru.mifi.shortener.domain.ShortLink;

/** Потокобезопасное хранилище ссылок в памяти. */
public final class InMemoryLinkRepository implements LinkRepository {

  private final Map<String, ShortLink> links = new ConcurrentHashMap<>();

  @Override
  public boolean saveIfAbsent(ShortLink link) {
    return links.putIfAbsent(link.code(), link) == null;
  }

  @Override
  public Optional<ShortLink> findByCode(String code) {
    return Optional.ofNullable(links.get(code));
  }

  @Override
  public List<ShortLink> findByOwner(UUID ownerId) {
    return links.values().stream()
        .filter(l -> l.isOwnedBy(ownerId))
        .sorted(Comparator.comparing(ShortLink::createdAt))
        .toList();
  }

  @Override
  public boolean delete(String code) {
    return links.remove(code) != null;
  }

  @Override
  public List<ShortLink> removeExpired(Instant now) {
    List<ShortLink> removed = new ArrayList<>();
    links
        .values()
        .removeIf(
            link -> {
              if (link.isExpired(now)) {
                removed.add(link);
                return true;
              }
              return false;
            });
    return removed;
  }

  @Override
  public int size() {
    return links.size();
  }
}
