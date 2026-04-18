package analyzer.scraper_playwrith;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class PlaywrightThreadFactory implements ThreadFactory {

    private static final AtomicInteger counter = new AtomicInteger(0);

    // Her thread için ayrı factory
    public static Playwright createPlaywright() {
        Playwright pw = Playwright.create();
        return pw;
    }

    public static Browser createBrowser(Playwright playwright) {
        return playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChannel("msedge")
                .setArgs(List.of(
                        "--disable-dev-shm-usage",
                        "--no-sandbox",
                        "--disable-blink-features=AutomationControlled",
                        "--disable-extensions",
                        "--disable-background-networking",
                        "--disable-sync",
                        "--hide-scrollbars",
                        "--mute-audio",
                        "--no-first-run",
                        "--js-flags=--max-old-space-size=512"
                )));
    }

    public static BrowserContext createContext(Browser browser) {
        BrowserContext context = browser.newContext();

        context.addInitScript("""
                    () => {
                        localStorage.setItem('ls_odds_format', '1');
                        Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                    }
                """);

        // Hız optimizasyonu
        context.route("**/*", route -> {
            String type = route.request().resourceType();
            if (List.of("image", "stylesheet", "font", "media").contains(type)) {
                route.abort();
            } else {
                route.resume();
            }
        });

        return context;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "Playwright-Worker-" + counter.incrementAndGet());
        t.setDaemon(true);
        return t;
    }
}