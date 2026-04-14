package flashscore.weeklydatascraping.flashscore.gui;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class WaitActionUtils {

    public static WebDriverWait getSmartWait(WebDriver driver, int seconds) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
        // Metodları zincirlemeden alt alta çağırıyoruz ki Java tip uyuşmazlığı yaşamasın.
        wait.pollingEvery(Duration.ofMillis(300));
        wait.ignoring(NoSuchElementException.class, StaleElementReferenceException.class);
        wait.ignoring(ElementClickInterceptedException.class, ElementNotInteractableException.class);
        return wait;
    }

    public static boolean smartClick(WebDriver driver, By locator, int timeoutSec) {
        try {
            WebElement element = getSmartWait(driver, timeoutSec).until(ExpectedConditions.elementToBeClickable(locator));
            return jsClick(driver, element);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean smartClick(WebDriver driver, WebElement element) {
        try {
            return jsClick(driver, element);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean jsClick(WebDriver driver, WebElement element) {
        try {
            element.click();
            return true;
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            return true;
        }
    }

    public static void waitForNetworkIdle(WebDriver driver) {
        getSmartWait(driver, 10).until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
    }
}