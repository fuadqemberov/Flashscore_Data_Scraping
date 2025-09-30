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

public class MackolikIdToFileV2List {

    private static final List<String> SEASON_IDS = Arrays.asList(
            "70270",  // Örnek sezon 1
            "69112",  // Örnek sezon 2
            "67234","70632","70423"

    );

    private static final int THREAD_COUNT = 4; // Selenium için optimal thread sayısı

    public static void main(String[] args) {
        System.out.println("=== MultiThread Mackolik ID Scraper V2 ===");
        System.out.println("İşlenecek sezon sayısı: " + SEASON_IDS.size());
        System.out.println("Thread sayısı: " + THREAD_COUNT);
        System.out.println("URL formatı: https://arsiv.mackolik.com/Puan-Durumu/s={SEASON_ID}/");

        // WebDriver setup (sadece bir kez)
        WebDriverManager.chromedriver().setup();

        // Thread-safe liste ve sayaçlar
        List<String> allTeamIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Thread pool oluştur
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<List<String>>> futures = new ArrayList<>();

        // Her sezon için ayrı task oluştur
        for (String seasonId : SEASON_IDS) {
            Future<List<String>> future = executor.submit(() -> {
                return scrapeSeasonTeamIds(seasonId, processedCount, successCount, SEASON_IDS.size());
            });
            futures.add(future);
        }

        // Tüm taskların tamamlanmasını bekle ve sonuçları topla
        try {
            for (Future<List<String>> future : futures) {
                List<String> seasonTeamIds = future.get(45, TimeUnit.SECONDS); // 45 saniye timeout
                allTeamIds.addAll(seasonTeamIds);
            }
        } catch (Exception e) {
            System.err.println("Thread execution hatası: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Sonuçları analiz et ve raporla
        analyzeAndWriteResults(allTeamIds, processedCount.get(), successCount.get());

        System.out.println("\n*** TÜM İŞLEMLER TAMAMLANDI ***");
    }

    private static List<String> scrapeSeasonTeamIds(String seasonId, AtomicInteger processedCount,
                                                    AtomicInteger successCount, int totalSeasons) {
        List<String> teamIds = new ArrayList<>();
        WebDriver driver = null;
        String threadName = Thread.currentThread().getName();

        try {
            System.out.println("\n[" + threadName + "] 🔄 Sezon ID: " + seasonId + " işleniyor...");

            // Her thread için ayrı WebDriver oluştur
            ChromeOptions options = createChromeOptions();
            driver = new ChromeDriver(options);

            // URL'ye git (V2 formatı: s= parametreli)
            String url = "https://arsiv.mackolik.com/Puan-Durumu/s=" + seasonId + "/";
            System.out.println("[" + threadName + "] 🌐 URL: " + url);
            driver.get(url);

            // Tablonun yüklenmesini bekle
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            System.out.println("[" + threadName + "] ⏳ Puan tablosunun yüklenmesi bekleniyor...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#tblStanding .td-team-name a")));
            System.out.println("[" + threadName + "] ✅ Tablo başarıyla yüklendi!");

            // Takım linklerini bul ve ID'leri topla
            List<WebElement> teamLinks = driver.findElements(By.cssSelector("#tblStanding .td-team-name a"));
            System.out.println("[" + threadName + "] 📊 " + teamLinks.size() + " takım bulundu, ID'ler toplanıyor...");

            for (WebElement link : teamLinks) {
                String href = link.getAttribute("href");
                if (href != null && !href.isEmpty()) {
                    String[] parts = href.split("/");
                    if (parts.length > 4 && parts[3].equalsIgnoreCase("Takim")) {
                        String teamId = parts[4];
                        teamIds.add(teamId);
                        System.out.println("[" + threadName + "] 🎯 Takım ID: " + teamId +
                                           " - " + link.getText().trim());
                    }
                }
            }

            successCount.incrementAndGet();
            System.out.println("[" + threadName + "] 🎉 Sezon " + seasonId +
                               " başarıyla tamamlandı! " + teamIds.size() + " takım ID'si toplandı.");

        } catch (Exception e) {
            System.err.println("[" + threadName + "] ❌ Sezon " + seasonId +
                               " işlenirken hata: " + e.getMessage());
            if (e.getMessage().contains("timeout") || e.getMessage().contains("Timeout")) {
                System.err.println("[" + threadName + "] ⚠️  Timeout hatası - Sayfa yüklenme süresi aşıldı");
            }
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("[" + threadName + "] 🔒 WebDriver kapatıldı.");
            }

            int completed = processedCount.incrementAndGet();
            System.out.println("[" + threadName + "] 📈 İlerleme: " + completed + "/" + totalSeasons +
                               " (" + String.format("%.1f", (completed * 100.0 / totalSeasons)) + "%)");
        }

