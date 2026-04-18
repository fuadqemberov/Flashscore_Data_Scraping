package analyzer.scraper_playwrith;

import com.microsoft.playwright.*;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

public class BrowserPool implements AutoCloseable {

    private static volatile BrowserPool instance;

    private final Playwright playwright;
    private final Browser browser;
    private final BlockingQueue<BrowserContext> contextPool;
    private final Semaphore semaphore;

    private BrowserPool(int poolSize) {
        this.playwright = Playwright.create();

        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChannel("msedge")
                .setArgs(List.of(
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--no-sandbox",
                        "--disable-blink-features=AutomationControlled"
                )));

        this.contextPool = new LinkedBlockingQueue<>(poolSize);
        this.semaphore = new Semaphore(poolSize);

        // Pool'u doldur
        for (int i = 0; i < poolSize; i++) {
            contextPool.offer(createNewContext());
        }
    }

    public static BrowserPool getInstance() {
        if (instance == null) {
            synchronized (BrowserPool.class) {
                if (instance == null) {
                    instance = new BrowserPool(ScraperConstants.MAX_CONCURRENT_DRIVERS);
                }
            }
        }
        return instance;
    }

    private BrowserContext createNewContext() {
        BrowserContext context = browser.newContext();

        // Decimal odds + anti-detection
        context.addInitScript("""
                    () => {
                        localStorage.setItem('ls_odds_format', '1');
                        Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                    }
                """);

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

    /**
     * Context kiralama
     */
    public BrowserContext acquire() {
        try {
            semaphore.acquire();
            BrowserContext ctx = contextPool.poll();
            if (ctx == null) {
                ctx = createNewContext(); // acil durum
            }
            return ctx;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Context'i geri verme
     */
    public void release(BrowserContext context) {
        if (context != null) {
            try {
                // Sayfaları temizle
                context.pages().forEach(Page::close);
                contextPool.offer(context);
            } finally {
                semaphore.release();
            }
        }
    }

    @Override
    public void close() {
        try {
            contextPool.forEach(BrowserContext::close);
            browser.close();
            playwright.close();
        } catch (Exception ignored) {
        }
    }
}