package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MackolikTarihArsiviScraper {

    public static void main(String[] args) {
        // --- Tarayıcı Ayarları ---
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        // options.addArguments("--headless"); // Tarayıcıyı gizli modda çalıştırmak için bu satırı aktif edin.

        WebDriverManager.chromedriver().setup();
        WebDriver driver = null; // finally bloğunda erişebilmek için dışarıda tanımlanır

        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // --- TARİH ARALIĞINI VE DOSYA ADINI BELİRLEYİN ---
            LocalDate startDate = LocalDate.of(2025, 5, 1); // Başlangıç tarihi
            LocalDate endDate = LocalDate.now().minusDays(1);     // Bitiş tarihi: Dün
            String outputFileName = "tum_mac_urlleri.txt";
            // --- ---

            // Başlamadan önce dosyayı temizle/oluştur
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName, false))) {
                writer.write(""); // Dosyayı boşaltır veya oluşturur
            }
            System.out.println("'" + outputFileName + "' dosyası sıfırlandı.");

            System.out.println("Mackolik sayfasına gidiliyor...");
            driver.get("https://www.mackolik.com/canli-sonuclar");
            driver.manage().window().maximize();

            // Çerezleri kabul et
            handleCookies(driver, wait);

            LocalDate currentDate = startDate;

            // Belirtilen tarih aralığında gün gün döngü
            while (!currentDate.isAfter(endDate)) {
                // Her günün işlemini ayrı bir try-catch-finally bloğuna alarak
                // bir günde oluşan hatanın tüm işlemi durdurmasını engelliyoruz.
                try {
                    System.out.println("\n=====================================");
                    System.out.println("İŞLENEN TARİH: " + currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    System.out.println("=====================================");

                    // Tarih seçiciyi aç
                    WebElement datePickerToggle = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector("button.widget-dateslider__datepicker-toggle")));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", datePickerToggle);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.widget-datepicker")));

                    // Hedef tarihe git
                    navigateToDate(driver, wait, currentDate.getYear(), getMonthAbbreviation(currentDate.getMonthValue()));

                    // Güne tıkla
                    String dataDateValue = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    boolean clickedSuccessfully = clickDayOnlyCurrentMonth(driver, wait, dataDateValue);

                    if (!clickedSuccessfully) {
                        // Eğer güne tıklanamazsa, bu günü atlamak için hata fırlat.
                        // Bu hata aşağıdaki catch bloğu tarafından yakalanacak.
                        throw new RuntimeException("Güne tıklama işlemi başarısız oldu veya takvim kapanmadı.");
                    }

                    System.out.println(dataDateValue + " tarihinin verileri yükleniyor...");
                    Thread.sleep(3000); // AJAX içeriğinin tamamen oturması için güvenlik beklemesi

                    // Maç elementlerini bul
                    List<WebElement> matchElements = driver.findElements(By.cssSelector("div.match-row__match-content[data-match-url]"));

                    if (matchElements.isEmpty()) {
                        System.out.println("Bu tarihte maç bulunamadı.");
                    } else {
                        // StaleElement hatasına karşı URL'leri hemen bir String listesine alıyoruz.
                        List<String> matchUrls = new ArrayList<>();
                        for (WebElement element : matchElements) {
                            matchUrls.add(element.getAttribute("data-match-url"));
                        }
                        appendUrlsToFile(matchUrls, outputFileName);
                    }
                } catch (Exception e) {
                    // Herhangi bir hata (StaleElement, Timeout, vb.) olursa, program çökmez.
                    // Hatayı loglar ve bir sonraki güne geçer.
                    System.err.println("\n!!! '" + currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + "' TARİHİ İŞLENİRKEN BİR HATA OLUŞTU. BU GÜN ATLANDI. !!!");
                    System.err.println("Hata Detayı: " + e.getClass().getSimpleName() + " - " + e.getMessage().split("\n")[0]);
                    // Sayfayı yenileyerek bir sonraki döngüye daha temiz bir başlangıç yapmasını sağlayabiliriz.
                    driver.navigate().refresh();
                    handleCookies(driver, wait); // Yenileme sonrası çerezler tekrar çıkabilir.
                } finally {
                    // Hata olsa da olmasa da, bir sonraki güne geçmek için bu blok her zaman çalışır.
                    currentDate = currentDate.plusDays(1);
                }
            }

        } catch (Exception e) {
            // Bu catch, döngü dışındaki kritik hataları yakalar (örneğin driver başlatılamazsa).
            System.err.println("\nİŞLEM SIRASINDA KRİTİK BİR HATA OLUŞTU! PROGRAM DURDURULUYOR.");
            e.printStackTrace();
        } finally {
            System.out.println("\nTüm işlemler tamamlandı. Tarayıcı kapatılıyor.");
            if (driver != null) {
                driver.quit();
            }
        }
    }

    /**
     * Sitede görünebilecek çerez bildirimlerini yönetir.
     */
    public static void handleCookies(WebDriver driver, WebDriverWait wait) {
        try {
            wait.withTimeout(Duration.ofSeconds(10)).until(ExpectedConditions.elementToBeClickable(By.id("didomi-notice-agree-button"))).click();
            System.out.println("Didomi çerez bildirimi kabul edildi.");
        } catch (Exception e) {
            // Hata mesajı yazdırmaya gerek yok, bu normal bir durum.
        }
        try {
            wait.withTimeout(Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(By.id("ez-accept-all"))).click();
            System.out.println("EZ çerez bildirimi kabul edildi.");
        } catch (Exception e) {
            // Hata mesajı yazdırmaya gerek yok, bu normal bir durum.
        }
    }

    /**
     * Verilen URL listesini belirtilen dosyaya ekler (append).
     */
    public static void appendUrlsToFile(List<String> urls, String fileName) {
        if (urls == null || urls.isEmpty()) return;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            for (String url : urls) {
                if (url != null && !url.isEmpty()) {
                    writer.write(url);
                    writer.newLine();
                }
            }
            System.out.printf("--> %d URL başarıyla '%s' dosyasına eklendi.%n", urls.size(), fileName);
        } catch (IOException e) {
            System.err.println("Dosyaya yazma sırasında bir hata oluştu: " + e.getMessage());
        }
    }

    /**
     * Ay numarasını Türkçe 3 harfli kısaltmaya çevirir.
     */
    public static String getMonthAbbreviation(int monthNumber) {
        return Map.ofEntries(
                Map.entry(1, "Oca"), Map.entry(2, "Şub"), Map.entry(3, "Mar"),
                Map.entry(4, "Nis"), Map.entry(5, "May"), Map.entry(6, "Haz"),
                Map.entry(7, "Tem"), Map.entry(8, "Ağu"), Map.entry(9, "Eyl"),
                Map.entry(10, "Eki"), Map.entry(11, "Kas"), Map.entry(12, "Ara")
        ).getOrDefault(monthNumber, "");
    }

    /**
     * Sadece içinde bulunulan ayın gününe tıklar ve takvimin kapanıp kapanmadığını kontrol eder.
     */
    public static boolean clickDayOnlyCurrentMonth(WebDriver driver, WebDriverWait wait, String dataDateValue) {
        try {
            By dayLocator = By.cssSelector(
                    "td.widget-datepicker__calendar-body-cell[data-date='" + dataDateValue + "']:not(.widget-datepicker__calendar-body-cell--not-month-day)"
            );
            WebElement dayCell = wait.until(ExpectedConditions.elementToBeClickable(dayLocator));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", dayCell);
            return isDatePickerClosed(driver, wait);
        } catch (Exception e) {
            System.err.println("Gün tıklama hatası: " + e.getMessage().split("\n")[0]);
            // Tıklama başarısız olursa, takvimi manuel kapatmaya çalışalım.
            try {
                driver.findElement(By.cssSelector("body")).click();
                isDatePickerClosed(driver, wait);
            } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Takvim elementinin görünmez olup olmadığını kontrol eder.
     */
    public static boolean isDatePickerClosed(WebDriver driver, WebDriverWait wait) {
        try {
            return wait.withTimeout(Duration.ofSeconds(5))
                    .until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("div.widget-datepicker")));
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Takvim arayüzünde istenen yıl ve aya navigasyon yapar.
     */
    public static void navigateToDate(WebDriver driver, WebDriverWait wait, int targetYear, String targetMonth) throws InterruptedException {
        int maxAttempts = 36; // 3 yıl ileri/geri gidebilmek için yeterli.
        for(int attempts = 0; attempts < maxAttempts; attempts++) {
            WebElement yearValueElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".widget-datepicker__selector--year .widget-datepicker__value")));
            WebElement monthValueElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".widget-datepicker__selector--month .widget-datepicker__value")));
            int currentYear = Integer.parseInt(yearValueElement.getText());
            String currentMonth = monthValueElement.getText().trim();

            if (currentYear == targetYear && currentMonth.equalsIgnoreCase(targetMonth)) {
                System.out.printf("Hedef ay ve yıla ulaşıldı: %s %d%n", targetMonth, targetYear);
                return;
            }

            if (currentYear < targetYear) {
                driver.findElement(By.cssSelector(".widget-datepicker__selector--year .widget-datepicker__nav--next")).click();
            } else if (currentYear > targetYear) {
                driver.findElement(By.cssSelector(".widget-datepicker__selector--year .widget-datepicker__nav--previous")).click();
            } else { // Yıl doğru, ay yanlış
                if (getMonthNumber(currentMonth) < getMonthNumber(targetMonth)) {
                    driver.findElement(By.cssSelector(".widget-datepicker__selector--month .widget-datepicker__nav--next")).click();
                } else {
                    driver.findElement(By.cssSelector(".widget-datepicker__selector--month .widget-datepicker__nav--previous")).click();
                }
            }
            Thread.sleep(150); // Navigasyonlar arası kısa bekleme
        }
        throw new RuntimeException("Hedef yıl ve ay bulunamadı: " + targetMonth + " " + targetYear);
    }

    /**
     * Türkçe ay kısaltmasını ay numarasına çevirir.
     */
    public static int getMonthNumber(String monthName) {
        return Map.ofEntries(
                Map.entry("Oca", 1), Map.entry("Şub", 2), Map.entry("Mar", 3),
                Map.entry("Nis", 4), Map.entry("May", 5), Map.entry("Haz", 6),
                Map.entry("Tem", 7), Map.entry("Ağu", 8), Map.entry("Eyl", 9),
                Map.entry("Eki", 10), Map.entry("Kas", 11), Map.entry("Ara", 12)
        ).getOrDefault(monthName.trim(), -1);
    }
}