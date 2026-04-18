package analyzer.flashscore.gui;

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
        options.addArguments("--disable-logging");
        options.addArguments("--log-level=3");
        options.addArguments("--window-size=1920,1080");

        // YENİ EKLENEN ANTİ-BOT AYARLARI
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", java.util.Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // EAGER YERİNE NORMAL YAPTIK (JS'nin tamamen yüklenmesini bekleyecek)
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        return new ChromeDriver(options);
    }
}