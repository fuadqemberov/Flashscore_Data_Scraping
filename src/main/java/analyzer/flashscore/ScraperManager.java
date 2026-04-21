package analyzer.flashscore;

import com.microsoft.playwright.Page;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScraperManager {
    // SADECE 6 Thread aynı anda çalışabilir. Kuyrukta bekleyenler thread açmaz.
    private static final ExecutorService executor = Executors.newFixedThreadPool(
            ScraperConstants.MAX_CONCURRENT_DRIVERS,
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ScraperWorker-" + counter.getAndIncrement());
                    t.setDaemon(true); // Main uygulama kapanınca bunlar da anında ölsün
                    return t;
                }
            }
    );

    /**
     * Bu metodu maç detaylarını çekerken kullanacaksın.
     */
    public static void submitMatchScrapeTask(MatchData match) {
        executor.submit(() -> {
            // Try-with-resources kullanarak thread bitince GC beklemeden Memory'i boşaltıyoruz.
            try (PlaywrightEnvironment env = new PlaywrightEnvironment()) {
                env.init();
                Page page = env.getPage();

                AppLogger.log("Scraping started for: " + match.homeTeam + " vs " + match.awayTeam);

                // TODO: Maç detaylarını çekme mantığını buraya yaz
                // page.navigate(ScraperConstants.MATCH_URL_PREFIX + match.matchId);

            } catch (Exception e) {
                AppLogger.log("Error scraping match " + match.matchId + ": " + e.getMessage());
            }
        });
    }

    public static void shutdown() {
        executor.shutdown();
    }
}
