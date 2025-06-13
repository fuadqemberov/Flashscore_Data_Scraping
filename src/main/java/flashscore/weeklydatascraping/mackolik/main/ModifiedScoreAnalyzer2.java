package flashscore.weeklydatascraping.mackolik.main;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
// import java.util.logging.Level; // SLF4J kullanılacak
// import java.util.logging.Logger; // SLF4J kullanılacak
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ModifiedScoreAnalyzer2 {

    private static final String CONFIG_FILE = "mackolik_config.properties";
    private static Properties properties = new Properties();
    private static final Logger logger = LoggerFactory.getLogger(ModifiedScoreAnalyzer2.class); // Logger for the outer class

    public static class MatchPattern {
        public String score1; // Should be private with getters if following encapsulation
        public String score2; // Should be private with getters
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

        // public static MatchPattern findCurrentSeasonLastTwoMatches(int id, WebDriver driver) throws InterruptedException { // Old
        // Logger for the inner class, can also use ModifiedScoreAnalyzer2.logger if appropriate
        private static final Logger logger = LoggerFactory.getLogger(MatchResult.class);

        public static MatchPattern findCurrentSeasonLastTwoMatches(int id, WebDriver driver, Properties config) {
            String baseUrl = config.getProperty("mackolik_arsiv_base_url", "https://arsiv.mackolik.com");
            String currentSeason = config.getProperty("current_season_for_pattern", "2024/2025");
            String link = baseUrl + "/Team/Default.aspx?id=" + id + "&season=" + currentSeason;
            logger.debug("Takım ID {} için mevcut sezon maçları alınıyor: {}", id, link);

            try {
                driver.get(link);
                // Thread.sleep(2000); // WebDriverWait kullanılmalı
                // Örnek WebDriverWait kullanımı (gerçek implementasyon için daha fazla detay gerekebilir):
                // WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                // wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"tblFixture\"]/tbody")));

                WebElement table = driver.findElement(By.xpath("//*[@id=\"tblFixture\"]/tbody"));
                List<WebElement> matchElements = table.findElements(By.tagName("tr"));

                List<String> allScores = new ArrayList<>();
                List<String> allHomeTeams = new ArrayList<>();
                List<String> allAwayTeams = new ArrayList<>();

                for (WebElement row : matchElements) {
                    try {
                        // Check for "Canlı" or other non-match rows if necessary
                        if (row.findElements(By.xpath(".//td[@itemprop='sportsevent']")).size() > 0) {
                             logger.trace("Takım ID {} için 'sportsevent' içeren satır atlanıyor.", id);
                            break; // Assuming this indicates end of relevant matches
                        }
                        String scoreText = row.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                        String homeTeamText = row.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                        String awayTeamText = row.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                        if (!scoreText.isEmpty() && !scoreText.equals("v") && !scoreText.equals("-")) { // Added "-" check
                            allScores.add(scoreText);
                            allHomeTeams.add(homeTeamText);
                            allAwayTeams.add(awayTeamText);
                        } else if (!scoreText.equals("-")) { // Only break if not a pending score "-"
                            logger.trace("Takım ID {} için skor boş veya 'v', maç listesi sonlandırılıyor. Skor: '{}'", id, scoreText);
                            break;
                        }
                    } catch (NoSuchElementException e) {
                        // This can happen if a row doesn't have the expected score/team structure, log and skip row
                        logger.warn("Takım ID {} için maç satırı işlenirken element bulunamadı, satır atlanıyor. Hata: {}", id, e.getMessage());
                    } catch (Exception e) {
                        logger.warn("Takım ID {} için maç satırı okunurken beklenmedik hata, satır atlanıyor.", id, e);
                    }
                }

                if (allScores.size() >= 2) {
                    return new MatchPattern(
                            allScores.get(allScores.size() - 2),
                            allScores.get(allScores.size() - 1),
                            allHomeTeams.get(allScores.size() - 2),
                            allAwayTeams.get(allScores.size() - 2),
                            allHomeTeams.get(allScores.size() - 1),
                            allAwayTeams.get(allScores.size() - 1)
                    );
                } else {
                    logger.warn("Takım ID {} için son iki maç skoru bulunamadı (bulunan geçerli skor sayısı: {}).", id, allScores.size());
                    return null;
                }
            } catch (TimeoutException e) {
                logger.error("Sayfa yüklenirken zaman aşımı oluştu (URL: {}), Takım ID: {}.", link, id, e);
                return null;
            } catch (NoSuchElementException e) {
                logger.error("Sayfada ana tablo veya beklenen bir element bulunamadı (URL: {}), Takım ID: {}. Element: {}", link, id, e.getMessage());
                return null;
            } /*catch (InterruptedException e) { // Thread.sleep kaldırıldığı için bu genellikle gereksiz
                logger.warn("Thread kesintiye uğradı, Takım ID: {}.", id, e);
                Thread.currentThread().interrupt();
                return null;
            }*/ catch (WebDriverException e) {
                logger.error("WebDriver hatası oluştu (URL: {}), Takım ID: {}.", link, id, e);
                return null;
            } catch (Exception e) { // Genel hatalar için
                logger.error("Takım ID {} için mevcut sezon maçları alınırken beklenmedik bir hata oluştu.", id, e);
                return null;
            }
        }

        // public static boolean findScorePattern(MatchPattern pattern, String yearSeason, int id, WebDriver driver2, StringBuilder results, Properties config) { // Old signature
        public static List<MatchResult> findScorePatternAndCollect(MatchPattern pattern, String yearSeason, int id, WebDriver driver2, Properties config) {
            List<MatchResult> foundMatchesInSeason = new ArrayList<>();
            String baseUrl = config.getProperty("mackolik_arsiv_base_url", "https://arsiv.mackolik.com");
            String url = baseUrl + "/Team/Default.aspx?id=" + id + "&season=" + yearSeason;
            logger.debug("Takım ID {}, Sezon {} için skor örüntüsü (toplama metodu) aranıyor: {}", id, yearSeason, url);

            if (pattern == null || pattern.score1 == null || pattern.score2 == null) {
                logger.warn("Geçersiz veya eksik maç örüntüsü sağlandı (toplama metodu). Takım ID: {}, Sezon: {}. Atlanıyor.", id, yearSeason);
                return foundMatchesInSeason; // Return empty list
            }
            // boolean foundMatchOverall = false; // This will be determined by the size of the returned list

            try {
                driver2.get(url);
                Thread.sleep(500); // Consider WebDriverWait

                List<WebElement> allTableRows = driver2.findElements(By.cssSelector("table tbody tr"));
                List<WebElement> leagueMatches = new ArrayList<>();
                boolean isFirstLeague = true;
                String currentLeagueName = "N/A";

                for (WebElement row : allTableRows) {
                    String rowClass = row.getAttribute("class");
                    if (rowClass != null && rowClass.contains("competition")) {
                        currentLeagueName = row.getText().trim();
                        if (!isFirstLeague) {
                            logger.trace("Takım ID {}, Sezon {} için sonraki lige geçildi: {}. Bu lig için analiz sonlandırılıyor (toplama metodu).", id, yearSeason, currentLeagueName);
                            break;
                        }
                        isFirstLeague = false;
                        logger.trace("Takım ID {}, Sezon {} için lige girildi: {} (toplama metodu).", id, yearSeason, currentLeagueName);
                        continue;
                    }
                    if (rowClass != null && rowClass.contains("row") && !isFirstLeague) {
                        leagueMatches.add(row);
                    }
                }
                logger.debug("Takım ID {}, Sezon {} ({}) için {} lig maçı bulundu (toplama metodu).", id, yearSeason, currentLeagueName, leagueMatches.size());

                for (int i = 0; i < leagueMatches.size() - 1; i++) {
                    try {
                        WebElement currentRow = leagueMatches.get(i);
                        WebElement nextRow = leagueMatches.get(i + 1);
                        String currentScore = currentRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                        String nextScore = nextRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();

                        if ((currentScore.equals(pattern.score1) && nextScore.equals(pattern.score2)) ||
                            (currentScore.equals(pattern.score2) && nextScore.equals(pattern.score1))) {
                            String homeTeam = currentRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                            String awayTeam = currentRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                            String currentHTScore = currentRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                            String secondMatchHomeTeam = nextRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                            String secondMatchAwayTeam = nextRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                            String secondMatchHTScore = nextRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();

                            MatchResult result = new MatchResult(homeTeam, awayTeam, currentScore, yearSeason);
                            result.firstMatchHTScore = currentHTScore;
                            result.secondMatchHomeTeam = secondMatchHomeTeam;
                            result.secondMatchScore = nextScore;
                            result.secondMatchAwayTeam = secondMatchAwayTeam;
                            result.secondMatchHTScore = secondMatchHTScore;

                            if (i > 0) {
                                WebElement precedingRow = leagueMatches.get(i - 1);
                                result.previousMatchScore = precedingRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim() + " " +
                                                            precedingRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim() + " " +
                                                            precedingRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                                result.previousHTScore = precedingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                            }
                            if (i + 2 < leagueMatches.size()) {
                                WebElement followingRow = leagueMatches.get(i + 2);
                                result.nextMatchScore = followingRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim() + " " +
                                                        followingRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim() + " " +
                                                        followingRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                                result.nextHTScore = followingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                            }

                            result.patternScore1 = pattern.score1; // These are from the searched pattern
                            result.patternScore2 = pattern.score2;
                            result.patternHome1 = pattern.homeTeam1;
                            result.patternAway1 = pattern.awayTeam1;
                            result.patternHome2 = pattern.homeTeam2;
                            result.patternAway2 = pattern.awayTeam2;

                            if (result.containsOriginalTeamVsOpponent(pattern)) {
                                // results.append(result).append("\n\n"); // Old way
                                foundMatchesInSeason.add(result); // Add to list
                                logger.debug("Takım ID {}, Sezon {} için eşleşen örüntü (toplama metodu) bulundu: {}", id, yearSeason, result.toString());
                            }
                        }
                    } catch (NoSuchElementException e) {
                        logger.warn("Takım ID {}, Sezon {} ({}) için maç satırı işlenirken element bulunamadı (toplama metodu). Satır atlanıyor. Hata: {}", id, yearSeason, currentLeagueName, e.getMessage());
                    } catch (Exception e) {
                        logger.error("Takım ID {}, Sezon {} ({}) için maç analizi sırasında beklenmedik bir hata (toplama metodu). Satır atlanıyor.", id, yearSeason, currentLeagueName, e);
                    }
                }
            } catch (TimeoutException e) {
                logger.error("Sayfa yüklenirken zaman aşımı oluştu (Takım ID: {}, Sezon {}, toplama metodu). URL: {}", id, yearSeason, url, e);
                // Return empty list, error already logged
            } catch (WebDriverException e) {
                logger.error("WebDriver hatası oluştu (Takım ID: {}, Sezon {}, toplama metodu). URL: {}", id, yearSeason, url, e);
                // Return empty list
            } catch (InterruptedException e) {
                 logger.warn("Thread kesintiye uğradı (findScorePatternAndCollect), Takım ID: {}, Sezon: {}.", id, yearSeason, e);
                 Thread.currentThread().interrupt();
                 // Return empty list
            } catch (Exception e) {
                 logger.error("Takım ID {}, Sezon {} için skor örüntüsü (toplama metodu) aranırken beklenmedik bir hata oluştu. URL: {}", id, yearSeason, url, e);
                 // Return empty list
            }
            return foundMatchesInSeason;
        }

        public static WebDriver initializeDriver(Properties config) {
            logger.info("MatchResult için WebDriver başlatılıyor...");
            System.setProperty("webdriver.chrome.driver", config.getProperty("webdriver_path", "src\\chrome\\chromedriver.exe"));
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            try {
                WebDriver newDriver = new ChromeDriver(options); // Renamed to newDriver to avoid conflict
                logger.info("MatchResult WebDriver başarıyla başlatıldı.");
                return newDriver;
            } catch (Exception e) {
                logger.error("MatchResult WebDriver başlatılırken hata oluştu.", e);
                // Potentially rethrow or handle more gracefully depending on application needs
                throw new RuntimeException("MatchResult WebDriver başlatılamadı", e);
            }
        }
        // Unused formatScore method removed as per previous observation.
    }

    // Outer class main method
    public static void main(String[] args) throws IOException {
        // java.util.logging.Logger.getLogger("org.openqa.selenium").setLevel(java.util.logging.Level.SEVERE); // Using SLF4J

        logger.info("ModifiedScoreAnalyzer2 (main) başlatılıyor...");
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            properties.load(fis);
            logger.info("{} başarıyla yüklendi (ModifiedScoreAnalyzer2.main).", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("{} yüklenirken hata (ModifiedScoreAnalyzer2.main).", CONFIG_FILE, e);
            return;
        }

        WebDriver driver = null; // Define driver outside try for finally block
        try {
            driver = MatchResult.initializeDriver(properties); // Initialize WebDriver via MatchResult

            // Example: Get team ID from properties or use a default
            int teamIdToAnalyze = Integer.parseInt(properties.getProperty("debug_team_id", "195"));
            logger.info("Analiz edilecek örnek takım ID (ModifiedScoreAnalyzer2.main): {}", teamIdToAnalyze);

            MatchPattern currentPattern = MatchResult.findCurrentSeasonLastTwoMatches(teamIdToAnalyze, driver, properties);

            if (currentPattern == null) {
                logger.error("Takım ID {} için mevcut sezon örüntüsü bulunamadı (ModifiedScoreAnalyzer2.main).", teamIdToAnalyze);
                return;
            }
            logger.info("Aranan skor paterni (Takım ID {}, ModifiedScoreAnalyzer2.main):\n{}", teamIdToAnalyze, currentPattern);

            boolean foundAnyMatchesOverall = false;
            StringBuilder resultsAggregator = new StringBuilder(); // Renamed for clarity
            String analysisYearsStr = properties.getProperty("analysis_years", "2023,2022,2021");
            String[] yearsToAnalyze = analysisYearsStr.split(",");

            for (String yearStr : yearsToAnalyze) {
                int year = Integer.parseInt(yearStr.trim());
                String yearsSeason = year + "/" + (year + 1);
                logger.info("Takım ID {} için {} sezonu analizi başlıyor (ModifiedScoreAnalyzer2.main).", teamIdToAnalyze, yearsSeason);

                StringBuilder resultsThisYear = new StringBuilder(); // Store results for only this year
                boolean foundMatchesThisSeason  = MatchResult.findScorePattern(currentPattern, yearsSeason, teamIdToAnalyze, driver, resultsThisYear, properties);

                if (foundMatchesThisSeason) {
                    resultsAggregator.append("\n").append(year).append(" Sezonu Analizi:\n------------------------\n");
                    resultsAggregator.append(resultsThisYear);
                    foundAnyMatchesOverall = true;
                    logger.info("Takım ID {} için {} sezonunda eşleşmeler bulundu (ModifiedScoreAnalyzer2.main).", teamIdToAnalyze, yearsSeason);
                } else {
                    logger.info("Takım ID {} için {} sezonunda eşleşme bulunamadı (ModifiedScoreAnalyzer2.main).", teamIdToAnalyze, yearsSeason);
                }
            }

            if (foundAnyMatchesOverall) {
                logger.info("Takım ID {} için bulunan toplam eşleşmeler (ModifiedScoreAnalyzer2.main):\n{}", teamIdToAnalyze, resultsAggregator.toString());
                // System.out.println(patternInfo); // Replaced by logger
                // System.out.println(results.toString()); // Replaced by logger
            } else {
                logger.info("Takım ID {} için belirtilen örüntüyle hiçbir sezonda eşleşme bulunamadı (ModifiedScoreAnalyzer2.main).", teamIdToAnalyze);
            }

        } catch (NumberFormatException e) {
            logger.error("Yapılandırmadaki 'debug_team_id' değeri geçersiz: {} (ModifiedScoreAnalyzer2.main).", properties.getProperty("debug_team_id"), e);
        } catch (Exception e) {
            logger.error("ModifiedScoreAnalyzer2 main metodunda beklenmedik bir hata oluştu.", e);
        } finally {
            if (driver != null) {
                driver.quit();
                logger.info("WebDriver kapatıldı (ModifiedScoreAnalyzer2.main).");
            }
            logger.info("ModifiedScoreAnalyzer2 (main) tamamlandı.");
        }
    }
}