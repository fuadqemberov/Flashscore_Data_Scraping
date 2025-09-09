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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bu sınıf, Aiscore.com sitesinden "Scheduled" sekmesindeki, SADECE belirtilen
 * başlangıç saatine sahip maçların linklerini, sayfanın sonuna kadar dinamik olarak
 * kaydırarak toplar ve 'urls_to_analyze.txt' dosyasına yazar.
 */
public class AiscoreScraper {

    private static final String OUTPUT_URL_FILE = "urls_to_analyze.txt";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("HATA: Lütfen toplanacak maçların saatini belirtin.");
            System.err.println("Örnek Kullanım: java flashscore.weeklydatascraping.aiscore.AiscoreScraper 22:00");
            return;
        }
        String targetTime = args[0];
        if (!targetTime.matches("\\d{2}:\\d{2}")) {
            System.err.println("HATA: Geçersiz saat formatı. Lütfen 'HH:mm' formatını kullanın (örn: 09:30 veya 22:00).");
            return;
        }

        suppressAllWarnings();
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = createChromeOptions();
        WebDriver driver = null;
        Set<String> matchLinks = new HashSet<>();

        System.out.println("Aiscore URL toplayıcı başlatıldı.");
        System.out.println("Sadece başlangıç saati '" + targetTime + "' olan maçların linkleri, sayfa sonuna kadar taranarak toplanacak...");

        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            driver.get("https://www.aiscore.com/");
            handleCookieBanner(driver, wait);
            clickScheduledTab(driver, wait);

            // Dinamik kaydırma ve filtreleme yapan metot çağrılıyor
            matchLinks = scrollAndCollectFilteredMatchLinks(driver, targetTime);

        } catch (Exception e) {
            System.err.println("URL toplama sırasında kritik bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        if (!matchLinks.isEmpty()) {
            System.out.println(matchLinks.size() + " adet unique maç linki bulundu ve '" + OUTPUT_URL_FILE + "' dosyasına yazılıyor...");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_URL_FILE))) {
                for (String link : matchLinks) {
                    writer.write(link);
                    writer.newLine();
                }
                System.out.println("Tüm linkler başarıyla '" + OUTPUT_URL_FILE + "' dosyasına yazıldı.");
            } catch (IOException e) {
                System.err.println("Linkler dosyaya yazılırken bir hata oluştu: " + e.getMessage());
            }
        } else {
            System.out.println("Belirtilen saatte ("+ targetTime +") hiç maç linki bulunamadı. Dosya oluşturulmadı.");
        }

        System.out.println("\n-- URL TOPLAMA İŞLEMİ TAMAMLANDI --");
    }


    // --- ANA MANTIK DEĞİŞİKLİĞİ BU METOTTA YAPILDI ---
    /**
     * Sayfayı sonuna kadar dinamik olarak kaydırarak, SADECE hedef saate uyan
     * maçların linklerini toplar.
     * @param driver WebDriver nesnesi
     * @param targetTime Aranacak maç saati (24-saat formatında, örn: "22:00")
     * @return Sadece hedeflenen saatteki maçların URL'lerini içeren bir Set.
     */
    private static Set<String> scrollAndCollectFilteredMatchLinks(WebDriver driver, String targetTime) throws InterruptedException {
        Actions actions = new Actions(driver);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Set<String> filteredMatchLinks = new HashSet<>();
        Pattern urlPattern = Pattern.compile("(https://www.aiscore.com/match-[^/]+/[^/]+)");

        DateTimeFormatter amPmFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        DateTimeFormatter h24Formatter = DateTimeFormatter.ofPattern("HH:mm");

        Thread.sleep(2000);

        // DEĞİŞİKLİK: Dinamik kaydırma için değişkenleri yeniden ekliyoruz
        int maxScrollAttempts = 250; // Sonsuz döngüye karşı bir güvenlik önlemi
        int stableCount = 0; // Yeni link bulunamayan kaydırma sayısını tutar
        int lastLinkCount = -1; // Bir önceki döngüdeki link sayısını tutar

        for (int scrollAttempt = 0; scrollAttempt < maxScrollAttempts; scrollAttempt++) {
            List<WebElement> matchContainers = driver.findElements(By.cssSelector("a.match-container"));

            for (WebElement container : matchContainers) {
                try {
                    WebElement timeElement = container.findElement(By.cssSelector("span.time.minitext"));
                    String rawMatchTime = timeElement.getText().trim();

                    LocalTime parsedTime = LocalTime.parse(rawMatchTime, amPmFormatter);
                    String h24MatchTime = parsedTime.format(h24Formatter);

                    if (targetTime.equals(h24MatchTime)) {
                        String href = container.getAttribute("href");
                        if (href != null && !href.isEmpty()) {
                            Matcher matcher = urlPattern.matcher(href);
                            if (matcher.find()) {
                                filteredMatchLinks.add(matcher.group(1));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Bu konteynerde saat yok veya formatı farklı, devam et.
                }
            }

            actions.scrollByAmount(0, 500).perform(); // Daha küçük adımlarla kaydır
            Thread.sleep(500); // İçerik yüklenmesi için bekle

            // Yeni link bulunup bulunmadığını kontrol et
            if (filteredMatchLinks.size() == lastLinkCount) {
                stableCount++;
            } else {
                stableCount = 0; // Yeni link bulunduysa sayacı sıfırla
            }

            lastLinkCount = filteredMatchLinks.size(); // Mevcut link sayısını güncelle

            if (scrollAttempt % 20 == 0 && scrollAttempt > 0) {
                System.out.println("Scroll denemesi: " + scrollAttempt + " | Toplanan eşleşen link sayısı: " + lastLinkCount);
            }

            // Eğer 20 deneme boyunca yeni link bulunamazsa, sayfa sonuna gelindiğini varsay ve döngüyü kır
            if (stableCount >= 20) {
                System.out.println("Yeni eşleşen link bulunamadığı için kaydırma işlemi sonlandırılıyor.");
                break;
            }
        }

        System.out.println("Kaydırma ve filtreleme işlemi tamamlandı.");
        return filteredMatchLinks;
    }


    // --- Diğer Yardımcı Metodlar (Değişiklik yok) ---
    private static void suppressAllWarnings() {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) { /* Hiçbir şey yapma */ }
        }));
    }

    public static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1920,1080", "--log-level=3", "--no-sandbox");
        options.addArguments("--disable-gpu", "--disable-extensions", "--disable-dev-shm-usage", "--disable-infobars");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }

    private static void handleCookieBanner(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
            acceptCookiesButton.click();
            System.out.println("Çerez banner'ı kabul edildi.");
        } catch (Exception e) {
            System.out.println("Çerez banner'ı bulunamadı, devam ediliyor.");
        }
    }

    private static void clickScheduledTab(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement scheduledButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(@class, 'changeItem') and contains(text(), 'Scheduled')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", scheduledButton);
            System.out.println("'Scheduled' sekmesine tıklandı.");
            Thread.sleep(3000);
        } catch (Exception e) {
            throw new RuntimeException("Scheduled sekmesi bulunamadı, işlem durduruldu.", e);
        }
    }
}