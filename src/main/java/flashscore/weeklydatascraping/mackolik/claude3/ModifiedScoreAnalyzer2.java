package flashscore.weeklydatascraping.mackolik.claude3;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class ModifiedScoreAnalyzer2 {
    public static class MatchPattern {
        public String score1;
        public String score2;
        public String homeTeam1;
        public String awayTeam1;
        public String homeTeam2;
        public String awayTeam2;

        public MatchPattern(String score1, String score2, String homeTeam1, String awayTeam1, String homeTeam2, String awayTeam2) {
            this.score1 = score1;
            this.score2 = score2;
            this.homeTeam1 = homeTeam1;
            this.awayTeam1 = awayTeam1;
            this.homeTeam2 = homeTeam2;
            this.awayTeam2 = awayTeam2;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s\n%s %s %s",
                    homeTeam1, score1, awayTeam1,
                    homeTeam2, score2, awayTeam2);
        }

        // Get all team names in the pattern
        public Set<String> getAllTeams() {
            Set<String> teams = new HashSet<>();
            teams.add(homeTeam1);
            teams.add(awayTeam1);
            teams.add(homeTeam2);
            teams.add(awayTeam2);
            return teams;
        }
    }

    public static class MatchResult {
        String homeTeam;
        String awayTeam;
        String score;
        String previousMatchScore;
        String previousHTScore;
        String nextMatchScore;
        String nextHTScore;
        String season;
        String patternScore1;
        String patternScore2;
        String patternHome1;
        String patternAway1;
        String patternHome2;
        String patternAway2;

        String firstMatchHTScore;
        String secondMatchHomeTeam;
        String secondMatchScore;
        String secondMatchAwayTeam;
        String secondMatchHTScore;

        public MatchResult(String homeTeam, String awayTeam, String score, String season) {
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.score = score;
            this.season = season;
        }

        @Override
        public String toString() {
            String prevMatch = previousMatchScore != null ?
                    previousMatchScore + " (HT: " + (previousHTScore != null ? previousHTScore : "N/A") + ")" :
                    "Bilgi Yok";

            String nextMatch = nextMatchScore != null ?
                    nextMatchScore + " (HT: " + (nextHTScore != null ? nextHTScore : "N/A") + ")" :
                    "Bilgi Yok";

            String matchPattern1 = homeTeam + " " + score + " " + awayTeam +
                    " (HT: " + (firstMatchHTScore != null ? firstMatchHTScore : "N/A") + ")";

            String matchPattern2 = secondMatchHomeTeam != null ?
                    secondMatchHomeTeam + " " + secondMatchScore + " " + secondMatchAwayTeam +
                            " (HT: " + (secondMatchHTScore != null ? secondMatchHTScore : "N/A") + ")" :
                    "Bilgi Yok";

            return String.format("önceki maç -> %s\npattern maçları -> %s\n                 %s\nsonraki maç -> %s",
                    prevMatch,
                    matchPattern1,
                    matchPattern2,
                    nextMatch);
        }

        // Check if this match result contains at least one of the original teams playing against the same opponent
        public boolean containsOriginalTeamVsOpponent(MatchPattern originalPattern) {
            // Check if first match has a team playing against the same opponent as in the original pattern
            if ((homeTeam.equals(originalPattern.homeTeam1) && awayTeam.equals(originalPattern.awayTeam1)) ||
                    (homeTeam.equals(originalPattern.awayTeam1) && awayTeam.equals(originalPattern.homeTeam1)) ||
                    (homeTeam.equals(originalPattern.homeTeam2) && awayTeam.equals(originalPattern.awayTeam2)) ||
                    (homeTeam.equals(originalPattern.awayTeam2) && awayTeam.equals(originalPattern.homeTeam2))) {
                return true;
            }

            // Check if second match has a team playing against the same opponent as in the original pattern
            if ((secondMatchHomeTeam.equals(originalPattern.homeTeam1) && secondMatchAwayTeam.equals(originalPattern.awayTeam1)) ||
                    (secondMatchHomeTeam.equals(originalPattern.awayTeam1) && secondMatchAwayTeam.equals(originalPattern.homeTeam1)) ||
                    (secondMatchHomeTeam.equals(originalPattern.homeTeam2) && secondMatchAwayTeam.equals(originalPattern.awayTeam2)) ||
                    (secondMatchHomeTeam.equals(originalPattern.awayTeam2) && secondMatchAwayTeam.equals(originalPattern.homeTeam2))) {
                return true;
            }

            return false;
        }

        public static MatchPattern findCurrentSeasonLastTwoMatches(int id, WebDriver driver) throws InterruptedException {
            String link = "https://arsiv.mackolik.com/Team/Default.aspx?id="+ id +"&season=2024/2025";
            driver.get(link);
            Thread.sleep(2000);

            WebElement table = driver.findElement(By.xpath("//*[@id=\"tblFixture\"]/tbody"));
            List<WebElement> matchElements = table.findElements(By.tagName("tr"));

            List<String> allScores = new ArrayList<>();
            List<String> allHomeTeams = new ArrayList<>();
            List<String> allAwayTeams = new ArrayList<>();
            List<String> lastTwoMatches = new ArrayList<>();

            for (WebElement row : matchElements) {
                try {
                    WebElement sportsevent = row.findElement(By.xpath(".//td[@itemprop='sportsevent']"));
                    if (sportsevent != null && sportsevent.isDisplayed()) {
                        break;
                    }
                } catch (Exception e) {
                    // Ignore
                }
                try {
                    String score = row.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                    String homeTeam = row.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                    String awayTeam = row.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                    if (!score.isEmpty() && !score.equals("v")) {
                        allScores.add(score);
                        allHomeTeams.add(homeTeam);
                        allAwayTeams.add(awayTeam);
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            if (allScores.size() >= 2) {
                lastTwoMatches.add(allScores.get(allScores.size() - 2));
                lastTwoMatches.add(allScores.get(allScores.size() - 1));
                return new MatchPattern(
                        lastTwoMatches.get(0),
                        lastTwoMatches.get(1),
                        allHomeTeams.get(allScores.size() - 2),
                        allAwayTeams.get(allScores.size() - 2),
                        allHomeTeams.get(allScores.size() - 1),
                        allAwayTeams.get(allScores.size() - 1)
                );
            } else {
                throw new RuntimeException("Son iki maç skoru bulunamadı!");
            }
        }

        public static boolean findScorePattern(MatchPattern pattern, String year, int id, WebDriver driver2, StringBuilder results) {
            boolean foundMatch = false;

            try {
                driver2.get("https://arsiv.mackolik.com/Team/Default.aspx?id="+id+"&season=" + year);

                List<WebElement> allTableRows = driver2.findElements(By.cssSelector("table tbody tr"));

                List<WebElement> leagueMatches = new ArrayList<>();
                String currentCompetition = null;

                boolean isFirstLeague = true;

                for (int i = 0; i < allTableRows.size(); i++) {
                    WebElement row = allTableRows.get(i);
                    String rowClass = row.getAttribute("class");

                    if (rowClass != null && rowClass.contains("competition")) {
                        String competitionText = row.getText().trim();
                        currentCompetition = competitionText;

                        if (!isFirstLeague) {
                            break;
                        }

                        isFirstLeague = false;
                        continue;
                    }

                    if (rowClass != null && rowClass.contains("row") && !isFirstLeague) {
                        leagueMatches.add(row);
                    }
                }

                for (int i = 0; i < leagueMatches.size() - 1; i++) {
                    WebElement currentRow = leagueMatches.get(i);
                    WebElement nextRow = leagueMatches.get(i + 1);

                    try {
                        String currentScore = currentRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                        String nextScore = nextRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();

                        if ((currentScore.equals(pattern.score1) && nextScore.equals(pattern.score2))
                                || (currentScore.equals(pattern.score2) && nextScore.equals(pattern.score1))) {
                            String homeTeam = currentRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                            String awayTeam = currentRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();

                            // Get HT score for the first match
                            String currentHTScore = currentRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();

                            // Get second match details
                            String secondMatchHomeTeam = nextRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                            String secondMatchAwayTeam = nextRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                            String secondMatchHTScore = nextRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();

                            MatchResult result = new MatchResult(homeTeam, awayTeam, currentScore, year);

                            // Set first match HT score
                            result.firstMatchHTScore = currentHTScore;

                            // Set second match details
                            result.secondMatchHomeTeam = secondMatchHomeTeam;
                            result.secondMatchScore = nextScore;
                            result.secondMatchAwayTeam = secondMatchAwayTeam;
                            result.secondMatchHTScore = secondMatchHTScore;

                            String precedingScore = null;
                            String precedingHTScore = null;
                            String precedingHome = null;
                            String precedingAway = null;
                            if (i > 0) {
                                WebElement precedingRow = leagueMatches.get(i - 1);
                                precedingScore = precedingRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                                precedingHTScore = precedingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                                precedingHome = precedingRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                                precedingAway = precedingRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                            }

                            String followingScore = null;
                            String followingHTScore = null;
                            String followingHome = null;
                            String followingAway = null;
                            if (i + 2 < leagueMatches.size()) {
                                WebElement followingRow = leagueMatches.get(i + 2);
                                followingScore = followingRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                                followingHTScore = followingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                                followingHome = followingRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                                followingAway = followingRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                            }

                            if (precedingHome != null) {
                                result.previousMatchScore = precedingHome + " " + precedingScore + " " + precedingAway;
                                result.previousHTScore = precedingHTScore;
                            } else {
                                result.previousMatchScore = "Bilgi Yok";
                                result.previousHTScore = null;
                            }

                            if (followingHome != null) {
                                result.nextMatchScore = followingHome + " " + followingScore + " " + followingAway;
                                result.nextHTScore = followingHTScore;
                            } else {
                                result.nextMatchScore = "Bilgi Yok";
                                result.nextHTScore = null;
                            }

                            result.patternScore1 = pattern.score1;
                            result.patternScore2 = pattern.score2;
                            result.patternHome1 = pattern.homeTeam1;
                            result.patternAway1 = pattern.awayTeam1;
                            result.patternHome2 = pattern.homeTeam2;
                            result.patternAway2 = pattern.awayTeam2;

                            // Only print the result if one of the original teams plays against the same opponent
                            if (result.containsOriginalTeamVsOpponent(pattern)) {
                                results.append(result).append("\n\n");
                                foundMatch = true;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Maç analizi sırasında hata: " + e.getMessage());
                        continue;
                    }
                }
            } catch (Exception e) {
                System.err.println(year + " yılı analizi sırasında hata: " + e.getMessage());
                e.printStackTrace();
            }

            return foundMatch;
        }

        static WebDriver initializeDriver() {
            System.setProperty("webdriver.chrome.driver", "src\\chrome\\chromedriver.exe");
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            WebDriver driver = new ChromeDriver(options);
            return driver;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Sample usage
        try {
            WebDriver driver = MatchResult.initializeDriver();
            MatchPattern currentPattern = MatchResult.findCurrentSeasonLastTwoMatches(195, driver);

            // Store the pattern info
            String patternInfo = "Aranan skor paterni:\n" + currentPattern;
            boolean foundAnyMatches = false;
            StringBuilder results = new StringBuilder();

            for (int year = 2023; year >= 2021; year--) {
                String yearInfo = "\n" + year + " Sezonu Analizi:\n------------------------";
                String years = year + "/" + (year + 1);

                boolean foundMatchesThisYear = MatchResult.findScorePattern(currentPattern, years, 195, driver, results);

                if (foundMatchesThisYear) {
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
            e.printStackTrace();
        }
    }
}