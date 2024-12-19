package flashscore.weeklydatascraping.mackolik;

import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static flashscore.weeklydatascraping.mackolik.DynamicScoreAnalyzer3.MatchResult.*;

public class OptimizedMultithreadedScoreAnalyzer2 extends DynamicScoreAnalyzer3 {
    // Maksimum eşzamanlı Chrome instance sayısını sınırla
    private static final int MAX_CONCURRENT_BROWSERS = 5;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final BlockingQueue<WebDriver> driverPool = new ArrayBlockingQueue<>(MAX_CONCURRENT_BROWSERS);
    private static final AtomicInteger activeDrivers = new AtomicInteger(0);

    private static WebDriver getDriver() {
        WebDriver driver = null;
        try {
            // Önce havuzdan bir driver almayı dene
            driver = driverPool.poll();
            if (driver == null && activeDrivers.get() < MAX_CONCURRENT_BROWSERS) {
                // Havuz boşsa ve limit aşılmamışsa yeni driver oluştur
                if (activeDrivers.incrementAndGet() <= MAX_CONCURRENT_BROWSERS) {
                    driver = initializeDriver();
                } else {
                    activeDrivers.decrementAndGet();
                    // Limit aşıldıysa havuzdan bir driver gelene kadar bekle
                    driver = driverPool.take();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return driver;
    }

    private static void releaseDriver(WebDriver driver) {
        if (driver != null) {
            try {
                driverPool.put(driver);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);

        // Tüm ID'leri oku ve sadece sayısal olanları filtrele
        List<String> ids = TeamIdFinder.readIdsFromFile();
        List<Integer> numericIds = ids.stream()
                .filter(id -> id.matches("\\d+")) // Sadece sayılar
                .map(Integer::parseInt)           // Sayıya çevir
                .toList();

        // Paralel işlemler için task'ları oluştur ve gönder
        executor.submit(() -> {
            WebDriver driver = null;
            try {
                for (Integer teamId : numericIds) { // Her bir takım ID'si için işlem
                    driver = getDriver();
                    if (driver != null) {
                        try {
                            processMatch(teamId, driver); // İşleme devam
                        } finally {
                            releaseDriver(driver);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Hata: " + e.getMessage());
            }
        });

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        // Tüm driverları temizle
        driverPool.forEach(driver -> {
            try {
                driver.quit();
            } catch (Exception e) {
                // İgnore cleanup errors
            }
        });

        writeMatchesToExcel();
        System.exit(0);
    }

    private static void processMatch(int currentId, WebDriver driver) {
        try {
            MatchPattern currentPattern = findCurrentSeasonLastTwoMatches(currentId, driver);
            System.out.println("Aranan skor paterni: " + currentPattern.score1 + " -> " + currentPattern.score2);
            System.out.println("\nGeçmiş yıllarda bu pattern araştırılıyor...\n");

            for (int year = 2023; year >= 2021; year--) {
                System.out.println("\n" + year + " Sezonu Analizi:");
                System.out.println("------------------------");
                String years = year + "/" + (year + 1);
                findScorePattern(currentPattern, years, currentId, driver);
            }
        } catch (Exception e) {
            System.err.println("Match processing error for ID " + currentId + ": " + e.getMessage());
        }
    }
}