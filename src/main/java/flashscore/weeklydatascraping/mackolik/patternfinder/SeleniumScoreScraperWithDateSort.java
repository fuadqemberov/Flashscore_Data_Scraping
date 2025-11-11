package flashscore.weeklydatascraping.mackolik.patternfinder;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SeleniumScoreScraperWithDateSort {

    private static final Logger log = LoggerFactory.getLogger(SeleniumScoreScraperWithDateSort.class);
    private static final String BASE_URL = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026";

    /**
     * Geliştirilmiş navigate ve sort metodu - headless mod için optimize
     */
    private static void navigateAndSortByDate(WebDriver driver, String url) {
        try {
            log.debug("Navigating to URL: {}", url);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // Sayfanın tamamen yüklenmesini bekle
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));

            // Daha güvenli buton bulma
            WebElement dateSortButton = findDateSortButton(driver);

            if (dateSortButton != null) {
                log.debug("Clicking 'Tarihe göre sıralı' button with JavaScript");

                // JavaScript ile tıkla - daha güvenli
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", dateSortButton);

                // Headless mod için daha uzun bekleme
                Thread.sleep(3000);

                // Sayfanın yeniden yüklenmesini bekle
                wait.until(webDriver ->
                        ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));

                log.debug("Date sort completed successfully");
            } else {
                log.warn("Date sort button not found, continuing without sorting");
            }

        } catch (Exception e) {
            log.error("Error navigating to URL: {}", url, e);
        }
    }

    /**
     * Birden fazla selector ile buton bulma - headless mod için
     */
    private static WebElement findDateSortButton(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // Farklı selector stratejileri
        By[] selectors = {
                By.id("tabDate"),
                By.cssSelector("#tabDate"),
                By.xpath("//a[contains(text(), 'Tarihe')]"),
                By.xpath("//*[contains(@id, 'tabDate')]"),
                By.xpath("//a[contains(@onclick, 'Date')]")
        };

        for (By selector : selectors) {
            try {
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(selector));
                log.info("Found date sort button with selector: {}", selector);
                return element;
            } catch (Exception e) {
                log.debug("Date sort button not found with selector: {}", selector);
            }
        }

        log.warn("Could not find date sort button with any selector");
        return null;
    }

    /**
     * Headless mod için optimize edilmiş metot
     */
    public static MatchPattern findCurrentSeasonLastTwoMatches(WebDriver driver, int teamId) throws RuntimeException {
        System.err.println("=== findCurrentSeasonLastTwoMatches STARTED: Team ID = " + teamId + " ===");

        String currentSeasonUrl = String.format(BASE_URL, teamId, CURRENT_SEASON);

        // Navigate with retry mechanism
        navigateAndSortByDateWithRetry(driver, currentSeasonUrl, 2);

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // Tablonun yüklenmesini bekle - daha spesifik bekleyelim
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("#tblFixture tbody tr")
            ));

            String teamName = "Unknown";
            try {
                String title = driver.getTitle();
                if (title != null && !title.isEmpty()) {
                    teamName = title.split("-")[0].trim();
                }
            } catch (Exception e) {
                log.warn("Could not extract team name for ID {}", teamId);
            }

            List<WebElement> allRows = driver.findElements(
                    By.cssSelector("#tblFixture tbody tr")
            );

            log.debug("Total rows found: {} for team {}", allRows.size(), teamId);
            System.err.println("Total rows found: " + allRows.size() + " for team " + teamId);

            List<WebElement> completedMatchRows = new ArrayList<>();

            for (WebElement row : allRows) {
                try {
                    // Tarihe göre sıralı görünümde skor 10. sütunda
                    List<WebElement> scoreElements = row.findElements(
                            By.cssSelector("td:nth-child(10) b a")
                    );

                    if (!scoreElements.isEmpty()) {
                        String score = scoreElements.get(0).getText().trim();
                        log.debug("Found score: '{}'", score);

                        // Skor validasyonu
                        if (!score.isEmpty() && !score.equalsIgnoreCase("v") && score.contains("-")) {
                            // Ek kontrol: sayılar ve tire içeriyor mu?
                            if (score.matches(".*\\d+.*-.*\\d+.*")) {
                                completedMatchRows.add(row);
                                log.debug("Valid completed match found: {}", score);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.trace("Error checking row: {}", e.getMessage());
                }
            }

            log.debug("Found {} completed matches for team {}", completedMatchRows.size(), teamId);
            System.err.println("Found " + completedMatchRows.size() + " completed matches for team " + teamId);

            if (completedMatchRows.size() >= 2) {
                int size = completedMatchRows.size();
                WebElement secondLastMatch = completedMatchRows.get(size - 2);
                WebElement lastMatch = completedMatchRows.get(size - 1);

                try {
                    // Tarihe göre sıralı görünümde sütun indexleri:
                    // 6. sütun: Ev sahibi
                    // 9. sütun: Deplasman
                    // 10. sütun: Skor
                    String score1 = secondLastMatch.findElement(By.cssSelector("td:nth-child(10) b a")).getText().trim();
                    String home1 = secondLastMatch.findElement(By.cssSelector("td:nth-child(6)")).getText().trim();
                    String away1 = secondLastMatch.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();

                    String score2 = lastMatch.findElement(By.cssSelector("td:nth-child(10) b a")).getText().trim();
                    String home2 = lastMatch.findElement(By.cssSelector("td:nth-child(6)")).getText().trim();
                    String away2 = lastMatch.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();

                    String logMessage = String.format("Last two matches for team ID %d: %s vs %s (%s), %s vs %s (%s)",
                            teamId, home1, away1, score1, home2, away2, score2);

                    String nextHomeTeam = null;
                    String nextAwayTeam = null;

                    for (WebElement row : allRows) {
                        try {
                            List<WebElement> scoreElements = row.findElements(
                                    By.cssSelector("td:nth-child(10) b a")
                            );
                            List<WebElement> homeElements = row.findElements(
                                    By.cssSelector("td:nth-child(6)")
                            );
                            List<WebElement> awayElements = row.findElements(
                                    By.cssSelector("td:nth-child(9)")
                            );

                            if (!scoreElements.isEmpty() && !homeElements.isEmpty() && !awayElements.isEmpty()) {
                                String score = scoreElements.get(0).getText().trim();

                                // Oynanmamış maç kontrolü (skor "v" veya boş)
                                if (score.isEmpty() || score.equalsIgnoreCase("v") || !score.contains("-")) {
                                    nextHomeTeam = homeElements.get(0).getText().trim();
                                    nextAwayTeam = awayElements.get(0).getText().trim();
                                    log.debug("Found next unplayed match: {} vs {}", nextHomeTeam, nextAwayTeam);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log.trace("Error checking for next match: {}", e.getMessage());
                        }
                    }
                    return new MatchPattern(score1, score2, home1, away1, home2, away2, teamName, nextHomeTeam, nextAwayTeam);

                } catch (Exception e) {
                    log.error("Error extracting last two matches: {}", e.getMessage(), e);
                    System.err.println("❌ Error extracting matches for team " + teamId + ": " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Could not extract last two match details for team: " + teamId, e);
                }
            } else {
                String errorMsg = "Could not find at least two completed match scores for team ID " + teamId +
                        " in season " + CURRENT_SEASON + ". Found: " + completedMatchRows.size();
                log.warn(errorMsg);
                System.err.println("⚠️ " + errorMsg);
                throw new RuntimeException("Son iki maç skoru bulunamadı for team ID: " + teamId + "! Found: " + completedMatchRows.size());
            }

        } catch (RuntimeException e) {
            System.err.println("❌ RuntimeException for team " + teamId + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error finding current season matches for team {}: {}", teamId, e.getMessage(), e);
            System.err.println("❌ General Exception for team " + teamId + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Could not find current season matches for team ID: " + teamId, e);
        }
    }

    /**
     * Retry mekanizmalı navigate metodu
     */
    private static void navigateAndSortByDateWithRetry(WebDriver driver, String url, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                navigateAndSortByDate(driver, url);
                return;
            } catch (Exception e) {
                log.warn("Navigate attempt {} failed: {}", i + 1, e.getMessage());
                if (i == maxRetries - 1) throw new RuntimeException("Navigation failed after " + maxRetries + " attempts");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Headless mod için optimize edilmiş pattern arama
     */
    public static List<MatchResult> findScorePattern(WebDriver driver, MatchPattern pattern, String seasonYear, int teamId) {
        List<MatchResult> foundResults = new ArrayList<>();
        String seasonUrl = String.format(BASE_URL, teamId, seasonYear);

        navigateAndSortByDateWithRetry(driver, seasonUrl, 2);

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#tblFixture tbody")));

            List<WebElement> allRows = driver.findElements(By.cssSelector("#tblFixture tbody tr"));
            List<WebElement> completedMatchRows = new ArrayList<>();

            // SADECE OYNANMIŞ MAÇLARI AL
            for (WebElement row : allRows) {
                try {
                    List<WebElement> scoreElements = row.findElements(By.cssSelector("td:nth-child(10) b a"));

                    if (!scoreElements.isEmpty()) {
                        String score = scoreElements.get(0).getText().trim();

                        if (!score.equalsIgnoreCase("v") && score.contains("-") && score.matches(".*\\d+.*-.*\\d+.*")) {
                            completedMatchRows.add(row);
                        }
                    }
                } catch (Exception e) {
                    // Sessizce devam et
                }
            }

            log.debug("Collected {} completed match rows for season {}", completedMatchRows.size(), seasonYear);

            // Pattern arama
            for (int i = 0; i < completedMatchRows.size() - 1; i++) {
                WebElement currentRow = completedMatchRows.get(i);
                WebElement nextRow = completedMatchRows.get(i + 1);

                try {
                    String currentScore = currentRow.findElement(By.cssSelector("td:nth-child(10) b a")).getText().trim();
                    String nextScore = nextRow.findElement(By.cssSelector("td:nth-child(10) b a")).getText().trim();

                    boolean matchOrder1 = currentScore.equals(pattern.score1) && nextScore.equals(pattern.score2);
                    boolean matchOrder2 = currentScore.equals(pattern.score2) && nextScore.equals(pattern.score1);

                    if (matchOrder1 || matchOrder2) {
                        log.debug("🎯 Pattern match found for season {} at index {}: {} -> {}",
                                seasonYear, i, currentScore, nextScore);

                        String homeTeam = currentRow.findElement(By.cssSelector("td:nth-child(6)")).getText().trim();
                        String awayTeam = currentRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();

                        String secondMatchHomeTeam = nextRow.findElement(By.cssSelector("td:nth-child(6)")).getText().trim();
                        String secondMatchAwayTeam = nextRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();

                        String currentHTScore = getHTScore(currentRow);
                        String secondMatchHTScore = getHTScore(nextRow);

                        MatchResult result = new MatchResult(homeTeam, awayTeam, currentScore, seasonYear, pattern);
                        result.firstMatchHTScore = currentHTScore;
                        result.secondMatchHomeTeam = secondMatchHomeTeam;
                        result.secondMatchScore = nextScore;
                        result.secondMatchAwayTeam = secondMatchAwayTeam;
                        result.secondMatchHTScore = secondMatchHTScore;

                        addPreviousAndNextMatches(result, completedMatchRows, i);

                        if (result.containsOriginalTeamVsOpponent()) {
                            foundResults.add(result);
                            log.info("✅ Filtered pattern match added for team {}, season {}", teamId, seasonYear);
                        } else {
                            log.debug("❌ Pattern match filtered out (opponent mismatch) for team {}, season {}", teamId, seasonYear);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error analyzing match pair at index {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.debug("Error finding score pattern for team {} in season {}: {}", teamId, seasonYear, e.getMessage());
        }

        return foundResults;
    }

    // Yardımcı metod: İlk yarı skoru al
    private static String getHTScore(WebElement row) {
        try {
            String htScore = row.findElement(By.cssSelector("td:nth-child(11)")).getText().trim();
            return htScore.isEmpty() ? null : htScore;
        } catch (Exception e) {
            return null;
        }
    }

    // Yardımcı metod: Önceki ve sonraki maçları ekle
    private static void addPreviousAndNextMatches(MatchResult result, List<WebElement> matchRows, int currentIndex) {
        // Önceki maç
        if (currentIndex > 0) {
            try {
                WebElement precedingRow = matchRows.get(currentIndex - 1);
                String precedingScore = precedingRow.findElement(By.cssSelector("td:nth-child(10) b a")).getText().trim();
                String precedingHome = precedingRow.findElement(By.cssSelector("td:nth-child(6)")).getText().trim();
                String precedingAway = precedingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                String precedingHTScore = getHTScore(precedingRow);

                result.previousMatchScore = precedingHome + " " + precedingScore + " " + precedingAway;
                result.previousHTScore = precedingHTScore;
            } catch (Exception e) {
                result.previousMatchScore = "Hata (Önceki)";
            }
        } else {
            result.previousMatchScore = "Bilgi Yok (İlk Maç)";
        }

        // Sonraki maç
        if (currentIndex + 2 < matchRows.size()) {
            try {
                WebElement followingRow = matchRows.get(currentIndex + 2);
                String followingScore = followingRow.findElement(By.cssSelector("td:nth-child(10) b a")).getText().trim();
                String followingHome = followingRow.findElement(By.cssSelector("td:nth-child(6)")).getText().trim();
                String followingAway = followingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                String followingHTScore = getHTScore(followingRow);

                result.nextMatchScore = followingHome + " " + followingScore + " " + followingAway;
                result.nextHTScore = followingHTScore;
            } catch (Exception e) {
                result.nextMatchScore = "Hata (Sonraki)";
            }
        } else {
            result.nextMatchScore = "Bilgi Yok (Son Maç)";
        }
    }
}