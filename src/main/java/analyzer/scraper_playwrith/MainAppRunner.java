package analyzer.scraper_playwrith;

import com.microsoft.playwright.*;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainAppRunner {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Kaç gün taransın? (1-7): ");
        int days = Integer.parseInt(scanner.nextLine().trim());

        try {
            System.out.println("\n=== FAZ 1: MAÇ LİSTESİ TOPLANIYOR ===");
            List<MatchData> pendingMatches;

            try (Playwright pw = Playwright.create();
                 Browser br = pw.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("msedge")
                         .setArgs(List.of(
                                 "--disable-dev-shm-usage",
                                 "--no-sandbox",
                                 "--disable-blink-features=AutomationControlled",
                                 "--disable-extensions",
                                 "--disable-background-networking",
                                 "--disable-sync",
                                 "--disable-translate",
                                 "--hide-scrollbars",
                                 "--metrics-recording-only",
                                 "--mute-audio",
                                 "--no-first-run",
                                 "--safebrowsing-disable-auto-update",
                                 "--js-flags=--max-old-space-size=512"
                         )));
                 BrowserContext ctx = br.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                         .setJavaScriptEnabled(true));
                 Page page = ctx.newPage()) {

                // Page crash olursa yeniden dene
                page.onCrash(p -> System.err.println("[WARN] Sayfa çöktü, yeniden deneniyor..."));

                pendingMatches = collectWithRetry(page, br, days);
            }

            if (pendingMatches.isEmpty()) {
                System.out.println("Hiç maç bulunamadı.");
                return;
            }

            System.out.println("Toplam " + pendingMatches.size() + " maç bulundu.");

            System.out.println("\n=== FAZ 2: PARALEL TARAMA ===");
            runParallelScraping(pendingMatches);

            ExcelReportService.generateReport(pendingMatches, "bet365_results.xlsx");
            System.out.println("Tamamlandı! Rapor kaydedildi.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            BrowserPool.getInstance().close();
        }
    }

    /**
     * Page crash olursa yeni page açarak tekrar dener
     */
    private static List<MatchData> collectWithRetry(Page page, Browser browser, int days) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return MatchListScraper.collectMatchesForDays(page, days);
            } catch (Exception e) {
                System.err.println("[FAZ1 RETRY " + attempt + "/" + maxAttempts + "] " + e.getMessage());
                if (attempt == maxAttempts) throw e;

                // Çöken page'i kapat, yenisini aç
                try {
                    page.close();
                } catch (Exception ignored) {}

                try {
                    Thread.sleep(3000);
                    BrowserContext newCtx = browser.newContext();
                    page = newCtx.newPage();
                } catch (Exception ignored) {}
            }
        }
        return List.of();
    }

    private static void runParallelScraping(List<MatchData> matches) {
        ExecutorService executor = Executors.newFixedThreadPool(
                ScraperConstants.MAX_CONCURRENT_DRIVERS,
                new PlaywrightThreadFactory()
        );

        for (MatchData m : matches) {
            executor.submit(() -> {
                Playwright pw = null;
                Browser br = null;
                BrowserContext ctx = null;
                Page page = null;
                try {
                    pw = PlaywrightThreadFactory.createPlaywright();
                    br = PlaywrightThreadFactory.createBrowser(pw);
                    ctx = PlaywrightThreadFactory.createContext(br);
                    page = ctx.newPage();

                    MatchDetailScraper.scrapeMatch(page, m);
                    System.out.println("[OK] " + m.homeTeam + " vs " + m.awayTeam);
                } catch (Exception e) {
                    System.err.println("[ERR] " + m.matchId + " -> " + e.getMessage());
                } finally {
                    try { if (page != null) page.close(); } catch (Exception ignored) {}
                    try { if (ctx != null) ctx.close(); } catch (Exception ignored) {}
                    try { if (br != null) br.close(); } catch (Exception ignored) {}
                    try { if (pw != null) pw.close(); } catch (Exception ignored) {}
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(4, TimeUnit.HOURS);
        } catch (Exception ignored) {}
    }
}