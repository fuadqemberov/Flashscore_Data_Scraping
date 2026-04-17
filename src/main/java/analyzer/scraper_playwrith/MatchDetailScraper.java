package analyzer.scraper_playwrith;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.HashMap;
import java.util.Map;

public class MatchDetailScraper {

    public static void scrapeMatch(Page page, MatchData md) {
        try {
            // Maç sayfasına git
            page.navigate(ScraperConstants.MATCH_URL_PREFIX + md.matchId);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Sayfanın iskeleti gelsin
            page.waitForSelector(".duelParticipant", new Page.WaitForSelectorOptions().setTimeout(10000));

            // 1. Temel Bilgileri Çek
            extractBasicInfo(page, md);

            // 2. HT Skoru için SUMMARY tabına tıkla
            clickTab(page, "SUMMARY");
            extractHtScore(page, md);

            // 3. Oranlar (ODDS) tabına geç
            if (!clickTab(page, "ODDS")) return;

            // Bet365 var mı kontrol et
            if (!waitForBet365(page)) return;

            // 4. Tüm Oranları Kazı (Matematiksel dönüştürme ile)
            scrapeAllOdds(page, md);

        } catch (Exception e) {
            System.err.println("   [ERR] " + md.matchId + " -> " + e.getMessage());
        }
    }

    private static void extractBasicInfo(Page page, MatchData md) {
        try {
            Locator items = page.locator(".wcl-breadcrumbItem_8btmf span[data-testid='wcl-scores-overline-03']");
            if (items.count() >= 3) {
                md.country = items.nth(1).innerText().trim();
                md.league  = items.nth(2).innerText().trim();
            }
            md.matchDateTime = page.locator(".duelParticipant__startTime div").innerText().trim();

            Locator scoreWrapper = page.locator(".detailScore__wrapper");
            String scoreText = scoreWrapper.innerText().replaceAll("\\n", "").replaceAll("\\s+", "");
            if (scoreText.contains("-")) md.ftScore = scoreText;
        } catch (Exception ignored) {}
    }

    private static void extractHtScore(Page page, MatchData md) {
        try {
            Locator ht = page.locator("span[data-testid='wcl-scores-overline-02'] div");
            if (ht.count() > 0) md.htScore = ht.first().innerText().trim();
        } catch (Exception ignored) {}
    }

    private static void scrapeAllOdds(Page page, MatchData md) {
        for (String[] tabDef : ScraperConstants.ODDS_TABS) {
            String tabKey = tabDef[0];
            String tabName = tabDef[1];

            if (!clickOddsSubTab(page, tabName)) continue;

            for (String period : ScraperConstants.HALF_TABS) {
                if (tabKey.equals("Correct score") && period.equals("2nd Half")) continue;
                if (!period.equals("Full Time") && !clickPeriodTab(page, period)) continue;

                if (!waitForBet365(page)) continue;

                switch (tabKey) {
                    case "1x2"           -> scrapeSimple(page, md, tabKey, period, new String[]{"Home", "Draw", "Away"});
                    case "Over/Under"    -> scrapeOverUnder(page, md, tabKey, period);
                    case "Both teams"    -> scrapeSimple(page, md, tabKey, period, new String[]{"Yes", "No"});
                    case "Double chance" -> scrapeSimple(page, md, tabKey, period, new String[]{"1X", "12", "X2"});
                    case "Correct score" -> scrapeCorrectScore(page, md, tabKey, period);
                }
            }
            clickTab(page, "ODDS");
        }
    }

    // --- MATEMATİKSEL DÖNÜŞTÜRÜCÜ (KESİNLİKLE ÇALIŞAN KISIM) ---
    private static String getOpeningOdd(Locator cell) {
        try {
            String raw = "";
            String title = cell.getAttribute("title");

            // Açılış oranı title içindeyse onu al (Selenium title mantığı)
            if (title != null && title.contains("»")) {
                raw = title.split("»")[0].trim();
            } else {
                raw = cell.innerText().trim();
            }

            // Eğer değer "21/50" veya "15/4" gibi kesirliyse çevir
            if (raw.contains("/")) {
                String[] p = raw.split("/");
                double pay = Double.parseDouble(p[0].trim());
                double payda = Double.parseDouble(p[1].trim());
                double result = (pay / payda) + 1;
                return String.format("%.2f", result).replace(",", ".");
            } else if (raw.equalsIgnoreCase("EVS")) {
                return "2.00";
            }
            return raw; // Zaten ondalıksa dokunma
        } catch (Exception e) {
            return "-";
        }
    }

