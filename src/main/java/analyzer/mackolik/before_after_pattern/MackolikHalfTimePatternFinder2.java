package analyzer.mackolik.before_after_pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class MackolikHalfTimePatternFinder2 {

    private static final int DRIVER_POOL_SIZE = 10;
    private static final int PAGE_TIMEOUT_SEC = 15;

    // Thread-safe koleksiyonlar
    public static final Set<String> upcomingMatchIds = Collections.synchronizedSet(new LinkedHashSet<>());
    public static final List<String> matchedPatterns = Collections.synchronizedList(new ArrayList<>());

    private static final BlockingQueue<WebDriver> driverPool = new LinkedBlockingQueue<>(DRIVER_POOL_SIZE);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile boolean shutdownRequested = false;

    static {
        initializeDriverPool();
    }

    private static void initializeDriverPool() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setPageLoadTimeout(Duration.ofSeconds(PAGE_TIMEOUT_SEC));

        for (int i = 0; i < DRIVER_POOL_SIZE; i++) {
            try {
                WebDriver driver = new ChromeDriver(options);
                driverPool.offer(driver);
            } catch (Exception e) {
                System.err.println("❌ Driver #" + (i + 1) + " oluşturulamadı: " + e.getMessage());
            }
        }
        System.out.println("✓ " + driverPool.size() + " adet WebDriver pool'a eklendi.\n");
    }

    private static WebDriver acquireDriver() throws InterruptedException {
        if (shutdownRequested) throw new IllegalStateException("Driver pool kapatıldı");
        return driverPool.take();
    }

    private static void releaseDriver(WebDriver driver) {
        if (driver != null && !shutdownRequested) {
            try {
                driverPool.put(driver);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                driver.quit();
            }
        }
    }

    private static void shutdownDriverPool() {
        shutdownRequested = true;
        WebDriver driver;
        while ((driver = driverPool.poll()) != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {}
        }
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        System.out.println("🚀 Mackolik Half-Time Pattern Finder - Ultra Hızlı Versiyon\n");

        ExecutorService executor = null;

        try {
            // 1. Maç ID'lerini topla
            getDailyUpcomingMatchIds();

            System.out.println("📋 Toplanan maç ID sayısı: " + upcomingMatchIds.size() + "\n");

            if (upcomingMatchIds.isEmpty()) {
                System.out.println("❌ Hiç maç bulunamadı!");
                return;
            }

            System.out.println("⚙️ Maç analizleri başlıyor (Virtual Threads + " + DRIVER_POOL_SIZE + " driver)...\n");

            executor = Executors.newVirtualThreadPerTaskExecutor();

            List<Future<?>> futures = new ArrayList<>();
            for (String matchId : upcomingMatchIds) {
                futures.add(executor.submit(() -> analyzeMatchFormWithDriver(matchId)));
            }

            // Tüm task'ları bekle
            int completed = 0;
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    printProgress(++completed, upcomingMatchIds.size());
                } catch (Exception e) {
                    // Timeout veya hata → devam et
                }
            }

            System.out.println("\n\n==== ANALİZ TAMAMLANDI ====\n");

            printFinalResults(startTime);

        } catch (Exception e) {
            System.err.println("❌ Ana işlemde hata: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
            shutdownDriverPool();
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("\n⏱️ Toplam süre: " + formatDuration(elapsed));
        }
    }

    private static void getDailyUpcomingMatchIds() {
        upcomingMatchIds.clear();

        // Önce hızlı API yöntemi
        if (tryMackolikApi()) return;

        // API başarısız olursa Selenium ile devam et
        WebDriver driver = null;
        try {
            driver = acquireDriver();
            scrapeWithSelenium(driver);
        } catch (Exception e) {
            System.err.println("Selenium ile maç toplama hatası: " + e.getMessage());
        } finally {
            if (driver != null) releaseDriver(driver);
        }
    }

    /** goapi.mackolik.com veya vd.mackolik.com üzerinden maç ID'leri alma */
    private static boolean tryMackolikApi() {
        try {
            String url = "https://goapi.mackolik.com/livedata?group=0&sport=1";
            // Alternatif: https://vd.mackolik.com/livedata?group=0

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) return false;

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(response.body(), JsonObject.class);

            JsonArray matches = root.has("Matches") ? root.getAsJsonArray("Matches") :
                    root.has("m") ? root.getAsJsonArray("m") : null;

            if (matches != null) {
                for (int i = 0; i < matches.size(); i++) {
                    try {
                        JsonObject match = matches.get(i).getAsJsonObject();
                        String id = match.has("Id") ? match.get("Id").getAsString() :
                                match.has("0") ? match.get("0").getAsString() : null; // array formatı için

                        if (id != null && id.matches("\\d+")) {
                            upcomingMatchIds.add(id);
                        }
                    } catch (Exception ignored) {}
                }
            }

            System.out.println("✅ API'den " + upcomingMatchIds.size() + " maç ID'si alındı.");
            return !upcomingMatchIds.isEmpty();

        } catch (Exception e) {
            System.out.println("⚠️ API yöntemi başarısız, Selenium'a geçiliyor...");
            return false;
        }
    }

    private static void scrapeWithSelenium(WebDriver driver) {
        try {
            driver.get("https://arsiv.mackolik.com/Canli-Sonuclar");

            JavascriptExecutor js = (JavascriptExecutor) driver;
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(12));

            // Reklamları temizle
            js.executeScript("document.querySelectorAll('iframe').forEach(i => i.remove());");

            // Tarih navigasyonu
            try {
                js.executeScript("if(typeof gotoDate === 'function') { gotoDate(-1); gotoDate(1); }");
                Thread.sleep(1500);
            } catch (Exception ignored) {}

            // Maç linklerinden ID çıkar
            List<WebElement> links = driver.findElements(By.xpath("//a[contains(@href,'/Mac/')]"));

            for (WebElement link : links) {
                String href = link.getAttribute("href");
                if (href != null && href.contains("/Mac/")) {
                    try {
                        String matchId = href.split("/Mac/")[1].split("[/?#]")[0];
                        if (matchId.matches("\\d+")) {
                            upcomingMatchIds.add(matchId);
                        }
                    } catch (Exception ignored) {}
                }
            }

            System.out.println("✅ Selenium ile " + upcomingMatchIds.size() + " maç ID'si toplandı.");

        } catch (Exception e) {
            System.err.println("Selenium scraping hatası: " + e.getMessage());
        }
    }

    private static void analyzeMatchFormWithDriver(String matchId) {
        WebDriver driver = null;
        try {
            driver = acquireDriver();
            analyzeMatchForm(driver, matchId);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Sessiz devam
        } finally {
            releaseDriver(driver);
        }
    }

    private static void analyzeMatchForm(WebDriver driver, String matchId) {
        String url = "https://arsiv.mackolik.com/Mac/" + matchId + "/#karsilastirma";

        try {
            driver.get(url);
            Thread.sleep(2200); // Form tablosunun yüklenmesi için

            List<WebElement> formDivs = driver.findElements(
                    By.xpath("//div[contains(@class, 'md') and .//div[contains(@class, 'detail-title') and contains(., 'Form Durumu')]]")
            );

            if (formDivs.size() < 2) return;

            TableAnalysis home = analyzeTableForTeamAndOpponents(formDivs.get(0), matchId);
            TableAnalysis away = analyzeTableForTeamAndOpponents(formDivs.get(1), matchId);

            detectAndRecordPatterns(url, home, away);

        } catch (Exception ignored) {
            // Tek maç hatası sistemi durdurmasın
        }
    }

    private static void detectAndRecordPatterns(String url, TableAnalysis home, TableAnalysis away) {
        // PATTERN 1: Havuz Eşleşmesi (2'li kesişim)
        Set<String> type1 = new HashSet<>(home.prevOpponents);
        type1.retainAll(away.nextOpponents);

        Set<String> type2 = new HashSet<>(home.nextOpponents);
        type2.retainAll(away.prevOpponents);

        if (type1.size() >= 2) {
            recordMatch(url, home.teamName, away.teamName, type1,
                    "BİLGİ-3 (Havuz Eşleşmesi)", "Ev Önceki 2 = Dep Sonraki 2", "🟢");
        }
        if (type2.size() >= 2) {
            recordMatch(url, home.teamName, away.teamName, type2,
                    "BİLGİ-3 (Havuz Eşleşmesi)", "Ev Sonraki 2 = Dep Önceki 2", "🟢");
        }

        // PATTERN 2: Çapraz Eşleşmeler
        boolean c1A = home.prev1 != null && away.next1 != null && home.prev1.equals(away.next1);
        boolean c1B = home.next1 != null && away.prev1 != null && home.next1.equals(away.prev1);
        boolean c2A = home.prev2 != null && away.next2 != null && home.prev2.equals(away.next2);
        boolean c2B = home.next2 != null && away.prev2 != null && home.next2.equals(away.prev2);

        if (c1A && c1B) {
            recordMatch(url, home.teamName, away.teamName, Set.of(home.prev1, home.next1),
                    "KUSURSUZ X ÇAPRAZ (Mesafe 1)", "Ev[-1]=Dep[+1] ve Ev[+1]=Dep[-1]", "🔥");
        } else {
            if (c1A) recordMatch(url, home.teamName, away.teamName, Set.of(home.prev1),
                    "ÇAPRAZ (Mesafe 1)", "Ev[-1] = Dep[+1]", "🔵");
            if (c1B) recordMatch(url, home.teamName, away.teamName, Set.of(home.next1),
                    "ÇAPRAZ (Mesafe 1)", "Ev[+1] = Dep[-1]", "🔵");
        }

        if (c2A && c2B) {
            recordMatch(url, home.teamName, away.teamName, Set.of(home.prev2, home.next2),
                    "KUSURSUZ X ÇAPRAZ (Mesafe 2)", "Ev[-2]=Dep[+2] ve Ev[+2]=Dep[-2]", "🔥");
        } else {
            if (c2A) recordMatch(url, home.teamName, away.teamName, Set.of(home.prev2),
                    "ÇAPRAZ (Mesafe 2)", "Ev[-2] = Dep[+2]", "🔵");
            if (c2B) recordMatch(url, home.teamName, away.teamName, Set.of(home.next2),
                    "ÇAPRAZ (Mesafe 2)", "Ev[+2] = Dep[-2]", "🔵");
        }
    }

    private static void recordMatch(String url, String home, String away, Set<String> common,
                                    String patternName, String desc, String emoji) {
        String msg = String.format("%s [%s] %s vs %s | %s | Ortak: %s | Link: %s",
                emoji, patternName, home, away, desc, common, url);

        System.out.println(" " + emoji + " PATTERN YAKALANDI → " + patternName);
        matchedPatterns.add(msg);
    }

    // ====================== TABLE ANALİZ METODLARI ======================

    static class TableAnalysis {
        String teamName;
        String prev2, prev1, next1, next2;
        Set<String> prevOpponents = new HashSet<>();
        Set<String> nextOpponents = new HashSet<>();

        TableAnalysis(String teamName, String prev2, String prev1, String next1, String next2) {
            this.teamName = teamName;
            this.prev2 = prev2; this.prev1 = prev1;
            this.next1 = next1; this.next2 = next2;

            if (prev2 != null) prevOpponents.add(prev2);
            if (prev1 != null) prevOpponents.add(prev1);
            if (next1 != null) nextOpponents.add(next1);
            if (next2 != null) nextOpponents.add(next2);
        }
    }

    private static TableAnalysis analyzeTableForTeamAndOpponents(WebElement wrapper, String targetMatchId) {
        String teamName = "Bilinmeyen";
        String prev2 = null, prev1 = null, next1 = null, next2 = null;

        try {
            WebElement title = wrapper.findElement(By.xpath(".//div[contains(@class,'detail-title')]"));
            String titleText = title.getText();
            teamName = titleText.contains("-") ? titleText.split("-")[0].trim() : titleText.replace("Form Durumu", "").trim();

            WebElement table = wrapper.findElement(By.tagName("table"));
            List<WebElement> rows = table.findElements(By.xpath(".//tr[contains(@class,'row')]"));

            int targetIndex = -1;
            for (int i = 0; i < rows.size(); i++) {
                List<WebElement> vLinks = rows.get(i).findElements(By.xpath(".//td[4]//a[contains(text(),'v')]"));
                if (!vLinks.isEmpty() && vLinks.get(0).getAttribute("href").contains(targetMatchId)) {
                    targetIndex = i;
                    break;
                }
            }

            if (targetIndex != -1) {
                prev2 = getOpponentFromRow(rows, targetIndex - 2);
                prev1 = getOpponentFromRow(rows, targetIndex - 1);
                next1 = getOpponentFromRow(rows, targetIndex + 1);
                next2 = getOpponentFromRow(rows, targetIndex + 2);
            }
        } catch (Exception ignored) {}

        return new TableAnalysis(teamName, prev2, prev1, next1, next2);
    }

    private static String getOpponentFromRow(List<WebElement> rows, int index) {
        if (index < 0 || index >= rows.size()) return null;
        try {
            WebElement row = rows.get(index);
            List<WebElement> teamLinks = row.findElements(By.xpath(".//td[3]//a[contains(@href,'/Takim/')] | .//td[5]//a[contains(@href,'/Takim/')]"));
            if (!teamLinks.isEmpty()) {
                return teamLinks.get(0).getText().trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void printProgress(int completed, int total) {
        int percent = (int) ((completed * 100.0) / total);
        System.out.print("\r✓ İlerleme: " + completed + "/" + total + " (" + percent + "%)");
    }

    private static void printFinalResults(long startTime) {
        System.out.println("\n\n" + "=".repeat(80));
        System.out.println("🔥 SİSTEMİN YAKALADIĞI PATTERN'LER 🔥");
        System.out.println("=".repeat(80));

        if (matchedPatterns.isEmpty()) {
            System.out.println("❌ Bugün herhangi bir pattern yakalanamadı.");
        } else {
            for (String result : matchedPatterns) {
                System.out.println(result);
                System.out.println("-".repeat(60));
            }
            System.out.println("\n✅ Toplam bulunan sinyal: " + matchedPatterns.size());
        }
        System.out.println("=".repeat(80));
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes > 0 ? minutes + " dk " + seconds + " sn" : seconds + " saniye";
    }
}