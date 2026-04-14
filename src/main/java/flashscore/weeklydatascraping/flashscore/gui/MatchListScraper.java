package flashscore.weeklydatascraping.flashscore.gui;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MatchListScraper {

    public static List<MatchData> collectMatchesForDays(WebDriver driver, int days) {
        List<MatchData> allMatches = new ArrayList<>();
        driver.get(ScraperConstants.BASE_URL);

        // Cookie onayı
        WaitActionUtils.smartClick(driver, By.id("onetrust-accept-btn-handler"), 5);

        OddsConfigurator.configureDecimalOdds(driver);

        for (int dayOffset = 1; dayOffset <= days; dayOffset++) {
            LocalDate targetDate = LocalDate.now().minusDays(dayOffset);
            System.out.println("\n>>> Tarih taraniyor: " + targetDate);

            // Geri gitme butonu
            for (int i = 0; i < dayOffset; i++) {
                WaitActionUtils.smartClick(driver, By.cssSelector("[data-day-picker-arrow='prev']"), 5);
                // SPA (Single Page Application) sitelerde tıklandıktan sonra DOM'un yenilenmesi için zorunlu esneme
                try { Thread.sleep(1500); } catch (Exception ignored) {}
            }

            // Sayfadaki maç bloklarının yüklenmesini bekle
            WaitActionUtils.getSmartWait(driver, 10).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[id^='g_1_']")));

            // Veri çekmeden önce arayüz animasyonlarının bitmesini bekle
            try { Thread.sleep(2000); } catch (Exception ignored) {}

            List<MatchData> dayMatches = extractMatchIdsFromPage(driver, targetDate.toString());
            System.out.println("  " + dayMatches.size() + " mac bulundu.");
            allMatches.addAll(dayMatches);

            driver.get(ScraperConstants.BASE_URL); // Sonraki gün için sayfayı sıfırla
        }
        return allMatches;
    }

    private static List<MatchData> extractMatchIdsFromPage(WebDriver driver, String date) {
        List<MatchData> result = new ArrayList<>();
        List<WebElement> rows = driver.findElements(By.cssSelector("div[id^='g_1_'].event__match"));

        for (WebElement row : rows) {
            try {
                String matchId = row.getAttribute("id").replace("g_1_", "");
                String home = row.findElement(By.cssSelector(".event__homeParticipant [data-testid='wcl-scores-simple-text-01']")).getText().trim();
                String away = row.findElement(By.cssSelector(".event__awayParticipant [data-testid='wcl-scores-simple-text-01']")).getText().trim();
                result.add(new MatchData(matchId, home, away, date));
            } catch (Exception ignored) {}
        }

        // İlk 10 maçı almak için ayarlanmış. Sınırı kaldırmak istersen "return result;" yapabilirsin.
        return result.subList(0, Math.min(10, result.size()));
    }
}