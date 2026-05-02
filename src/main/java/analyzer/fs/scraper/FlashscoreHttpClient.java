package analyzer.fs.scraper;

import analyzer.fs.util.FlashscoreConfig;
import analyzer.fs.util.FlashscoreParser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;

public class FlashscoreHttpClient implements AutoCloseable {

    private final HttpClient httpClient;
    private final Semaphore rateLimiter;
    private volatile String fsignToken;
    private com.microsoft.playwright.Playwright playwright;
    private com.microsoft.playwright.Browser browser;

    public FlashscoreHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(FlashscoreConfig.CONNECT_TIMEOUT_SEC))
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.rateLimiter = new Semaphore(50);

        this.playwright = com.microsoft.playwright.Playwright.create();
        this.browser = playwright.chromium().launch(
                new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true)
        );

        this.fsignToken = fetchFsignFromBrowser();
    }

    public String get(String endpoint) throws Exception {
        return get(endpoint, true);
    }

    public String get(String endpoint, boolean useFsign) throws Exception {
        rateLimiter.acquire();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(FlashscoreConfig.API_BASE + endpoint))
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", FlashscoreConfig.DOMAIN + "/")
                    .header("User-Agent", FlashscoreConfig.USER_AGENT)
                    .timeout(Duration.ofSeconds(FlashscoreConfig.REQUEST_TIMEOUT_SEC))
                    .GET();

            if (useFsign) {
                builder.header("X-Fsign", fsignToken);
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 403) {
                refreshFsignToken();
                builder.header("X-Fsign", fsignToken);
                response = httpClient.send(
                        builder.build(), HttpResponse.BodyHandlers.ofString()
                );
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode() + " for " + endpoint);
            }

            Thread.sleep(FlashscoreConfig.RATE_LIMIT_MS);
            return response.body();

        } finally {
            rateLimiter.release();
        }
    }

    public String getHtml(String url) throws Exception {
        rateLimiter.acquire();
        try {
            var context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent(FlashscoreConfig.USER_AGENT));
            var page = context.newPage();
            page.setDefaultTimeout(60000);

            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
            } catch (PlaywrightException e) {
                page.close(); context.close();
                throw new RuntimeException("Bağlantı başarısız: " + url, e);
            }

            // Çerezleri kapat
            try {
                var cookieBtn = page.locator("#onetrust-accept-btn-handler");
                if (cookieBtn.isVisible()) { cookieBtn.click(); page.waitForTimeout(500); }
            } catch (Exception ignored) {}

            // İlk yükleme için bekle
            page.waitForTimeout(2000);

            int prevCount = 0;
            int staleRounds = 0;
            final int MAX_STALE = 10;
            boolean clickedLastRound = false;

            while (true) {
                int currentCount = page.locator("[id^='g_1_']").count();

                if (currentCount > prevCount) {
                    staleRounds = 0;
                    prevCount = currentCount;
                    clickedLastRound = false;
                    System.out.print("\r⏳ Maçlar yükleniyor... " + currentCount + " maç bulundu");
                } else {
                    if (clickedLastRound) {
                        // Tıklama etkisi gecikmiş olabilir, daha uzun bekle
                        page.waitForTimeout(2000);
                        clickedLastRound = false;
                        continue;
                    }
                    staleRounds++;
                    if (staleRounds >= MAX_STALE) break;
                }

                // Sayfayı en alta kaydır
                page.evaluate("window.scrollTo(0, document.documentElement.scrollHeight)");
                page.waitForTimeout(500);

                boolean clicked = false;

                // Ana selector: button elementi (NOT <a>!)
                try {
                    Locator btn = page.locator("button[data-testid='wcl-buttonLink']").last();
                    if (btn.count() > 0 && btn.isVisible()) {
                        btn.scrollIntoViewIfNeeded();
                        page.waitForTimeout(300);
                        btn.click(new Locator.ClickOptions().setForce(true));
                        clicked = true;
                    }
                } catch (Exception ignored) {}

                // Yedek selector 1: metne göre button
                if (!clicked) {
                    try {
                        Locator textBtn = page.locator("button:has-text('Show more matches')").last();
                        if (textBtn.count() > 0 && textBtn.isVisible()) {
                            textBtn.scrollIntoViewIfNeeded();
                            page.waitForTimeout(300);
                            textBtn.click(new Locator.ClickOptions().setForce(true));
                            clicked = true;
                        }
                    } catch (Exception ignored) {}
                }

                // Yedek selector 2: wcl-section-footer içindeki buton
                if (!clicked) {
                    try {
                        Locator footerBtn = page.locator("[data-testid='wcl-section-footer'] button").last();
                        if (footerBtn.count() > 0 && footerBtn.isVisible()) {
                            footerBtn.scrollIntoViewIfNeeded();
                            page.waitForTimeout(300);
                            footerBtn.click(new Locator.ClickOptions().setForce(true));
                            clicked = true;
                        }
                    } catch (Exception ignored) {}
                }

                if (clicked) {
                    staleRounds = 0;
                    clickedLastRound = true;
                    page.waitForTimeout(3000);
                } else {
                    page.waitForTimeout(1000);
                }
            }

            System.out.println("\n✅ Tüm maçlar yüklendi. Toplam: " + prevCount + " maç");

            String html = page.content();
            page.close();
            context.close();

            return html;
        } finally {
            rateLimiter.release();
        }
    }

    public String getHtmlWithSeasonDropdown(String url) throws Exception {
        rateLimiter.acquire();
        try {
            var context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent(FlashscoreConfig.USER_AGENT));
            var page = context.newPage();
            page.setDefaultTimeout(60000);
            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
            page.waitForTimeout(3000);
            String html = page.content();
            page.close();
            context.close();
            return html;
        } finally {
            rateLimiter.release();
        }
    }

    private void refreshFsignToken() {
        try {
            String html = getHtml(FlashscoreConfig.DOMAIN + "/football/");
            String newToken = FlashscoreParser.parseFsignToken(html);
            if (newToken != null && !newToken.isEmpty()) {
                this.fsignToken = newToken;
            }
        } catch (Exception ignored) {}
    }

    public String getFsignToken() {
        return fsignToken;
    }

    private String fetchFsignFromBrowser() {
        try {
            var context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent(FlashscoreConfig.USER_AGENT));
            var page = context.newPage();
            page.navigate(FlashscoreConfig.DOMAIN + "/football/");
            page.waitForTimeout(3000);

            String token = (String) page.evaluate(
                    "() => { " +
                            "  const scripts = document.querySelectorAll('script'); " +
                            "  for (let s of scripts) { " +
                            "    const m = s.textContent.match(/fsign[\"\\s:]+['\"]([^'\"]+)['\"]/); " +
                            "    if (m) return m[1]; " +
                            "  } " +
                            "  return null; " +
                            "}"
            );
            page.close();
            context.close();
            if (token != null && !token.isEmpty()) return token;
        } catch (Exception ignored) {}
        return FlashscoreConfig.DEFAULT_FSIGN;
    }

    @Override
    public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}