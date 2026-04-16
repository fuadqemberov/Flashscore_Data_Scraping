package analyzer.scraper_playwrith;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import java.util.Arrays;
import java.util.Collections;

public class PlaywrightFactory implements AutoCloseable {
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;

    public PlaywrightFactory() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChannel("msedge")
                .setArgs(Arrays.asList("--disable-dev-shm-usage", "--disable-gpu", "--no-sandbox")));

        this.context = browser.newContext();

        // 🚀 KİLİT 1: Sayfa daha açılmadan .co.uk için ondalık ayarını enjekte et
        this.context.addInitScript("() => { localStorage.setItem('ls_odds_format', '1'); }");

        this.context.addCookies(Collections.singletonList(
                new Cookie("fs_odds_format", "1").setDomain(".flashscore.co.uk").setPath("/")
        ));

        // Hız için gereksizleri engelle
        this.context.route("**/*", route -> {
            String type = route.request().resourceType();
            if (Arrays.asList("image", "stylesheet", "font", "media").contains(type)) {
                route.abort();
            } else {
                route.resume();
            }
        });
    }

    public Page createPage() {
        return context.newPage();
    }

    @Override
    public void close() {
        try {
            if (context != null) context.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        } catch (Exception ignored) {}
    }
}