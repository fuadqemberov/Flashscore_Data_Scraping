package flashscore.weeklydatascraping.flashscore;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

public class FlashScoreScraper3 {

    static WebDriver driver;
    static WebDriverWait wait;
    static WebDriverWait shortWait;

    static final String[][] ODDS_TABS = {
            {"1x2",          "1X2"},
            {"Over/Under",   "Over/Under"},
            {"Both teams",   "Both teams to score"},
            {"Double chance","Double chance"},
            {"Correct score","Correct score"}
    };

    static final String[] HALF_TABS = {"Full Time", "1st Half", "2nd Half"};

    static final List<String> STATIC_COLUMN_KEYS = new ArrayList<>();
    static final List<Double> OU_THRESHOLDS = Arrays.asList(0.5, 1.5, 2.5, 3.5, 4.5);
    static final List<String> CORRECT_SCORES = Arrays.asList(
            "1:0", "0:1", "1:1", "2:0", "0:2", "2:1", "1:2", "2:2",
            "3:0", "0:3", "3:1", "1:3", "3:2", "2:3", "3:3",
            "4:0", "0:4", "4:1", "1:4", "4:2", "2:4", "4:3", "3:4", "4:4"
    );

    static {
        for (String[] tabDef : ODDS_TABS) {
            String tabKey = tabDef[0];
            for (String period : HALF_TABS) {
                if (tabKey.equals("Correct score") && period.equals("2nd Half")) continue;
                switch (tabKey) {
                    case "1x2":
                        for (String label : new String[]{"Home", "Draw", "Away"})
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + label);
                        break;
                    case "Over/Under":
                        for (double th : OU_THRESHOLDS) {
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|O " + th);
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|U " + th);
                        }
                        break;
                    case "Both teams":
                        for (String label : new String[]{"Yes", "No"})
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + label);
                        break;
                    case "Double chance":
                        for (String label : new String[]{"1X", "12", "X2"})
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + label);
                        break;
                    case "Correct score":
                        for (String score : CORRECT_SCORES)
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + score);
                        break;
                }
            }
        }
    }

    static class MatchData {
        String matchId, homeTeam, awayTeam, date, country, league, matchDateTime, ftScore, htScore;
        Map<String, String> oddsMap = new HashMap<>();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);
        int days = 0;
        while (days < 1 || days > 7) {
            System.out.print("Kac gunun datasini cekmek isteyirsiniz? (1-7): ");
            try { days = Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException ignored) {}
            if (days < 1 || days > 7) System.out.println("1 ile 7 arasinda bir deger girin.");
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-extensions");
        options.addArguments("--window-size=1920,1080");
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        driver = new ChromeDriver(options);
        wait      = new WebDriverWait(driver, Duration.ofSeconds(15));
        shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));

        List<MatchData> allData = new ArrayList<>();

        try {
            driver.get("https://www.flashscore.co.uk/football/");
            acceptCookiesIfPresent();
            configureDecimalOdds();

            for (int dayOffset = 1; dayOffset <= days; dayOffset++) {
                LocalDate targetDate = LocalDate.now().minusDays(dayOffset);
                System.out.println("\n>>> " + targetDate);

                navigateToDay(dayOffset);

                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[id^='g_1_']")));
                } catch (Exception ignored) {}

                List<String[]> matches = collectMatchIds();
                System.out.println("  " + matches.size() + " mac bulundu.");

                for (String[] m : matches) {
                    MatchData md = scrapeMatch(m[0], m[1], m[2], targetDate.toString());
                    if (md != null) {
                        allData.add(md);
                        System.out.println("  [OK] " + m[1] + " vs " + m[2]);
                    }
                }

                //driver.get("https://www.flashscore.co.uk/football/");
                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[id^='g_1_']")));
                } catch (Exception ignored) {}
            }
        } finally {
            driver.quit();
        }

        System.out.println("\n=== STATIC COLUMN KEYS (" + STATIC_COLUMN_KEYS.size() + " adet) ===");

        String filename = "bet365.xlsx";
        exportToExcel(allData, filename);
        System.out.println("\nExcel olusturuldu: " + filename);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // MATCH SCRAPER
    // ══════════════════════════════════════════════════════════════════════════════
    static MatchData scrapeMatch(String matchId, String home, String away, String date) {
        driver.get("https://www.flashscore.co.uk/match/football/" + matchId);

        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".detail__breadcrumbs, .duelParticipant")));
        } catch (Exception e) {
            System.out.println("    [SKIP] Mac sayfasi yuklenemedi: " + matchId);
            return null;
        }

        MatchData md = new MatchData();
        md.matchId       = matchId;
        md.homeTeam      = home;
        md.awayTeam      = away;
        md.date          = date;
        md.country       = "-";
        md.league        = "-";
        md.matchDateTime = "-";
        md.ftScore       = "-";
        md.htScore       = "-";

        // Ülke, lig
        try {
            Thread.sleep(300);
            List<WebElement> breadcrumbs = driver.findElements(
                    By.cssSelector(".detail__breadcrumbs a span[itemprop='name']"));
            if (breadcrumbs.size() >= 3) {
                md.country = breadcrumbs.get(1).getText().trim();
                md.league  = breadcrumbs.get(2).getText().trim();
            }
        } catch (Exception ignored) {}

        // Maç saati
        try {
            Thread.sleep(300);
            md.matchDateTime = driver.findElement(
                    By.cssSelector(".duelParticipant__startTime div")).getText().trim();
        } catch (Exception ignored) {}

        // FT skoru
        try {
            Thread.sleep(300);
            List<WebElement> scoreSpans = driver.findElements(
                    By.cssSelector(".detailScore__wrapper span"));
            if (scoreSpans.size() >= 3)
                md.ftScore = scoreSpans.get(0).getText().trim() + "-" + scoreSpans.get(2).getText().trim();
        } catch (Exception ignored) {}

        // HT skoru
        try {
            if (clickMainTab("Match")) {
                Thread.sleep(300);
                List<WebElement> htElements = driver.findElements(
                        By.cssSelector("span[data-testid='wcl-scores-overline-02'] div"));
                if (!htElements.isEmpty()) {
                    md.htScore = htElements.get(0).getText().trim();
                } else {
                    List<WebElement> incidentHeaders = driver.findElements(
                            By.cssSelector(".smv__incidentsHeader"));
                    if (!incidentHeaders.isEmpty()) {
                        String htRawText = incidentHeaders.get(0).getText();
                        md.htScore = htRawText.toUpperCase()
                                .replace("1ST HALF", "").replace("\n", "").trim();
                    }
                }
            }
        } catch (Exception ignored) {}

        // ── Odds tab'ına geç ──────────────────────────────────────────────────
        if (!clickMainTab("Odds")) {
            System.out.println("    [SKIP] Odds tab bulunamadi: " + matchId);
            return null;
        }

        // Odds sayfasının yüklenmesini bekle (tablo çıkana dek)
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".ui-table, .oddsTab, [class*='oddsCompare']")));
        } catch (Exception ignored) {}

        // ── Her odds tipi için ────────────────────────────────────────────────
        for (String[] tabDef : ODDS_TABS) {
            String tabName = tabDef[0];  // "1x2", "Over/Under", ...
            String tabText = tabDef[1];  // "1X2", "Over/Under", ...

            if (!clickOddsSubTab(tabText)) {
                System.out.println("    [WARN] Odds sub-tab bulunamadi: " + tabText);
                continue;
            }

            // ── Her period için ───────────────────────────────────────────────
            for (String period : HALF_TABS) {
                if (tabName.equals("Correct score") && period.equals("2nd Half")) continue;

                if (!period.equals("Full Time")) {
                    // Full Time dışındaki period'lara tıkla
                    if (!clickPeriodTab(period)) {
                        // Period tab yoksa bu period'u atla
                        continue;
                    }
                }
                // Full Time zaten varsayılan; tekrar tıklamaya gerek yok

                // bet365 satırının DOM'a girmesini bekle
                if (!waitForBet365Row()) {
                    // Bu period için bet365 verisi yok
                    continue;
                }

                // Tab tipine göre scrape
                switch (tabName) {
                    case "1x2":
                        scrapeSimpleTab(md, tabName, period, new String[]{"Home", "Draw", "Away"});
                        break;
                    case "Over/Under":
                        scrapeOverUnderTab(md, tabName, period);
                        break;
                    case "Both teams":
                        scrapeSimpleTab(md, tabName, period, new String[]{"Yes", "No"});
                        break;
                    case "Double chance":
                        scrapeSimpleTab(md, tabName, period, new String[]{"1X", "12", "X2"});
                        break;
                    case "Correct score":
                        scrapeCorrectScoreTab(md, tabName, period);
                        break;
                }
            }

            // Bir sonraki odds tipine geçmeden önce tekrar ana Odds tab'ına dön
            // (period tab'ı değiştirmiş olabiliriz, sub-tab seçimi sıfırlanabilir)
            clickMainTab("Odds");
            try { Thread.sleep(300); } catch (Exception ignored) {}
        }

        return md;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // TAB TIKLAMA METODLARİ
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Üst seviye tab'ları tıklar: "Match", "Odds", "H2H", "Standings" vb.
     * wcl-tab elementleri arasında text eşleşmesi arar.
     */
    static boolean clickMainTab(String text) {
        try {
            List<WebElement> tabs = shortWait.until(
                    ExpectedConditions.presenceOfAllElementsLocatedBy(
                            By.cssSelector("[data-testid='wcl-tab']")));
            for (WebElement tab : tabs) {
                if (tab.getText().trim().toUpperCase().contains(text.toUpperCase())) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
                    Thread.sleep(400);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Odds sayfasının İÇİNDEKİ tip tab'larını tıklar: "1X2", "Over/Under", ...
     *
     * DÜZELTME: Odds tab'ına geçtikten sonra sayfa URL'si değişir.
     * Odds sub-tab'ları genellikle farklı bir container içindedir.
     * Tüm wcl-tab elementlerini alıp "Odds" ana tab'ını HARİÇ tutarak
     * (zira o zaten aktif) arama yapıyoruz.
     * Ayrıca ana tab'larla çakışmayı önlemek için bilinen ana tab metinlerini filtreleriz.
     */
    static final Set<String> MAIN_TAB_TEXTS = new HashSet<>(Arrays.asList(
            "MATCH", "ODDS", "H2H", "STANDINGS", "TABLE", "DRAW", "STATS", "NEWS"
    ));

    static boolean clickOddsSubTab(String tabText) {
        try {
            // Kısa bir bekleme: odds container'ının DOM'a girmesi için
            Thread.sleep(300);

            List<WebElement> tabs = driver.findElements(
                    By.cssSelector("[data-testid='wcl-tab']"));

            for (WebElement tab : tabs) {
                if (!tab.isDisplayed()) continue;
                String txt = tab.getText().trim().toUpperCase();
                // Ana tab metinleriyle çakışıyorsa atla
                if (MAIN_TAB_TEXTS.contains(txt)) continue;
                if (txt.contains(tabText.toUpperCase())) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
                    Thread.sleep(500);
                    return true;
                }
            }

            // Fallback: button elementleri
            List<WebElement> buttons = driver.findElements(By.cssSelector("button"));
            for (WebElement btn : buttons) {
                if (!btn.isDisplayed()) continue;
                String txt = btn.getText().trim().toUpperCase();
                if (MAIN_TAB_TEXTS.contains(txt)) continue;
                if (txt.contains(tabText.toUpperCase())) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                    Thread.sleep(500);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Period tab'larını tıklar: "1st Half", "2nd Half"
     *
     * DÜZELTME: Period tab'ları da wcl-tab selector'ı kullanabilir.
     * Ana tab metinlerini ve odds sub-tab metinlerini hariç tutarak
     * yalnızca period tab'larını hedefliyoruz.
     */
    static final Set<String> ODDS_SUB_TAB_TEXTS = new HashSet<>(Arrays.asList(
            "1X2", "OVER/UNDER", "BOTH TEAMS TO SCORE", "DOUBLE CHANCE", "CORRECT SCORE",
            "ASIAN HANDICAP", "DRAW NO BET", "EUROPEAN HANDICAP"
    ));

    static boolean clickPeriodTab(String period) {
        try {
            Thread.sleep(200);
            List<WebElement> tabs = driver.findElements(
                    By.cssSelector("[data-testid='wcl-tab']"));

            for (WebElement tab : tabs) {
                if (!tab.isDisplayed()) continue;
                String txt = tab.getText().trim().toUpperCase().replaceAll("\\s+", " ");

                // Ana ve odds sub-tab metinleriyle çakışıyorsa atla
                if (MAIN_TAB_TEXTS.contains(txt.replaceAll("\\s+", ""))) continue;
                boolean isOddsSubTab = false;
                for (String s : ODDS_SUB_TAB_TEXTS) {
                    if (txt.contains(s)) { isOddsSubTab = true; break; }
                }
                if (isOddsSubTab) continue;

                boolean match = false;
                if (period.equals("1st Half") &&
                        (txt.contains("1ST") || txt.contains("1 ST") || txt.equals("HT")))
                    match = true;
                if (period.equals("2nd Half") &&
                        (txt.contains("2ND") || txt.contains("2 ND")))
                    match = true;

                if (match) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
                    Thread.sleep(400);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SCRAPE METODLARİ
    // ══════════════════════════════════════════════════════════════════════════════

    static void scrapeSimpleTab(MatchData md, String tabName, String period, String[] labels) {
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
            for (WebElement row : rows) {
                if (!isBet365Row(row)) continue;
                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                for (int i = 0; i < Math.min(cells.size(), labels.length); i++) {
                    String key = tabName + "|" + period + "|" + labels[i];
                    md.oddsMap.put(key, extractOpeningOdd(cells.get(i)));
                }
                break;
            }
        } catch (Exception ignored) {}
    }

    static void scrapeOverUnderTab(MatchData md, String tabName, String period) {
        Map<Double, String[]> thresholdOdds = new HashMap<>();
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
            for (WebElement row : rows) {
                if (!isBet365Row(row)) continue;

                String totalText = "";
                try {
                    totalText = row.findElement(
                            By.cssSelector("[data-testid='wcl-oddsValue']")).getText().trim();
                } catch (Exception ignored) {}

                double threshold;
                try {
                    threshold = Double.parseDouble(totalText);
                } catch (NumberFormatException e) {
                    continue;
                }

                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                String overOdd  = (cells.size() >= 1) ? extractOpeningOdd(cells.get(0)) : null;
                String underOdd = (cells.size() >= 2) ? extractOpeningOdd(cells.get(1)) : null;
                thresholdOdds.put(threshold, new String[]{overOdd, underOdd});
            }
        } catch (Exception ignored) {}

        for (double th : OU_THRESHOLDS) {
            String overKey  = tabName + "|" + period + "|O " + th;
            String underKey = tabName + "|" + period + "|U " + th;
            String[] odds = thresholdOdds.getOrDefault(th, new String[]{null, null});
            md.oddsMap.put(overKey,  (odds[0] != null && !odds[0].isEmpty()) ? odds[0] : "-");
            md.oddsMap.put(underKey, (odds[1] != null && !odds[1].isEmpty()) ? odds[1] : "-");
        }
    }

    static void scrapeCorrectScoreTab(MatchData md, String tabName, String period) {
        if (period.equals("2nd Half")) return;

        Map<String, String> scoreOddMap = new HashMap<>();
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
            for (WebElement row : rows) {
                if (!isBet365Row(row)) continue;

                String scoreLabel = null;
                try {
                    scoreLabel = (String) ((JavascriptExecutor) driver).executeScript(
                            "var el = arguments[0];" +
                                    "var spans = el.querySelectorAll('span:not(.oddsCell__arrow):not(.oddsCell__linkIcon)');" +
                                    "for(var i=0;i<spans.length;i++){" +
                                    "  var t=spans[i].textContent.trim();" +
                                    "  if(t.match(/^\\d+:\\d+$/)) return t;" +
                                    "} return null;",
                            row);
                } catch (Exception ignored) {}

                if (scoreLabel == null) {
                    Pattern p = Pattern.compile("(\\d+:\\d+)");
                    java.util.regex.Matcher m = p.matcher(row.getText());
                    if (m.find()) scoreLabel = m.group(1);
                }
                if (scoreLabel == null) continue;

                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                if (!cells.isEmpty())
                    scoreOddMap.put(scoreLabel, extractOpeningOdd(cells.get(0)));
            }
        } catch (Exception ignored) {}

        for (String score : CORRECT_SCORES) {
            String key = tabName + "|" + period + "|" + score;
            md.oddsMap.put(key, scoreOddMap.getOrDefault(score, "-"));
        }
    }

    /**
     * DÜZELTME: bet365 satırı tespiti merkezi metoda taşındı.
     * Bookmaker ID 16 olan elementi arar; bulamazsa logo alt text ile dener.
     */
    static boolean isBet365Row(WebElement row) {
        // Birincil: data-analytics-bookmaker-id attribute
        if (!row.findElements(By.cssSelector("[data-analytics-bookmaker-id='16']")).isEmpty())
            return true;
        // İkincil: img alt text ile bet365 logosu
        try {
            List<WebElement> imgs = row.findElements(By.tagName("img"));
            for (WebElement img : imgs) {
                String alt = img.getAttribute("alt");
                if (alt != null && alt.toLowerCase().contains("bet365")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * DÜZELTME: DOM'un güncellendiğinden emin olmak için
     * önce eski tablo elementinin staleness'ini bekleyebiliriz.
     * Burada sadece bet365 row'un varlığını bekliyoruz.
     */
    static boolean waitForBet365Row() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(6)).until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".ui-table__row [data-analytics-bookmaker-id='16']")));
            return true;
        } catch (TimeoutException e) {
            // Fallback: img alt bet365
            try {
                new WebDriverWait(driver, Duration.ofSeconds(2)).until(
                        ExpectedConditions.presenceOfElementLocated(
                                By.cssSelector(".ui-table__row img[alt*='bet365'], .ui-table__row img[alt*='Bet365']")));
                return true;
            } catch (Exception ignored) {}
            return false;
        }
    }

    static String extractOpeningOdd(WebElement cell) {
        try {
            // title attribute: "opening_odd » current_odd" formatında
            String title = cell.getAttribute("title");
            if (title != null && !title.isBlank()) {
                if (title.contains("»")) return title.split("»")[0].trim();
                return title.trim();
            }
            return cell.findElement(By.tagName("span")).getText().trim();
        } catch (Exception e) {
            return "-";
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SETTINGS / NAV HELPERS
    // ══════════════════════════════════════════════════════════════════════════════
    static void configureDecimalOdds() throws InterruptedException {
        System.out.println("  [Settings] Decimal odds formati seciliyor...");
        try {
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#hamburger-menu div[role='button']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
            Thread.sleep(1500);

            Thread.sleep(300);
            WebElement settingsRow = null;
            for (WebElement row : driver.findElements(By.cssSelector(".contextMenu__row"))) {
                if (row.getText().contains("Settings")) { settingsRow = row; break; }
            }
            if (settingsRow == null) {
                Thread.sleep(300);
                for (WebElement el : driver.findElements(By.cssSelector(".contextMenu__text"))) {
                    if (el.getText().trim().equals("Settings")) {
                        try {
                            Thread.sleep(300);
                            settingsRow = el.findElement(By.xpath("./ancestor::*[@role='button'][1]"));
                        } catch (Exception ignored) {}
                        break;
                    }
                }
            }
            if (settingsRow == null) {
                System.out.println("  [Settings] Bulunamadi.");
                return;
            }
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", settingsRow);
            Thread.sleep(300);

            for (WebElement lbl : driver.findElements(By.cssSelector("label"))) {
                if (lbl.getText().contains("DECIMAL")) {
                    WebElement radio = lbl.findElement(By.cssSelector("input[type='radio']"));
                    if (!radio.isSelected())
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", radio);
                    Thread.sleep(600);
                    break;
                }
            }

            boolean closed = false;
            for (String sel : new String[]{
                    "[data-testid='wcl-dialogCloseButton']",
                    "button.settings__closeButton",
                    "button.wcl-closeButton_6bc3P"}) {
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();",
                            driver.findElement(By.cssSelector(sel)));
                    closed = true;
                    break;
                } catch (Exception ignored) {}
            }
            if (!closed) driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            Thread.sleep(800);
            System.out.println("  [Settings] Tamam.");
        } catch (Exception e) {
            System.out.println("  [Settings] Hata: " + e.getMessage());
        }
    }

    static void acceptCookiesIfPresent() {
        try {
            WebElement cookieBtn = shortWait.until(
                    ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
            cookieBtn.click();
        } catch (Exception ignored) {}
    }

    static void navigateToDay(int dayOffset) {
        for (int i = 0; i < dayOffset; i++) {
            try {
                WebElement prev = wait.until(
                        ExpectedConditions.elementToBeClickable(
                                By.cssSelector("[data-day-picker-arrow='prev']")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", prev);
                Thread.sleep(500);
            } catch (Exception e) {
                System.out.println("  Geri ok hatasi: " + e.getMessage());
            }
        }
    }

    static List<String[]> collectMatchIds() throws InterruptedException {
        Thread.sleep(2000);
        List<String[]> result = new ArrayList<>();
//        result.add(new String[]{"WYNDZibL", "Crystal Palace", "Newcastle"});
        for (WebElement row : driver.findElements(
                By.cssSelector("div[id^='g_1_'].event__match"))) {
            try {
                String matchId = row.getAttribute("id").replace("g_1_","");
                String home="", away="";
                try { home = row.findElement(By.cssSelector(
                  ".event__homeParticipant [data-testid='wcl-scores-simple-text-01']"))
                  .getText().trim(); } catch (Exception ignored) {}
                try { away = row.findElement(By.cssSelector(
                  ".event__awayParticipant [data-testid='wcl-scores-simple-text-01']"))
                  .getText().trim(); } catch (Exception ignored) {}
                if (!matchId.isEmpty()) result.add(new String[]{matchId, home, away});
            } catch (Exception ignored) {}
        }
        return result.subList(0,1);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // EXCEL EXPORT
    // ══════════════════════════════════════════════════════════════════════════════
    static void exportToExcel(List<MatchData> data, String filename) throws IOException {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Bet365 Odds");

        CellStyle hStyle  = makeStyle(wb, IndexedColors.DARK_BLUE,  IndexedColors.WHITE, true);
        CellStyle tStyle  = makeStyle(wb, IndexedColors.DARK_TEAL,  IndexedColors.WHITE, true);
        CellStyle altStyle = wb.createCellStyle();
        altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final int FIXED = 8;

        // Satır 0: Grup başlıkları
        Row grpRow = sheet.createRow(0);
        setCell(grpRow, 0, "Date/Time", hStyle); sheet.setColumnWidth(0, 18 * 256);
        setCell(grpRow, 1, "Match ID",  hStyle); sheet.setColumnWidth(1, 14 * 256);
        setCell(grpRow, 2, "Country",   hStyle); sheet.setColumnWidth(2, 15 * 256);
        setCell(grpRow, 3, "League",    hStyle); sheet.setColumnWidth(3, 25 * 256);
        setCell(grpRow, 4, "Home",      hStyle); sheet.setColumnWidth(4, 20 * 256);
        setCell(grpRow, 5, "Away",      hStyle); sheet.setColumnWidth(5, 20 * 256);
        setCell(grpRow, 6, "FT Score",  hStyle); sheet.setColumnWidth(6, 12 * 256);
        setCell(grpRow, 7, "HT Score",  hStyle); sheet.setColumnWidth(7, 12 * 256);

        String lastGrp   = "";
        int grpStartCol  = FIXED;
        for (int i = 0; i < STATIC_COLUMN_KEYS.size(); i++) {
            String key   = STATIC_COLUMN_KEYS.get(i);
            String[] p   = key.split("\\|");
            String grp   = p[0] + " | " + p[1];
            int col      = FIXED + i;
            sheet.setColumnWidth(col, 13 * 256);

            if (!grp.equals(lastGrp)) {
                if (!lastGrp.isEmpty() && grpStartCol < col - 1)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, grpStartCol, col - 1));
                setCell(grpRow, col, grp, tStyle);
                lastGrp = grp;
                grpStartCol = col;
            }
        }
        if (!lastGrp.isEmpty()) {
            int lastDataCol = FIXED + STATIC_COLUMN_KEYS.size() - 1;
            if (grpStartCol < lastDataCol)
                sheet.addMergedRegion(new CellRangeAddress(0, 0, grpStartCol, lastDataCol));
        }

        // Satır 1: Etiketler
        Row lblRow = sheet.createRow(1);
        setCell(lblRow, 0, "Date/Time", hStyle);
        setCell(lblRow, 1, "Match ID",  hStyle);
        setCell(lblRow, 2, "Country",   hStyle);
        setCell(lblRow, 3, "League",    hStyle);
        setCell(lblRow, 4, "Home",      hStyle);
        setCell(lblRow, 5, "Away",      hStyle);
        setCell(lblRow, 6, "FT Score",  hStyle);
        setCell(lblRow, 7, "HT Score",  hStyle);
        for (int i = 0; i < STATIC_COLUMN_KEYS.size(); i++) {
            String label = STATIC_COLUMN_KEYS.get(i).split("\\|")[2];
            setCell(lblRow, FIXED + i, label, hStyle);
        }

        // Veri satırları
        int rowNum = 2;
        for (MatchData md : data) {
            Row row    = sheet.createRow(rowNum);
            CellStyle cs = (rowNum % 2 == 0) ? altStyle : null;

            setCell(row, 0, (md.matchDateTime != null && !md.matchDateTime.equals("-"))
                    ? md.matchDateTime : md.date, cs);
            setCell(row, 1, md.matchId,   cs);
            setCell(row, 2, md.country,   cs);
            setCell(row, 3, md.league,    cs);
            setCell(row, 4, md.homeTeam,  cs);
            setCell(row, 5, md.awayTeam,  cs);
            setCell(row, 6, md.ftScore,   cs);
            setCell(row, 7, md.htScore,   cs);

            for (int i = 0; i < STATIC_COLUMN_KEYS.size(); i++) {
                String val = md.oddsMap.getOrDefault(STATIC_COLUMN_KEYS.get(i), "-");
                setCell(row, FIXED + i, val, cs);
            }
            rowNum++;
        }

        sheet.createFreezePane(0, 2);
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wb.write(fos);
        }
        wb.close();
        System.out.println("Toplam " + (rowNum - 2) + " mac, " +
                STATIC_COLUMN_KEYS.size() + " sutun yazildi.");
    }

    static CellStyle makeStyle(Workbook wb, IndexedColors bg, IndexedColors fg, boolean bold) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(bold);
        f.setColor(fg.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(bg.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    static void setCell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        if (style != null) c.setCellStyle(style);
    }
}