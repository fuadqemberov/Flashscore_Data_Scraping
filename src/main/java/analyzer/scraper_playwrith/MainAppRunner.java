package analyzer.scraper_playwrith;

import com.microsoft.playwright.Page;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainAppRunner {
    private static final CopyOnWriteArrayList<MatchData> resultList = new CopyOnWriteArrayList<>();
    private static final AtomicInteger doneCount = new AtomicInteger(0);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int days = getDaysInput(scanner);

        try {
            System.out.println("\n=== FAZ 1: MAC ID TOPLAMA ===");
            List<MatchData> pendingMatches;

            // factory ARTIK AutoCloseable ve createPage() metoduna sahip
            try (PlaywrightFactory factory = new PlaywrightFactory();
                 Page listPage = factory.createPage()) {
                pendingMatches = MatchListScraper.collectMatchesForDays(listPage, days);
            }

            if (pendingMatches.isEmpty()) return;

            System.out.println("\n=== FAZ 2: PARALEL TARAMA (" + pendingMatches.size() + ") ===");
            runParallelScraping(pendingMatches, ScraperConstants.MAX_CONCURRENT_DRIVERS);

            ExcelReportService.generateReport(resultList, "bet365.xlsx");
            System.out.println("Bitti. Kaydedildi.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runParallelScraping(List<MatchData> matches, int parallelism) {
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<Void>> futures = matches.stream()
                .map(match -> CompletableFuture.runAsync(() -> {
                    try (PlaywrightFactory factory = new PlaywrightFactory();
                         Page page = factory.createPage()) {
                        MatchDetailScraper.scrapeMatch(page, match);
                        resultList.add(match);
                        System.out.println("[OK] " + doneCount.incrementAndGet() + "/" + matches.size());
                    } catch (Exception e) {
                        System.err.println("[ERR] " + match.matchId);
                    }
                }, executor)).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
    }

    private static int getDaysInput(Scanner s) {
        System.out.print("Gun sayisi (1-7): ");
        return Integer.parseInt(s.nextLine().trim());
    }
}