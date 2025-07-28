package flashscore.weeklydatascraping.aiscore;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bu sınıfın tek görevi, Aiscore.com sitesinden "Scheduled" sekmesindeki tüm maçların
 * linklerini toplayıp 'urls_to_analyze.txt' adında bir dosyaya yazmaktır.
 * Bu sınıf, MatchAnalyzer'dan tamamen bağımsızdır.
 */
public class AiscoreScraper {

    private static final String OUTPUT_URL_FILE = "urls_to_analyze.txt";

    public static void main(String[] args) {
        // Tüm konsol uyarılarını bastırır
        suppressAllWarnings();

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = createChromeOptions();
        WebDriver driver = null;
        Set<String> matchLinks = new HashSet<>();

        System.out.println("Aiscore URL toplayıcı başlatıldı. Maç linkleri toplanıyor...");

        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            driver.get("https://www.aiscore.com/");

            handleCookieBanner(driver, wait);
            clickScheduledTab(driver, wait);
            matchLinks = scrollAndCollectAllMatchLinks(driver);

        } catch (Exception e) {
            System.err.println("URL toplama sırasında kritik bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        if (!matchLinks.isEmpty()) {
            System.out.println(matchLinks.size() + " adet unique maç linki bulundu. '" + OUTPUT_URL_FILE + "' dosyasına yazılıyor...");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_URL_FILE))) {
                for (String link : matchLinks) {
                    writer.write(link);
                    writer.newLine(); // Her linki yeni bir satıra yaz
                }
                System.out.println("Tüm linkler başarıyla '" + OUTPUT_URL_FILE + "' dosyasına yazıldı.");
            } catch (IOException e) {
                System.err.println("Linkler dosyaya yazılırken bir hata oluştu: " + e.getMessage());
            }
        } else {
            System.out.println("Hiç maç linki bulunamadı. Dosya oluşturulmadı.");
        }

        System.out.println("\n-- URL TOPLAMA İŞLEMİ TAMAMLANDI --");
    }

    /**
     * Selenium ve ilgili kütüphanelerden gelen tüm logları ve uyarıları kapatır.
     */
    private static void suppressAllWarnings() {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        System.setProperty("webdriver.chrome.verboseLogging", "false");

        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver").setLevel(Level.OFF);

        Logger.getGlobal().setLevel(Level.OFF);
        Logger.getLogger("").setLevel(Level.OFF);

        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) { /* Hiçbir şey yapma */ }
        }));
    }

    /**
     * Headless modda çalışacak ve uyarıları bastıracak ChromeOptions nesnesi oluşturur.
     */
    public static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--log-level=3", "--silent", "--disable-logging");
        options.addArguments("--disable-dev-shm-usage", "--disable-extensions", "--no-sandbox", "--disable-gpu");
        options.addArguments("--disable-dev-tools", "--disable-features=VizDisplayCompositor");
        options.addArguments("--disable-background-timer-throttling", "--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding", "--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars", "--disable-notifications");
        options.addArguments("--headless=new", "--window-size=1920,1200", "--ignore-certificate-errors");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }

    /**
     * Varsa, çerez onay banner'ını tıklar.
     */
    private static void handleCookieBanner(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
            acceptCookiesButton.click();
        } catch (Exception e) { /* Banner yoksa veya tıklanamıyorsa devam et */ }
    }

    /**
     * "Scheduled" (Planlanmış) sekmesine tıklar.
     */
    private static void clickScheduledTab(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement scheduledButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(@class, 'changeItem') and contains(text(), 'Scheduled')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", scheduledButton);
            Thread.sleep(3000); // Sekmenin içeriğinin yüklenmesi için bekle
        } catch (Exception e) {
            System.err.println("Scheduled sekmesi tıklanırken hata oluştu. Devam ediliyor...");
        }
    }

    /**
     * Sayfayı aşağı kaydırarak dinamik olarak yüklenen tüm maç linklerini toplar.
     */
    private static Set<String> scrollAndCollectAllMatchLinks(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Actions actions = new Actions(driver);
        Set<String> allMatchLinks = new HashSet<>();
        Pattern pattern = Pattern.compile("(https://www.aiscore.com/match-[^/]+/[^/]+)");

        System.out.println("Sayfa kaydırılarak tüm maç linkleri toplanıyor...");
        Thread.sleep(5000); // İlk yükleme için bekle

        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(2000);

        WebElement body = driver.findElement(By.tagName("body"));
        actions.moveToElement(body).perform();

        int maxScrollAttempts = 200;
        int stableCount = 0;
        int lastLinkCount = 0;

        for (int scrollAttempts = 1; scrollAttempts <= maxScrollAttempts; scrollAttempts++) {
            List<WebElement> currentMatchElements = driver.findElements(By.cssSelector("a.match-container, a[href*='/match-']"));

            for (WebElement element : currentMatchElements) {
                String fullHref = element.getAttribute("href");
                if (fullHref != null && !fullHref.isEmpty()) {
                    if (fullHref.startsWith("/match-")) {
                        fullHref = "https://www.aiscore.com" + fullHref;
                    }
                    Matcher matcher = pattern.matcher(fullHref);
                    if (matcher.find()) {
                        allMatchLinks.add(matcher.group(1));
                    }
                }
            }

            actions.scrollByAmount(0, 300).perform(); // Daha küçük adımlarla kaydır
            Thread.sleep(500); // İçerik yüklenmesi için bekle

            if (scrollAttempts % 20 == 0) {
                System.out.println("Scroll denemesi: " + scrollAttempts + "/" + maxScrollAttempts +
                                   " | Toplanan unique link sayısı: " + allMatchLinks.size());
            }

            if (allMatchLinks.size() == lastLinkCount) {
                stableCount++;
                if (stableCount >= 15) { // 15 deneme boyunca yeni link gelmezse dur
                    System.out.println("Yeni link bulunamadı, kaydırma işlemi sonlandırılıyor.");
                    break;
                }
            } else {
                stableCount = 0;
                lastLinkCount = allMatchLinks.size();
            }

            Boolean isAtBottom = (Boolean) js.executeScript(
                    "return (window.innerHeight + window.scrollY) >= document.body.scrollHeight - 100"
            );
            if (isAtBottom && stableCount > 10) {
                System.out.println("Sayfa sonuna ulaşıldı, kaydırma işlemi sonlandırılıyor.");
                break;
            }
        }

        System.out.println("Kaydırma ve link toplama işlemi tamamlandı.");
        return allMatchLinks;
    }
}