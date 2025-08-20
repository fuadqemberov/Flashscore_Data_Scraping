package flashscore.weeklydatascraping.ais365;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OddsScraper {

    private static final String URL_FILE = "match_urls.txt";
    private static final String EXCEL_FILE = "Aiscore_Bet365_Odds.xlsx";

    public static void main(String[] args) {
        System.out.println("--- Aiscore Oran Scraper Başlatıldı ---");
        System.out.println("'" + URL_FILE + "' dosyasından URL'ler okunuyor ve işleniyor...");
        OddsScraper scraper = new OddsScraper();
        scraper.scrapeOddsAndSaveToExcel();
        System.out.println("\n--- ORAN ÇEKME İŞLEMİ TAMAMLANDI ---");
    }

    private void scrapeOddsAndSaveToExcel() {
        List<String> urls = readUrlsFromFile();
        if (urls.isEmpty()) {
            System.out.println("İşlenecek URL bulunamadı. Lütfen önce UrlCollector'ı çalıştırdığınızdan emin olun.");
            return;
        }

        suppressAllWarnings();
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = createChromeOptions();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Bet365 Oranları");
        createHeaderRow(sheet);

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            int rowNum = 1;
            int urlCounter = 1;

            for (String url : urls) {
                System.out.println("İşleniyor (" + urlCounter + "/" + urls.size() + "): " + url);
                urlCounter++;
                try {
                    driver.get(url + "/odds");

                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//img[contains(@src, 'fe8aec51afeb2de633c9')]")));

                    String league = safeGetText(driver, By.cssSelector("div.comp-name > a"));
                    String round = safeGetText(driver, By.xpath("//div[@class='comp-name']/span[contains(text(), 'Round')]"));
                    String homeTeam = safeGetText(driver, By.cssSelector("div.home-box a[itemprop='homeTeam']"));
                    String awayTeam = safeGetText(driver, By.cssSelector("div.away-box a[itemprop='awayTeam']"));
                    String homeScore = safeGetText(driver, By.cssSelector("div.home-score > span"));
                    String awayScore = safeGetText(driver, By.cssSelector("div.away-score > span"));
                    String htScore = safeGetText(driver, By.cssSelector("div.smallStatus"));

                    WebElement bet365Row = driver.findElement(By.xpath("//img[contains(@src, 'fe8aec51afeb2de633c9')]/ancestor::div[contains(@class, 'borderBottom')]"));

                    // --- BURASI DEĞİŞTİRİLDİ ---
                    // Önceki hatalı seçici yerine, sadece ilk oran sütununu (1X2 marketi) hedef alan daha spesifik bir CSS seçici kullanıldı.
                    List<WebElement> openingOddsElements = bet365Row.findElements(By.cssSelector("div.flex.flex-1:first-child div.openingBg1 div.oddItems"));

                    String opening1 = "N/A", openingX = "N/A", opening2 = "N/A";
                    if(openingOddsElements.size() >= 3) {
                        opening1 = openingOddsElements.get(0).getText().trim();
                        openingX = openingOddsElements.get(1).getText().trim();
                        opening2 = openingOddsElements.get(2).getText().trim();
                    }

                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(league);
                    row.createCell(1).setCellValue(round);
                    row.createCell(2).setCellValue(homeTeam);
                    row.createCell(3).setCellValue(awayTeam);
                    row.createCell(4).setCellValue(htScore.replace("HT ", ""));
                    row.createCell(5).setCellValue(homeScore + " - " + awayScore);
                    row.createCell(6).setCellValue(opening1);
                    row.createCell(7).setCellValue(openingX);
                    row.createCell(8).setCellValue(opening2);

                } catch (TimeoutException | NoSuchElementException e) {
                    System.err.println("  -> Veri çekilemedi (muhtemelen Bet365 oranı yok veya sayfa yapısı farklı): " + url);
                } catch (Exception e) {
                    System.err.println("  -> Beklenmedik bir hata oluştu: " + url + " - " + e.getMessage());
                }
            }
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE)) {
            workbook.write(fileOut);
            System.out.println("\nTüm veriler başarıyla '" + EXCEL_FILE + "' dosyasına yazıldı.");
        } catch (IOException e) {
            System.err.println("Excel dosyası yazılırken hata oluştu: " + e.getMessage());
        }
    }

    private String safeGetText(WebDriver driver, By by) {
        try {
            return driver.findElement(by).getText().trim();
        } catch (NoSuchElementException e) {
            return "N/A";
        }
    }

    private void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] columns = {"Lig", "Hafta", "Ev Sahibi", "Deplasman", "İY Skoru", "MS Skoru", "Bet365 Açılış 1", "Bet365 Açılış X", "Bet365 Açılış 2"};
        for (int i = 0; i < columns.length; i++) {
            headerRow.createCell(i).setCellValue(columns[i]);
        }
    }

    private List<String> readUrlsFromFile() {
        List<String> urls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(URL_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    urls.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.err.println("'" + URL_FILE + "' dosyası okunurken hata oluştu. " + e.getMessage());
        }
        return urls;
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
}