    private static void scrapeSimple(Page page, MatchData md, String tab, String period, String[] labels) {
        try {
            Locator rows = page.locator(".ui-table__row");
            for (int i = 0; i < rows.count(); i++) {
                Locator row = rows.nth(i);
                if (isBet365(row)) {
                    Locator cells = row.locator("a.oddsCell__odd");
                    for (int j = 0; j < Math.min(cells.count(), labels.length); j++) {
                        md.oddsMap.put(tab + "|" + period + "|" + labels[j], getOpeningOdd(cells.nth(j)));
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private static void scrapeOverUnder(Page page, MatchData md, String tab, String period) {
        Map<Double, String[]> map = new HashMap<>();
        try {
            Locator rows = page.locator(".ui-table__row");
            for (int i = 0; i < rows.count(); i++) {
                Locator row = rows.nth(i);
                if (isBet365(row)) {
                    double th = Double.parseDouble(row.locator("[data-testid='wcl-oddsValue']").innerText().trim());
                    Locator cells = row.locator("a.oddsCell__odd");
                    map.put(th, new String[]{
                            cells.count() >= 1 ? getOpeningOdd(cells.nth(0)) : "-",
                            cells.count() >= 2 ? getOpeningOdd(cells.nth(1)) : "-"
                    });
                }
            }
        } catch (Exception ignored) {}

        for (double th : ScraperConstants.OU_THRESHOLDS) {
            String[] odds = map.getOrDefault(th, new String[]{"-", "-"});
            md.oddsMap.put(tab + "|" + period + "|O " + th, odds[0]);
            md.oddsMap.put(tab + "|" + period + "|U " + th, odds[1]);
        }
    }

    private static void scrapeCorrectScore(Page page, MatchData md, String tab, String period) {
        try {
            Locator rows = page.locator(".ui-table__row");
            for (int i = 0; i < rows.count(); i++) {
                Locator row = rows.nth(i);
                if (isBet365(row)) {
                    String score = (String) row.evaluate("el => { " +
                                                         "let s = el.querySelectorAll('span:not(.oddsCell__arrow)'); " +
                                                         "for(let x of s){ if(x.textContent.match(/\\d+:\\d+/)) return x.textContent.trim(); } " +
                                                         "return null; }");
                    if (score != null) {
                        md.oddsMap.put(tab + "|" + period + "|" + score, getOpeningOdd(row.locator("a.oddsCell__odd").first()));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // --- YARDIMCI METODLAR ---
    private static boolean isBet365(Locator row) {
        return row.locator("img[alt*='bet365'], [data-analytics-bookmaker-id='16']").count() > 0;
    }

    private static boolean clickTab(Page page, String name) {
        try {
            Locator t = page.locator("[data-testid='wcl-tab']:has-text('" + name + "')").first();
            if (t.isVisible()) { t.click(); page.waitForTimeout(300); return true; }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean clickOddsSubTab(Page page, String name) {
        try {
            Locator t = page.locator("button:has-text('" + name + "'), [data-testid='wcl-tab']:has-text('" + name + "')").first();
            if (t.isVisible()) { t.click(); page.waitForTimeout(400); return true; }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean clickPeriodTab(Page page, String period) {
        try {
            Locator t = page.locator("[data-testid='wcl-tab']:has-text('" + period.toUpperCase() + "')").first();
            if (t.isVisible()) { t.click(); page.waitForTimeout(300); return true; }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean waitForBet365(Page page) {
        try {
            page.waitForSelector("img[alt*='bet365'], [data-analytics-bookmaker-id='16']",
                    new Page.WaitForSelectorOptions().setTimeout(3000));
            return true;
        } catch (Exception e) { return false; }
    }
}