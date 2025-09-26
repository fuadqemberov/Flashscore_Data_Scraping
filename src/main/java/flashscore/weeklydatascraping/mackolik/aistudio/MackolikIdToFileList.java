package flashscore.weeklydatascraping.mackolik.aistudio;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MackolikIdToFileList {

    private static final List<String> LEAGUE_IDS = Arrays.asList(
            "20",
            "24"

    );

    private static final int THREAD_COUNT = 4; // Selenium için fazla thread kullanma, tarayıcı yükü fazla

    public static void main(String[] args) {
        System.out.println("=== MultiThread Mackolik ID Scraper ===");
        System.out.println("İşlenecek lig sayısı: " + LEAGUE_IDS.size());
        System.out.println("Thread sayısı: " + THREAD_COUNT);

        // WebDriver setup (sadece bir kez)
        WebDriverManager.chromedriver().setup();

        // Thread-safe liste ve sayaçlar
        List<String> allTeamIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Thread pool oluştur
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<List<String>>> futures = new ArrayList<>();

        // Her lig için ayrı task oluştur
        for (String leagueId : LEAGUE_IDS) {
            Future<List<String>> future = executor.submit(() -> {
                return scrapeLeagueTeamIds(leagueId, processedCount, successCount, LEAGUE_IDS.size());
            });
            futures.add(future);
        }

        // Tüm taskların tamamlanmasını bekle ve sonuçları topla
        try {
            for (Future<List<String>> future : futures) {
                List<String> leagueTeamIds = future.get(30, TimeUnit.SECONDS); // 30 saniye timeout
                allTeamIds.addAll(leagueTeamIds);
            }
        } catch (Exception e) {
            System.err.println("Thread execution hatası: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Sonuçları raporla ve dosyaya yaz
        System.out.println("\n=== SONUÇLAR ===");
        System.out.println("İşlenen lig sayısı: " + processedCount.get() + "/" + LEAGUE_IDS.size());
        System.out.println("Başarılı lig sayısı: " + successCount.get());
        System.out.println("Toplam bulunan takım ID'si: " + allTeamIds.size());

        // Duplicate ID'leri temizle
        List<String> uniqueTeamIds = new ArrayList<>();
        for (String id : allTeamIds) {
            if (!uniqueTeamIds.contains(id)) {
                uniqueTeamIds.add(id);
            }
        }

        if (uniqueTeamIds.size() < allTeamIds.size()) {
            System.out.println("Duplicate ID'ler temizlendi. Unique ID sayısı: " + uniqueTeamIds.size());
        }

        // ID'leri dosyaya yaz
        writeTeamIdsToFile(uniqueTeamIds);

        System.out.println("\n*** TÜM İŞLEMLER TAMAMLANDI ***");
    }

    private static List<String> scrapeLeagueTeamIds(String leagueId, AtomicInteger processedCount,
                                                    AtomicInteger successCount, int totalLeagues) {
        List<String> teamIds = new ArrayList<>();
        WebDriver driver = null;
        String threadName = Thread.currentThread().getName();

        try {
            System.out.println("\n[" + threadName + "] Lig ID: " + leagueId + " işleniyor...");

            // Her thread için ayrı WebDriver oluştur
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            driver = new ChromeDriver(options);

            // URL'ye git
            String url = "https://arsiv.mackolik.com/Puan-Durumu/" + leagueId + "/";
            System.out.println("[" + threadName + "] URL: " + url);
            driver.get(url);

            // Tablonun yüklenmesini bekle
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#tblStanding .td-team-name a")));

            // Takım linklerini bul ve ID'leri topla
            List<WebElement> teamLinks = driver.findElements(By.cssSelector("#tblStanding .td-team-name a"));
            System.out.println("[" + threadName + "] " + teamLinks.size() + " takım bulundu");

            for (WebElement link : teamLinks) {
                String href = link.getAttribute("href");
                String[] parts = href.split("/");
                if (parts.length > 4 && parts[3].equalsIgnoreCase("Takim")) {
                    String teamId = parts[4];
                    teamIds.add(teamId);
                    System.out.println("[" + threadName + "] Takım ID: " + teamId + " - " + link.getText());
                }
            }

            successCount.incrementAndGet();
            System.out.println("[" + threadName + "] Lig " + leagueId + " başarıyla tamamlandı! " +
                               teamIds.size() + " takım ID'si toplandı.");

        } catch (Exception e) {
            System.err.println("[" + threadName + "] Lig " + leagueId + " işlenirken hata: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("[" + threadName + "] WebDriver kapatıldı.");
            }

            int completed = processedCount.incrementAndGet();
            System.out.println("[" + threadName + "] İlerleme: " + completed + "/" + totalLeagues);
        }

        return teamIds;
    }

    private static void writeTeamIdsToFile(List<String> teamIds) {
        if (teamIds.isEmpty()) {
            System.out.println("Yazılacak ID bulunamadı, dosya oluşturulmadı.");
            return;
        }

        try {
            // 1. ID'leri virgülle ayırarak tek String yap
            String commaSeparatedIds = String.join(",", teamIds);

            // 2. Dosya yolu
            Path file = Paths.get("takim_idleri.txt");

            // 3. Dosyaya yaz
            Files.write(file, commaSeparatedIds.getBytes());

            System.out.println("\n✅ BAŞARILI: " + teamIds.size() +
                               " adet ID başarıyla '" + file.toAbsolutePath() + "' dosyasına yazıldı.");
            System.out.println("📄 Dosya içeriği önizlemesi: " +
                               (commaSeparatedIds.length() > 100 ?
                                       commaSeparatedIds.substring(0, 100) + "..." :
                                       commaSeparatedIds));

        } catch (IOException e) {
            System.err.println("❌ HATA: Dosyaya yazma sırasında hata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Utility method: Belirli lig ID'lerini dinamik olarak ayarlamak için
    public static void setSingleLeagueId(String leagueId) {
        LEAGUE_IDS.clear();
        LEAGUE_IDS.add(leagueId);
    }

    // Utility method: Birden fazla lig ID'sini ayarlamak için
    public static void setMultipleLeagueIds(String... leagueIds) {
        LEAGUE_IDS.clear();
        LEAGUE_IDS.addAll(Arrays.asList(leagueIds));
    }

    // Utility method: Mevcut ID'leri görmek için
    public static void printCurrentLeagueIds() {
        System.out.println("Mevcut lig ID'leri: " + LEAGUE_IDS);
    }
}