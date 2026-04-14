package analyzer.flashscore.gui;


import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class MainAppRunner {

    private static final CopyOnWriteArrayList<MatchData> resultList = new CopyOnWriteArrayList<>();
    private static final AtomicInteger doneCount = new AtomicInteger(0);

    static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        int days = getDaysInput(scanner);

        System.out.println("\n=== FAZ 1: MAC ID'LERI TOPLANIYOR ===");
        WebDriver listDriver = DriverFactory.createHeadlessDriver();
        List<MatchData> pendingMatches;
        try {
            pendingMatches = MatchListScraper.collectMatchesForDays(listDriver, days);
        } finally {
            listDriver.quit();
        }

        System.out.println("\n=== FAZ 2: " + pendingMatches.size() + " MAC PARALEL OLARAK TARANIYOR ===");
        runParallelScraping(pendingMatches);

        System.out.println("\n=== FAZ 3: EXCEL RAPORU OLUSTURULUYOR ===");
        ExcelReportService.generateReport(resultList, "bet365.xlsx");
        System.out.println("Islem tamamlandi!");
    }

    private static void runParallelScraping(List<MatchData> matches) throws InterruptedException {
        Semaphore semaphore = new Semaphore(ScraperConstants.MAX_CONCURRENT_DRIVERS, true);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            for (MatchData match : matches) {
                executor.submit(() -> {
                    semaphore.acquireUninterruptibly(); // Slot bekle
                    WebDriver driver = null;
                    try {
                        driver = DriverFactory.createHeadlessDriver();
                        driver.get(ScraperConstants.BASE_URL);
                        WaitActionUtils.smartClick(driver, By.id("onetrust-accept-btn-handler"), 3);
                        OddsConfigurator.configureDecimalOdds(driver);

                        MatchDetailScraper.scrapeMatch(driver, match);
                        resultList.add(match);

                        System.out.printf("  [OK %d/%d] %s vs %s%n", doneCount.incrementAndGet(), matches.size(), match.homeTeam, match.awayTeam);
                    } catch (Exception e) {
                        System.out.printf("  [ERR] %s - %s%n", match.homeTeam, e.getMessage());
                        doneCount.incrementAndGet();
                    } finally {
                        if (driver != null) driver.quit();
                        semaphore.release(); // Slotu serbest bırak
                    }
                });
            }
        } // try-with-resources executor'un bitmesini otomatik bekler (awaitTermination)
    }

    private static int getDaysInput(Scanner scanner) {
        int days = 0;
        while (days < 1 || days > 7) {
            System.out.print("Kac gunun datasini cekmek istiyorsunuz? (1-7): ");
            try {
                days = Integer.parseInt(scanner.nextLine().trim());
            } catch (Exception ignored) {
            }
        }
        return days;
    }
}