
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

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiscoreScraper {

    private static final int MAX_THREADS = 8;

    public static void main(String[] args) {
        // --- TÜM UYARILARI SUSTURMA KISMI ---
        System.setProperty("webdriver.chrome.silentOutput", "true");
        System.setProperty("webdriver.chrome.verboseLogging", "false");

        // Selenium ve Chrome DevTools Protocol loglarını tamamen kapat
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver").setLevel(Level.OFF);

        // Genel Java logging'i sustur
        Logger.getGlobal().setLevel(Level.OFF);
        Logger.getLogger("").setLevel(Level.OFF);

        // System.err'i geçici olarak sustur
        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) {
                // Hiçbir şey yapma - error mesajlarını görmezden gel
            }
        }));
        // --- BİTİŞ ---

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = createChromeOptions();
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        Set<String> matchLinks = new HashSet<>();

        System.out.println("Aiscore analizcisi başlatıldı. Maç linkleri toplanıyor...");

        try {
            driver.get("https://www.aiscore.com/");

            handleCookieBanner(driver, wait);
            clickScheduledTab(driver, wait);
            matchLinks = getMatchLinks(driver);
            System.out.println(matchLinks.size() + " adet maç bulundu. Paralel analiz başlıyor...");

        } catch (Exception e) {
            // Hata olursa yine de sessiz kal, program çökmesin.
        } finally {
            driver.quit();
        }

        if (!matchLinks.isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

            for (String link : matchLinks) {
                executor.submit(new MatchAnalyzer(link));
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n-- TÜM İŞLEMLER TAMAMLANDI --");
    }

    public static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // ChromeDriver'dan gelen tüm logları ve uyarıları sustur
        options.addArguments("--log-level=3");
        options.addArguments("--silent");
        options.addArguments("--disable-logging");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");

        // CDP uyarılarını susturmak için özel parametreler
        options.addArguments("--disable-dev-tools");
        options.addArguments("--disable-features=VizDisplayCompositor");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");

        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1200");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");

        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        return options;
    }

    private static void handleCookieBanner(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
            acceptCookiesButton.click();
        } catch (Exception e) { /* Banner yoksa devam et */ }
    }

    private static void clickScheduledTab(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement scheduledButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(@class, 'changeItem') and contains(text(), 'Scheduled')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", scheduledButton);
            Thread.sleep(3000);
        } catch (Exception e) { /* Hata olursa sessiz kal */ }
    }

    private static Set<String> getMatchLinks(WebDriver driver) throws InterruptedException {
        // Her scroll adımında linkleri toplayan yöntem
        return scrollAndCollectAllMatchLinks(driver);
    }

    private static Set<String> scrollAndCollectAllMatchLinks(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Actions actions = new Actions(driver);
        Set<String> allMatchLinks = new HashSet<>();
        Pattern pattern = Pattern.compile("(https://www.aiscore.com/match-[^/]+/[^/]+)");

        System.out.println("Scroll ederek tüm maç linklerini topluyoruz...");

        // İlk olarak sayfanın yüklenmesini bekle
        Thread.sleep(5000);

        // Sayfanın en üstüne git
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(2000);

        // Sayfanın ortasına odaklan
        WebElement body = driver.findElement(By.tagName("body"));
        actions.moveToElement(body).perform();

        int scrollAttempts = 0;
        int maxScrollAttempts = 200; // Daha fazla scroll denemesi
        int stableCount = 0;
        int lastLinkCount = 0;

        while (scrollAttempts < maxScrollAttempts) {
            // Her scroll adımında görünür linkleri topla
            List<WebElement> currentMatchElements = driver.findElements(By.cssSelector("a.match-container"));
            if (currentMatchElements.isEmpty()) {
                currentMatchElements = driver.findElements(By.cssSelector("a[href*='/match-']"));
            }

            // Mevcut görünür linkleri set'e ekle
            for (WebElement element : currentMatchElements) {
                String fullHref = element.getAttribute("href");
                if (fullHref != null && !fullHref.isEmpty()) {
                    // Tam URL değilse tamamla
                    if (fullHref.startsWith("/match-")) {
                        fullHref = "https://www.aiscore.com" + fullHref;
                    }

                    Matcher matcher = pattern.matcher(fullHref);
                    if (matcher.find()) {
                        allMatchLinks.add(matcher.group(1));
                    }
                }
            }

            // Mouse wheel ile scroll down - daha küçük adımlar
            for (int i = 0; i < 3; i++) {
                actions.scrollByAmount(0, 200).perform();
                Thread.sleep(150); // Kısa bekleme
            }

            Thread.sleep(1500); // Sayfanın içerik yüklemesi için bekle

            scrollAttempts++;

            // Progress göster - her 20 scroll'da bir
            if (scrollAttempts % 20 == 0) {
                System.out.println("Scroll: " + scrollAttempts + "/" + maxScrollAttempts +
                                   " - Toplanan unique link: " + allMatchLinks.size());
            }

            // Link sayısı kontrolü
            if (allMatchLinks.size() == lastLinkCount) {
                stableCount++;
                // 15 kez üst üste aynı sayıda kaldıysa dur
                if (stableCount >= 15) {
                    System.out.println("Link sayısı sabit kaldı (" + allMatchLinks.size() + "), scroll tamamlandı.");
                    break;
                }
            } else {
                stableCount = 0; // Sayı değiştiyse sayacı sıfırla
                lastLinkCount = allMatchLinks.size();
            }

            // Sayfa sonuna gelip gelmediğini kontrol et
            Boolean isAtBottom = (Boolean) js.executeScript(
                    "return (window.innerHeight + window.scrollY) >= document.body.scrollHeight - 50"
            );

            if (isAtBottom && stableCount >= 10) {
                System.out.println("Sayfa sonu reached. Toplam unique link: " + allMatchLinks.size());
                break;
            }
        }

        // Son bir tur - en üstten aşağı yavaş scroll
        System.out.println("Son kontrol turu başlıyor...");
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(3000);

        for (int i = 0; i < 50; i++) {
            // Her adımda linkleri tekrar kontrol et
            List<WebElement> finalElements = driver.findElements(By.cssSelector("a.match-container, a[href*='/match-']"));
            for (WebElement element : finalElements) {
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

            actions.scrollByAmount(0, 300).perform();
            Thread.sleep(300);
        }

        System.out.println("Scroll ve link toplama işlemi tamamlandı. Toplam unique link: " + allMatchLinks.size());
        return allMatchLinks;
    }

    private static void gradualScrollToBottom(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long pageHeight = ((Number) js.executeScript("return document.body.scrollHeight")).longValue();

        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(2000);
            long newPageHeight = ((Number) js.executeScript("return document.body.scrollHeight")).longValue();
            if (newPageHeight == pageHeight) {
                break;
            }
            pageHeight = newPageHeight;
        }
    }


}