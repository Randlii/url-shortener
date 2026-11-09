package ru.mifi.shortener.cli;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

/** Открывает ссылку в системном браузере через java.awt.Desktop. */
public final class DesktopBrowserOpener implements BrowserOpener {

  @Override
  public void open(String url) throws Exception {
    if (GraphicsEnvironment.isHeadless()
        || !Desktop.isDesktopSupported()
        || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      throw new UnsupportedOperationException(
          "открытие браузера не поддерживается в этом окружении");
    }
    Desktop.getDesktop().browse(new URI(url));
  }
}
