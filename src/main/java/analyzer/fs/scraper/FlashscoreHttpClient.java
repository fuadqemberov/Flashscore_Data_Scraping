package analyzer.fs.scraper;



import analyzer.fs.FlashscoreConfig;
import analyzer.fs.FlashscoreParser;

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

    public FlashscoreHttpClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(FlashscoreConfig.CONNECT_TIMEOUT_SEC))
            .version(HttpClient.Version.HTTP_2)
            .build();

        this.rateLimiter = new Semaphore(50);
        this.fsignToken = FlashscoreConfig.DEFAULT_FSIGN;
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
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", FlashscoreConfig.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(Duration.ofSeconds(FlashscoreConfig.REQUEST_TIMEOUT_SEC))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
            }

            Thread.sleep(FlashscoreConfig.RATE_LIMIT_MS);
            return response.body();

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
                System.out.println("\n🔄 Fsign token yenilendi: " + newToken);
            }
        } catch (Exception e) {
            System.err.println("Fsign refresh failed: " + e.getMessage());
        }
    }

    public String getFsignToken() {
        return fsignToken;
    }

    @Override
    public void close() {
        // HttpClient otomatik kapanır
    }
}
