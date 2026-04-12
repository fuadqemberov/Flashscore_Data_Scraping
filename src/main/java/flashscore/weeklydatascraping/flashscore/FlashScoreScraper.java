package flashscore.weeklydatascraping.flashscore;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class FlashScoreScraper {

    static WebDriver driver;
    static WebDriverWait wait;

    static final String[][] ODDS_TABS = {
            {"1x2",          "1X2"},
            {"Over/Under",   "Over/Under"},
            {"Both teams",   "Both teams to score"},
            {"Double chance","Double chance"},
            {"Correct score","Correct score"}
    };

    static final String[] HALF_TABS = {"1st Half", "2nd Half"};

    static class OddEntry {
        String tab, period, label, opening;
        OddEntry(String t, String p, String l, String o) {
            tab=t; period=p; label=l; opening=o;
        }
    }

    static class MatchData {
        String matchId, homeTeam, awayTeam, date;
        List<OddEntry> odds = new ArrayList<>();
    }

    // columnMap: "tab|period|label" -> sutun indeksi (siralama ucun ArrayList istifade edirik)
    static List<String> columnKeys = new ArrayList<>();

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
        options.addArguments("--no-sandbox","--disable-dev-shm-usage","--window-size=1920,1080");
        driver = new ChromeDriver(options);
        wait   = new WebDriverWait(driver, Duration.ofSeconds(20));

        List<MatchData> allData = new ArrayList<>();

        try {
            driver.get("https://www.flashscore.co.uk/football/");
            Thread.sleep(3000);
            acceptCookiesIfPresent();
            configureDecimalOdds();

            for (int dayOffset = 1; dayOffset <= days; dayOffset++) {
                LocalDate targetDate = LocalDate.now().minusDays(dayOffset);
                System.out.println("\n>>> " + targetDate);

                navigateToDay(dayOffset);
                Thread.sleep(3000);

                List<String[]> matches = collectMatchIds();
                System.out.println("  " + matches.size() + " mac bulundu.");

                for (String[] m : matches) {
                    MatchData md = scrapeMatch(m[0], m[1], m[2], targetDate.toString());
                    if (md != null) {
                        allData.add(md);
                        System.out.println("  [OK] " + m[1] + " vs " + m[2]);
                    }
                }

                driver.get("https://www.flashscore.co.uk/football/");
                Thread.sleep(2500);
                acceptCookiesIfPresent();
            }
        } finally {
            driver.quit();
        }

        // DEBUG: columnKeys-i yoxla
        System.out.println("\n=== DEBUG: columnKeys (" + columnKeys.size() + " eded) ===");
        for (String k : columnKeys) System.out.println("  " + k);
        System.out.println("=== DEBUG END ===\n");

        String filename = "flashscore_bet365_" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        exportToExcel(allData, filename);
        System.out.println("\nExcel olusturuldu: " + filename);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // MATCH SCRAPER
    // ══════════════════════════════════════════════════════════════════════════════
    static MatchData scrapeMatch(String matchId, String home, String away, String date)
            throws InterruptedException {

        driver.get("https://www.flashscore.co.uk/match/football/" + matchId);
        Thread.sleep(3000);

        if (!clickButtonContaining("Odds")) {
            System.out.println("    [SKIP] Odds tab bulunamadi: " + matchId);
            return null;
        }
        Thread.sleep(2500);

        MatchData md = new MatchData();
        md.matchId=matchId; md.homeTeam=home; md.awayTeam=away; md.date=date;

        for (String[] tabDef : ODDS_TABS) {
            String tabName = tabDef[0];
            String tabText = tabDef[1];

            if (!clickOddsTypeTab(tabText)) {
                System.out.println("    [SKIP] Odds type tab bulunamadi: " + tabText);
                continue;
            }
            Thread.sleep(2000);

            // ── FULL TIME ─────────────────────────────────────────────────────────
            // Odds type tabina kecdikde default olaraq Full Time aktiv olur.
            waitForBet365Row();
            List<OddEntry> ftEntries = scrapeBet365Rows(tabName, "Full Time");
            md.odds.addAll(ftEntries);
            for (OddEntry e : ftEntries) {
                String key = e.tab + "|" + e.period + "|" + e.label;
                if (!columnKeys.contains(key)) columnKeys.add(key);
            }
            System.out.println("      [FT] " + tabName + " -> " + ftEntries.size() + " odd");

            // ── 1ST HALF & 2ND HALF ───────────────────────────────────────────────
            for (String period : HALF_TABS) {
                if (!clickTimeTab(period)) {
                    System.out.println("      [SKIP] Period tab yoxdur: " + period + " / " + tabName);
                    continue;
                }
                waitForBet365Row();
                List<OddEntry> entries = scrapeBet365Rows(tabName, period);
                md.odds.addAll(entries);
                for (OddEntry e : entries) {
                    String key = e.tab + "|" + e.period + "|" + e.label;
                    if (!columnKeys.contains(key)) columnKeys.add(key);
                }
                System.out.println("      [" + period + "] " + tabName + " -> " + entries.size() + " odd");
            }
        }
        return md;
    }

    // ── Bet365 rowunun yuklenmesini gozle (max 8 saniye) ─────────────────────────
    static void waitForBet365Row() throws InterruptedException {
        int maxAttempts = 16;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
                for (WebElement row : rows) {
                    if (!row.findElements(
                            By.cssSelector("[data-analytics-bookmaker-id='16']")).isEmpty()) {
                        Thread.sleep(300);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            Thread.sleep(500);
        }
        System.out.println("      [WARN] Bet365 rowu gozleme muddeti asdi.");
    }

    // ── Esas mac tabina klik ──────────────────────────────────────────────────────
    static boolean clickButtonContaining(String text) throws InterruptedException {
        try {
            List<WebElement> tabs = wait.until(
                    ExpectedConditions.presenceOfAllElementsLocatedBy(
                            By.cssSelector("[data-testid='wcl-tab']")));
            for (WebElement tab : tabs) {
                if (tab.getText().trim().toUpperCase().contains(text.toUpperCase())) {
                    ((JavascriptExecutor)driver).executeScript("arguments[0].click();", tab);
                    Thread.sleep(1200);
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("    clickButton hatasi (" + text + "): " + e.getMessage());
        }
        return false;
    }

    // ── Odds type tabina klik ─────────────────────────────────────────────────────
    static boolean clickOddsTypeTab(String tabText) throws InterruptedException {
        try {
            List<WebElement> anchors = driver.findElements(
                    By.cssSelector("a[data-analytics-alias]"));
            for (WebElement a : anchors) {
                String title = a.getAttribute("title");
                if (title != null && title.toLowerCase().contains(tabText.toLowerCase())) {
                    WebElement btn = a.findElement(By.cssSelector("button"));
                    ((JavascriptExecutor)driver).executeScript("arguments[0].click();", btn);
                    Thread.sleep(1200);
                    return true;
                }
            }
            for (WebElement a : anchors) {
                try {
                    WebElement btn = a.findElement(By.cssSelector("button"));
                    if (btn.getText().trim().toLowerCase().contains(tabText.toLowerCase())) {
                        ((JavascriptExecutor)driver).executeScript("arguments[0].click();", btn);
                        Thread.sleep(1200);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.out.println("    clickOddsTypeTab hatasi (" + tabText + "): " + e.getMessage());
        }
        return false;
    }

    // ── 1st Half / 2nd Half tabina klik ──────────────────────────────────────────
    static boolean clickTimeTab(String period) throws InterruptedException {
        try {
            List<WebElement> tabs = driver.findElements(
                    By.cssSelector("[data-testid='wcl-tab']"));
            for (WebElement tab : tabs) {
                String txt = tab.getText().trim().toUpperCase().replaceAll("\\s+", "");
                boolean match = false;
                if (period.equals("1st Half") &&
                        (txt.startsWith("1ST") || txt.equals("HT") ||
                                txt.startsWith("1STHALF"))) match = true;
                if (period.equals("2nd Half") &&
                        (txt.startsWith("2ND") || txt.startsWith("2NDHALF"))) match = true;
                if (match) {
                    ((JavascriptExecutor)driver).executeScript("arguments[0].click();", tab);
                    Thread.sleep(1500);
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("    clickTimeTab hatasi (" + period + "): " + e.getMessage());
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // ODDS PARSERS
    // ══════════════════════════════════════════════════════════════════════════════
    static List<OddEntry> scrapeBet365Rows(String tabName, String period) {
        List<OddEntry> result = new ArrayList<>();
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
            for (WebElement row : rows) {
                if (row.findElements(
                        By.cssSelector("[data-analytics-bookmaker-id='16']")).isEmpty())
                    continue;

                switch (tabName) {
                    case "1x2":
                        result.addAll(parse1x2(row, tabName, period));
                        break;
                    case "Over/Under":
                        result.addAll(parseOverUnder(row, tabName, period));
                        continue;
                    case "Both teams":
                        result.addAll(parseLabeledCells(row, tabName, period,
                                new String[]{"Yes", "No"}));
                        break;
                    case "Double chance":
                        result.addAll(parseLabeledCells(row, tabName, period,
                                new String[]{"1X", "12", "X2"}));
                        break;
                    case "Correct score":
                        result.addAll(parseCorrectScore(row, tabName, period));
                        continue;
                    default:
                        break;
                }
                break; // Over/Under ve Correct score xaricinde ilk Bet365 rowundan sonra dayan
            }
        } catch (Exception e) {
            System.out.println("    scrapeBet365Rows [" + tabName + "/" + period + "]: "
                    + e.getMessage());
        }
        return result;
    }

    static List<OddEntry> parse1x2(WebElement row, String tab, String period) {
        List<OddEntry> r = new ArrayList<>();
        String[] labels = {"Home", "Draw", "Away"};
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        for (int i = 0; i < Math.min(cells.size(), labels.length); i++)
            r.add(new OddEntry(tab, period, labels[i], extractOpeningOdd(cells.get(i))));
        return r;
    }

    static List<OddEntry> parseOverUnder(WebElement row, String tab, String period) {
        List<OddEntry> r = new ArrayList<>();
        String total = "?";
        try {
            total = row.findElement(
                    By.cssSelector("[data-testid='wcl-oddsValue']")).getText().trim();
        } catch (Exception ignored) {}
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        if (cells.size() >= 1)
            r.add(new OddEntry(tab, period, "O " + total, extractOpeningOdd(cells.get(0))));
        if (cells.size() >= 2)
            r.add(new OddEntry(tab, period, "U " + total, extractOpeningOdd(cells.get(1))));
        return r;
    }

    static List<OddEntry> parseLabeledCells(WebElement row, String tab,
                                            String period, String[] labels) {
        List<OddEntry> r = new ArrayList<>();
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        for (int i = 0; i < Math.min(cells.size(), labels.length); i++)
            r.add(new OddEntry(tab, period, labels[i], extractOpeningOdd(cells.get(i))));
        return r;
    }

    static List<OddEntry> parseCorrectScore(WebElement row, String tab, String period) {
        List<OddEntry> r = new ArrayList<>();
        String scoreLabel = "?";
        try {
            scoreLabel = (String) ((JavascriptExecutor)driver).executeScript(
                    "var el = arguments[0]; " +
                            "var spans = el.querySelectorAll(" +
                            "'span:not(.oddsCell__arrow):not(.oddsCell__linkIcon)'); " +
                            "for(var i=0;i<spans.length;i++){" +
                            " var t=spans[i].textContent.trim(); " +
                            " if(t.match(/^\\d+:\\d+$/)) return t; } return null;", row);
        } catch (Exception ignored) {}
        if (scoreLabel == null || scoreLabel.equals("?")) {
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\d+:\\d+)").matcher(row.getText().trim());
                if (m.find()) scoreLabel = m.group(1);
            } catch (Exception ignored) {}
        }
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        for (int i = 0; i < cells.size(); i++) {
            String lbl = scoreLabel + (cells.size() > 1 ? "_" + i : "");
            r.add(new OddEntry(tab, period, lbl, extractOpeningOdd(cells.get(i))));
        }
        return r;
    }

    static String extractOpeningOdd(WebElement cell) {
        try {
            String title = cell.getAttribute("title");
            if (title != null && title.contains("»"))
                return title.split("»")[0].trim();
            return cell.findElement(By.tagName("span")).getText().trim();
        } catch (Exception e) { return "N/A"; }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SETTINGS / NAV HELPERS
    // ══════════════════════════════════════════════════════════════════════════════
    static void configureDecimalOdds() throws InterruptedException {
        System.out.println("  [Settings] Decimal odds formati seciliyor...");
        try {
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#hamburger-menu div[role='button']")));
            ((JavascriptExecutor)driver).executeScript("arguments[0].click();", btn);
            Thread.sleep(1500);

            WebElement settingsRow = null;
            for (WebElement row : driver.findElements(By.cssSelector(".contextMenu__row"))) {
                if (row.getText().contains("Settings")) { settingsRow = row; break; }
            }
            if (settingsRow == null) {
                for (WebElement el : driver.findElements(By.cssSelector(".contextMenu__text"))) {
                    if (el.getText().trim().equals("Settings")) {
                        try {
                            settingsRow = el.findElement(
                                    By.xpath("./ancestor::*[@role='button'][1]"));
                        } catch (Exception ignored) {}
                        break;
                    }
                }
            }
            if (settingsRow == null) {
                System.out.println("  [Settings] Bulunamadi.");
                return;
            }
            ((JavascriptExecutor)driver).executeScript("arguments[0].click();", settingsRow);
            Thread.sleep(2000);

            for (WebElement lbl : driver.findElements(By.cssSelector("label"))) {
                if (lbl.getText().contains("DECIMAL")) {
                    WebElement radio = lbl.findElement(By.cssSelector("input[type='radio']"));
                    if (!radio.isSelected())
                        ((JavascriptExecutor)driver).executeScript("arguments[0].click();", radio);
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
                    ((JavascriptExecutor)driver).executeScript("arguments[0].click();",
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
            driver.findElement(By.id("onetrust-accept-btn-handler")).click();
            Thread.sleep(1000);
        } catch (Exception ignored) {}
    }

    static void navigateToDay(int dayOffset) throws InterruptedException {
        for (int i = 0; i < dayOffset; i++) {
            try {
                WebElement prev = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("[data-day-picker-arrow='prev']")));
                prev.click();
                Thread.sleep(1200);
            } catch (Exception e) {
                System.out.println("  Geri ok hatasi: " + e.getMessage());
            }
        }
    }

    static List<String[]> collectMatchIds() throws InterruptedException {
        Thread.sleep(2000);
        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"WYNDZibL", "Crystal Palace", "Newcastle"});
//        for (WebElement row : driver.findElements(
//                By.cssSelector("div[id^='g_1_'].event__match"))) {
//            try {
//                String matchId = row.getAttribute("id").replace("g_1_","");
//                String home="", away="";
//                try { home = row.findElement(By.cssSelector(
//                  ".event__homeParticipant [data-testid='wcl-scores-simple-text-01']"))
//                  .getText().trim(); } catch (Exception ignored) {}
//                try { away = row.findElement(By.cssSelector(
//                  ".event__awayParticipant [data-testid='wcl-scores-simple-text-01']"))
//                  .getText().trim(); } catch (Exception ignored) {}
//                if (!matchId.isEmpty()) result.add(new String[]{matchId, home, away});
//            } catch (Exception ignored) {}
//        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // EXCEL EXPORT
    // ══════════════════════════════════════════════════════════════════════════════
    static void exportToExcel(List<MatchData> data, String filename) throws IOException {

        // Sutunlari siralayiriq: tab sirasi > period sirasi > label
        columnKeys.sort((a, b) -> {
            String[] pa = a.split("\\|"), pb = b.split("\\|");
            int t = tabIdx(pa[0]) - tabIdx(pb[0]);
            if (t != 0) return t;
            int p = periodIdx(pa[1]) - periodIdx(pb[1]);
            if (p != 0) return p;
            return pa[2].compareTo(pb[2]);
        });

        // Siralama sonrasi index xeritesi
        Map<String, Integer> colIndex = new LinkedHashMap<>();
        for (int i = 0; i < columnKeys.size(); i++) colIndex.put(columnKeys.get(i), i);

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Bet365 Odds");

        CellStyle hStyle   = makeStyle(wb, IndexedColors.DARK_BLUE,  IndexedColors.WHITE, true);
        CellStyle tStyle   = makeStyle(wb, IndexedColors.DARK_TEAL,  IndexedColors.WHITE, true);
        CellStyle altStyle = wb.createCellStyle();
        altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final int FIXED = 4; // Date, Match ID, Home, Away

        // ── Setir 0: qrup basliqlari ──────────────────────────────────────────────
        Row grpRow = sheet.createRow(0);
        setCell(grpRow, 0, "Date",     hStyle); sheet.setColumnWidth(0, 14*256);
        setCell(grpRow, 1, "Match ID", hStyle); sheet.setColumnWidth(1, 14*256);
        setCell(grpRow, 2, "Home",     hStyle); sheet.setColumnWidth(2, 20*256);
        setCell(grpRow, 3, "Away",     hStyle); sheet.setColumnWidth(3, 20*256);

        // Her sutun ucun bashliq yaz, sonra eyni qruplari birles
        String lastGrp = "";
        int grpStartCol = FIXED;
        for (int i = 0; i < columnKeys.size(); i++) {
            String key   = columnKeys.get(i);
            String[] p   = key.split("\\|");
            String grp   = p[0] + " | " + p[1];
            int col      = FIXED + i;

            sheet.setColumnWidth(col, 13*256);

            if (!grp.equals(lastGrp)) {
                // Yeni qrup bashlayir - evvelki qrupu merge et
                if (!lastGrp.isEmpty() && grpStartCol < col - 1) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, grpStartCol, col - 1));
                }
                // Yeni qrup bashligi yaz
                setCell(grpRow, col, grp, tStyle);
                lastGrp = grp;
                grpStartCol = col;
            }
        }
        // Sonuncu qrupu merge et
        if (!lastGrp.isEmpty()) {
            int lastDataCol = FIXED + columnKeys.size() - 1;
            if (grpStartCol < lastDataCol) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, grpStartCol, lastDataCol));
            }
        }

        // ── Setir 1: label basliqlari ─────────────────────────────────────────────
        Row lblRow = sheet.createRow(1);
        setCell(lblRow, 0, "Date",     hStyle);
        setCell(lblRow, 1, "Match ID", hStyle);
        setCell(lblRow, 2, "Home",     hStyle);
        setCell(lblRow, 3, "Away",     hStyle);
        for (int i = 0; i < columnKeys.size(); i++) {
            String label = columnKeys.get(i).split("\\|")[2];
            setCell(lblRow, FIXED + i, label, hStyle);
        }

        // ── Data setirleri ────────────────────────────────────────────────────────
        int rowNum = 2;
        for (MatchData md : data) {
            Row row      = sheet.createRow(rowNum);
            CellStyle cs = (rowNum % 2 == 0) ? altStyle : null;
            setCell(row, 0, md.date,     cs);
            setCell(row, 1, md.matchId,  cs);
            setCell(row, 2, md.homeTeam, cs);
            setCell(row, 3, md.awayTeam, cs);

            // Macin oddlarini map-e yukle
            Map<String,String> oddMap = new HashMap<>();
            for (OddEntry oe : md.odds)
                oddMap.put(oe.tab + "|" + oe.period + "|" + oe.label, oe.opening);

            // Her sutun ucun deger yaz
            for (int i = 0; i < columnKeys.size(); i++) {
                String val = oddMap.getOrDefault(columnKeys.get(i), "N/A");
                setCell(row, FIXED + i, val, cs);
            }
            rowNum++;
        }

        sheet.createFreezePane(0, 2);
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wb.write(fos);
        }
        wb.close();
        System.out.println("Toplam " + (rowNum - 2) + " mac, "
                + columnKeys.size() + " sutun yazildi.");
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

    static int tabIdx(String tab) {
        for (int i = 0; i < ODDS_TABS.length; i++)
            if (ODDS_TABS[i][0].equals(tab)) return i;
        return 99;
    }

    static int periodIdx(String p) {
        if (p.equals("Full Time")) return 0;
        if (p.equals("1st Half"))  return 1;
        if (p.equals("2nd Half"))  return 2;
        return 3;
    }
}