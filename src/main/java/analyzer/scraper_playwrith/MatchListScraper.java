package analyzer.scraper_playwrith;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MatchListScraper {

    public static List<MatchData> collectMatchesForDays(Page page, int days) {
        List<MatchData> allMatches = new ArrayList<>();
        page.navigate(ScraperConstants.BASE_URL);

        // Cookie kabul (Varsa)
        try {
            page.locator("#onetrust-accept-btn-handler").click(new Locator.ClickOptions().setTimeout(3000));
        } catch (Exception ignored) {}

        OddsConfigurator.configureDecimalOdds(page);

        for (int dayOffset = 1; dayOffset <= days; dayOffset++) {
            LocalDate targetDate = LocalDate.now().minusDays(dayOffset);

            for (int i = 0; i < dayOffset; i++) {
                page.locator("[data-day-picker-arrow='prev']").click();
                page.waitForTimeout(1000);
            }

            page.waitForSelector("div[id^='g_1_']");
            page.waitForTimeout(1000);

            Locator rows = page.locator("div[id^='g_1_'].event__match");
            for (int i = 0; i < rows.count(); i++) {
                try {
                    Locator row = rows.nth(i);
                    String matchId = row.getAttribute("id").replace("g_1_", "");
                    String home = row.locator(".event__homeParticipant").innerText().trim();
                    String away = row.locator(".event__awayParticipant").innerText().trim();
                    allMatches.add(new MatchData(matchId, home, away, targetDate.toString()));
                } catch (Exception ignored) {}
            }
            page.navigate(ScraperConstants.BASE_URL);
        }
        return allMatches;
    }
}