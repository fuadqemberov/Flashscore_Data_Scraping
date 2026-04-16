package analyzer.scraper_playwrith;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.HashMap;
import java.util.Map;

public class MatchDetailScraper {

    public static void scrapeMatch(Page page, MatchData md) {
        try {
            page.navigate(ScraperConstants.MATCH_URL_PREFIX + md.matchId);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);

            // Sayfa iskeleti gelsin
            page.waitForSelector(".duelParticipant", new Page.WaitForSelectorOptions().setTimeout(10000));

            // 🚀 ORANLARI DÜZELT (Artık Timeout vermeyecek!)
            OddsConfigurator.configureDecimalOdds(page);

            extractBasicInfo(page, md);
            clickTab(page, "SUMMARY");
            extractHtScore(page, md);

            if (!clickTab(page, "ODDS")) return;

            // ODDS tabında Bet365'i bekle
            if (!waitForBet365(page)) return;

            // Kazı
            scrapeAllOdds(page, md);

        } catch (Exception e) {
            System.err.println("   [ERR] " + md.matchId + " -> " + e.getMessage());
        }
    }

    private static void extractBasicInfo(Page page, MatchData md) {
        try {
            // Country & League (Breadcrumb üzerinden)
            Locator items = page.locator(".wcl-breadcrumbItem_8btmf span[data-testid='wcl-scores-overline-03']");
            if (items.count() >= 3) {
                md.country = items.nth(1).innerText().trim();
                md.league  = items.nth(2).innerText().trim();
            } else if (items.count() >= 2) {
                md.country = items.nth(0).innerText().trim();
                md.league  = items.nth(1).innerText().trim();
            }

            // Maç Zamanı
            md.matchDateTime = page.locator(".duelParticipant__startTime div").innerText().trim();

            // FT Skoru (Maç Sonu)
            Locator scoreWrapper = page.locator(".detailScore__wrapper");
            String scoreText = scoreWrapper.innerText().replaceAll("\\n", "").replaceAll("\\s+", "");
            if (scoreText.contains("-")) {
                md.ftScore = scoreText;
            }
        } catch (Exception ignored) {}
    }

    private static void extractHtScore(Page page, MatchData md) {
        try {
            // Üstteki HT yazısından skoru yakala
            Locator ht = page.locator("span[data-testid='wcl-scores-overline-02'] div");
            if (ht.count() > 0) {
                String val = ht.first().innerText().trim();
                if (!val.isEmpty()) md.htScore = val;
            }
        } catch (Exception ignored) {}
    }

    private static void scrapeAllOdds(Page page, MatchData md) {
        for (String[] tabDef : ScraperConstants.ODDS_TABS) {
            String tabKey = tabDef[0];
            String tabName = tabDef[1];

            if (!clickOddsSubTab(page, tabName)) continue;

            for (String period : ScraperConstants.HALF_TABS) {
                // Correct Score'da 2. yarı genelde olmaz
                if (tabKey.equals("Correct score") && period.equals("2nd Half")) continue;

                // Period tabına tıkla (Full Time değilse)
                if (!period.equals("Full Time") && !clickPeriodTab(page, period)) continue;

                if (!waitForBet365(page)) continue;

                // Veri kazıma işlemi
                switch (tabKey) {
                    case "1x2"           -> scrapeSimple(page, md, tabKey, period, new String[]{"Home", "Draw", "Away"});
                    case "Over/Under"    -> scrapeOverUnder(page, md, tabKey, period);
                    case "Both teams"    -> scrapeSimple(page, md, tabKey, period, new String[]{"Yes", "No"});
                    case "Double chance" -> scrapeSimple(page, md, tabKey, period, new String[]{"1X", "12", "X2"});
                    case "Correct score" -> scrapeCorrectScore(page, md, tabKey, period);
                }
            }
            // Ana ODDS tabına dön
            clickTab(page, "ODDS");
        }
    }

    // --- YARDIMCI METODLAR (Tıklama ve Bekleme) ---

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
            String pStr = period.toUpperCase();
            Locator t = page.locator("[data-testid='wcl-tab']:has-text('" + pStr + "')").first();
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

    // --- VERİ ÇEKME METODLARI ---

    private static void scrapeSimple(Page page, MatchData md, String tab, String period, String[] labels) {
        try {
            Locator rows = page.locator(".ui-table__row");
            for (int i = 0; i < rows.count(); i++) {
                Locator row = rows.nth(i);
                if (row.locator("img[alt*='bet365'], [data-analytics-bookmaker-id='16']").count() > 0) {
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
                if (row.locator("img[alt*='bet365'], [data-analytics-bookmaker-id='16']").count() > 0) {
                    double threshold = Double.parseDouble(row.locator("[data-testid='wcl-oddsValue']").innerText().trim());
                    Locator cells = row.locator("a.oddsCell__odd");
                    map.put(threshold, new String[]{
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
                if (row.locator("img[alt*='bet365'], [data-analytics-bookmaker-id='16']").count() > 0) {
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

    private static String getOpeningOdd(Locator cell) {
        try {
            String title = cell.getAttribute("title");
            if (title != null && title.contains("»")) return title.split("»")[0].trim();
            return cell.innerText().trim();
        } catch (Exception e) { return "-"; }
    }
}