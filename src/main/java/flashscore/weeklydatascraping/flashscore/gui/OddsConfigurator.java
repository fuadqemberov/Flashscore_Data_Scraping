package flashscore.weeklydatascraping.flashscore.gui;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import java.time.Duration;
import java.util.List;

public class OddsConfigurator {

    // METODDAN WAIT PARAMETRESİNİ SİLDİK (Sadece driver alıyor)
    public static void configureDecimalOdds(WebDriver driver) {
        System.out.println("  [Settings] Decimal odds formati seciliyor...");

        int maxRetries = 3;
        boolean isSuccess = false;

        Wait<WebDriver> smartWait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(15))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class, StaleElementReferenceException.class)
                .ignoring(ElementClickInterceptedException.class, ElementNotInteractableException.class);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                WebElement menuBtn = smartWait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("#hamburger-menu div[role='button']")));
                clickSmart(driver, menuBtn);

                WebElement settingsRow = smartWait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//*[contains(@class, 'contextMenu')]//*[normalize-space(text())='Settings']/ancestor-or-self::*[@role='button' or contains(@class,'contextMenu__row')][1]")));
                clickSmart(driver, settingsRow);

                WebElement decimalLabel = smartWait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//label[contains(translate(., 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'DECIMAL')]")));

                WebElement decimalRadio = decimalLabel.findElement(By.cssSelector("input[type='radio']"));

                if (!decimalRadio.isSelected()) {
                    clickSmart(driver, decimalLabel);
                    smartWait.until(ExpectedConditions.elementSelectionStateToBe(decimalRadio, true));
                }

                closeDialogSmart(driver, smartWait);
                isSuccess = true;
                break;

            } catch (Exception e) {
                if (attempt == maxRetries) {
                    System.err.println("  [Settings] MAKSIMUM DENEME SAYISINA ULASILDI!");
                } else {
                    driver.navigate().refresh();
                    smartWait.until(ExpectedConditions.jsReturnsValue("return document.readyState == 'complete';"));
                }
            }
        }

        if (!isSuccess) {
            throw new IllegalStateException("FATAL ERROR: Decimal odds ayarlanamadi!");
        }
    }

    private static void clickSmart(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    private static void closeDialogSmart(WebDriver driver, Wait<WebDriver> wait) {
        String[] closeSelectors = {
                "[data-testid='wcl-dialogCloseButton']", "button.settings__closeButton", "button.wcl-closeButton_6bc3P"
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
        if (!isClosed) driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        try {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".settings__closeButton, [data-testid='wcl-dialogCloseButton']")));
        } catch (Exception e) {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        }
    }
}