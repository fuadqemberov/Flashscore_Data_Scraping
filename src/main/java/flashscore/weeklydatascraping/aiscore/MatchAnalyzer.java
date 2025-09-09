package flashscore.weeklydatascraping.aiscore;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bu sınıf, 'urls_to_analyze.txt' dosyasındaki URL'leri okur, her birini
 * paralel olarak analiz eder ve belirli oran kalıplarını (pattern) arar.
 * Bulunan sonuçları SADECE konsola yazar.
 * Bu sınıf, AiscoreScraper'dan tamamen bağımsızdır.
 */
public class MatchAnalyzer implements Runnable {

    // --- Ana Program (Tüketici) Bölümü ---
    private static final int MAX_THREADS = 8;
    private static final String URL_SOURCE_FILE = "urls_to_analyze.txt"; // Okunacak girdi dosyası

    public static void main(String[] args) {
        List<String> urlsToAnalyze;
        try {
            // URL listesini dosyadan oku
            urlsToAnalyze = Files.readAllLines(Paths.get(URL_SOURCE_FILE));
            if (urlsToAnalyze.isEmpty() || urlsToAnalyze.stream().allMatch(String::isEmpty)) {
                System.out.println("'" + URL_SOURCE_FILE + "' dosyası boş. Analiz edilecek URL bulunamadı.");
                return;
            }
        } catch (IOException e) {
            System.err.println("HATA: '" + URL_SOURCE_FILE + "' dosyası okunamadı! Lütfen önce AiscoreScraper programını çalıştırdığınızdan emin olun.");
            return;
        }

        System.out.println(urlsToAnalyze.size() + " adet URL analiz için yüklendi. " + MAX_THREADS + " thread ile paralel analiz başlıyor...");

        // Thread havuzunu oluştur ve görevleri gönder
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        for (String url : urlsToAnalyze) {
            if (url != null && !url.trim().isEmpty()) {
                executor.submit(new MatchAnalyzer(url));
            }
        }

        // Executor'ı kapat ve tüm thread'lerin bitmesini bekle
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("\n-- ANALİZ İŞLEMLERİ TAMAMLANDI --");
        System.out.println("Bulunan tüm pattern'ler konsola yazdırıldı.");
    }

    // --- Runnable (Her bir thread'in çalıştıracağı kod) Bölümü ---
    private final String matchBaseUrl;

    public MatchAnalyzer(String matchUrl) {
        this.matchBaseUrl = matchUrl;
    }

