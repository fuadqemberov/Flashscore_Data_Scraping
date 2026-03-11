package flashscore.weeklydatascraping.aiscore;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiscoreFastScraper {

    private static final String OUTPUT_URL_FILE = "aiscore_urls_history_fast.txt";
    private static final Pattern URL_PATTERN = Pattern.compile("(https://www\\.aiscore\\.com/match-[^/]+/[^/]+)");

    // --- PERFORMANS AYARLARI ---
    // Aynı anda çalışacak tarayıcı sayısı. İşlemcin iyiyse 10-12 yapabilirsin.
    private static final int MAX_CONCURRENT_BROWSERS = 10;

    private static final Set<String> GLOBAL_MATCH_LINKS = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        // Logları kapat
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        System.setProperty("webdriver.chrome.silentOutput", "true");

        Scanner scanner = new Scanner(System.in);
        System.out.println("--- AISCORE ULTRA FAST SCRAPER ---");
        System.out.println("Headless Mod: AKTİF | Görseller: KAPALI | Thread: " + MAX_CONCURRENT_BROWSERS);

        System.out.print("Gün (Örn: 1): ");
        int day = scanner.nextInt();
        System.out.print("Ay (Örn: 1): ");
        int month = scanner.nextInt();
        System.out.print("Yıl (Örn: 2023): ");
        int year = scanner.nextInt();
        scanner.close();

        LocalDate startDate = LocalDate.of(year, month, day);
        LocalDate endDate = LocalDate.now();

        System.out.println("Başlıyor... Arka planda çalışacak.");
        long startTime = System.currentTimeMillis();

        WebDriverManager.chromedriver().setup();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore semaphore = new Semaphore(MAX_CONCURRENT_BROWSERS);
            List<Future<Void>> futures = new ArrayList<>();

            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                final LocalDate dateToScrape = current;
                Future<Void> future = executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        scrapeDate(dateToScrape);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                    return null;
                });
                futures.add(future);
                current = current.plusDays(1);
            }

            // İlerleme durumunu takip etmek istersen buraya ek kod yazılabilir
            // Şimdilik sadece bitmesini bekliyoruz
            for (Future<Void> f : futures) {
                try { f.get(); } catch (Exception e) { e.printStackTrace(); }
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;

        System.out.println("\n------------------------------------------------");
        System.out.println("TAMAMLANDI. Süre: " + duration + " saniye.");
        System.out.println("Toplam Benzersiz Link: " + GLOBAL_MATCH_LINKS.size());

        if (!GLOBAL_MATCH_LINKS.isEmpty()) {
            writeLinksToFile();
        }
    }

    private static void scrapeDate(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String url = "https://www.aiscore.com/" + dateStr;

        // System.out.println ile konsolu çok kirletmemek için sadece başlangıcı yazıyoruz
        // System.out.println("Başladı: " + date);

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(createChromeOptions());
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            driver.get(url);

            // 1. Çerez (Hızlı geç)
            handleCookieBanner(driver, wait);

            // 2. "All" Sekmesi Tıklama
            try {
                WebElement allTab = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//span[contains(@class, 'changeItem') and contains(normalize-space(), 'All')]")
                ));
                js.executeScript("arguments[0].click();", allTab);
                // Tıklama sonrası bekleme süresini azalttım (3sn -> 1.5sn)
                Thread.sleep(1500);
            } catch (Exception e) {
                // All sekmesi zaten seçili olabilir veya bulunamamıştır, devam et.
            }

            // 3. Hızlı Scroll
            Set<String> links = scrollAndCollectLinks(driver);

            if (!links.isEmpty()) {
                GLOBAL_MATCH_LINKS.addAll(links);
                System.out.println("✓ " + date + " -> " + links.size() + " link.");
            } else {
                System.out.println("X " + date + " -> Link yok.");
            }

        } catch (Exception e) {
            System.err.println("Hata (" + date + "): " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private static Set<String> scrollAndCollectLinks(WebDriver driver) throws InterruptedException {
        Set<String> dailyLinks = new HashSet<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
        int noChangeCount = 0;

        // Scroll limitini ve adımını optimize ettim
        for (int i = 0; i < 150; i++) {
            // XPath araması maliyetlidir, bunu her scroll'da yapmak yerine
            // topluca yapmak daha iyidir ama dinamik yüklemede mecburen yapıyoruz.
            // Sadece 'href' attribute'u olan 'a' taglarını alıp regex ile filtrelemek daha hızlıdır.

            List<WebElement> elements = driver.findElements(By.xpath("//a[contains(@href, '/match-')]"));

            for (WebElement element : elements) {
                // Try-catch bloğunu daralttık performans için
                String href = element.getAttribute("href");
                if (href != null) { // Regex kontrolünü sonraya sakla, null check hızlıdır
                    Matcher matcher = URL_PATTERN.matcher(href);
                    if (matcher.find()) {
                        dailyLinks.add(matcher.group(1));
                    }
                }
            }

            // Daha büyük adımlarla scroll (1000 -> 1500px)
            js.executeScript("window.scrollBy(0, 1500);");

            // Bekleme süresini azalttım (750ms -> 400ms)
            // Eğer sayfa yüklenemezse burayı 500-600 yap.
            Thread.sleep(400);

            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == lastHeight) {
                noChangeCount++;
                if (noChangeCount >= 8) break; // 8 denemede değişmezse çık
            } else {
                noChangeCount = 0;
                lastHeight = newHeight;
            }
        }
        return dailyLinks;
    }

    private static void handleCookieBanner(WebDriver driver, WebDriverWait wait) {
        try {
            // Bekleme süresini çok kısa tut, yoksa hemen geç
            WebElement acceptButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[@id='onetrust-accept-btn-handler' or contains(text(), 'Allow') or contains(text(), 'Accept')]")
            ));
            ((JavascriptExecutor)driver).executeScript("arguments[0].click();", acceptButton);
        } catch (Exception e) {
            // Ignored
        }
    }

    private static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // HEADLESS YENİ MOD (Daha stabil ve hızlı)
        options.addArguments("--headless=new");

        // GÖRSELLERİ KAPATMA (Büyük Hız Artışı)
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2); // 2 = Block images
        options.setExperimentalOption("prefs", prefs);

        // Gereksiz servisleri kapat
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--blink-settings=imagesEnabled=false"); // Ekstra görsel engelleme

        // Bot Korumasını Aşma (Headless modda bile insan gibi görünmeli)
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        return options;
    }

    private static void writeLinksToFile() {
        try (FileWriter writer = new FileWriter(OUTPUT_URL_FILE)) {
            List<String> sortedLinks = new ArrayList<>(GLOBAL_MATCH_LINKS);
            Collections.sort(sortedLinks); // Sıralı yaz
            for (String link : sortedLinks) {
                writer.write(link + "\n");
            }
            System.out.println("Dosya yazıldı: " + OUTPUT_URL_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}