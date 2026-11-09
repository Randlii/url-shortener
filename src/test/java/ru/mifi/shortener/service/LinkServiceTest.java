package ru.mifi.shortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mifi.shortener.MutableClock;
import ru.mifi.shortener.config.AppConfig;
import ru.mifi.shortener.domain.LinkStatus;
import ru.mifi.shortener.domain.ShortLink;
import ru.mifi.shortener.domain.VisitResult;
import ru.mifi.shortener.repository.InMemoryLinkRepository;

class LinkServiceTest {

  private static final String URL = "https://www.baeldung.com/java-9-http-client";

  private MutableClock clock;
  private InMemoryLinkRepository repository;
  private NotificationService notifications;
  private LinkService service;
  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
    repository = new InMemoryLinkRepository();
    notifications = new NotificationService();
    AppConfig config =
        new AppConfig("clck.ru/", 6, Duration.ofHours(24), 10, 100, Duration.ofSeconds(60));
    service = new LinkService(repository, new RandomCodeGenerator(6), notifications, config, clock);
  }

  @Test
  void sameUrlFromDifferentUsersGivesDifferentLinks() {
    ShortLink a = service.create(alice, URL, null);
    ShortLink b = service.create(bob, URL, null);
    assertNotEquals(a.code(), b.code());
    assertEquals(URL, a.originalUrl());
    assertEquals(URL, b.originalUrl());
  }

  @Test
  void sameUserCanHaveSeveralLinks() {
    service.create(alice, URL, null);
    service.create(alice, URL, null);
    service.create(alice, "https://example.com", null);
    assertEquals(3, service.listOwn(alice).size());
    assertEquals(0, service.listOwn(bob).size());
  }

  @Test
  void manyLinksHaveUniqueCodes() {
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 5000; i++) {
      codes.add(service.create(alice, URL, null).code());
    }
    assertEquals(5000, codes.size());
  }

  @Test
  void collisionIsRetried() {
    Iterator<String> codes = List.of("AAAAAA", "AAAAAA", "BBBBBB").iterator();
    AppConfig config =
        new AppConfig("clck.ru/", 6, Duration.ofHours(24), 10, 100, Duration.ofSeconds(60));
    LinkService s = new LinkService(repository, codes::next, notifications, config, clock);
    assertEquals("AAAAAA", s.create(alice, URL, null).code());
    assertEquals("BBBBBB", s.create(bob, URL, null).code());
  }

  @Test
  void defaultLimitTakenFromConfig() {
    assertEquals(10, service.create(alice, URL, null).maxClicks());
  }

  @Test
  void visitReturnsOriginalUrlAndCountsClicks() {
    ShortLink link = service.create(alice, URL, 3);
    VisitResult r = service.visit("clck.ru/" + link.code());
    assertTrue(r.isOk());
    assertEquals(URL, r.originalUrl());
    assertEquals(2, r.remainingClicks());
    assertEquals(1, link.clicks());
  }

  @Test
  void linkIsBlockedWhenLimitReachedAndOwnerNotified() {
    ShortLink link = service.create(alice, URL, 2);
    assertTrue(service.visit(link.code()).isOk());
    assertTrue(service.visit(link.code()).isOk());
    assertEquals(LinkStatus.LIMIT_REACHED, service.visit(link.code()).status());
    assertEquals(2, link.clicks());
    List<String> msgs = notifications.drain(alice);
    assertEquals(1, msgs.size());
    assertTrue(msgs.get(0).contains("Лимит переходов"));
  }

  @Test
  void expiredLinkIsUnavailableAndRemoved() {
    ShortLink link = service.create(alice, URL, 5);
    clock.advance(Duration.ofHours(24));
    assertEquals(LinkStatus.EXPIRED, service.visit(link.code()).status());
    assertEquals(0, repository.size());
    assertTrue(notifications.drain(alice).get(0).contains("Срок жизни"));
    assertEquals(LinkStatus.NOT_FOUND, service.visit(link.code()).status());
  }

  @Test
  void linkWorksJustBeforeExpiry() {
    ShortLink link = service.create(alice, URL, 5);
    clock.advance(Duration.ofHours(24).minusSeconds(1));
    assertTrue(service.visit(link.code()).isOk());
  }

  @Test
  void cleanupRemovesOnlyExpiredLinks() {
    service.create(alice, URL, 5);
    clock.advance(Duration.ofHours(12));
    service.create(bob, URL, 5);
    clock.advance(Duration.ofHours(13));
    assertEquals(1, service.cleanupExpired());
    assertEquals(1, repository.size());
    assertEquals(1, notifications.pendingCount(alice));
    assertEquals(0, notifications.pendingCount(bob));
  }

  @Test
  void onlyOwnerCanChangeLimit() {
    ShortLink link = service.create(alice, URL, 5);
    assertThrows(AccessDeniedException.class, () -> service.changeLimit(bob, link.code(), 50));
    assertEquals(50, service.changeLimit(alice, link.code(), 50).maxClicks());
  }

  @Test
  void raisingLimitUnblocksLink() {
    ShortLink link = service.create(alice, URL, 1);
    service.visit(link.code());
    assertEquals(LinkStatus.LIMIT_REACHED, service.visit(link.code()).status());
    service.changeLimit(alice, link.code(), 2);
    assertTrue(service.visit(link.code()).isOk());
  }

  @Test
  void limitCannotBeLowerThanClicks() {
    ShortLink link = service.create(alice, URL, 5);
    service.visit(link.code());
    service.visit(link.code());
    assertThrows(ValidationException.class, () -> service.changeLimit(alice, link.code(), 1));
  }

  @Test
  void onlyOwnerCanDelete() {
    ShortLink link = service.create(alice, URL, 5);
    assertThrows(AccessDeniedException.class, () -> service.delete(bob, link.code()));
    service.delete(alice, link.code());
    assertEquals(LinkStatus.NOT_FOUND, service.visit(link.code()).status());
  }

  @Test
  void invalidLimitsRejected() {
    assertThrows(ValidationException.class, () -> service.create(alice, URL, 0));
    assertThrows(ValidationException.class, () -> service.create(alice, URL, -3));
    assertThrows(ValidationException.class, () -> service.create(alice, URL, 101));
  }

  @Test
  void invalidUrlsRejected() {
    assertThrows(ValidationException.class, () -> service.create(alice, "", null));
    assertThrows(ValidationException.class, () -> service.create(alice, "ftp://x.ru", null));
    assertThrows(ValidationException.class, () -> service.create(alice, "http://", null));
    assertThrows(ValidationException.class, () -> service.create(alice, "not a url", null));
    assertThrows(ValidationException.class, () -> service.create(alice, "https://abc", null));
  }

  @Test
  void urlWithoutSchemeGetsHttps() {
    assertEquals("https://ya.ru", service.create(alice, "ya.ru", null).originalUrl());
  }

  @Test
  void extractCodeAcceptsDifferentForms() {
    assertEquals("abc123", service.extractCode("abc123"));
    assertEquals("abc123", service.extractCode("clck.ru/abc123"));
    assertEquals("abc123", service.extractCode("https://clck.ru/abc123"));
    assertThrows(ValidationException.class, () -> service.extractCode("clck.ru/"));
  }

  @Test
  void concurrentVisitsNeverExceedLimit() throws Exception {
    ShortLink link = service.create(alice, URL, 50);
    Thread[] threads = new Thread[8];
    int[] ok = new int[1];
    for (int i = 0; i < threads.length; i++) {
      threads[i] =
          new Thread(
              () -> {
                for (int j = 0; j < 100; j++) {
                  if (service.visit(link.code()).isOk()) {
                    synchronized (ok) {
                      ok[0]++;
                    }
                  }
                }
              });
      threads[i].start();
    }
    for (Thread t : threads) {
      t.join();
    }
    assertEquals(50, ok[0]);
    assertFalse(link.tryRegisterClick());
  }
}
