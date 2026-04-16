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
        int maxRetries = 3; // 3 defa deneme hakkı
        boolean success = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                driver.get(ScraperConstants.MATCH_URL_PREFIX + md.matchId);

                // YENİ EKLENDİ: Bekleme süresini 10'dan 20 saniyeye çıkardık.
                WaitActionUtils.getSmartWait(driver, 20).until(
                        ExpectedConditions.presenceOfElementLocated(
                                By.cssSelector(".duelParticipant")));

                // Network trafiğinin sakinleşmesini bekle
                WaitActionUtils.waitForNetworkIdle(driver);

                // Temel bilgileri (Skor, Tarih, Lig) çek
                extractBasicInfo(driver, md);

                // HT skorunu çekmek için Match veya Summary tabına tıkla
                clickMatchOrSummaryTab(driver);

                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                extractHtScore(driver, md);

                // ODDS tabına keç
                if (!clickMainTab(driver, "ODDS")) {
                    System.out.println("    [SKIP] Odds tab bulunamadi: " + md.matchId);
                    break; // Odds yoksa diğer denemeye gerek yok
                }
                WaitActionUtils.waitForNetworkIdle(driver);

                if (!hasBet365OnScreen(driver)) {
                    System.out.println("    [SKIP] Bet365 bu macda yoxdur: " + md.matchId);
                    break;
                }

                scrapeAllOdds(driver, md);
                success = true; // Her şey yolunda bitti
                break; // Başarılı olduğu için For döngüsünden çık

            } catch (Exception e) {
                if (attempt < maxRetries) {
                    System.out.println("    [RETRY] " + md.matchId + " yuklenemedi. Tekrar deneniyor... (" + attempt + "/" + maxRetries + ")");
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {} // 2 saniye nefes al
                } else {
                    System.out.println("    [SKIP] Mac sayfasi hatasi (3 deneme basarisiz): " + md.matchId + " -> " + e.getMessage());
                }
            }
        }
    }

    // =====================================================================
    // İlk açılan odds səhifəsində Bet365 var mı? (sürətli yoxlama)
    // =====================================================================

    private static boolean hasBet365OnScreen(WebDriver driver) {
        try {
            WaitActionUtils.getSmartWait(driver, 4).until(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(
                            ".ui-table__row [data-analytics-bookmaker-id='16']," +
                            ".ui-table__row img[alt*='bet365']," +
                            ".ui-table__row img[alt*='Bet365']")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =====================================================================
    // Temel bilgileri çekme (GÜNCELLENDİ - BEKLEME EKLENDİ)
    // =====================================================================

    private static void extractBasicInfo(WebDriver driver, MatchData md) {

        // Country & League
        try {
            List<WebElement> items = WaitActionUtils.getSmartWait(driver, 5)
                    .until(d -> {
                        List<WebElement> els = d.findElements(
                                By.cssSelector(".wcl-breadcrumbItem_8btmf span[data-testid='wcl-scores-overline-03']"));
                        return els.size() >= 2 ? els : null;
                    });
            if (items != null && items.size() >= 3) {
                md.country = items.get(1).getText().trim();
                md.league  = items.get(2).getText().trim();
            } else if (items != null && items.size() >= 2) {
                md.country = items.get(0).getText().trim();
                md.league  = items.get(1).getText().trim();
            }
        } catch (Exception e) {
            System.out.println("    [WARN] Country/League alinamadi: " + md.matchId);
        }

        // DateTime (Bekleme eklendi)
        try {
            WebElement dateEl = WaitActionUtils.getSmartWait(driver, 5).until(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".duelParticipant__startTime div")));
            md.matchDateTime = dateEl.getText().trim();
        } catch (Exception ignored) {
            System.out.println("    [WARN] DateTime alinamadi: " + md.matchId);
        }

        // FT Score (Bekleme ve Alternatif okuma yöntemi eklendi)
        try {
            WebElement scoreWrapper = WaitActionUtils.getSmartWait(driver, 5).until(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detailScore__wrapper")));

            List<WebElement> spans = scoreWrapper.findElements(By.tagName("span"));
            if (spans.size() >= 3) {
                String home = spans.get(0).getText().trim();
                String away = spans.get(2).getText().trim();
                if (!home.isEmpty() && !away.isEmpty()) md.ftScore = home + "-" + away;
            } else {
                // Flashscore bazen span yerine direkt text basabiliyor, yedek çözüm:
                String rawScore = scoreWrapper.getText().replaceAll("\\n", "").replaceAll("\\s+", "");
                if (rawScore.contains("-") && rawScore.length() >= 3) {
                    md.ftScore = rawScore;
                }
            }
        } catch (Exception ignored) {
            System.out.println("    [WARN] FT Score alinamadi: " + md.matchId);
        }
    }

    // =====================================================================
    // HT skoru çekme (GÜNCELLENDİ - BEKLEME EKLENDİ)
    // =====================================================================

    private static void extractHtScore(WebDriver driver, MatchData md) {
        try {
            WebElement htEl = WaitActionUtils.getSmartWait(driver, 4).until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("span[data-testid='wcl-scores-overline-02'] div")));
            String text = htEl.getText().trim();
            if (!text.isEmpty()) {
                md.htScore = text;
                return;
            }
        } catch (Exception ignored) {}

        // Eğer ilk yöntem çalışmazsa fallback (ikinci) yöntemi dene
        try {
            List<WebElement> incidentRows = driver.findElements(
                    By.cssSelector(".smv__incidentsHeader,.smv__halfTime,[class*='halfTime'],[class*='half-time']"));
            for (WebElement row : incidentRows) {
                java.util.regex.Matcher m = Pattern.compile("(\\d+\\s*[:\\-]\\s*\\d+)").matcher(row.getText().trim());
                if (m.find()) {
                    md.htScore = m.group(1).replaceAll("\\s+", "");
                    return;
                }
            }
        } catch (Exception ignored) {}
    }


    // =====================================================================
    // AŞAĞIDAKİ HİÇBİR KODA (ODDS VE TAB TIKLAMA) DOKUNULMAMIŞTIR !
    // =====================================================================

    private static void scrapeAllOdds(WebDriver driver, MatchData md) {
        for (String[] tabDef : ScraperConstants.ODDS_TABS) {
            String tabName = tabDef[0];
            String tabText = tabDef[1];

            if (!clickOddsSubTab(driver, tabText)) continue;

            for (String period : ScraperConstants.HALF_TABS) {
                if (tabName.equals("Correct score") && period.equals("2nd Half")) continue;
                if (!period.equals("Full Time") && !clickPeriodTab(driver, period)) continue;

                if (!waitForBet365(driver)) {
                    System.out.println("    [SKIP] Bu period ucun Bet365 yoxdur: " + tabName + " | " + period);
                    continue;
                }

                switch (tabName) {
                    case "1x2"           -> scrapeSimpleTab(driver, md, tabName, period, new String[]{"Home","Draw","Away"});
                    case "Over/Under"    -> scrapeOverUnderTab(driver, md, tabName, period);
                    case "Both teams"    -> scrapeSimpleTab(driver, md, tabName, period, new String[]{"Yes","No"});
                    case "Double chance" -> scrapeSimpleTab(driver, md, tabName, period, new String[]{"1X","12","X2"});
                    case "Correct score" -> scrapeCorrectScoreTab(driver, md, tabName, period);
                }
            }
            clickMainTab(driver, "ODDS");
            try { Thread.sleep(300); } catch (Exception ignored) {}
        }
    }

    // =====================================================================
    // TAB TIKLAMA
    // =====================================================================

    private static boolean clickMatchOrSummaryTab(WebDriver driver) {
        for (String c : new String[]{"MATCH","SUMMARY"})
            if (clickMainTab(driver, c)) return true;
        return false;
    }

    static boolean clickMainTab(WebDriver driver, String tabName) {
        for (WebElement tab : driver.findElements(By.cssSelector("[data-testid='wcl-tab']"))) {
            if (!tab.isDisplayed()) continue;
            if (tab.getText().trim().toUpperCase().contains(tabName.toUpperCase())) {
                WaitActionUtils.smartClick(driver, tab);
                try { Thread.sleep(400); } catch (Exception ignored) {}
                return true;
            }
        }
        for (WebElement tab : driver.findElements(By.cssSelector("a[role='tab'],button[role='tab'],.tabs__tab"))) {
            if (!tab.isDisplayed()) continue;
            if (tab.getText().trim().toUpperCase().contains(tabName.toUpperCase())) {
                WaitActionUtils.smartClick(driver, tab);
                try { Thread.sleep(400); } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    private static boolean clickOddsSubTab(WebDriver driver, String tabText) {
        try { Thread.sleep(300); } catch (Exception ignored) {}
        for (WebElement el : driver.findElements(By.cssSelector("[data-testid='wcl-tab'],button"))) {
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
        for (WebElement tab : driver.findElements(By.cssSelector("[data-testid='wcl-tab']"))) {
            if (!tab.isDisplayed()) continue;
            String txt = tab.getText().trim().toUpperCase().replaceAll("\\s+"," ");
            if (ScraperConstants.MAIN_TAB_TEXTS.contains(txt.replaceAll("\\s+",""))) continue;
            boolean match =
                    (period.equals("1st Half") && (txt.contains("1ST")||txt.contains("1 ST")||txt.equals("HT"))) ||
                    (period.equals("2nd Half") && (txt.contains("2ND")||txt.contains("2 ND")));
            if (match) {
                WaitActionUtils.smartClick(driver, tab);
                try { Thread.sleep(400); } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // BET365 HELPERS
    // =====================================================================

    private static boolean waitForBet365(WebDriver driver) {
        try {
            WaitActionUtils.getSmartWait(driver, 4).until(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(
                            ".ui-table__row [data-analytics-bookmaker-id='16']," +
                            ".ui-table__row img[alt*='bet365']," +
                            ".ui-table__row img[alt*='Bet365']")));
            return true;
        } catch (Exception e) { return false; }
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
                if (title.contains("»")) return title.split("»")[0].trim();
                return title.trim();
            }
            return cell.findElement(By.tagName("span")).getText().trim();
        } catch (Exception e) { return "-"; }
    }

    // =====================================================================
    // VERİ ÇEKME
    // =====================================================================

    private static void scrapeSimpleTab(WebDriver driver, MatchData md,
                                        String tabName, String period, String[] labels) {
        try {
            for (WebElement row : driver.findElements(By.cssSelector(".ui-table__row"))) {
                if (!isBet365Row(row)) continue;
                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                for (int i = 0; i < Math.min(cells.size(), labels.length); i++)
                    md.oddsMap.put(tabName+"|"+period+"|"+labels[i], extractOpeningOdd(cells.get(i)));
                break;
            }
        } catch (Exception ignored) {}
    }

    private static void scrapeOverUnderTab(WebDriver driver, MatchData md,
                                           String tabName, String period) {
        Map<Double,String[]> thresholdOdds = new HashMap<>();
        try {
            for (WebElement row : driver.findElements(By.cssSelector(".ui-table__row"))) {
                if (!isBet365Row(row)) continue;
                double threshold;
                try { threshold = Double.parseDouble(row.findElement(
                        By.cssSelector("[data-testid='wcl-oddsValue']")).getText().trim());
                } catch (Exception e) { continue; }
                List<WebElement> cells = row.findElements(By.cssSelector("a.oddsCell__odd"));
                thresholdOdds.put(threshold, new String[]{
                        cells.size()>=1 ? extractOpeningOdd(cells.get(0)) : null,
                        cells.size()>=2 ? extractOpeningOdd(cells.get(1)) : null});
            }
        } catch (Exception ignored) {}

        for (double th : ScraperConstants.OU_THRESHOLDS) {
            String[] odds = thresholdOdds.getOrDefault(th, new String[]{null,null});
            md.oddsMap.put(tabName+"|"+period+"|O "+th, odds[0]!=null?odds[0]:"-");
            md.oddsMap.put(tabName+"|"+period+"|U "+th, odds[1]!=null?odds[1]:"-");
        }
    }

    private static void scrapeCorrectScoreTab(WebDriver driver, MatchData md,
                                              String tabName, String period) {
        if (period.equals("2nd Half")) return;
        Map<String,String> scoreOddMap = new HashMap<>();
        try {
            for (WebElement row : driver.findElements(By.cssSelector(".ui-table__row"))) {
                if (!isBet365Row(row)) continue;
                String scoreLabel = null;
                try {
                    scoreLabel = (String)((JavascriptExecutor)driver).executeScript(
                            "var s=arguments[0].querySelectorAll('span:not(.oddsCell__arrow):not(.oddsCell__linkIcon)');" +
                            "for(var i=0;i<s.length;i++){var t=s[i].textContent.trim();if(t.match(/^\\d+:\\d+$/))return t;}return null;",row);
                } catch (Exception ignored) {}
                if (scoreLabel==null) {
                    java.util.regex.Matcher m=Pattern.compile("(\\d+:\\d+)").matcher(row.getText());
                    if(m.find()) scoreLabel=m.group(1);
                }
                if (scoreLabel==null) continue;
                List<WebElement> cells=row.findElements(By.cssSelector("a.oddsCell__odd"));
                if(!cells.isEmpty()) scoreOddMap.put(scoreLabel, extractOpeningOdd(cells.get(0)));
            }
        } catch (Exception ignored) {}

        for (String score : ScraperConstants.CORRECT_SCORES)
            md.oddsMap.put(tabName+"|"+period+"|"+score, scoreOddMap.getOrDefault(score,"-"));
    }
}