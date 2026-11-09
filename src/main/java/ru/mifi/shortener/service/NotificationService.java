package ru.mifi.shortener.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Хранит уведомления для пользователей, пока они их не прочитают. */
public final class NotificationService {

  private final Map<UUID, List<String>> inbox = new ConcurrentHashMap<>();

  public void notify(UUID userId, String message) {
    inbox.computeIfAbsent(userId, k -> new ArrayList<>());
    synchronized (inbox.get(userId)) {
      inbox.get(userId).add(message);
    }
  }

  /** Возвращает и очищает все непрочитанные уведомления пользователя. */
  public List<String> drain(UUID userId) {
    List<String> messages = inbox.remove(userId);
    if (messages == null) {
      return List.of();
    }
    synchronized (messages) {
      return List.copyOf(messages);
    }
  }

  public int pendingCount(UUID userId) {
    List<String> messages = inbox.get(userId);
    return messages == null ? 0 : messages.size();
  }
}
