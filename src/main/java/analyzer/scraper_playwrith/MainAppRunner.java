package analyzer.scraper_playwrith;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

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
                 Browser br = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                 BrowserContext ctx = br.newContext();
                 Page page = ctx.newPage()) {

                pendingMatches = MatchListScraper.collectMatchesForDays(page, days);
            }

            if (pendingMatches.isEmpty()) {
                System.out.println("Hiç maç bulunamadı.");
                return;
            }

            System.out.println("Toplam " + pendingMatches.size() + " maç bulundu.");

            System.out.println("\n=== FAZ 2: PARALEL TARAMA ===");
            runParallelScraping(pendingMatches);

            ExcelReportService.generateReport(pendingMatches, "bet365_results.xlsx"); // resultList yerine pendingMatches kullanılıyor, MatchData içinde oddsMap var
            System.out.println("Tamamlandı! Rapor kaydedildi.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            BrowserPool.getInstance().close();
        }
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
                    try {
                        if (page != null) page.close();
                        if (ctx != null) ctx.close();
                        if (br != null) br.close();
                        if (pw != null) pw.close();
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(4, TimeUnit.HOURS);
        } catch (Exception ignored) {
        }
    }
}