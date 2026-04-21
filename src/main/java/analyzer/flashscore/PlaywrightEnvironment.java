package analyzer.flashscore;

import com.microsoft.playwright.*;

import java.util.List;

/**
 * 20-Year Senior Note: Her worker thread'in KENDİNE AİT bir Playwright instance'ı olmalıdır.
 * Asla instance'ları threadler arası paylaşmayın.
 */
public class PlaywrightEnvironment implements AutoCloseable {
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    public void init() {
        // Bu metod çağrıldığı thread içinde Playwright'ı hapseder.
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--no-sandbox",
                        "--disable-blink-features=AutomationControlled",
                        "--mute-audio"
                )));

        this.context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

        this.context.addInitScript("() => { " +
                                   "localStorage.setItem('ls_odds_format', '1'); " +
                                   "Object.defineProperty(navigator, 'webdriver', {get: () => undefined}); }");

        // Network optimizasyonu: SPA yapısını bozmayacak şekilde gereksizleri engelle
        this.context.route("**/*", route -> {
            String type = route.request().resourceType();
            if (List.of("image", "font", "media").contains(type)) {
                route.abort();
            } else {
                route.resume();
            }
        });

        this.page = context.newPage();
    }

    public Page getPage() {
        return page;
    }

    @Override
    public void close() {
        if (page != null) page.close();
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}