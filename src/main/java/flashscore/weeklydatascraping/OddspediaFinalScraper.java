package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.time.Duration;
import java.util.*;

public class OddspediaFinalScraper {

    static class MatchResult {
        String url;
        String league;
        String round;
        String homeTeam;
        String awayTeam;
        String score;
        String htScore;
        String status;
        String date;
        Map<String, String> odds = new LinkedHashMap<>();
    }

    public static void main(String[] args) throws Exception {
        WebDriverManager.chromedriver().setup();

        // ── Mevcut Chrome'a bağlan (9222 debug port) ──
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Actions actions = new Actions(driver);

        // ── ADIM 1: Önce TÜM URL'leri topla ──
        System.out.println("URL'ler toplanıyor...");
        List<WebElement> elements = driver.findElements(By.cssSelector("a.match-url"));
        List<String> urls = new ArrayList<>();
        for (WebElement el : elements) {
            String href = el.getAttribute("href");
            if (href != null) urls.add(href.split("/predictions")[0]);
        }
        System.out.println("Toplam " + urls.size() + " maç bulundu.");

        // ── ADIM 2: Sırayla her URL'ye git ve işle ──
        List<MatchResult> allResults = new ArrayList<>();

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            System.out.println("\n[" + (i+1) + "/" + urls.size() + "] ===== MAÇ: " + url + " =====");

            MatchResult result = new MatchResult();
            result.url = url;

            try {
                driver.get(url);
                Thread.sleep(2000);

                // Maç detaylarını çek
                extractMatchDetails(driver, result);

                // Odds tabına geç
                WebElement oddsTab = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//a[contains(@class,'page-nav-item__link') and contains(.,'Odds')]")));
                js.executeScript("arguments[0].click();", oddsTab);
                Thread.sleep(1500);

                // Compare Odds aç
                WebElement compareBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[.//span[contains(text(),'Compare odds')]]")));
                js.executeScript("arguments[0].click();", compareBtn);
                Thread.sleep(1500);

                String[] targetMarkets = {"Full Time Result", "Total Goals", "Both Teams to Score", "Double Chance"};

                for (String marketName : targetMarkets) {
                    try {
                        System.out.println("  --- Market: " + marketName);
                        boolean selected = selectMarket(driver, wait, js, actions, marketName);
                        if (!selected) {
                            System.out.println("  ATLANDI: " + marketName);
                            continue;
                        }
                        Thread.sleep(1500);

                        if (marketName.equals("Total Goals")) {
                            processTotalGoals(driver, wait, js, result);
                        } else {
                            processPeriods(driver, wait, js, marketName, result);
                        }
                    } catch (Exception e) {
                        System.out.println("  Market hatası: " + marketName + " -> " + e.getMessage());
                        try { driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE); } catch (Exception ignored) {}
                    }
                }

            } catch (Exception e) {
                System.out.println("Maç hatası: " + url + " -> " + e.getMessage());
            }

            allResults.add(result);
            System.out.println("✓ " + result.homeTeam + " vs " + result.awayTeam + " tamamlandı.");
        }

