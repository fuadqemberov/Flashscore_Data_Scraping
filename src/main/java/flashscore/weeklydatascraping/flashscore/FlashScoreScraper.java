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

    // ── Odds tab display names (data-analytics-alias values for matching) ─────────
    // Format: { internalName, partialText to match in button }
    // Excluded: Asian handicap, European handicap, Draw no bet, Odd/Even
    static final String[][] ODDS_TABS = {
            {"1x2",          "1X2"},
            {"Over/Under",   "Over/Under"},
            {"Both teams",   "Both teams to score"},
            {"Double chance","Double chance"},
            {"Correct score","Correct score"},
            {"HT/FT",        "Half Time/Full Time"}
    };

    static final String[] TIME_TABS = {"Full Time", "1st Half", "2nd Half"};

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

    static LinkedHashMap<String, Integer> columnMap = new LinkedHashMap<>();

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

        // 1. Open match page
        driver.get("https://www.flashscore.co.uk/match/football/" + matchId);
        Thread.sleep(3000);

        // 2. Click "Odds" main tab (the top-level tab on the match page)
        if (!clickButtonContaining("Odds")) {
            System.out.println("    [SKIP] Odds tab bulunamadi: " + matchId);
            return null;
        }
        Thread.sleep(2500);

        MatchData md = new MatchData();
        md.matchId=matchId; md.homeTeam=home; md.awayTeam=away; md.date=date;

        // 3. For each odds type tab (1X2, Over/Under, ...)
        for (String[] tabDef : ODDS_TABS) {
            String tabName    = tabDef[0];
            String tabText    = tabDef[1];

            // Click the secondary odds-type tab (1X2, Over/Under, etc.)
            // These are <a data-analytics-alias="..."><button> elements
            if (!clickOddsTypeTab(tabText)) {
                System.out.println("    [SKIP] Odds tab bulunamadi: " + tabText);
                continue;
            }
            Thread.sleep(2000);

            // 4. For each time period (Full Time, 1st Half, 2nd Half)
            for (String period : TIME_TABS) {
                if (!clickTimeTab(period)) continue;
                Thread.sleep(1800);

                List<OddEntry> entries = scrapeBet365Rows(tabName, period);
                md.odds.addAll(entries);
                for (OddEntry e : entries) {
                    columnMap.putIfAbsent(e.tab+"|"+e.period+"|"+e.label, columnMap.size());
                }
            }
        }
        return md;
    }

    // ── Click a top-level match tab (Odds, Summary, etc.) ────────────────────────
    // These are [data-testid='wcl-tab'] buttons
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

    // ── Click secondary odds-type tab (1X2 / Over/Under / Both teams / ...) ───────
    // These are <a data-analytics-alias="..."> containing a <button>
    // We match on the <a title="..."> attribute or the button text
    static boolean clickOddsTypeTab(String tabText) throws InterruptedException {
        try {
            // Strategy A: match <a> by title attribute
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
            // Strategy B: match button text inside those anchors
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

    // ── Click Full Time / 1st Half / 2nd Half inner tab ──────────────────────────
    // These are also [data-testid='wcl-tab'] but with text FULLTIME / 1ST HALF / 2ND HALF
    static boolean clickTimeTab(String period) throws InterruptedException {
        try {
            List<WebElement> tabs = driver.findElements(
                    By.cssSelector("[data-testid='wcl-tab']"));
            for (WebElement tab : tabs) {
                String txt = tab.getText().trim().toUpperCase().replaceAll("\\s+","");
                boolean match = false;
                if (period.equals("Full Time") && (txt.startsWith("FULLTIME") || txt.startsWith("FULL"))) match=true;
                if (period.equals("1st Half")  && (txt.startsWith("1ST") || txt.startsWith("1STHALF")))  match=true;
                if (period.equals("2nd Half")  && (txt.startsWith("2ND") || txt.startsWith("2NDHALF")))  match=true;
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
                // Is this a Bet365 row?
                if (row.findElements(By.cssSelector("[data-analytics-bookmaker-id='16']")).isEmpty())
                    continue;

                List<OddEntry> parsed;
                switch (tabName) {
                    case "1x2":
                        parsed = parse1x2(row, tabName, period);
                        result.addAll(parsed);
                        break; // only one Bet365 row
                    case "Over/Under":
                        parsed = parseOverUnder(row, tabName, period);
                        result.addAll(parsed);
                        // DON'T break — multiple rows (one per total line)
                        continue;
                    case "Both teams":
                        parsed = parseLabeledCells(row, tabName, period, new String[]{"Yes","No"});
                        result.addAll(parsed);
                        break;
                    case "Double chance":
                        parsed = parseLabeledCells(row, tabName, period, new String[]{"1X","12","X2"});
                        result.addAll(parsed);
                        break;
                    case "Correct score":
                        parsed = parseCorrectScore(row, tabName, period);
                        result.addAll(parsed);
                        continue; // multiple rows
                    case "HT/FT":
                        parsed = parseLabeledCells(row, tabName, period,
                                new String[]{"1/1","1/X","1/2","X/1","X/X","X/2","2/1","2/X","2/2"});
                        result.addAll(parsed);
                        continue; // multiple rows possible
                    default:
                        break;
                }
                break;
            }
        } catch (Exception e) {
            System.out.println("    scrapeBet365Rows ["+tabName+"/"+period+"]: " + e.getMessage());
        }
        return result;
    }

    static List<OddEntry> parse1x2(WebElement row, String tab, String period) {
        List<OddEntry> r = new ArrayList<>();
        String[] labels = {"Home","Draw","Away"};
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        for (int i = 0; i < Math.min(cells.size(), labels.length); i++)
            r.add(new OddEntry(tab, period, labels[i], extractOpeningOdd(cells.get(i))));
        return r;
    }

    static List<OddEntry> parseOverUnder(WebElement row, String tab, String period) {
        List<OddEntry> r = new ArrayList<>();
        String total = "?";
        try {
            total = row.findElement(By.cssSelector("[data-testid='wcl-oddsValue']")).getText().trim();
        } catch (Exception ignored) {}
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        if (cells.size() >= 1) r.add(new OddEntry(tab, period, "O "+total, extractOpeningOdd(cells.get(0))));
        if (cells.size() >= 2) r.add(new OddEntry(tab, period, "U "+total, extractOpeningOdd(cells.get(1))));
        return r;
    }

    static List<OddEntry> parseLabeledCells(WebElement row, String tab, String period, String[] labels) {
        List<OddEntry> r = new ArrayList<>();
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        for (int i = 0; i < Math.min(cells.size(), labels.length); i++)
            r.add(new OddEntry(tab, period, labels[i], extractOpeningOdd(cells.get(i))));
        return r;
    }

    static List<OddEntry> parseCorrectScore(WebElement row, String tab, String period) {
        List<OddEntry> r = new ArrayList<>();
        // Score label: try to find it from a dedicated element, fallback to row text prefix
        String scoreLabel = "?";
        try {
            // The score label is often the first text element in the row before odds cells
            // Try reading via JavaScript the first text node or a specific child
            scoreLabel = (String) ((JavascriptExecutor)driver).executeScript(
                    "var el = arguments[0]; " +
                            "var spans = el.querySelectorAll('span:not(.oddsCell__arrow):not(.oddsCell__linkIcon)'); " +
                            "for(var i=0;i<spans.length;i++){ var t=spans[i].textContent.trim(); " +
                            "if(t.match(/^\\d+:\\d+$/)) return t; } return null;", row);
        } catch (Exception ignored) {}
        if (scoreLabel == null || scoreLabel.equals("?")) {
            try {
                String rawText = row.getText().trim();
                // Extract "0:0", "1:0" etc patterns
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\d+:\\d+)").matcher(rawText);
                if (m.find()) scoreLabel = m.group(1);
            } catch (Exception ignored) {}
        }
        List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
        // Correct score rows usually have 1 odd cell
        for (int i = 0; i < cells.size(); i++) {
            String lbl = scoreLabel + (cells.size() > 1 ? "_"+i : "");
            r.add(new OddEntry(tab, period, lbl, extractOpeningOdd(cells.get(i))));
        }
        return r;
    }

    static String extractOpeningOdd(WebElement cell) {
        try {
            String title = cell.getAttribute("title");
            if (title != null && title.contains("»"))
                return title.split("»")[0].trim();
            // fallback: visible span text
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
                        try { settingsRow = el.findElement(By.xpath("./ancestor::*[@role='button'][1]")); }
                        catch (Exception ignored) {}
                        break;
                    }
                }
            }
            if (settingsRow == null) { System.out.println("  [Settings] Bulunamadi."); return; }
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
            for (String sel : new String[]{"[data-testid='wcl-dialogCloseButton']",
                    "button.settings__closeButton","button.wcl-closeButton_6bc3P"}) {
                try {
                    ((JavascriptExecutor)driver).executeScript("arguments[0].click();",
                            driver.findElement(By.cssSelector(sel)));
                    closed = true; break;
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
//        for (WebElement row : driver.findElements(By.cssSelector("div[id^='g_1_'].event__match"))) {
//            try {
//                String matchId = row.getAttribute("id").replace("g_1_","");
//                String home="", away="";
//                try { home = row.findElement(By.cssSelector(
//                        ".event__homeParticipant [data-testid='wcl-scores-simple-text-01']")).getText().trim(); }
//                catch (Exception ignored) {}
//                try { away = row.findElement(By.cssSelector(
//                        ".event__awayParticipant [data-testid='wcl-scores-simple-text-01']")).getText().trim(); }
//                catch (Exception ignored) {}
//                if (!matchId.isEmpty()) result.add(new String[]{matchId, home, away});
//            } catch (Exception ignored) {}
//        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // EXCEL EXPORT
    // ══════════════════════════════════════════════════════════════════════════════
    static void exportToExcel(List<MatchData> data, String filename) throws IOException {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Bet365 Odds");

        // Styles
        CellStyle hStyle = makeStyle(wb, IndexedColors.DARK_BLUE,  IndexedColors.WHITE, true);
        CellStyle tStyle = makeStyle(wb, IndexedColors.DARK_TEAL,  IndexedColors.WHITE, true);
        CellStyle altStyle = wb.createCellStyle();
        altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Sort columns: by tab order → period order → label
        List<String> colKeys = new ArrayList<>(columnMap.keySet());
        colKeys.sort((a, b) -> {
            String[] pa = a.split("\\|"), pb = b.split("\\|");
            int t = tabIdx(pa[0]) - tabIdx(pb[0]);
            if (t!=0) return t;
            int p = periodIdx(pa[1]) - periodIdx(pb[1]);
            if (p!=0) return p;
            return pa[2].compareTo(pb[2]);
        });
        columnMap.clear();
        for (int i=0; i<colKeys.size(); i++) columnMap.put(colKeys.get(i), i);

        int FIXED = 4;

        // Row 0: group headers (Tab | Period) merged
        Row grpRow = sheet.createRow(0);
        setCell(grpRow,0,"Date",hStyle);    sheet.setColumnWidth(0,14*256);
        setCell(grpRow,1,"Match ID",hStyle);sheet.setColumnWidth(1,14*256);
        setCell(grpRow,2,"Home",hStyle);    sheet.setColumnWidth(2,20*256);
        setCell(grpRow,3,"Away",hStyle);    sheet.setColumnWidth(3,20*256);

        String lastGrp=""; int grpStart=FIXED;
        for (Map.Entry<String,Integer> e : columnMap.entrySet()) {
            String[] p = e.getKey().split("\\|");
            String grp = p[0]+" | "+p[1];
            int col = FIXED+e.getValue();
            if (!grp.equals(lastGrp)) {
                if (!lastGrp.isEmpty() && grpStart < col-1)
                    sheet.addMergedRegion(new CellRangeAddress(0,0,grpStart,col-1));
                setCell(grpRow, grpStart, lastGrp.isEmpty()? grp : grp, tStyle);
                lastGrp=grp; grpStart=col;
            }
            sheet.setColumnWidth(col, 13*256);
        }
        if (!lastGrp.isEmpty()) {
            int lastCol = FIXED+columnMap.size()-1;
            if (grpStart<lastCol) sheet.addMergedRegion(new CellRangeAddress(0,0,grpStart,lastCol));
            Cell c = grpRow.getCell(grpStart);
            if (c==null) setCell(grpRow,grpStart,lastGrp,tStyle);
        }

        // Row 1: label headers
        Row lblRow = sheet.createRow(1);
        setCell(lblRow,0,"Date",hStyle);
        setCell(lblRow,1,"Match ID",hStyle);
        setCell(lblRow,2,"Home",hStyle);
        setCell(lblRow,3,"Away",hStyle);
        for (Map.Entry<String,Integer> e : columnMap.entrySet())
            setCell(lblRow, FIXED+e.getValue(), e.getKey().split("\\|")[2], hStyle);

        // Data rows
        int rowNum=2;
        for (MatchData md : data) {
            Row row = sheet.createRow(rowNum);
            CellStyle cs = (rowNum%2==0)? altStyle : null;
            setCell(row,0,md.date,cs);
            setCell(row,1,md.matchId,cs);
            setCell(row,2,md.homeTeam,cs);
            setCell(row,3,md.awayTeam,cs);

            Map<String,String> om = new HashMap<>();
            for (OddEntry oe : md.odds) om.put(oe.tab+"|"+oe.period+"|"+oe.label, oe.opening);
            for (Map.Entry<String,Integer> e : columnMap.entrySet())
                setCell(row, FIXED+e.getValue(), om.getOrDefault(e.getKey(),"N/A"), cs);
            rowNum++;
        }

        sheet.createFreezePane(0,2);
        try (FileOutputStream fos = new FileOutputStream(filename)) { wb.write(fos); }
        wb.close();
        System.out.println("Toplam " + (rowNum-2) + " mac, " + columnMap.size() + " sutun yazildi.");
    }

    static CellStyle makeStyle(Workbook wb, IndexedColors bg, IndexedColors fg, boolean bold) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(bold); f.setColor(fg.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(bg.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    static void setCell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val!=null?val:"");
        if (style!=null) c.setCellStyle(style);
    }

    static int tabIdx(String tab) {
        for (int i=0;i<ODDS_TABS.length;i++) if(ODDS_TABS[i][0].equals(tab)) return i;
        return 99;
    }
    static int periodIdx(String p) {
        if(p.equals("Full Time")) return 0;
        if(p.equals("1st Half"))  return 1;
        if(p.equals("2nd Half"))  return 2;
        return 3;
    }
}