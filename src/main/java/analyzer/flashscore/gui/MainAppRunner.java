package analyzer.flashscore.gui;

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

    static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        int days = getDaysInput(scanner);

        // ── FAZ 1: Match ID topla ──────────────────────────────────────────
        System.out.println("\n=== FAZ 1: MAC ID'LERI TOPLANIYOR ===");
        DriverPool listPool = DriverPool.create(1);
        List<MatchData> pendingMatches;
        try {
            var listDriver = listPool.borrow();
            try {
                pendingMatches = MatchListScraper.collectMatchesForDays(listDriver, days);
            } finally {
                listPool.returnDriver(listDriver);
            }
        } finally {
            listPool.shutdown();
        }
        System.out.println("Toplam " + pendingMatches.size() + " mac tapildi.");

        // ── FAZ 2: Driver pool yarat + paralel scrape ──────────────────────
        System.out.println("\n=== FAZ 2: " + pendingMatches.size() + " MAC PARALEL OLARAK TARANIYOR ===");
        int poolSize = Math.min(ScraperConstants.MAX_CONCURRENT_DRIVERS, pendingMatches.size());
        DriverPool pool = DriverPool.create(poolSize);

        try {
            runParallelScraping(pendingMatches, pool, poolSize);
        } finally {
            pool.shutdown();
        }

        // ── FAZ 3: Excel raporu ────────────────────────────────────────────
        System.out.println("\n=== FAZ 3: EXCEL RAPORU OLUSTURULUYOR ===");
        ExcelReportService.generateReport(resultList, "bet365.xlsx");
        System.out.println("Islem tamamlandi! Toplam " + resultList.size() + " mac yazildi.");
    }

    private static void runParallelScraping(List<MatchData> matches,
                                            DriverPool pool,
                                            int parallelism) throws InterruptedException {
        int total = matches.size();

        // Virtual thread pool — parallelism pool boyutuyla sınırlı
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);

        // Her match için bir CompletableFuture yarat
        List<CompletableFuture<Void>> futures = matches.stream()
                .map(match -> CompletableFuture.runAsync(() -> {
                    var driver = borrowSafe(pool);
                    if (driver == null) {
                        System.out.printf("  [ERR] Driver alinamadi: %s%n", match.matchId);
                        doneCount.incrementAndGet();
                        return;
                    }
                    try {
                        MatchDetailScraper.scrapeMatch(driver, match);
                        resultList.add(match);
                        System.out.printf("  [OK %d/%d] %s vs %s%n",
                                doneCount.incrementAndGet(), total,
                                match.homeTeam, match.awayTeam);
                    } catch (Exception e) {
                        System.out.printf("  [ERR %d/%d] %s - %s%n",
                                doneCount.incrementAndGet(), total,
                                match.matchId, e.getMessage());
                    } finally {
                        pool.returnDriver(driver);
                    }
                }, executor))
                .toList();

        // Hamısı bitənə qədər gözlə
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
    }

    /** InterruptedException-i udub null qaytarır — lambda içindən çağırmaq üçün */
    private static org.openqa.selenium.WebDriver borrowSafe(DriverPool pool) {
        try {
            return pool.borrow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static int getDaysInput(Scanner scanner) {
        int days = 0;
        while (days < 1 || days > 7) {
            System.out.print("Kac gunun datasini cekmek istiyorsunuz? (1-7): ");
            try {
                days = Integer.parseInt(scanner.nextLine().trim());
            } catch (Exception ignored) {}
        }
        return days;
    }
}