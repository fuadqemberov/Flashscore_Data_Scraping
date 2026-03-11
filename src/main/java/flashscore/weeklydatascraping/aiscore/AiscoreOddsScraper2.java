package flashscore.weeklydatascraping.aiscore;


import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AiscoreOddsScraper2 {

    // Giriş faylı (Birinci koddan çıxan fayl)
    private static final String INPUT_FILE = "aiscore_urls_history_fast.txt";
    // Çıxış Excel faylı
    private static final String EXCEL_FILE = "Aiscore_Final_Data.xlsx";

    // Eyni anda neçə tab açılsın? (PC gücünə görə artır/azalt)
    private static final int MAX_CONCURRENT_BROWSERS = 16;

    // Excel-ə yazmaq üçün sinxronizasiya kilidi
    private static final Object EXCEL_LOCK = new Object();

    public static void main(String[] args) {
        // Logları təmizləyək
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        System.setProperty("webdriver.chrome.silentOutput", "true");

        System.out.println("--- AISCORE ODDS & STATS SCRAPER ---");

        // URL-ləri oxu
        List<String> urls = readUrlsFromFile();
        if (urls.isEmpty()) {
            System.out.println("URL faylı boşdur və ya tapılmadı.");
            return;
        }
        System.out.println(urls.size() + " ədəd URL yükləndi. Emal başlayır...");

        // WebDriver Setup
        WebDriverManager.chromedriver().setup();

        // Excel faylını və başlığını yarat
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Data");
        createHeaderRow(sheet);

        long startTime = System.currentTimeMillis();

        // Virtual Threads Executor
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore semaphore = new Semaphore(MAX_CONCURRENT_BROWSERS);
            List<Future<Void>> futures = new ArrayList<>();

            int counter = 0;
            for (String url : urls) {
                counter++;
                final int currentCount = counter;

                Future<Void> future = executor.submit(() -> {
                    WebDriver driver = null;
                    try {
                        semaphore.acquire(); // Növbə al

                        // Brauzeri aç və HTML-i gətir
                        driver = new ChromeDriver(createChromeOptions());
                        String pageSource = fetchPageSource(driver, url);

                        // Brauzer işini bitirdi, bağla ki, RAM boşalsın
                        driver.quit();
                        driver = null;

                        // HTML Parse et (Jsoup ilə - çox sürətli)
                        if (pageSource != null) {
                            MatchData data = parseHtmlData(pageSource, url);
                            if (data != null) {
                                synchronized (EXCEL_LOCK) {
                                    writeRowToExcel(sheet, data);
                                }
                                System.out.println("[" + currentCount + "/" + urls.size() + "] Uğurlu: " + data.homeTeam + " vs " + data.awayTeam);
                            } else {
                                System.out.println("[" + currentCount + "/" + urls.size() + "] Data tapılmadı: " + url);
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("Xəta (" + url + "): " + e.getMessage());
                    } finally {
                        if (driver != null) driver.quit();
                        semaphore.release(); // Növbəni burax
                    }
                    return null;
                });
                futures.add(future);
            }

            // Bütün işlərin bitməsini gözlə
            for (Future<Void> f : futures) {
                try { f.get(); } catch (Exception e) { e.printStackTrace(); }
            }
        }

        // Excel-i yaddaşa yaz
        try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE)) {
            workbook.write(fileOut);
            workbook.close();
            System.out.println("\n------------------------------------------------");
            System.out.println("BİTDİ! Bütün datalar '" + EXCEL_FILE + "' faylına yazıldı.");
            long endTime = System.currentTimeMillis();
            System.out.println("Keçən vaxt: " + (endTime - startTime) / 1000 + " saniyə.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- 1. MƏRHƏLƏ: Səhifəni gətirmək (Selenium) ---
    private static String fetchPageSource(WebDriver driver, String url) {
        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Sənin göndərdiyin "Odds" cədvəlinin yüklənməsini gözləyirik
            // Bet365 adətən default gəlir, amma hər ehtimala qarşı odds containeri yoxlayırıq
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.flex.odds")));
            } catch (Exception e) {
                // Bəzən odds olmur, amma oyun məlumatları ola bilər, davam edirik
            }

            // Səhifənin qaynaq kodunu qaytar
            return driver.getPageSource();
        } catch (Exception e) {
            return null;
        }
    }

    // --- 2. MƏRHƏLƏ: HTML Parse etmək (Jsoup) ---
    private static MatchData parseHtmlData(String html, String url) {
        Document doc = Jsoup.parse(html);
        MatchData data = new MatchData();
        data.url = url;

        try {
            // --- INFO PARSING (Sənin göndərdiyin 2-ci və 3-cü HTML hissələri) ---

            // League & Round & Date
            Element compNameBox = doc.selectFirst("div.comp-name");
            if (compNameBox != null) {
                data.league = compNameBox.select("a").text();
                // "Round 20" tapmaq üçün
                Elements spans = compNameBox.select("span");
                for (Element span : spans) {
                    if (span.text().contains("Round")) {
                        data.round = span.text();
                    }
                    if (span.hasAttr("itemprop") && span.attr("itemprop").equals("startDate")) {
                        data.date = span.text();
                    }
                }
            }

            // Teams & Scores
            data.homeTeam = doc.select(".home-box .teamName a").text();
            data.awayTeam = doc.select(".away-box .teamName a").text();

            // Full Time Score (Örn: 2 - 1)
            data.homeScore = doc.select(".home-score span").text();
            data.awayScore = doc.select(".away-score span").text();

            // Half Time Score (Örn: HT 1-1)
            data.htScore = doc.select(".smallStatus").text();

            // --- ODDS PARSING (Sənin göndərdiyin 1-ci HTML hissəsi) ---

            Element oddsContainer = doc.selectFirst("div.flex.odds");
            if (oddsContainer != null) {

                // 1. 1X2 ODDS (.table.eu)
                parse1x2Odds(oddsContainer, data);

                // 2. ASIAN HANDICAP (.table.asia)
                parseAsianOdds(oddsContainer, data);

                // 3. OVER/UNDER (.table.bs)
                parseOverUnderOdds(oddsContainer, data);
            }

            return data;
        } catch (Exception e) {
            // Hər hansı parse xətası olsa belə, ən azından tapdıqlarını qaytar
            return data;
        }
    }

    private static void parse1x2Odds(Element container, MatchData data) {
        Element table = container.selectFirst(".table.eu");
        if (table == null) return;

        Elements rows = table.select(".row.box");
        // Row 0 = Opening, Row 1 = Pre-match

        if (rows.size() > 0) { // Opening
            Elements cols = rows.get(0).select(".col span");
            if (cols.size() >= 3) {
                data.open1 = cols.get(0).text();
                data.openX = cols.get(1).text();
                data.open2 = cols.get(2).text();
            }
        }
        if (rows.size() > 1) { // Pre-match
            Elements cols = rows.get(1).select(".col span");
            if (cols.size() >= 3) {
                data.pre1 = cols.get(0).text();
                data.preX = cols.get(1).text();
                data.pre2 = cols.get(2).text();
            }
        }
    }

    private static void parseAsianOdds(Element container, MatchData data) {
        Element table = container.selectFirst(".table.asia");
        if (table == null) return;

        Elements rows = table.select(".row.box");

        if (rows.size() > 0) { // Opening
            Elements cols = rows.get(0).select(".col");
            if (cols.size() >= 2) {
                // Home Handicap & Odd
                data.openAHLine = cols.get(0).select(".handicap").text();
                data.openAHHome = cols.get(0).select(".rightText").text();
                // Away Handicap (Line adətən eyni olur tərsi, odd lazımdır)
                data.openAHAway = cols.get(1).select(".rightText").text();
            }
        }
        if (rows.size() > 1) { // Pre-match
            Elements cols = rows.get(1).select(".col");
            if (cols.size() >= 2) {
                data.preAHLine = cols.get(0).select(".handicap").text();
                data.preAHHome = cols.get(0).select(".rightText").text();
                data.preAHAway = cols.get(1).select(".rightText").text();
            }
        }
    }

    private static void parseOverUnderOdds(Element container, MatchData data) {
        Element table = container.selectFirst(".table.bs"); // bs = Goals/OverUnder
        if (table == null) return;

        Elements rows = table.select(".row.box");

        if (rows.size() > 0) { // Opening
            Elements cols = rows.get(0).select(".col");
            if (cols.size() >= 3) {
                data.openOULine = cols.get(0).select("span").text(); // Line (2.5 vs)
                data.openOver = cols.get(1).select("span").text();   // Over
                data.openUnder = cols.get(2).select("span").text();  // Under
            }
        }
        if (rows.size() > 1) { // Pre-match
            Elements cols = rows.get(1).select(".col");
            if (cols.size() >= 3) {
                data.preOULine = cols.get(0).select("span").text();
                data.preOver = cols.get(1).select("span").text();
                data.preUnder = cols.get(2).select("span").text();
            }
        }
    }

    // --- HELPERS ---

    private static List<String> readUrlsFromFile() {
        List<String> urls = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(INPUT_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    urls.add(line.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return urls;
    }

    private static void createHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {
                "URL", "Date", "League", "Round", "Home Team", "Away Team", "HT Score", "FT Home", "FT Away",
                "Open 1", "Open X", "Open 2", "Pre 1", "Pre X", "Pre 2",
                "Open AH Line", "Open AH Home", "Open AH Away", "Pre AH Line", "Pre AH Home", "Pre AH Away",
                "Open O/U Line", "Open Over", "Open Under", "Pre O/U Line", "Pre Over", "Pre Under"
        };

        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        style.setFont(font);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private static void writeRowToExcel(Sheet sheet, MatchData d) {
        int rowNum = sheet.getLastRowNum() + 1;
        Row row = sheet.createRow(rowNum);

        int c = 0;
        row.createCell(c++).setCellValue(d.url);
        row.createCell(c++).setCellValue(d.date);
        row.createCell(c++).setCellValue(d.league);
        row.createCell(c++).setCellValue(d.round);
        row.createCell(c++).setCellValue(d.homeTeam);
        row.createCell(c++).setCellValue(d.awayTeam);
        row.createCell(c++).setCellValue(d.htScore);
        row.createCell(c++).setCellValue(d.homeScore);
        row.createCell(c++).setCellValue(d.awayScore);

        // 1X2
        row.createCell(c++).setCellValue(d.open1);
        row.createCell(c++).setCellValue(d.openX);
        row.createCell(c++).setCellValue(d.open2);
        row.createCell(c++).setCellValue(d.pre1);
        row.createCell(c++).setCellValue(d.preX);
        row.createCell(c++).setCellValue(d.pre2);

        // Asian
        row.createCell(c++).setCellValue(d.openAHLine);
        row.createCell(c++).setCellValue(d.openAHHome);
        row.createCell(c++).setCellValue(d.openAHAway);
        row.createCell(c++).setCellValue(d.preAHLine);
        row.createCell(c++).setCellValue(d.preAHHome);
        row.createCell(c++).setCellValue(d.preAHAway);

        // Over/Under
        row.createCell(c++).setCellValue(d.openOULine);
        row.createCell(c++).setCellValue(d.openOver);
        row.createCell(c++).setCellValue(d.openUnder);
        row.createCell(c++).setCellValue(d.preOULine);
        row.createCell(c++).setCellValue(d.preOver);
        row.createCell(c++).setCellValue(d.preUnder);
    }

    private static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new"); // Arxa planda işləsin
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-notifications");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        // Şəkilləri bağlayırıq (Sürət üçün)
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        options.setExperimentalOption("prefs", prefs);
        return options;
    }

    // Datanı saxlamaq üçün sadə daxili class
    static class MatchData {
        String url = "", date = "", league = "", round = "";
        String homeTeam = "", awayTeam = "", htScore = "", homeScore = "", awayScore = "";
        String open1 = "", openX = "", open2 = "", pre1 = "", preX = "", pre2 = "";
        String openAHLine = "", openAHHome = "", openAHAway = "", preAHLine = "", preAHHome = "", preAHAway = "";
        String openOULine = "", openOver = "", openUnder = "", preOULine = "", preOver = "", preUnder = "";
    }
}