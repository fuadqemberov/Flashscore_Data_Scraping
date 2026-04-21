package analyzer.flashscore;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class OddsConfigurator {

    public static void configureDecimalOdds(Page page) {
        try {
            // 🚀 1. Çerez Banner'ını Kapat (En önemli adım bu, yoksa tıklama yapılamaz!)
            try {
                Locator acceptBtn = page.locator("#onetrust-accept-btn-handler");
                if (acceptBtn.isVisible()) {
                    acceptBtn.click(new Locator.ClickOptions().setTimeout(3000));
                }
            } catch (Exception ignored) {}

            // 🚀 2. Kontrol: Eğer oranlar hala kesirliyse (/) fiziksel tıklama yap
            if (page.locator(".oddsCell__odd:has-text('/')").count() > 0) {
                // Hamburger Menü
                page.locator("#hamburger-menu div[role='button']").first().click();

                // Settings
                page.locator("text=Settings").first().click();

                // Decimal Butonu (Kısa timeout ile hızlı dene)
                page.locator("label:has-text('DECIMAL'), label:has-text('Decimal')").first().click(new Locator.ClickOptions().setTimeout(5000));

                // Kapat
                page.keyboard().press("Escape");
                page.waitForTimeout(500);
            }
        } catch (Exception e) {
            // Eğer hala hata verirse, sayfayı zorla yenileyerek ayarı oturt
            page.evaluate("() => { localStorage.setItem('ls_odds_format', '1'); location.reload(); }");
        }
    }
}