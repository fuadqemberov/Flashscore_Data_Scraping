package flashscore.weeklydatascraping.mackolik.claude;

import flashscore.weeklydatascraping.mackolik.TeamIdFinder;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static flashscore.weeklydatascraping.mackolik.claude.ModifiedScoreAnalyzer.MatchPattern;
import static flashscore.weeklydatascraping.mackolik.claude.ModifiedScoreAnalyzer.MatchResult;

public class OptimizedScoreAnalyzer {

    public static void main(String[] args) throws InterruptedException, IOException {
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);

        for (String id : TeamIdFinder.readIdsFromFile()) {
            final int currentId = Integer.parseInt(id);

            WebDriver driver = MatchResult.initializeDriver();
            try {
                MatchPattern currentPattern = MatchResult.findCurrentSeasonLastTwoMatches(currentId, driver);
                System.out.println("Aranan skor paterni:");
                System.out.println(currentPattern.homeTeam1 + "  " + currentPattern.score1 + "  " + currentPattern.awayTeam1);
                System.out.println(currentPattern.homeTeam2 + "  " + currentPattern.score2 + "  " + currentPattern.awayTeam2);

                for (int year = 2023; year >= 2021; year--) {
                    System.out.println("\n" + year + " Sezonu Analizi:");
                    System.out.println("------------------------");
                    String years = year + "/" + (year + 1);
                    MatchResult.findScorePattern(currentPattern, years, currentId, driver);
                }

                driver.quit();
            } catch (Exception e) {
                System.err.println("Hata: " + e.getMessage());
                driver.quit();
            }
        }

        System.exit(0);
    }
}