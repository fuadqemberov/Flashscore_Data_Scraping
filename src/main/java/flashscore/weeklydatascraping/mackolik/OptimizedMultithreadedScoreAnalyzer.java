package flashscore.weeklydatascraping.mackolik;

import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static flashscore.weeklydatascraping.mackolik.DynamicScoreAnalyzer.MatchResult.*;
import static flashscore.weeklydatascraping.mackolik.TeamIdFinder.readIdsFromFile;

public class OptimizedMultithreadedScoreAnalyzer extends DynamicScoreAnalyzer {
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws InterruptedException, IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);
        for (String id : readIdsFromFile()) {
            final int currentId = Integer.parseInt(id);

            executor.submit(() -> {
                WebDriver driver = initializeDriver();
                try {
                    MatchPattern currentPattern = findCurrentSeasonLastTwoMatches(currentId,driver);
                    System.out.println("Aranan skor paterni: " + currentPattern.score1 + " -> " + currentPattern.score2);
                    System.out.println("\nGeçmiş yıllarda bu pattern araştırılıyor...\n");

                    for (int year = 2023; year >= 2021; year--) {
                        System.out.println("\n" + year + " Sezonu Analizi:");
                        System.out.println("------------------------");
                        String years = year + "/" + (year + 1);
                        findScorePattern(currentPattern, years, currentId,driver);
                    }
                    driver.quit();
                } catch (Exception e) {
                    System.err.println("Hata: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        writeMatchesToExcel();
        System.exit(0);
    }
}