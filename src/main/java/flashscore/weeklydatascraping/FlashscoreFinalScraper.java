package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class FlashscoreFinalScraper {

    // --- Sabitler ve Ayarlar ---
    private static final int TARGET_BOOKMAKER_ID = 977;
    private static final int THREAD_POOL_SIZE = 10;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private static final String API_URL_TEMPLATE = "https://global.ds.lsapp.eu/odds/pq_graphql?_hash=oce&eventId=%s&projectId=2&geoIpCode=AZ&geoIpSubdivisionCode=AZBA";
    private static final String MATCH_URL_TEMPLATE = "https://www.flashscore.com/match/%s/#/match-summary";
    private static final Map<String, String> POISON_PILL = new HashMap<>();

    public static void main(String[] args) {
        // 1. ADIM: Selenium ile tüm maç ID'lerini topla
        Set<String> allMatchIds = scrapeAllMatchIds();
        if (allMatchIds.isEmpty()) {
            System.out.println("Hiç maç ID'si bulunamadı. Program sonlandırılıyor.");
            return;
        }

        // 2. ADIM: Her maçı işle (Detayları sıralı, oranları paralel çek)
        processAllMatches(allMatchIds);
    }

    private static void processAllMatches(Set<String> matchIds) {
        BlockingQueue<Map<String, String>> queue = new LinkedBlockingQueue<>(200);
        ExcelWriterConsumer consumer = new ExcelWriterConsumer(queue, createHeaderMap());
        Thread consumerThread = new Thread(consumer);
        consumerThread.start();

        ExecutorService oddsProducerExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        WebDriver detailDriver = createWebDriver(); // Detaylar için tek bir WebDriver

        try {
            System.out.println("\nMaç detayları ve oranlar çekilmeye başlanıyor...");
            for (String matchId : matchIds) {
                // ADIM 2.1: Maç detaylarını Selenium ile sıralı olarak çek
                Map<String, String> matchDetails = scrapeMatchDetailsWithSelenium(detailDriver, matchId);

                // ADIM 2.2: Oranları çekme görevini paralel havuza gönder
                oddsProducerExecutor.submit(new OddsDataProducer(matchDetails, queue));
            }
        } finally {
            if (detailDriver != null) {
                detailDriver.quit();
            }
            // Tüm işlemlerin bitmesini bekle
            shutdownAndAwaitTermination(oddsProducerExecutor);
            try {
                queue.put(POISON_PILL);
                consumerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // --- Üretici ve Tüketici Sınıfları ---
    static class OddsDataProducer implements Runnable {
        private final Map<String, String> matchData;
        private final BlockingQueue<Map<String, String>> queue;

        OddsDataProducer(Map<String, String> matchData, BlockingQueue<Map<String, String>> queue) {
            this.matchData = matchData;
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                processOdds(matchData);
                queue.put(matchData);
                System.out.println("-> Oranları işlendi: " + matchData.get("Maç ID"));
            } catch (Exception e) {
                System.err.println("Hata (Oran Çekme - " + matchData.get("Maç ID") + "): " + e.getMessage());
            }
        }
    }

    static class ExcelWriterConsumer implements Runnable {
        private final BlockingQueue<Map<String, String>> queue;
        private final Map<String, Integer> headerMap;
        private final String fileName = "Flashscore_Full_Data_Correct.xlsx";

        ExcelWriterConsumer(BlockingQueue<Map<String, String>> queue, Map<String, Integer> headerMap) {
            this.queue = queue;
            this.headerMap = headerMap;
        }

        @Override
        public void run() {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Maç Verileri");
                writeHeaderRow(sheet, headerMap);
                int rowNum = 1;

                while (true) {
                    Map<String, String> rowData = queue.take();
                    if (rowData == POISON_PILL) break;

                    Row row = sheet.createRow(rowNum++);
                    for (Map.Entry<String, Integer> headerEntry : headerMap.entrySet()) {
                        String headerText = headerEntry.getKey();
                        row.createCell(headerEntry.getValue()).setCellValue(rowData.getOrDefault(headerText, "-"));
                    }
                }

                for (int i = 0; i < headerMap.size(); i++) sheet.autoSizeColumn(i);
                try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                    workbook.write(fileOut);
                }
                System.out.println("\n*** İŞLEM BAŞARIYLA TAMAMLANDI! Sonuçlar '" + fileName + "' dosyasına yazıldı. ***");

            } catch (Exception e) {
                System.err.println("Excel yazma işlemi sırasında hata: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // --- Selenium ile Maç Detayı Çekme Metodu ---
    private static Map<String, String> scrapeMatchDetailsWithSelenium(WebDriver driver, String matchId) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Maç ID", matchId);
        String url = String.format(MATCH_URL_TEMPLATE, matchId);

        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement container = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".duelParticipant__container")));

            String homeTeam = container.findElement(By.cssSelector(".duelParticipant__home .participant__participantName a")).getText();
            String awayTeam = container.findElement(By.cssSelector(".duelParticipant__away .participant__participantName a")).getText();
            details.put("Ev Sahibi", homeTeam);
            details.put("Deplasman", awayTeam);

            String score = container.findElement(By.cssSelector(".detailScore__wrapper")).getText().replace("\n", "");
            details.put("Maç Skoru", score);

            try {
                WebElement htScoreElement = container.findElement(By.xpath("//div[contains(text(), '(0:0, 1:2)')]"));
                details.put("İlk Yarı Skoru", htScoreElement.getText().split(",")[0].replace("(", "").trim());

            } catch (NoSuchElementException e) {
                details.put("İlk Yarı Skoru", "N/A");
            }
            System.out.println("Detaylar çekildi: " + matchId + " (" + homeTeam + " vs " + awayTeam + ")");

        } catch (Exception e) {
            System.err.println("Hata (Detay Çekme - " + matchId + "): " + e.getMessage());
            fillMatchDetailsWithError(details, "Detay Hatası");
        }
        return details;
    }

    // --- Diğer Tüm Metodlar ---
    // (Önceki kodlardan kopyalanmıştır ve doğru çalışmaktadır)

    private static Set<String> scrapeAllMatchIds() {
        WebDriver driver = createWebDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        Set<String> allMatchIds = new HashSet<>();
        try {
            driver.get("https://www.flashscore.com/");
            System.out.println("Flashscore sitesine gidildi (headless mod)...");
            try {
                wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler"))).click();
                System.out.println("Çerez bildirimi kabul edildi.");
            } catch (Exception e) {
                System.out.println("Çerez bildirimi bulunamadı veya kabul edilemedi.");
            }
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".sportName.soccer")));
            By skeletonLoader = By.cssSelector("div.sk");
            System.out.println("Geriye doğru 7 gün taranacak...");
            for (int i = 0; i < 7; i++) {
                WebElement dateButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='wcl-dayPickerButton']")));
                System.out.println("\nİşleniyor: " + dateButton.getText());
                List<WebElement> matchElements = driver.findElements(By.cssSelector("div[id^='g_1_']"));
                if (!matchElements.isEmpty()) {
                    String prefixToRemove = "g_1_";
                    for (WebElement matchElement : matchElements) {
                        allMatchIds.add(matchElement.getAttribute("id").substring(prefixToRemove.length()));
                    }
                }
                WebElement prevButton = driver.findElement(By.xpath("//button[@aria-label='Previous day']"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", prevButton);
                try {
                    wait.until(ExpectedConditions.invisibilityOfElementLocated(skeletonLoader));
                } catch (Exception e) { /* Timeout olursa devam et */ }
            }
        } catch (Exception e) {
            System.err.println("Maç ID'leri toplanırken bir hata oluştu: " + e.getMessage());
        } finally {
            if (driver != null) driver.quit();
        }
        System.out.println("\n-------------------------------------------");
        System.out.println("Toplam " + allMatchIds.size() + " benzersiz maç ID'si toplandı.");
        System.out.println("-------------------------------------------");
        return allMatchIds;
    }

    private static WebDriver createWebDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1080");
        return new ChromeDriver(options);
    }

    private static void processOdds(Map<String, String> rowData) throws IOException, InterruptedException {
        String matchId = rowData.get("Maç ID");
        String apiUrl = String.format(API_URL_TEMPLATE, matchId);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            fillOddsWithError(rowData, "API Hatası: " + response.statusCode());
            return;
        }

        JSONObject root = new JSONObject(response.body());
        if (!root.has("data") || root.isNull("data") || root.getJSONObject("data").optJSONObject("findOddsByEventId") == null) {
            fillOddsWithError(rowData, "Oran Verisi Yok");
            return;
        }

        JSONArray allOdds = root.getJSONObject("data").getJSONObject("findOddsByEventId").getJSONArray("odds");
        Map<String, JSONObject> requiredData = extractRequiredOdds(allOdds, TARGET_BOOKMAKER_ID);

        String[] scopes = {"FULL_TIME", "FIRST_HALF", "SECOND_HALF"};
        for (String scope : scopes) {
            fillScopeData(rowData, requiredData, scope);
        }
    }

    private static void fillScopeData(Map<String, String> rowData, Map<String, JSONObject> requiredData, String scope) {
        if (requiredData.containsKey(scope)) {
            JSONArray oddsItems = requiredData.get(scope).optJSONArray("odds");
            if (oddsItems != null && oddsItems.length() >= 3) {
                JSONObject home = oddsItems.getJSONObject(0);
                JSONObject away = oddsItems.getJSONObject(1);
                JSONObject draw = oddsItems.getJSONObject(2);
                rowData.put(getReadableScope(scope) + " - Ev Sahibi (1) - Mevcut Oran", home.optString("value", "-"));
                rowData.put(getReadableScope(scope) + " - Ev Sahibi (1) - Açılış Oranı", home.optString("opening", "-"));
                rowData.put(getReadableScope(scope) + " - Deplasman (2) - Mevcut Oran", away.optString("value", "-"));
                rowData.put(getReadableScope(scope) + " - Deplasman (2) - Açılış Oranı", away.optString("opening", "-"));
                rowData.put(getReadableScope(scope) + " - Beraberlik (X) - Mevcut Oran", draw.optString("value", "-"));
                rowData.put(getReadableScope(scope) + " - Beraberlik (X) - Açılış Oranı", draw.optString("opening", "-"));
                return;
            }
        }
        fillScopeWithError(rowData, getReadableScope(scope), "-");
    }

    private static Map<String, JSONObject> extractRequiredOdds(JSONArray allOdds, int bookmakerId) {
        Map<String, JSONObject> dataMap = new HashMap<>();
        for (int i = 0; i < allOdds.length(); i++) {
            JSONObject oddsBlock = allOdds.getJSONObject(i);
            if (oddsBlock.getInt("bookmakerId") == bookmakerId && "HOME_DRAW_AWAY".equals(oddsBlock.getString("bettingType"))) {
                dataMap.put(oddsBlock.getString("bettingScope"), oddsBlock);
            }
        }
        return dataMap;
    }

    private static Map<String, Integer> createHeaderMap() {
        Map<String, Integer> headers = new LinkedHashMap<>();
        headers.put("Maç ID", 0);
        headers.put("Ev Sahibi", 1);
        headers.put("Deplasman", 2);
        headers.put("Maç Skoru", 3);
        headers.put("İlk Yarı Skoru", 4);
        int colIdx = 5;
        String[] scopePrefixes = {"Maç Sonucu", "İlk Yarı", "İkinci Yarı"};
        String[] outcomes = {"Ev Sahibi (1)", "Deplasman (2)", "Beraberlik (X)"};
        String[] oddTypes = {"Mevcut Oran", "Açılış Oranı"};
        for (String scopePrefix : scopePrefixes) {
            for (String outcome : outcomes) {
                for (String oddType : oddTypes) {
                    headers.put(scopePrefix + " - " + outcome + " - " + oddType, colIdx++);
                }
            }
        }
        return headers;
    }

    private static void writeHeaderRow(Sheet sheet, Map<String, Integer> headerMap) {
        Row headerRow = sheet.createRow(0);
        for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
            headerRow.createCell(entry.getValue()).setCellValue(entry.getKey());
        }
    }

    private static void fillMatchDetailsWithError(Map<String, String> map, String value) {
        map.put("Ev Sahibi", value);
        map.put("Deplasman", value);
        map.put("Maç Skoru", value);
        map.put("İlk Yarı Skoru", value);
    }

    private static void fillOddsWithError(Map<String, String> map, String value) {
        String[] scopes = {"Maç Sonucu", "İlk Yarı", "İkinci Yarı"};
        for(String s : scopes) {
            fillScopeWithError(map, s, value);
        }
    }

    private static void fillScopeWithError(Map<String, String> map, String readableScope, String value) {
        Map<String, Integer> headers = createHeaderMap();
        headers.keySet().stream()
                .filter(key -> key.startsWith(readableScope))
                .forEach(key -> map.put(key, value));
    }

    private static String getReadableScope(String internalScope) {
        switch(internalScope) {
            case "FULL_TIME": return "Maç Sonucu";
            case "FIRST_HALF": return "İlk Yarı";
            case "SECOND_HALF": return "İkinci Yarı";
            default: return internalScope;
        }
    }

    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}