package analyzer.flashscore.gui;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class MatchDetailScraper {

    // =====================================================================
    // Ana scrape metodu
    // =====================================================================

    public static void scrapeMatch(WebDriver driver, MatchData md) {
        driver.get(ScraperConstants.MATCH_URL_PREFIX + md.matchId);

        try {
            WaitActionUtils.getSmartWait(driver, 10).until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".detail__breadcrumbs, .duelParticipant")));

            extractBasicInfo(driver, md);

            // HT skoru için: "MATCH" veya "SUMMARY" sekmelerinden birini dene
            if (clickMatchOrSummaryTab(driver)) {
                extractHtScore(driver, md);
            } else {
                // Hiçbir sekme tıklanamadıysa, sayfa zaten doğru içeriği
                // gösteriyor olabilir — direkt dene
                extractHtScore(driver, md);
            }

            if (clickMainTab(driver, "ODDS")) {
                WaitActionUtils.waitForNetworkIdle(driver);
                scrapeAllOdds(driver, md);
            } else {
                System.out.println("    [SKIP] Odds tab bulunamadi: " + md.matchId);
            }
        } catch (Exception e) {
            System.out.println("    [SKIP] Mac sayfasi hatasi: " + md.matchId + " -> " + e.getMessage());
        }
    }

    // =====================================================================
    // Temel bilgileri çekme
    // =====================================================================



    // =====================================================================
    // HT skoru çekme  (birden fazla CSS selector deneniyor)
    // =====================================================================

    private static void extractHtScore(WebDriver driver, MatchData md) {
        // Yöntem 1: wcl-scores-overline-02 (yeni tasarım)
        try {
            WebElement htEl = WaitActionUtils.getSmartWait(driver, 3).until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("span[data-testid='wcl-scores-overline-02'] div")));
            String text = htEl.getText().trim();
            if (!text.isEmpty()) {
                md.htScore = text;
                return;
            }
        } catch (Exception ignored) {}

        // Yöntem 2: Genel HT/1st Half text arama (eski/alternatif tasarım)
        try {
            List<WebElement> incidentRows = driver.findElements(
                    By.cssSelector(".smv__incidentsHeader, .smv__halfTime, [class*='halfTime'], [class*='half-time']"));
            for (WebElement row : incidentRows) {
                String text = row.getText().trim();
                // "HT 2-0" veya "2-0" gibi bir pattern ara
                Pattern p = Pattern.compile("(\\d+\\s*[:\\-]\\s*\\d+)");
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    md.htScore = m.group(1).replaceAll("\\s+", "");
                    return;
                }
            }
        } catch (Exception ignored) {}

        // Yöntem 3: Sayfa metninde "1ST HALF" veya "HT" bloğu ara
        try {
            List<WebElement> allRows = driver.findElements(By.cssSelector(".smv__incident"));
            // Bu satırlar arasında HT separator'ı bulunabilir; şimdilik pass
        } catch (Exception ignored) {}
    }

    // =====================================================================
    // Odds sekmesini scrape etme
    // =====================================================================

    private static void scrapeAllOdds(WebDriver driver, MatchData md) {
        for (String[] tabDef : ScraperConstants.ODDS_TABS) {
            String tabName = tabDef[0];
            String tabText = tabDef[1];

            if (!clickOddsSubTab(driver, tabText)) continue;

            for (String period : ScraperConstants.HALF_TABS) {
                if (tabName.equals("Correct score") && period.equals("2nd Half")) continue;

                if (!period.equals("Full Time") && !clickPeriodTab(driver, period)) continue;

                if (!waitForBet365(driver)) continue;

                switch (tabName) {
                    case "1x2":
                        scrapeSimpleTab(driver, md, tabName, period, new String[]{"Home", "Draw", "Away"});
                        break;
                    case "Over/Under":
                        scrapeOverUnderTab(driver, md, tabName, period);
                        break;
                    case "Both teams":
                        scrapeSimpleTab(driver, md, tabName, period, new String[]{"Yes", "No"});
                        break;
                    case "Double chance":
                        scrapeSimpleTab(driver, md, tabName, period, new String[]{"1X", "12", "X2"});
                        break;
                    case "Correct score":
                        scrapeCorrectScoreTab(driver, md, tabName, period);
                        break;
                }
            }
            clickMainTab(driver, "ODDS");
            try { Thread.sleep(300); } catch (Exception ignored) {}
        }
    }

    // =====================================================================
    // TAB TIKLAMA — ROBuST VERSİYON
    // =====================================================================

    /**
     * "MATCH" veya "SUMMARY" sekmelerinden hangisi varsa onu tıklar.
     * Her iki isim de aynı içeriği (maç özeti + HT skoru) gösterir.
     * Hangi tab aktif olursa olsun true döner; ikisi de yoksa false.
     */
    private static boolean clickMatchOrSummaryTab(WebDriver driver) {
        // Önce MATCH'i dene, yoksa SUMMARY'i dene
        for (String candidate : new String[]{"MATCH", "SUMMARY"}) {
            if (clickMainTab(driver, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verilen tabName'i içeren herhangi bir ana sekmeye tıklar.
     * Büyük/küçük harf duyarsız, kısmi eşleşme desteklenir.
     */
    private static boolean clickMainTab(WebDriver driver, String tabName) {
        // wcl-tab data-testid'li elementleri tara
        List<WebElement> tabs = driver.findElements(By.cssSelector("[data-testid='wcl-tab']"));
        for (WebElement tab : tabs) {
            if (!tab.isDisplayed()) continue;
            String txt = tab.getText().trim().toUpperCase();
            if (txt.contains(tabName.toUpperCase())) {
                WaitActionUtils.smartClick(driver, tab);
                try { Thread.sleep(400); } catch (Exception ignored) {}
                return true;
            }
        }

        // Fallback: herhangi bir görünür sekme/buton dene (bazı tasarımlarda
        // data-testid olmayabilir)
        List<WebElement> allTabs = driver.findElements(By.cssSelector("a[role='tab'], button[role='tab'], .tabs__tab"));
        for (WebElement tab : allTabs) {
            if (!tab.isDisplayed()) continue;
            String txt = tab.getText().trim().toUpperCase();
            if (txt.contains(tabName.toUpperCase())) {
                WaitActionUtils.smartClick(driver, tab);
                try { Thread.sleep(400); } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    private static boolean clickOddsSubTab(WebDriver driver, String tabText) {
        try { Thread.sleep(300); } catch (Exception ignored) {}
        List<WebElement> elements = driver.findElements(By.cssSelector("[data-testid='wcl-tab'], button"));
        for (WebElement el : elements) {
            if (!el.isDisplayed()) continue;
            String txt = el.getText().trim().toUpperCase();
            if (ScraperConstants.MAIN_TAB_TEXTS.contains(txt)) continue;
            if (txt.contains(tabText.toUpperCase())) {
                WaitActionUtils.smartClick(driver, el);
                try { Thread.sleep(500); } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    private static boolean clickPeriodTab(WebDriver driver, String period) {
        try { Thread.sleep(200); } catch (Exception ignored) {}
        List<WebElement> tabs = driver.findElements(By.cssSelector("[data-testid='wcl-tab']"));
        for (WebElement tab : tabs) {
            if (!tab.isDisplayed()) continue;
            String txt = tab.getText().trim().toUpperCase().replaceAll("\\s+", " ");
            // Ana sekme isimleriyle çakışıyorsa atla
            if (ScraperConstants.MAIN_TAB_TEXTS.contains(txt.replaceAll("\\s+", ""))) continue;

            boolean match = false;
            if (period.equals("1st Half") && (txt.contains("1ST") || txt.contains("1 ST") || txt.equals("HT"))) match = true;
            if (period.equals("2nd Half") && (txt.contains("2ND") || txt.contains("2 ND")))                     match = true;

            if (match) {
                WaitActionUtils.smartClick(driver, tab);
                try { Thread.sleep(400); } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // BET365 ROW HELPERS
    // =====================================================================

    private static boolean waitForBet365(WebDriver driver) {
        try {
            WaitActionUtils.getSmartWait(driver, 4).until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(
                            ".ui-table__row [data-analytics-bookmaker-id='16']," +
                            ".ui-table__row img[alt*='bet365']," +
                            ".ui-table__row img[alt*='Bet365']")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isBet365Row(WebElement row) {
        if (!row.findElements(By.cssSelector("[data-analytics-bookmaker-id='16']")).isEmpty()) return true;
        try {
            for (WebElement img : row.findElements(By.tagName("img"))) {
                String alt = img.getAttribute("alt");
                if (alt != null && alt.toLowerCase().contains("bet365")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String extractOpeningOdd(WebElement cell) {
        try {
            String title = cell.getAttribute("title");
            if (title != null && !title.isBlank()) {
                // "1.85 » 1.90" formatından açılış oranını al
                if (title.contains("»")) return title.split("»")[0].trim();
                return title.trim();
            }
            return cell.findElement(By.tagName("span")).getText().trim();
        } catch (Exception e) {
            return "-";
        }
    }

    // =====================================================================
    // VERİ ÇEKME (SCRAPING) FONKSİYONLARI
    // =====================================================================

    private static void scrapeSimpleTab(WebDriver driver, MatchData md,
                                        String tabName, String period, String[] labels) {
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
            for (WebElement row : rows) {
                if (!isBet365Row(row)) continue;
                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                for (int i = 0; i < Math.min(cells.size(), labels.length); i++) {
                    String key = tabName + "|" + period + "|" + labels[i];
                    md.oddsMap.put(key, extractOpeningOdd(cells.get(i)));
                }
                break; // İlk Bet365 satırı yeterli
            }
        } catch (Exception ignored) {}
    }

    private static void scrapeOverUnderTab(WebDriver driver, MatchData md,
                                           String tabName, String period) {
        Map<Double, String[]> thresholdOdds = new HashMap<>();
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
            for (WebElement row : rows) {
                if (!isBet365Row(row)) continue;
                double threshold;
                try {
                    String totalText = row.findElement(
                            By.cssSelector("[data-testid='wcl-oddsValue']")).getText().trim();
                    threshold = Double.parseDouble(totalText);
                } catch (Exception e) { continue; }

                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                String overOdd  = (cells.size() >= 1) ? extractOpeningOdd(cells.get(0)) : null;
                String underOdd = (cells.size() >= 2) ? extractOpeningOdd(cells.get(1)) : null;
                thresholdOdds.put(threshold, new String[]{overOdd, underOdd});
            }
        } catch (Exception ignored) {}

        for (double th : ScraperConstants.OU_THRESHOLDS) {
            String overKey  = tabName + "|" + period + "|O " + th;
            String underKey = tabName + "|" + period + "|U " + th;
            String[] odds   = thresholdOdds.getOrDefault(th, new String[]{null, null});
            md.oddsMap.put(overKey,  (odds[0] != null && !odds[0].isEmpty()) ? odds[0] : "-");
            md.oddsMap.put(underKey, (odds[1] != null && !odds[1].isEmpty()) ? odds[1] : "-");
        }
    }

    private static void scrapeCorrectScoreTab(WebDriver driver, MatchData md,
                                              String tabName, String period) {
        if (period.equals("2nd Half")) return;

        Map<String, String> scoreOddMap = new HashMap<>();
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector(".ui-table__row"));
            for (WebElement row : rows) {
                if (!isBet365Row(row)) continue;

                String scoreLabel = null;
                try {
                    scoreLabel = (String) ((JavascriptExecutor) driver).executeScript(
                            "var spans = arguments[0].querySelectorAll('span:not(.oddsCell__arrow):not(.oddsCell__linkIcon)');" +
                            "for(var i=0;i<spans.length;i++){" +
                            "  var t=spans[i].textContent.trim();" +
                            "  if(t.match(/^\\d+:\\d+$/)) return t;" +
                            "} return null;", row);
                } catch (Exception ignored) {}

                if (scoreLabel == null) {
                    Pattern p = Pattern.compile("(\\d+:\\d+)");
                    java.util.regex.Matcher m = p.matcher(row.getText());
                    if (m.find()) scoreLabel = m.group(1);
                }
                if (scoreLabel == null) continue;

                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                if (!cells.isEmpty()) {
                    scoreOddMap.put(scoreLabel, extractOpeningOdd(cells.get(0)));
                }
            }
        } catch (Exception ignored) {}

        for (String score : ScraperConstants.CORRECT_SCORES) {
            String key = tabName + "|" + period + "|" + score;
            md.oddsMap.put(key, scoreOddMap.getOrDefault(score, "-"));
        }
    }
    private static void extractBasicInfo(WebDriver driver, MatchData md) {

        // Sayfa içeriğinin gerçekten yüklenmesini bekle
        try {
            WaitActionUtils.getSmartWait(driver, 10).until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".wcl-breadcrumbItem_8btmf span[data-testid='wcl-scores-overline-03']")));
        } catch (Exception ignored) {}

        // Country & League — her biri ayrı try/catch
        try {
            List<WebElement> items = driver.findElements(
                    By.cssSelector(".wcl-breadcrumbItem_8btmf span[data-testid='wcl-scores-overline-03']"));
            // [0]=Football, [1]=Country, [2]=League
            if (items.size() >= 3) {
                md.country = items.get(1).getText().trim();
                md.league  = items.get(2).getText().trim();
            } else if (items.size() == 2) {
                md.country = items.get(0).getText().trim();
                md.league  = items.get(1).getText().trim();
            }
        } catch (Exception e) {
            System.out.println("    [WARN] Breadcrumb alinamadi: " + md.matchId);
        }

        // DateTime
        try {
            md.matchDateTime = driver.findElement(
                    By.cssSelector(".duelParticipant__startTime div")).getText().trim();
        } catch (Exception e) {
            System.out.println("    [WARN] DateTime alinamadi: " + md.matchId);
        }

        // FT Score — index'e göre direkt al (yapı her zaman aynı)
        try {
            List<WebElement> spans = driver.findElements(
                    By.cssSelector(".detailScore__wrapper span"));
            // spans: [0]=homeScore, [1]=divider("-"), [2]=awayScore
            if (spans.size() >= 3) {
                String home = spans.get(0).getText().trim();
                String away = spans.get(2).getText().trim();
                if (!home.isEmpty() && !away.isEmpty()) {
                    md.ftScore = home + "-" + away;
                }
            }
        } catch (Exception e) {
            System.out.println("    [WARN] FT Score alinamadi: " + md.matchId);
        }
    }
}