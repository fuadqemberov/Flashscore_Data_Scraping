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

    // YANLIŞLIKLA SİLİNEN GET METOTLARI GERİ EKLENDİ
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

            // Token expire olduysa yenile ve tekrar dene
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

            try {
                var cookieBtn = page.locator("#onetrust-accept-btn-handler");
                if (cookieBtn.isVisible()) { cookieBtn.click(); page.waitForTimeout(500); }
            } catch (Exception ignored) {}

            int prevCount = 0;
            int staleRounds = 0;
            final int MAX_STALE = 6;

            while (true) {
                int currentCount = page.locator("[id^='g_1_']").count();

                if (currentCount > prevCount) {
                    staleRounds = 0;
                    prevCount = currentCount;
                    System.out.print("\r⏳ Maçlar yükleniyor... " + currentCount + " maç");
                } else {
                    staleRounds++;
                    if (staleRounds >= MAX_STALE) break;
                }

                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(1500);

                // YENİ UI SHOW MORE TIKLAYICI
                tryClickShowMore(page, "a:has-text('Show more matches')");
                tryClickShowMore(page, "a[data-testid='wcl-buttonLink']");
                tryClickShowMore(page, ".event__more");

                page.waitForTimeout(1000);
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

    private void tryClickShowMore(Page page, String selector) {
        try {
            Locator btn = page.locator(selector).first();
            if (btn.isVisible()) {
                btn.click();
                page.waitForTimeout(500);
            }
        } catch (Exception ignored) {}
    }

    // YANLIŞLIKLA SİLİNEN TOKEN FONKSİYONLARI EKLENDİ
    private void refreshFsignToken() {
        try {
            String html = getHtml(FlashscoreConfig.DOMAIN + "/football/");
            String newToken = FlashscoreParser.parseFsignToken(html);
            if (newToken != null && !newToken.isEmpty()) {
                this.fsignToken = newToken;
                System.out.println("\n🔄 Fsign token yenilendi: " + newToken);
            }
        } catch (Exception e) {
            System.err.println("Fsign refresh failed: " + e.getMessage());
        }
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

            if (token != null && !token.isEmpty()) {
                return token;
            }
        } catch (Exception e) {
            System.err.println("Fsign fetch hatası: " + e.getMessage());
        }
        return FlashscoreConfig.DEFAULT_FSIGN;
    }

    @Override
    public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}