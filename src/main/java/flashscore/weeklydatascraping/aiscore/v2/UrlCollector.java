package flashscore.weeklydatascraping.aiscore.v2;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlCollector {

    private static final String OUTPUT_URL_FILE = "urls_to_analyze.txt";
    private static final Pattern URL_PATTERN = Pattern.compile("(https://www.aiscore.com/match-[^/]+/[^/]+)");

    public static void main(String[] args) {
        System.out.println("Aiscore URL ve Saat Toplayıcı başladı...");

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = createChromeOptions();
        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            driver.get("https://www.aiscore.com/");
            handleCookieBanner(driver, wait);
            clickScheduledTab(driver, wait);
            Map<String, String> matchData = scrollAndCollectAllMatchData(driver);

            if (!matchData.isEmpty()) {
                System.out.printf("%d benzersiz maç verisi bulundu. '%s' dosyasına yazılıyor...%n", matchData.size(), OUTPUT_URL_FILE);
                try (FileWriter writer = new FileWriter(OUTPUT_URL_FILE)) {
                    for (Map.Entry<String, String> entry : matchData.entrySet()) {
                        writer.write(String.format("%s,%s%n", entry.getKey(), entry.getValue()));
                    }
                }
                System.out.printf("Tüm veriler başarıyla '%s' dosyasına yazıldı.%n", OUTPUT_URL_FILE);
            } else {
                System.out.println("Hiç maç verisi bulunamadı. Dosya oluşturulmadı.");
            }

        } catch (Exception e) {
            System.err.println("URL toplama sırasında kritik hata: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
            System.out.println("\n-- URL VE SAAT TOPLAMA İŞLEMİ TAMAMLANDI --");
        }
    }

    private static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--log-level=3", "--silent", "--disable-logging", "--disable-dev-shm-usage",
                "--disable-extensions", "--no-sandbox", "--disable-gpu",
                "--disable-blink-features=AutomationControlled", "--headless=new",
                "--window-size=1920,1200",
                "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }

    private static void handleCookieBanner(WebDriver driver, WebDriverWait wait) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler"))).click();
        } catch (Exception e) {
            // Banner yoksa devam et
        }
    }

    private static void clickScheduledTab(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement scheduledButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(@class, 'changeItem') and contains(text(), 'Scheduled')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", scheduledButton);
            Thread.sleep(3000);
        } catch (Exception e) {
            System.err.println("Error clicking Scheduled tab. Continuing...");
        }
    }

    private static String getBakuTime(String isoDateTimeStr) {
        if (isoDateTimeStr == null || isoDateTimeStr.isEmpty()) {
            return null;
        }
        try {
            OffsetDateTime dtObject = OffsetDateTime.parse(isoDateTimeStr);
            ZoneId bakuTz = ZoneId.of("Asia/Baku");
            ZonedDateTime bakuTime = dtObject.atZoneSameInstant(bakuTz);
            return bakuTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ"));
        } catch (Exception e) {
            return null;
        }
    }

    // *** METODUN GÜNCELLENMİŞ HALİ ***
    private static Map<String, String> scrollAndCollectAllMatchData(WebDriver driver) throws InterruptedException {
        Actions actions = new Actions(driver);
        Map<String, String> matchData = new LinkedHashMap<>();

        System.out.println("Sayfa kaydırılarak maç linkleri ve saatleri toplanıyor...");
        Thread.sleep(5000);
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        Thread.sleep(2000);

        int maxScrollAttempts = 200;
        int stableCount = 0;
        int lastLinkCount = 0;

        for (int scrollAttempt = 1; scrollAttempt <= maxScrollAttempts; scrollAttempt++) {
            List<WebElement> currentMatchContainers = driver.findElements(By.cssSelector("a.match-container[data-id]"));

            for (WebElement container : currentMatchContainers) {
                // *** DEĞİŞİKLİK BAŞLANGICI ***
                // Her bir elementin işlemi, StaleElementReferenceException'a karşı bir try-catch bloğuna alınır.
                try {
                    String fullHref = container.getAttribute("href");
                    if (fullHref == null) continue;

                    Matcher matcher = URL_PATTERN.matcher(fullHref);
                    if (matcher.find()) {
                        String cleanUrl = matcher.group(1);
                        if (!matchData.containsKey(cleanUrl)) {
                            // findElement işlemi de try bloğu içinde kalmalı
                            WebElement startDateMeta = container.findElement(By.cssSelector("meta[itemprop='startDate']"));
                            String startDateStr = startDateMeta.getAttribute("content");
                            String bakuTimeStr = getBakuTime(startDateStr);
                            if (bakuTimeStr != null) {
                                matchData.put(cleanUrl, bakuTimeStr);
                            }
                        }
                    }
                } catch (StaleElementReferenceException | NoSuchElementException e) {
                    // Element bayatladıysa veya içindeki meta tag bulunamadıysa,
                    // bu elementi atla ve döngüye devam et.
                    continue;
                }
                // *** DEĞİŞİKLİK SONU ***
            }

            actions.scrollByAmount(0, 300).perform();
            Thread.sleep(500);

            if (scrollAttempt % 20 == 0) {
                System.out.printf("Scroll denemesi: %d/%d | Toplanan benzersiz link: %d%n", scrollAttempt, maxScrollAttempts, matchData.size());
            }

            if (matchData.size() == lastLinkCount) {
                stableCount++;
                if (stableCount >= 15) {
                    System.out.println("Yeni link bulunamadı, kaydırma durduruluyor.");
                    break;
                }
            } else {
                stableCount = 0;
                lastLinkCount = matchData.size();
            }

            boolean isAtBottom = (boolean) ((JavascriptExecutor) driver).executeScript(
                    "return (window.innerHeight + window.scrollY) >= document.body.scrollHeight - 100"
            );

            if (isAtBottom && stableCount > 10) {
                System.out.println("Sayfa sonuna ulaşıldı, kaydırma durduruluyor.");
                break;
            }
        }

        System.out.println("Kaydırma ve veri toplama tamamlandı.");
        return matchData;
    }
}