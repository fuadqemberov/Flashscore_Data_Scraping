package analyzer.flashscore;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MatchListScraper {

    public static List<MatchData> collectMatchesForDays(Page page, int days) {
        List<MatchData> allMatches = new ArrayList<>();

        AppLogger.log("Navigating to Flashscore...");
        page.navigate(ScraperConstants.BASE_URL);

        // Çerez kabul
        try {
            page.locator("#onetrust-accept-btn-handler").click(new Locator.ClickOptions().setTimeout(3000));
        } catch (Exception ignored) {}

        OddsConfigurator.configureDecimalOdds(page);

        for (int dayOffset = 1; dayOffset <= days; dayOffset++) {
            LocalDate targetDate = LocalDate.now().minusDays(dayOffset);
            AppLogger.log("Collecting matches for: " + targetDate);

            // Geri (Önceki Gün) tuşuna bas
            page.locator("[data-day-picker-arrow='prev']").click();

            // ⚠️ SENIOR FIX: NETWORKIDLE kaldırıldı!
            // SPA'nın eski DOM'u silip yenisini çizmesi için güvenli bir süre veriyoruz.
            // 1500ms bekleme hem ban yemeni engeller hem de UI'ın oturmasını sağlar.
            page.waitForTimeout(1500);

            try {
                // Maç satırlarının görünür olmasını max 10 saniye bekle
                page.waitForSelector("div[id^='g_1_']", new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10000));
            } catch (Exception e) {
                AppLogger.log("O gün için maç bulunamadı veya sayfa yüklenemedi: " + targetDate);
                continue; // Hata verip patlamak yerine bir sonraki güne geç
            }

            Locator rows = page.locator("div[id^='g_1_'].event__match");
            int count = rows.count();

            for (int i = 0; i < count; i++) {
                try {
                    Locator row = rows.nth(i);
                    String matchId = row.getAttribute("id").replace("g_1_", "");
                    String home = row.locator(".event__homeParticipant").innerText().trim();
                    String away = row.locator(".event__awayParticipant").innerText().trim();
                    allMatches.add(new MatchData(matchId, home, away, targetDate.toString()));
                } catch (Exception ignored) {}
            }

            AppLogger.log(targetDate + " tarihi için " + count + " maç bulundu.");
        }
        return allMatches;
    }
}