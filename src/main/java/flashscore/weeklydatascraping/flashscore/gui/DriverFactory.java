package flashscore.weeklydatascraping.flashscore.gui;

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DriverFactory {
    public static WebDriver createHeadlessDriver() {
        // CDP Uyarılarını ve Kırmızı Selenium Loglarını Susturur
        System.setProperty("webdriver.chrome.silentOutput", "true");
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-logging"); // Tarayıcı loglarını kapat
        options.addArguments("--log-level=3");     // Sadece fatal hataları göster
        options.addArguments("--window-size=1920,1080");
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        return new ChromeDriver(options);
    }
}