        // ── ADIM 3: Excel'e yaz ──
        writeExcel(allResults);
        System.out.println("\n=== BİTTİ! " + allResults.size() + " maç Excel'e yazıldı. ===");
    }

    // ── Maç detayları ──
    private static void extractMatchDetails(WebDriver driver, MatchResult result) {
        // League + Round
        try {
            String leagueText = driver.findElement(By.cssSelector(".matchup-header-league__name")).getText().trim();
            if (leagueText.contains(" - ")) {
                result.league = leagueText.substring(0, leagueText.lastIndexOf(" - ")).trim();
                result.round  = leagueText.substring(leagueText.lastIndexOf(" - ") + 3).trim();
            } else {
                result.league = leagueText;
                result.round  = "";
            }
        } catch (Exception e) { result.league = ""; result.round = ""; }

        // Home Team (justify-content-end olan ilk takım)
        try {
            result.homeTeam = driver.findElement(By.xpath(
                    "//div[contains(@class,'matchup-header-match-info__team') and contains(@class,'justify-content-end')]" +
                            "//div[contains(@class,'font-brand')]")).getText().trim();
        } catch (Exception e) { result.homeTeam = ""; }

        // Away Team (ikinci takım)
        try {
            List<WebElement> teams = driver.findElements(By.xpath(
                    "//div[contains(@class,'matchup-header-match-info__team')]//div[contains(@class,'font-brand')]"));
            result.awayTeam = teams.size() >= 2 ? teams.get(1).getText().trim() : "";
        } catch (Exception e) { result.awayTeam = ""; }

        // Status
        try {
            result.status = driver.findElement(By.cssSelector(".matchup-header-postmatch-info .h5")).getText().trim();
        } catch (Exception e) { result.status = ""; }

        // Score
        try {
            List<WebElement> spans = driver.findElements(By.cssSelector(".matchup-header-postmatch-info .h1 span"));
            if (spans.size() >= 3)
                result.score = spans.get(0).getText().trim() + " - " + spans.get(2).getText().trim();
        } catch (Exception e) { result.score = ""; }

        // HT Score
        try {
            result.htScore = driver.findElement(By.xpath("//span[contains(text(),'HT')]"))
                    .getText().trim().replace("(","").replace(")","").replace("HT","").trim();
        } catch (Exception e) { result.htScore = ""; }

        // Date
        try {
            List<WebElement> neutralEls = driver.findElements(By.xpath(
                    "//div[contains(@class,'matchup-header-postmatch-info')]" +
                            "//div[contains(@class,'color-neutral-500')]"));
            for (WebElement el : neutralEls) {
                String t = el.getText().trim();
                if (!t.contains("HT") && !t.isEmpty()) { result.date = t; break; }
            }
        } catch (Exception e) { result.date = ""; }

        System.out.println("  Detay: " + result.league + " | " + result.homeTeam +
                " vs " + result.awayTeam + " | " + result.score + " | " + result.date);
    }

    // ── Market seçimi ──
    private static boolean selectMarket(WebDriver driver, WebDriverWait wait,
                                        JavascriptExecutor js, Actions actions, String marketName) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement marketDD = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.id("matchup-odds-comparison-nav-market-dd")));
                js.executeScript("arguments[0].scrollIntoView({block:'center'});", marketDD);
                Thread.sleep(500);

                if (attempt == 1)      js.executeScript("arguments[0].click();", marketDD);
                else if (attempt == 2) actions.moveToElement(marketDD).click().perform();
                else                   js.executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));", marketDD);
                Thread.sleep(1000);

                List<WebElement> items = driver.findElements(By.xpath(
                        "//div[contains(@class,'dropdown__menu') and not(contains(@style,'display: none'))]" +
                                "//div[contains(@class,'dropdown-menu-item')]"));
                if (items.isEmpty()) continue;

                for (WebElement item : items) {
                    if (item.getText().trim().equals(marketName)) {
                        js.executeScript("arguments[0].scrollIntoView({block:'center'});", item);
                        Thread.sleep(200);
                        js.executeScript("arguments[0].click();", item);
                        Thread.sleep(500);
                        return true;
                    }
                }
                driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
                return false;
            } catch (Exception e) {
                System.out.println("  selectMarket deneme " + attempt + ": " + e.getMessage());
            }
        }
        return false;
    }

    // ── Line seçimi ──
    private static boolean selectLine(WebDriver driver, WebDriverWait wait,
                                      JavascriptExecutor js, String line) {
        try {
            WebElement lineDD = wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("matchup-odds-comparison-nav-handicap-dd")));
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", lineDD);
            Thread.sleep(300);
            js.executeScript("arguments[0].click();", lineDD);
            Thread.sleep(800);

            List<WebElement> items = driver.findElements(By.xpath(
                    "//div[@aria-labelledby='matchup-odds-comparison-nav-handicap-dd']" +
                            "//div[contains(@class,'dropdown-menu-item')]"));
            for (WebElement item : items) {
                if (item.getText().trim().equals(line)) {
                    js.executeScript("arguments[0].click();", item);
                    Thread.sleep(1200);
                    return true;
                }
            }
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            return false;
        } catch (Exception e) {
            System.out.println("  selectLine hatası: " + e.getMessage());
            return false;
        }
    }

    // ── Period seçimi ──
    private static boolean selectPeriod(WebDriver driver, WebDriverWait wait,
                                        JavascriptExecutor js, String period) {
        try {
            WebElement prdBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[./span[@data-text='" + period + "']]")));
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", prdBtn);
            Thread.sleep(300);
            js.executeScript("arguments[0].click();", prdBtn);
            Thread.sleep(1500);
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[contains(@class,'matchup-odds-comparison-table-row')]")));
            return true;
        } catch (Exception e) { return false; }
    }

    // ── Bet365 odds çek ──
    private static void fetchBet365Odds(WebDriver driver, WebDriverWait wait,
                                        JavascriptExecutor js, String period,
                                        String marketKey, MatchResult result) {
        try {
            List<WebElement> b365Buttons = driver.findElements(By.xpath(
                    "//div[contains(@class,'matchup-odds-comparison-table-row') and " +
                            ".//img[contains(@alt,'bet365')]]//button[contains(@class,'movement-button')]"));
            if (b365Buttons.isEmpty()) {
                System.out.println("      [" + period + "] Bet365 yok.");
                return;
            }

            js.executeScript("arguments[0].scrollIntoView({block:'center'});", b365Buttons.get(0));
            Thread.sleep(300);
            js.executeScript("arguments[0].click();", b365Buttons.get(0));

            WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[contains(@class,'old-modal') and .//div[text()='Odds movement']]")));

            wait.until(d -> {
                List<WebElement> c = modal.findElements(By.className("matchup-odd-movement__odd-box--opening"));
                return !c.isEmpty() && !c.get(0).getText().trim().isEmpty();
            });

            List<WebElement> labels   = modal.findElements(By.cssSelector(".color-white.font-brand.fs-400"));
            List<WebElement> openings = modal.findElements(By.className("matchup-odd-movement__odd-box--opening"));

            StringBuilder log = new StringBuilder();
            for (int i = 0; i < openings.size(); i++) {
                String label = (i < labels.size()) ? labels.get(i).getText().trim() : "OPT" + i;
                String val   = openings.get(i).getText().trim();
                if (!val.isEmpty()) {
                    String key = marketKey + "_" + label.toUpperCase().replace(" ", "_");
                    result.odds.put(key, val);
                    log.append(label).append(":").append(val).append(" | ");
                }
            }
            System.out.println("      [" + period + "] " + marketKey + " -> " + log);

            modal.findElement(By.xpath(".//button[@data-e2e='modal-close-btn']")).click();
            wait.until(ExpectedConditions.invisibilityOf(modal));
            Thread.sleep(800);

        } catch (Exception e) {
            System.out.println("      fetchBet365 hatası [" + period + "]: " + e.getMessage());
        }
    }

    // ── Total Goals ──
    private static void processTotalGoals(WebDriver driver, WebDriverWait wait,
                                          JavascriptExecutor js, MatchResult result) {
        // 0.5, 1.5 -> Full Time + 1st Half + 2nd Half
        // 2.5, 3.5, 4.5 -> sadece Full Time
        String[] allPeriodLines = {"0.5", "1.5"};
        String[] fullOnlyLines  = {"2.5", "3.5", "4.5"};
        String[] allPeriods     = {"Full Time", "1st Half", "2nd Half"};
        String[] fullOnly       = {"Full Time"};

        for (String[] group : new String[][]{{},{}}) {} // sadece okunabilirlik için

        // Grup 1: 0.5 ve 1.5
        for (String line : allPeriodLines) {
            String lineSafe = line.replace(".", "");
            System.out.println("   -> Line " + line + " (tüm periodlar)");
            for (String period : allPeriods) {
                String prdKey = period.equals("Full Time") ? "FT" : period.equals("1st Half") ? "1H" : "2H";
                String marketKey = "TG_" + lineSafe + "_" + prdKey;
                // Her period değişiminde: önce period seç, sonra line seç (dropdown reset olur!)
                if (selectPeriod(driver, wait, js, period)) {
                    if (selectLine(driver, wait, js, line)) {
                        fetchBet365Odds(driver, wait, js, period, marketKey, result);
                    }
                }
            }
        }

        // Grup 2: 2.5, 3.5, 4.5 sadece Full Time
        for (String line : fullOnlyLines) {
            String lineSafe = line.replace(".", "");
            System.out.println("   -> Line " + line + " (sadece Full Time)");
            if (selectPeriod(driver, wait, js, "Full Time")) {
                if (selectLine(driver, wait, js, line)) {
                    fetchBet365Odds(driver, wait, js, "Full Time", "TG_" + lineSafe + "_FT", result);
                }
            }
        }
    }

    // ── Diğer marketler ──
    private static void processPeriods(WebDriver driver, WebDriverWait wait,
                                       JavascriptExecutor js, String market, MatchResult result) {
        String prefix = market.equals("Full Time Result")   ? "FTR"
                : market.equals("Both Teams to Score") ? "BTS"
                : market.equals("Double Chance")       ? "DC"
                : market.replace(" ", "_").toUpperCase();

        String[] periods = {"Full Time", "1st Half", "2nd Half"};
        for (String prd : periods) {
            String prdKey    = prd.equals("Full Time") ? "FT" : prd.equals("1st Half") ? "1H" : "2H";
            String marketKey = prefix + "_" + prdKey;
            if (selectPeriod(driver, wait, js, prd)) {
                fetchBet365Odds(driver, wait, js, prd, marketKey, result);
            }
        }
    }

    // ── Excel ──
    private static void writeExcel(List<MatchResult> results) {
        LinkedHashSet<String> allOddKeys = new LinkedHashSet<>();
        for (MatchResult r : results) allOddKeys.addAll(r.odds.keySet());

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Matches");

            // Header stili
            CellStyle headerStyle = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true);
            hf.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hf);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Alternating row stili
            CellStyle altStyle = wb.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<String> columns = new ArrayList<>(Arrays.asList(
                    "URL", "League", "Round", "Home Team", "Away Team",
                    "Score", "HT Score", "Status", "Date"));
            columns.addAll(allOddKeys);

            // Header satırı
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Data satırları
            int rowIdx = 1;
            for (MatchResult r : results) {
                Row row = sheet.createRow(rowIdx);
                CellStyle rowStyle = (rowIdx % 2 == 0) ? altStyle : null;

                String[] baseVals = {r.url, r.league, r.round, r.homeTeam, r.awayTeam,
                        r.score, r.htScore, r.status, r.date};
                for (int i = 0; i < baseVals.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(baseVals[i] != null ? baseVals[i] : "");
                    if (rowStyle != null) cell.setCellStyle(rowStyle);
                }

                int offset = baseVals.length;
                for (String key : allOddKeys) {
                    Cell cell = row.createCell(offset++);
                    cell.setCellValue(r.odds.getOrDefault(key, ""));
                    if (rowStyle != null) cell.setCellStyle(rowStyle);
                }
                rowIdx++;
            }

            // Kolon genişliklerini otomatik ayarla
            for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);

            // Freeze header
            sheet.createFreezePane(0, 1);

            String fileName = "oddspedia_" + System.currentTimeMillis() + ".xlsx";
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                wb.write(fos);
            }
            System.out.println("Excel yazıldı: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}