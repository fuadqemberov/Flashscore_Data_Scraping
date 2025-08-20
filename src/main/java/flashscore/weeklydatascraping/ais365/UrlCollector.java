package flashscore.weeklydatascraping.ais365;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlCollector {

    private static final String BASE_URL = "https://www.aiscore.com/";
    private static final String OUTPUT_URL_FILE = "match_urls.txt";

    // --- ANA ÇALIŞTIRMA METODU ---
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- Aiscore.com URL Toplayıcı ---");

        // Kullanıcıdan zaman periyodunu al
        String periodType = "";
        while (true) {
            System.out.print("Verileri haftalık (h), aylık (a) veya yıllık (y) olarak mı çekmek istersiniz? [h/a/y]: ");
            String input = scanner.nextLine().toLowerCase();
            if (input.equals("h") || input.equals("a") || input.equals("y")) {
                periodType = input;
                break;
            }
            System.out.println("Geçersiz giriş. Lütfen 'h', 'a' veya 'y' girin.");
        }

        // Kullanıcıdan miktarı al
        int amount = 0;
        while (true) {
            try {
                String prompt = "";
                if (periodType.equals("h")) prompt = "Kaç hafta geriye gitmek istersiniz? ";
                if (periodType.equals("a")) prompt = "Kaç ay geriye gitmek istersiniz? ";
                if (periodType.equals("y")) prompt = "Kaç yıl geriye gitmek istersiniz? ";
                System.out.print(prompt);
                amount = scanner.nextInt();
                if (amount > 0) {
                    break;
                }
                System.out.println("Lütfen 0'dan büyük bir sayı girin.");
            } catch (InputMismatchException e) {
                System.out.println("Geçersiz giriş. Lütfen bir sayı girin.");
                scanner.next(); // Hatalı girişi temizle
            }
        }
        scanner.close();

        // Tarih aralığını hesapla
        LocalDate endDate = LocalDate.now().minusDays(1); // Dün
        LocalDate startDate;

        switch (periodType) {
            case "h":
                startDate = endDate.minusWeeks(amount);
                break;
            case "a":
                startDate = endDate.minusMonths(amount);
                break;
            case "y":
                startDate = endDate.minusYears(amount);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + periodType);
        }

        startDate = startDate.plusDays(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        System.out.println("\nURL'ler şu tarih aralığı için toplanacak: " +
                           startDate.format(formatter) + " - " + endDate.format(formatter));
        System.out.println("İşlem başlıyor...");

        // Sınıfın kendi örneğini oluştur ve asıl işi yapan metodu çağır
        UrlCollector collector = new UrlCollector();
        collector.collectUrlsForDateRange(startDate, endDate);

        System.out.println("\n--- URL TOPLAMA İŞLEMİ TAMAMLANDI ---");
    }

    // Bu metot artık main tarafından çağrılıyor
    private void collectUrlsForDateRange(LocalDate startDate, LocalDate endDate) {
        suppressAllWarnings();
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = createChromeOptions();
        Set<String> allMatchLinks = new HashSet<>();

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String url = BASE_URL + formattedDate;
                System.out.println("\n" + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + " tarihindeki maçlar için URL'ler toplanıyor: " + url);

                driver.get(url);
                handleCookieBanner(driver);
                clickAllTab(driver);
                Set<String> dailyLinks = scrollAndCollectAllMatchLinks(driver);
                allMatchLinks.addAll(dailyLinks);
                System.out.println(dailyLinks.size() + " yeni link bulundu. Toplam unique link: " + allMatchLinks.size());
            }
        } catch (Exception e) {
            System.err.println("URL toplama sırasında kritik bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        if (!allMatchLinks.isEmpty()) {
            System.out.println("\nToplam " + allMatchLinks.size() + " adet unique maç linki bulundu. '" + OUTPUT_URL_FILE + "' dosyasına yazılıyor...");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_URL_FILE))) {
                for (String link : allMatchLinks) {
                    writer.write(link);
                    writer.newLine();
                }
                System.out.println("Tüm linkler başarıyla dosyaya yazıldı.");
            } catch (IOException e) {
                System.err.println("Linkler dosyaya yazılırken bir hata oluştu: " + e.getMessage());
            }
        } else {
            System.out.println("Hiç maç linki bulunamadı. Dosya oluşturulmadı.");
        }
    }

    // --- Diğer Yardımcı Metotlar (Değişiklik yok) ---
    private Set<String> scrollAndCollectAllMatchLinks(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Actions actions = new Actions(driver);
        Set<String> allMatchLinks = new HashSet<>();
        Pattern pattern = Pattern.compile("(https://www.aiscore.com/match-[^/]+/[^/]+)");
        System.out.println("Sayfa kaydırılarak tüm maç linkleri toplanıyor...");
        Thread.sleep(3000);
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(1000);

        int maxScrollAttempts = 300;
        int stableCount = 0;
        int lastLinkCount = 0;

        for (int scrollAttempts = 1; scrollAttempts <= maxScrollAttempts; scrollAttempts++) {
            List<WebElement> currentMatchElements = driver.findElements(By.cssSelector("a[href*='/match-']"));
            for (WebElement element : currentMatchElements) {
                String fullHref = element.getAttribute("href");
                if (fullHref != null && !fullHref.isEmpty()) {
                    Matcher matcher = pattern.matcher(fullHref);
                    if (matcher.find()) {
                        allMatchLinks.add(matcher.group(1));
                    }
                }
            }
            actions.scrollByAmount(0, 400).perform();
            Thread.sleep(500);

            if (scrollAttempts % 20 == 0) {
                System.out.println("  Scroll denemesi: " + scrollAttempts + "/" + maxScrollAttempts +
                                   " | Toplanan unique link sayısı: " + allMatchLinks.size());
            }

            if (allMatchLinks.size() == lastLinkCount) {
                stableCount++;
                if (stableCount >= 15) {
                    System.out.println("  Yeni link bulunamadı, bu gün için kaydırma işlemi sonlandırılıyor.");
                    break;
                }
            } else {
                stableCount = 0;
                lastLinkCount = allMatchLinks.size();
            }

            Boolean isAtBottom = (Boolean) js.executeScript(
                    "return (window.innerHeight + window.scrollY) >= document.body.scrollHeight - 150"
            );
            if (isAtBottom && stableCount > 10) {
                System.out.println("  Sayfa sonuna ulaşıldı, kaydırma işlemi sonlandırılıyor.");
                break;
            }
        }
        return allMatchLinks;
    }

    private void clickAllTab(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement allButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(@class, 'changeItem') and normalize-space()='All']")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", allButton);
            System.out.println("'All' sekmesine başarıyla tıklandı.");
            Thread.sleep(2000);
        } catch (Exception e) {
            System.err.println("'All' sekmesi tıklanırken bir sorun oluştu, muhtemelen zaten aktifti. Devam ediliyor...");
        }
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1920,1200", "--log-level=3");
        options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        return options;
    }

    private void suppressAllWarnings() {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
    }

    private void handleCookieBanner(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
            acceptCookiesButton.click();
            Thread.sleep(1000);
        } catch (Exception e) { /* Banner yoksa devam et */ }
    }
}