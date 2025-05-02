package flashscore.weeklydatascraping.mackolik.main;

import flashscore.weeklydatascraping.mackolik.TeamIdFinder;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static flashscore.weeklydatascraping.mackolik.main.ModifiedScoreAnalyzer2.MatchPattern;
import static flashscore.weeklydatascraping.mackolik.main.ModifiedScoreAnalyzer2.MatchResult;

public class OptimizedScoreAnalyzer2 {

    public static void main(String[] args) throws InterruptedException, IOException {
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);

        for (String id : TeamIdFinder.readIdsFromFile()) {
            final int currentId = Integer.parseInt(id);

            WebDriver driver = MatchResult.initializeDriver();
            try {
                MatchPattern currentPattern = MatchResult.findCurrentSeasonLastTwoMatches(currentId, driver);

                // Store the pattern info but don't print it yet
                String patternInfo = "Aranan skor paterni:\n" +
                        currentPattern.homeTeam1 + "  " + currentPattern.score1 + "  " + currentPattern.awayTeam1 + "\n" +
                        currentPattern.homeTeam2 + "  " + currentPattern.score2 + "  " + currentPattern.awayTeam2;

                boolean foundAnyMatches = false;
                StringBuilder results = new StringBuilder();

                for (int year = 2023; year >= 2021; year--) {
                    String yearInfo = "\n" + year + " Sezonu Analizi:\n------------------------";
                    String years = year + "/" + (year + 1);

                    boolean foundMatchesThisYear = MatchResult.findScorePattern(currentPattern, years, currentId, driver, results);

                    if (foundMatchesThisYear) {
                        // If we found a match for this year, append the year header to results
                        results.insert(results.length() - (results.length() > 0 ? 1 : 0), yearInfo + "\n");
                        foundAnyMatches = true;
                    }
                }

                // Only print the pattern and results if we found matches
                if (foundAnyMatches) {
                    System.out.println(patternInfo);
                    System.out.println(results.toString());
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