    @Override
    public void run() {
        suppressWarnings();
        ChromeOptions options = createChromeOptions();
        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            String oddsUrl = matchBaseUrl + "/odds";

            driver.get(oddsUrl);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.newOdds")));

            List<WebElement> companyRows = driver.findElements(By.cssSelector("div.flex.w100.borderBottom"));
            WebElement bet365Row = null;
            for (WebElement row : companyRows) {
                try {
                    WebElement logo = row.findElement(By.cssSelector("img.logo"));
                    // Bet365 logosunun SRC'sindeki unique parça
                    if (logo.getAttribute("src").contains("fe8aec51afeb2de633c9")) {
                        bet365Row = row;
                        break;
                    }
                } catch (Exception e) {
                    // Bu satırda logo yok, devam et.
                }
            }

            if (bet365Row == null) return; // Bet365 satırı bulunamazsa bu URL'yi atla

            String matchTitle = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1.text"))).getText().split(" betting odds")[0].trim();

            String matchStartTimeBaku = "Bilinmiyor";
            try {
                WebElement startTimeElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("span[itemprop='startDate']")));
                String rawDateTimeString = startTimeElement.getText();
                DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("h:mm a EEEE, MMMM d, yyyy", Locale.ENGLISH);
                LocalDateTime localDateTime = LocalDateTime.parse(rawDateTimeString, inputFormatter);
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm 'Baku Saati'");
                matchStartTimeBaku = localDateTime.format(outputFormatter);
            } catch (Exception e) { /* Saat alınamazsa varsayılan değer kullanılır */ }

            WebElement openingOddsContainer = bet365Row.findElement(By.cssSelector("div.openingBg1 .oddsItemBox"));
            List<WebElement> openingOddsElements = openingOddsContainer.findElements(By.cssSelector("div.oddItems span"));

            WebElement preMatchOddsContainer = bet365Row.findElement(By.cssSelector("div.preMatchBg1 .oddsItemBox"));
            List<WebElement> preMatchOddsElements = preMatchOddsContainer.findElements(By.cssSelector("div.oddItems span"));

            if (openingOddsElements.size() < 3 || preMatchOddsElements.size() < 3) return;

            double open1 = parseDouble(openingOddsElements.get(1).getText());
            double openX = parseDouble(openingOddsElements.get(2).getText());
            double open2 = parseDouble(openingOddsElements.get(4).getText());

            double pre1 = parseDouble(preMatchOddsElements.get(1).getText());
            double preX = parseDouble(preMatchOddsElements.get(2).getText());
            double pre2 = parseDouble(preMatchOddsElements.get(4).getText());

            if (open1 == 0.0 || pre1 == 0.0) return; // Geçersiz oranları atla

            checkPatterns(matchTitle, oddsUrl, open1, openX, open2, pre1, preX, pre2, matchStartTimeBaku);

        } catch (Exception e) {
            // Hataları sessizce yoksay, diğer thread'lerin çalışmasını etkilemesin.
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private void checkPatterns(String matchTitle, String url, double o1, double oX, double o2, double p1, double pX, double p2, String startTime) {
        String report = "";

        if (p1 == 2.62 || p2 == 2.62) {
            report += "    -> PATTERN 1 - SİHİRLİ 2.62\n";
        }
        if (pX == p2 || p1 == pX) {
            report += "    -> PATTERN 2 - ORAN TEKRARI\n";
        }
        boolean homeWasFavoriteLostIt = (o1 < o2) && (p1 > p2);
        boolean awayWasFavoriteLostIt = (o2 < o1) && (p2 > p1);
        if (homeWasFavoriteLostIt || awayWasFavoriteLostIt) {
            report += "    -> PATTERN 3 - FAVORİ DEĞİŞİMİ\n";
        }

        boolean newPatternHomeFav = (p1 >= 1.60 && p1 <= 1.69) && (pX >= 4.00) && (p2 >= 4.70 && p2 <= 4.79);
        boolean newPatternAwayFav = (p2 >= 1.60 && p2 <= 1.69) && (pX >= 4.00) && (p1 >= 4.70 && p1 <= 4.79);
        if(newPatternHomeFav || newPatternAwayFav) {
            report += "    -> PATTERN 5 - 1.6x / 4.xx / 4.7x ORANI\n";
        }

        boolean pattern6_case1 = (p1 >= 2.30 && p1 <= 2.39) && (pX >= 3.20 && pX <= 3.29) && (p2 >= 2.90 && p2 <= 2.99);
        boolean pattern6_case2_reverse = (p1 >= 2.90 && p1 <= 2.99) && (pX >= 3.20 && pX <= 3.29) && (p2 >= 2.30 && p2 <= 2.39);
        if (pattern6_case1 || pattern6_case2_reverse) {
            report += "    -> PATTERN 6 - 2.3x / 3.2x / 2.9x ORANI (ve tersi)\n";
        }

        // Eğer herhangi bir pattern bulunduysa, senkronize bir şekilde konsola yazdır.
        if (!report.isEmpty()) {
            synchronized (System.out) {
                System.out.println("\n------------------ [!] PATTERN(LER) BULUNDU: " + matchTitle + " ------------------");
                System.out.printf("    Maç Başlama Saati: %s%n", startTime);
                System.out.print(report);
                System.out.printf("    URL: %s%n", url);
                System.out.printf("    Açılış Oranları: %.2f | %.2f | %.2f%n", o1, oX, o2);
                System.out.printf("    Kapanış Oranları: %.2f | %.2f | %.2f%n", p1, pX, p2);
                System.out.println("----------------------------------------------------------------------------------\n");
            }
        }
    }

    // --- Yardımcı Metodlar ---
    private double parseDouble(String text) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                return Double.parseDouble(text.trim().replace(',', '.'));
            }
        } catch (NumberFormatException e) { /* Hata olursa 0.0 döner */ }
        return 0.0;
    }

    private void suppressWarnings() {
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
    }

    public static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1920,1200");
        options.addArguments("--log-level=3", "--silent", "--disable-logging", "--no-sandbox");
        options.addArguments("--disable-dev-shm-usage", "--disable-extensions", "--disable-gpu");
        options.addArguments("--disable-blink-features=AutomationControlled", "--disable-infobars", "--disable-notifications");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }
}