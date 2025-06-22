package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

public class MackolikAutomation {

    public static void main(String[] args) throws Exception {
        analyzeMatches();
    }

    public static class MatchData {
        String url;
        String homeTeam;
        String awayTeam;
        List<String> homeLast5Scores = new ArrayList<>();
        List<String> awayLast5Scores = new ArrayList<>();
    }

    public static void analyzeMatches() {
        System.out.println("\n--- Adım 2: Maçlar Analiz Ediliyor ---");
        String excelPath = "C:\\Users\\FD\\Desktop\\mackolik.xlsx"; // DÜZENLE
        String urlFilePath = "football_match_urls2.txt"; // DÜZENLE

        List<Map<String, String>> filterRules;
        List<String> matchUrls;
        try {
            filterRules = readFiltersFromExcel(excelPath);
            matchUrls = readLinesFromFile(urlFilePath);
        } catch (IOException e) {
            System.err.println("Gerekli dosyalar okunamadı: " + e.getMessage());
            return;
        }

        WebDriver driver = setupDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        List<String> reportLines = new ArrayList<>();
        int processedCount = 0;

        System.out.println(filterRules.size() + " kural ve " + matchUrls.size() + " URL ile analiz başlıyor.");

        for (String baseUrl : matchUrls) {
            processedCount++;
            String comparisonUrl = buildCorrectComparisonUrl(baseUrl);
            System.out.printf("\n(%d/%d) Analiz ediliyor: %s\n", processedCount, matchUrls.size(), comparisonUrl);

            try {
                MatchData currentMatchData = scrapeMatchData(driver, wait, comparisonUrl);

                // SIRALAMA DÜZELTME: Sadece bir kez ters çevir!
                if (currentMatchData.homeLast5Scores.size() > 1)
                    Collections.reverse(currentMatchData.homeLast5Scores);
                if (currentMatchData.awayLast5Scores.size() > 1)
                    Collections.reverse(currentMatchData.awayLast5Scores);

                if (currentMatchData.awayLast5Scores.size() < 5 || currentMatchData.homeLast5Scores.size() < 5) {
                    System.out.println("--> Ev sahibi veya deplasman takımı için 5'ten az maç verisi var, atlanıyor.");
                    continue;
                }

                String urlTitle = "";
                try {
                    urlTitle = comparisonUrl.split("/mac/")[1].split("/")[0];
                } catch (Exception ignored) {
                    urlTitle = comparisonUrl;
                }

                List<String> matchedRows = new ArrayList<>();

                for (Map<String, String> rule : filterRules) {
                    boolean allFilledHome = true, allMatchHome = true;
                    boolean allFilledAway = true, allMatchAway = true;
                    StringBuilder debugHome = new StringBuilder("EV SAHIBI: ");
                    StringBuilder debugAway = new StringBuilder("DEPLASMAN: ");

                    // Ev sahibi için kontrol
                    for (int i = 1; i <= 5; i++) {
                        String excelKey = "Son Maç " + i;
                        String filterScore = rule.get(excelKey);
                        String webScoreHome = currentMatchData.homeLast5Scores.get(i - 1);

                        debugHome.append("[").append(filterScore).append(" vs ").append(webScoreHome).append("] ");
                        if (filterScore == null || filterScore.isEmpty()) {
                            allFilledHome = false;
                            break;
                        }
                        if (!areScoresMatching(webScoreHome, filterScore)) {
                            allMatchHome = false;
                            break;
                        }
                    }

                    // Deplasman için kontrol
                    for (int i = 1; i <= 5; i++) {
                        String excelKey = "Son Maç " + i;
                        String filterScore = rule.get(excelKey);
                        String webScoreAway = currentMatchData.awayLast5Scores.get(i - 1);

                        debugAway.append("[").append(filterScore).append(" vs ").append(webScoreAway).append("] ");
                        if (filterScore == null || filterScore.isEmpty()) {
                            allFilledAway = false;
                            break;
                        }
                        if (!areScoresMatching(webScoreAway, filterScore)) {
                            allMatchAway = false;
                            break;
                        }
                    }

                    // Konsola debug
                    if (!(allFilledHome && allMatchHome) && !(allFilledAway && allMatchAway)) {
                        System.out.println(debugHome + " allFilled: " + allFilledHome + ", allMatch: " + allMatchHome);
                        System.out.println(debugAway + " allFilled: " + allFilledAway + ", allMatch: " + allMatchAway);
                    }

                    // Her iki takımdan herhangi biri eşleşirse rapora ekle
                    if ((allFilledHome && allMatchHome) || (allFilledAway && allMatchAway)) {
                        StringBuilder rowStr = new StringBuilder();
                        for (String header : rule.keySet()) {
                            rowStr.append(rule.get(header)).append("\t");
                        }
                        matchedRows.add(rowStr.toString().trim());
                    }
                }

                if (!matchedRows.isEmpty()) {
                    reportLines.add(urlTitle + "    -    ");
                    for (String line : matchedRows) {
                        reportLines.add(line);
                    }
                    reportLines.add(""); // boş satır
                }
            } catch (Exception e) {
                System.err.println("Bu URL işlenirken hata: " + comparisonUrl + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        try {
            writeToFile(reportLines, "analiz_raporu.txt");
            System.out.println("\nAnaliz tamamlandı. Filtreye uyan " + reportLines.size() + " satır 'analiz_raporu.txt' dosyasına yazıldı.");
        } catch (IOException e) {
            System.err.println("Rapor dosyası yazılırken hata: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
            System.out.println("--- Analiz İşlemi Tamamlandı ---");
        }
    }

    // ===================== YARDIMCI METOTLAR ===========================

    private static WebDriver setupDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1200");
        options.addArguments("--log-level=3");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-extensions");
        return new ChromeDriver(options);
    }

    private static String buildCorrectComparisonUrl(String baseUrl) {
        int lastSlashIndex = baseUrl.lastIndexOf('/');
        if (lastSlashIndex == -1) return baseUrl;
        String partBeforeId = baseUrl.substring(0, lastSlashIndex);
        String matchIdPart = baseUrl.substring(lastSlashIndex);
        return partBeforeId + "/karsilastirma" + matchIdPart;
    }

    private static MatchData scrapeMatchData(WebDriver driver, WebDriverWait wait, String url) {
        driver.get(url);
        MatchData data = new MatchData();
        data.url = url;

        WebElement mainContainer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p0c-match-head-to-head__played-matches")));
        List<WebElement> allSections = mainContainer.findElements(By.xpath("./div[contains(@class, 'p0c-match-head-to-head__played-matches--')]"));

        for (WebElement section : allSections) {
            String title = "";
            try {
                WebElement titleElement = section.findElement(By.cssSelector(".p0c-match-head-to-head__container-title"));
                title = titleElement.getText().trim();
            } catch (Exception ignored) {}

            if (title.isEmpty()) continue;
            String teamName = title.split("-")[0].trim();

            List<String> scores = getScoresFromSectionForTeam(section, teamName);

            if (data.homeTeam == null) {
                data.homeTeam = teamName;
                data.homeLast5Scores = scores;
            } else if (data.awayTeam == null && !teamName.equals(data.homeTeam)) {
                data.awayTeam = teamName;
                data.awayLast5Scores = scores;
            }
        }
        if (data.homeLast5Scores == null) data.homeLast5Scores = new ArrayList<>();
        if (data.awayLast5Scores == null) data.awayLast5Scores = new ArrayList<>();
        if (data.homeTeam == null) data.homeTeam = "";
        if (data.awayTeam == null) data.awayTeam = "";

        return data;
    }

    private static List<String> getScoresFromSectionForTeam(WebElement section, String teamName) {
        List<String> scores = new ArrayList<>();
        List<WebElement> matchRows = section.findElements(By.cssSelector(".p0c-match-head-to-head__last-games--row"));
        for (WebElement row : matchRows) {
            try {
                String homeName = row.findElement(By.cssSelector(".p0c-match-head-to-head__last-games--home-team-name")).getText().trim();
                String awayName = row.findElement(By.cssSelector(".p0c-match-head-to-head__last-games--away-team-name")).getText().trim();
                String score = row.findElement(By.cssSelector(".p0c-match-head-to-head__last-games--score")).getText().trim();

                if (homeName.equalsIgnoreCase(teamName)) {
                    scores.add(score);
                } else if (awayName.equalsIgnoreCase(teamName)) {
                    String[] parts = score.split("-");
                    if (parts.length == 2) {
                        scores.add(parts[1].trim() + "-" + parts[0].trim());
                    } else {
                        scores.add(score);
                    }
                }
            } catch (Exception ignored) {}
        }
        // SIRALAMA: En yeni maç listenin başında olacak!
        if (scores.size() > 1) Collections.reverse(scores);
        return scores;
    }

    private static List<Map<String, String>> readFiltersFromExcel(String filePath) throws IOException {
        List<Map<String, String>> filterList = new ArrayList<>();
        try (FileInputStream file = new FileInputStream(new File(filePath));
             XSSFWorkbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return filterList;

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(dataFormatter.formatCellValue(cell).trim());
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean rowHasData = false;
                for (int j = 0; j < headers.size(); j++) {
                    Cell currentCell = currentRow.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (currentCell != null) {
                        String cellValue = dataFormatter.formatCellValue(currentCell).trim();
                        rowMap.put(headers.get(j), cellValue);
                        if (!cellValue.isEmpty()) {
                            rowHasData = true;
                        }
                    } else {
                        rowMap.put(headers.get(j), "");
                    }
                }
                if (rowHasData) {
                    filterList.add(rowMap);
                }
            }
        }
        return filterList;
    }

    private static boolean areScoresMatching(String score1, String score2) {
        if (score1 == null || score2 == null || score1.isEmpty() || score2.isEmpty()) return false;
        String s1 = score1.replaceAll("\\s+", "").trim();
        String s2 = score2.replaceAll("\\s+", "").trim();
        if (s1.equals(s2)) return true; // Tam eşleşme

        // Skorları "-" işaretine göre ayır ve tersini de kontrol et
        String[] parts1 = s1.split("-");
        String[] parts2 = s2.split("-");
        if (parts1.length == 2 && parts2.length == 2) {
            return parts1[0].equals(parts2[1]) && parts1[1].equals(parts2[0]);
        }
        return false;
    }

    private static List<String> readLinesFromFile(String fileName) throws IOException {
        return Files.readAllLines(Paths.get(fileName));
    }

    private static void writeToFile(List<String> lines, String fileName) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, false))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}