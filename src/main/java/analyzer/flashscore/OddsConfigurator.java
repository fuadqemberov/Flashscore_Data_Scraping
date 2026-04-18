package analyzer.flashscore;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;
import java.util.List;

public class OddsConfigurator {

    /**
     * Kesinlikle başarılı olması gereken Decimal Odds ayarlama metodu.
     * Başarısız olursa exception fırlatır ve kodun devam etmesini ENGELLER.
     */
    public static void configureDecimalOdds(WebDriver driver) {
        System.out.println("  [Settings] Decimal odds formati secilme islemi baslatiliyor...");

        int maxRetries = 3; // İşlemi 3 kere deneme hakkı
        boolean isSuccess = false;

        // İleri düzey (Advanced) FluentWait tanımı
        // Element bulunamadığında veya bayatladığında (Stale) çökmek yerine belirlediğimiz süre boyunca tekrar arar.
        Wait<WebDriver> smartWait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(15))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(ElementClickInterceptedException.class)
                .ignoring(ElementNotInteractableException.class);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("  [Settings] Deneme " + attempt + "/" + maxRetries);

                // 1. Hamburger Menüyü Aç
                WebElement menuBtn = smartWait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("#hamburger-menu div[role='button']")));
                clickSmart(driver, menuBtn);

                // 2. Settings Butonunu Bul ve Tıkla (Döngü yerine akıllı XPath)
                // XPath açıklaması: 'Settings' yazan elementi bul ve onun tıklanabilir en üst div/button atasına git.
                WebElement settingsRow = smartWait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//*[contains(@class, 'contextMenu')]//*[normalize-space(text())='Settings']/ancestor-or-self::*[@role='button' or contains(@class,'contextMenu__row')][1]")));
                clickSmart(driver, settingsRow);

                // 3. Decimal Seçeneğini Bul ve Tıkla
                // Sadece text'i bulmakla kalmaz, görünür olmasını bekler.
                WebElement decimalLabel = smartWait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//label[contains(translate(., 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'DECIMAL')]")));

                WebElement decimalRadio = decimalLabel.findElement(By.cssSelector("input[type='radio']"));

                // Eğer zaten seçili değilse tıkla
                if (!decimalRadio.isSelected()) {
                    clickSmart(driver, decimalLabel); // Label'a tıklamak genelde radio'yu tetikler.

                    // ADVANCED WAIT: Elementin class'ı değil, gerçekten "seçili" durumuna (state) geçmesini bekle.
                    smartWait.until(ExpectedConditions.elementSelectionStateToBe(decimalRadio, true));
                }

                // 4. Ayarlar Menüsünü Kapat
                closeDialogSmart(driver, smartWait);

                // 5. Başarı Onayı
                System.out.println("  [Settings] Decimal odds formati BASARIYLA ayarlandi.");
                isSuccess = true;
                break; // Başarılı olduysa Retry döngüsünden çık.

            } catch (Exception e) {
                System.err.println("  [Settings] Hata (Deneme " + attempt + "): " + e.getMessage());
                // Eğer son denemede değilsek DOM'un rahatlaması için ufak bir es verip tekrar deneyecek
                if (attempt == maxRetries) {
                    System.err.println("  [Settings] MAKSIMUM DENEME SAYISINA ULASILDI!");
                } else {
                    driver.navigate().refresh(); // State sıfırlamak için sayfayı yenile (Opsiyonel, duruma göre silinebilir)
                    smartWait.until(ExpectedConditions.jsReturnsValue("return document.readyState == 'complete';"));
                }
            }
        }

        // KESİNLİK (HARD STOP) KONTROLÜ
        // Eğer 3 denemenin sonunda hala başarılı olamadıysa, Exception fırlat ve programı durdur!
        if (!isSuccess) {
            throw new IllegalStateException("FATAL ERROR: Decimal odds ayarlanamadi! Test/Program guvenlik amaciyla durduruldu.");
        }
    }

    /**
     * Akıllı Tıklama (High-Level Click)
     * Önce normal selenium click dener, önüne popup çıkmışsa veya scroll kaymışsa Javascript ile zorlar.
     */
    private static void clickSmart(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (ElementNotInteractableException e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    /**
     * Akıllı Diyalog Kapatma Modülü
     */
    private static void closeDialogSmart(WebDriver driver, Wait<WebDriver> wait) {
        String[] closeSelectors = {
                "[data-testid='wcl-dialogCloseButton']",
                "button.settings__closeButton",
                "button.wcl-closeButton_6bc3P"
        };

        boolean isClosed = false;

        for (String selector : closeSelectors) {
            List<WebElement> closeBtns = driver.findElements(By.cssSelector(selector));
            if (!closeBtns.isEmpty() && closeBtns.get(0).isDisplayed()) {
                clickSmart(driver, closeBtns.get(0));
                isClosed = true;
                break;
            }
        }

        // Buton bulunamadıysa ESC tuşu ile çıkmayı dene
        if (!isClosed) {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        }

        // ADVANCED WAIT: Dialog'un ekrandan *GERÇEKTEN* kaybolduğunu (invisibility) bekle.
        // Kodun hemen devam edip başka elementlere tıklarken patlamasını önler.
        try {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".settings__closeButton, [data-testid='wcl-dialogCloseButton']")));
        } catch (TimeoutException e) {
            // Animasyon takılması ihtimaline karşı son bir kez daha ESC gönder
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        }
    }
}