        return teamIds;
    }

    private static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images"); // Daha hızlı yükleme için görselleri devre dışı bırak
        options.addArguments("--disable-javascript"); // JS gereksizse devre dışı bırak
        return options;
    }

    private static void analyzeAndWriteResults(List<String> allTeamIds, int processedCount, int successCount) {
        System.out.println("\n=== 📊 SONUÇ ANALİZİ ===");
        System.out.println("İşlenen sezon sayısı: " + processedCount + "/" + SEASON_IDS.size());
        System.out.println("Başarılı sezon sayısı: " + successCount);
        System.out.println("Başarı oranı: " + String.format("%.1f", (successCount * 100.0 / SEASON_IDS.size())) + "%");
        System.out.println("Ham toplam takım ID'si: " + allTeamIds.size());

        if (allTeamIds.isEmpty()) {
            System.out.println("❌ Hiç takım ID'si bulunamadı, dosya oluşturulmadı.");
            return;
        }

        // Duplicate ID'leri temizle ve analiz et
        List<String> uniqueTeamIds = removeDuplicates(allTeamIds);

        if (uniqueTeamIds.size() < allTeamIds.size()) {
            int duplicateCount = allTeamIds.size() - uniqueTeamIds.size();
            System.out.println("🧹 " + duplicateCount + " duplicate ID temizlendi.");
        }
        System.out.println("✨ Unique takım ID sayısı: " + uniqueTeamIds.size());

        // ID'leri dosyaya yaz
        writeTeamIdsToFile(uniqueTeamIds);

        // İstatistikleri yazdır
        printStatistics(uniqueTeamIds);
    }

    private static List<String> removeDuplicates(List<String> list) {
        List<String> uniqueList = new ArrayList<>();
        for (String item : list) {
            if (!uniqueList.contains(item)) {
                uniqueList.add(item);
            }
        }
        return uniqueList;
    }

    private static void writeTeamIdsToFile(List<String> teamIds) {
        try {
            // 1. ID'leri virgülle ayırarak tek String yap
            String commaSeparatedIds = String.join(",", teamIds);

            // 2. Dosya yolu
            Path file = Paths.get("takim_idleri.txt");

            // 3. Dosyaya yaz
            Files.write(file, commaSeparatedIds.getBytes());

            System.out.println("\n✅ BAŞARILI KAYIT!");
            System.out.println("📁 Dosya: " + file.toAbsolutePath());
            System.out.println("📊 Kayıt edilen ID sayısı: " + teamIds.size());
            System.out.println("📄 Dosya boyutu: " + commaSeparatedIds.length() + " karakter");

            // Dosya önizlemesi
            String preview = commaSeparatedIds.length() > 150 ?
                    commaSeparatedIds.substring(0, 150) + "..." :
                    commaSeparatedIds;
            System.out.println("👀 Önizleme: " + preview);

        } catch (IOException e) {
            System.err.println("❌ DOSYA YAZMA HATASI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printStatistics(List<String> teamIds) {
        System.out.println("\n=== 📈 İSTATİSTİKLER ===");

        if (teamIds.isEmpty()) return;

        // En küçük ve en büyük ID'leri bul
        String minId = teamIds.get(0);
        String maxId = teamIds.get(0);

        for (String id : teamIds) {
            if (id.compareTo(minId) < 0) minId = id;
            if (id.compareTo(maxId) > 0) maxId = id;
        }

        System.out.println("🔢 En küçük ID: " + minId);
        System.out.println("🔢 En büyük ID: " + maxId);
        System.out.println("📏 Ortalama ID uzunluğu: " +
                           String.format("%.1f", teamIds.stream().mapToInt(String::length).average().orElse(0.0)));

        // İlk 5 ve son 5 ID'yi göster
        System.out.println("🥇 İlk 5 ID: " + teamIds.subList(0, Math.min(5, teamIds.size())));
        if (teamIds.size() > 5) {
            System.out.println("🏁 Son 5 ID: " + teamIds.subList(Math.max(0, teamIds.size() - 5), teamIds.size()));
        }
    }

    // ========== UTILITY METODLARI ==========

    // Tek sezon ID'si ayarlamak için
    public static void setSingleSeasonId(String seasonId) {
        SEASON_IDS.clear();
        SEASON_IDS.add(seasonId);
    }

    // Birden fazla sezon ID'si ayarlamak için
    public static void setMultipleSeasonIds(String... seasonIds) {
        SEASON_IDS.clear();
        SEASON_IDS.addAll(Arrays.asList(seasonIds));
    }

    // Mevcut sezon ID'lerini görmek için
    public static void printCurrentSeasonIds() {
        System.out.println("Mevcut sezon ID'leri: " + SEASON_IDS);
    }

    // Thread sayısını değiştirmek için (dikkatli kullan!)
    public static void setThreadCount(int threadCount) {
        if (threadCount > 0 && threadCount <= 8) {
            System.setProperty("mackolik.thread.count", String.valueOf(threadCount));
            System.out.println("Thread sayısı " + threadCount + " olarak ayarlandı.");
        } else {
            System.err.println("Geçersiz thread sayısı! 1-8 arası olmalı.");
        }
    }
}