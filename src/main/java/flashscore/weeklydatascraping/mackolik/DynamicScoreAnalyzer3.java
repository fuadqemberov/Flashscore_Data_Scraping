package flashscore.weeklydatascraping.mackolik;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DynamicScoreAnalyzer3 {
    private static List<MatchResult> matchResults = new ArrayList<>();

    public static class MatchPattern {
        public String score1;
        public String score2;
        public String score3;
        public String team1Home;
        public String team1Away;
        public String team2Home;
        public String team2Away;
        public String team3Home;
        public String team3Away;

        public MatchPattern(String score1, String score2,
                            String team1Home, String team1Away,
                            String team2Home, String team2Away,
                            String score3,String team3Home,String team3Away) {
            this.score1 = score1;
            this.score2 = score2;
            this.team1Home = team1Home;
            this.team1Away = team1Away;
            this.team2Home = team2Home;
            this.team2Away = team2Away;
        }
    }

    public static class MatchResult {
        String homeTeam;
        String awayTeam;
        String score;
        String nextMatchScore;
        String season;

        public MatchResult(String homeTeam, String awayTeam, String score, String season) {
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.score = score;
            this.season = season;
        }

        @Override
        public String toString() {
            return String.format("%s - %s: %s vs %s -> Sonraki Maç: %s",
                    season, score, homeTeam, awayTeam,
                    nextMatchScore != null ? nextMatchScore : "Bilgi Yok");
        }

        public static MatchPattern findCurrentSeasonLastTwoMatches(int id, WebDriver driver) throws InterruptedException {
            String link = "https://arsiv.mackolik.com/Team/Default.aspx?id=" + id + "&season=2024/2025";
            driver.get(link);
            Thread.sleep(2000);

            WebElement table = driver.findElement(By.xpath("//*[@id=\"tblFixture\"]/tbody"));
            List<WebElement> matchElements = table.findElements(By.tagName("tr"));

            List<MatchInfo> allMatches = new ArrayList<>();

            for (WebElement row : matchElements) {
                try {
                    String score = row.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                    if (!score.isEmpty() && !score.equals("v")) {
                        String homeTeam = row.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                        String awayTeam = row.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                        allMatches.add(new MatchInfo(score, homeTeam, awayTeam));
                    }
                    else {
                        break;
                    }
                } catch (Exception e) {
                    // Hataları yoksay
                }
            }

            if (allMatches.size() >= 2) {
                MatchInfo match1 = allMatches.get(allMatches.size() - 2);
                MatchInfo match2 = allMatches.get(allMatches.size() - 1);
                MatchInfo match3 = allMatches.get(allMatches.size() - 3);

                return new MatchPattern(
                        match1.score,
                        match2.score,
                        match1.homeTeam,
                        match1.awayTeam,
                        match2.homeTeam,
                        match2.awayTeam,
                        match3.score,
                        match3.homeTeam,
                        match3.awayTeam
                );
            } else {
                throw new RuntimeException("Son iki maç skoru bulunamadı!");
            }
        }

        static void findScorePattern(MatchPattern pattern, String year, int id, WebDriver driver2) {
            try {
                driver2.get("https://arsiv.mackolik.com/Team/Default.aspx?id=" + id + "&season=" + year);
                List<WebElement> matchElements = driver2.findElements(By.cssSelector("tr.row"));

                for (int i = 0; i < matchElements.size() - 1; i++) {
                    WebElement currentRow = matchElements.get(i);
                    WebElement nextRow = matchElements.get(i + 1);

                    String currentScore = currentRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                    String nextScore = nextRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();

                    String currentHomeTeam = currentRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                    String currentAwayTeam = currentRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                    String nextHomeTeam = nextRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                    String nextAwayTeam = nextRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();

                    // Eşleşme ihtimalleri - Doğrudan sıralama
                    boolean crossPattern = false;
                    if (pattern.score1.equals(currentScore) && pattern.score2.equals(nextScore)) {
                        crossPattern = true; // 1. ihtimal
                    } else if (pattern.score1.equals(reverseString(currentScore)) && pattern.score2.equals(reverseString(nextScore))) {
                        crossPattern = true; // 2. ihtimal
                    } else if (pattern.score1.equals(currentScore) && pattern.score2.equals(reverseString(nextScore))) {
                        crossPattern = true; // 3. ihtimal
                    } else if (pattern.score1.equals(reverseString(currentScore)) && pattern.score2.equals(nextScore)) {
                        crossPattern = true; // 4. ihtimal
                    }

                    // Çapraz eşleşme ihtimalleri
                    if (!crossPattern) { // Eğer yukarıdaki eşleşmeler olmadıysa
                        if (pattern.score1.equals(nextScore) && pattern.score2.equals(currentScore)) {
                            crossPattern = true; // 1. çapraz ihtimal
                        } else if (pattern.score1.equals(reverseString(nextScore)) && pattern.score2.equals(reverseString(currentScore))) {
                            crossPattern = true; // 2. çapraz ihtimal
                        } else if (pattern.score1.equals(nextScore) && pattern.score2.equals(reverseString(currentScore))) {
                            crossPattern = true; // 3. çapraz ihtimal
                        } else if (pattern.score1.equals(reverseString(nextScore)) && pattern.score2.equals(currentScore)) {
                            crossPattern = true; // 4. çapraz ihtimal
                        }
                    }

                    if (crossPattern) {
                        System.out.println("Cross pattern matched: " + currentScore + " -> " + nextScore);
                    }


                    boolean teamPatternMatched = checkTeamPattern(pattern,
                            currentHomeTeam, currentAwayTeam,
                            nextHomeTeam, nextAwayTeam);

                    if (crossPattern && teamPatternMatched) {
                        String followingScore = null;
                        String followingHTScore = null;
                        String followingHome = null;
                        String followingAway = null;

                        if (i + 2 < matchElements.size()) {
                            WebElement followingRow = matchElements.get(i + 2);
                            followingScore = followingRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                            followingHTScore = followingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                            followingHome = followingRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                            followingAway = followingRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                        }

                        MatchResult result = new MatchResult(currentHomeTeam, currentAwayTeam, currentScore, year);
                        result.nextMatchScore = followingHome + "  " + followingHTScore + " / " + followingScore + "  " + followingAway;
                        System.out.println(result);
                        matchResults.add(result);
                    }
                }
            } catch (Exception e) {
            }
        }

        private static boolean checkTeamPattern(MatchPattern pattern,
                                                String currentHome, String currentAway,
                                                String nextHome, String nextAway) {
            // İlk maç (current veya next)
            boolean isFirstMatchValid =
                    (pattern.team1Home.equals(currentHome) && pattern.team1Away.equals(currentAway)) ||
                    (pattern.team1Home.equals(currentAway) && pattern.team1Away.equals(currentHome)) ||
                    (pattern.team1Home.equals(nextHome) && pattern.team1Away.equals(nextAway)) ||
                    (pattern.team1Home.equals(nextAway) && pattern.team1Away.equals(nextHome));

            // İkinci maç (current veya next)
            boolean isSecondMatchValid =
                    (pattern.team2Home.equals(currentHome) && pattern.team2Away.equals(currentAway)) ||
                    (pattern.team2Home.equals(currentAway) && pattern.team2Away.equals(currentHome)) ||
                    (pattern.team2Home.equals(nextHome) && pattern.team2Away.equals(nextAway)) ||
                    (pattern.team2Home.equals(nextAway) && pattern.team2Away.equals(nextHome));

            return isFirstMatchValid && isSecondMatchValid;
        }

        public static String reverseString(String input) {
            StringBuilder reversed = new StringBuilder(input);
            return reversed.reverse().toString();
        }

        static void writeMatchesToExcel() {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Match Results");
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Result");

                int rowNum = 1;
                for (MatchResult match : matchResults) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(match.toString());
                }

                try (FileOutputStream fileOut = new FileOutputStream("match_results.xlsx")) {
                    workbook.write(fileOut);
                    System.out.println("Excel dosyası başarıyla oluşturuldu.");
                }
            } catch (IOException e) {
                System.err.println("Excel yazım hatası: " + e.getMessage());
            }
        }
    }

    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        return new ChromeDriver(options);
    }

    private static class MatchInfo {
        String score;
        String homeTeam;
        String awayTeam;

        public MatchInfo(String score, String homeTeam, String awayTeam) {
            this.score = score;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
        }
    }
}
