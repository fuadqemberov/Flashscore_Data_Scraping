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

public class DynamicScoreAnalyzer {
    private static List<MatchResult> matchResults = new ArrayList<>();

    public static class MatchPattern {
        public String score1;
        public String score2;

        public MatchPattern(String score1, String score2) {
            this.score1 = score1;
            this.score2 = score2;
        }
    }

    public static class MatchResult {
        String homeTeam;
        String awayTeam;
        String score;
        String previousMatchScore;
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
            return String.format("%s - %s: %s vs %s  -> Önceki Maç: %s -> Sonraki Maç: %s",
                    season, score, homeTeam, awayTeam,
                    previousMatchScore != null ? previousMatchScore : "Bilgi Yok",
                    nextMatchScore != null ? nextMatchScore : "Bilgi Yok");
        }

        public static MatchPattern findCurrentSeasonLastTwoMatches(int id, WebDriver driver) throws InterruptedException {
            String link = "https://arsiv.mackolik.com/Team/Default.aspx?id="+ id +"&season=2024/2025";
            driver.get(link);
            Thread.sleep(2000);

            WebElement table = driver.findElement(By.xpath("//*[@id=\"tblFixture\"]/tbody"));
            List<WebElement> matchElements = table.findElements(By.tagName("tr"));

            List<String> allMatches = new ArrayList<>();
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
                    if (!score.isEmpty() && !score.equals("v")) {
                        allMatches.add(score);
                    } else {
                       break;
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            if (allMatches.size() >= 2) {
                lastTwoMatches.add(allMatches.get(allMatches.size() - 2));
                lastTwoMatches.add(allMatches.get(allMatches.size() - 1));
                return new MatchPattern(lastTwoMatches.get(0), lastTwoMatches.get(1));
            } else {
                throw new RuntimeException("Son iki maç skoru bulunamadı!");
            }
        }


        public static void findScorePattern(MatchPattern pattern, String year, int id, WebDriver driver2) {
            try {
                driver2.get("https://arsiv.mackolik.com/Team/Default.aspx?id="+id+"&season=" + year);

                List<WebElement> matchElements = driver2.findElements(By.cssSelector("tr.row"));

                for (int i = 0; i < matchElements.size() - 1; i++) {
                    WebElement currentRow = matchElements.get(i);
                    WebElement nextRow = matchElements.get(i + 1);

                    String currentScore = currentRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                    String nextScore = nextRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();

                    if ((currentScore.equals(pattern.score1) && nextScore.equals(pattern.score2))
                            || (currentScore.equals(pattern.score2) && nextScore.equals(pattern.score1))) {
                        String homeTeam = currentRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                        String awayTeam = currentRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();

                        // Get preceding match details (if available)
                        String precedingScore = null;
                        String precedingHTScore = null;
                        String precedingHome = null;
                        String precedingAway = null;
                        if (i > 0) {
                            WebElement precedingRow = matchElements.get(i - 1);
                            precedingScore = precedingRow.findElement(By.cssSelector("td:nth-child(5) b a")).getText().trim();
                            precedingHTScore = precedingRow.findElement(By.cssSelector("td:nth-child(9)")).getText().trim();
                            precedingHome = precedingRow.findElement(By.cssSelector("td:nth-child(3)")).getText().trim();
                            precedingAway = precedingRow.findElement(By.cssSelector("td:nth-child(7)")).getText().trim();
                        }

                        // Get following match details (if available)
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

                        MatchResult result = new MatchResult(homeTeam, awayTeam, currentScore, year);

                        // Add preceding match info to the result object
                        if (precedingHome != null) {
                            result.previousMatchScore = precedingHome + "  " + precedingHTScore + " / " + precedingScore + "  " + precedingAway;
                        } else {
                            result.previousMatchScore = "Bilgi Yok";
                        }

                        // Add following match info to the result object
                        if (followingHome != null) {
                            result.nextMatchScore = followingHome + "  " + followingHTScore + " / " + followingScore + "  " + followingAway;
                        } else {
                            result.nextMatchScore = "Bilgi Yok";
                        }

                        System.out.println(result);
                        matchResults.add(result);
                    }
                }
            } catch (Exception e) {
                //System.err.println(year + " yılı analizi sırasında hata: " + e.getMessage());
            }
        }

        public static WebDriver initializeDriver() {
            System.setProperty("webdriver.chrome.driver", "src\\chrome\\chromedriver.exe");
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless"); // Başsız modda çalıştır
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            WebDriver driver = new ChromeDriver(options);
            return driver;
        }

        private static void createHeaderRow(Sheet sheet) {
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Result"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
        }

        private static void fillDataRows(Sheet sheet) {
            int rowNum = 1;
            for (int i = 0; i < matchResults.size(); i++) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(i).setCellValue(matchResults.get(i).toString());
            }

        }

        public static void writeMatchesToExcel() {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Match Results");
                createHeaderRow(sheet);
                fillDataRows(sheet);

                try (FileOutputStream fileOut = new FileOutputStream("wnn.xlsx")) {
                    workbook.write(fileOut);
                    System.out.println("Excel file created successfully!");
                }
            } catch (IOException e) {
                System.err.println("Error while writing to Excel file: " + e.getMessage());
            }
        }
    }

}
