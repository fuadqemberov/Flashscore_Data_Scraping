package flashscore.weeklydatascraping.nesine;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class NesineSeleniumHeadlessScraper {

    private static final String BASE_URL = "https://www.nesine.com/sportoto/mac-sonuclari?pNo=";

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        // WebDriver'ı otomatik olarak kur
        WebDriverManager.chromedriver().setup();

        // --- CHROME OPTIONS: HEADLESS AYARLARI ---
        ChromeOptions options = new ChromeOptions();
        // Bu argüman, Chrome'un arayüzü olmadan arka planda çalışmasını sağlar.
        options.addArguments("--headless=new");
        // Headless modda stabilite için önerilen diğer argümanlar:
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
        // --- --- --- --- --- --- --- --- --- --- ---

        WebDriver driver = new ChromeDriver(options);
        // Sayfa elemanlarının yüklenmesini beklemek için bekleme nesnesi (15 saniye)
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        List<String[]> allMatches = new ArrayList<>();

        for (int pNo = 253; pNo <= 302; pNo++) {
            String currentUrl = BASE_URL + pNo;
            System.out.println("Veriler çekiliyor (Headless): " + currentUrl);

            try {
                driver.get(currentUrl);

                // Veri tablosunun satırlarının yüklenmesini bekle.
                // Güvenilir 'data-test-id' seçicisini kullanıyoruz.
                By rowLocator = By.cssSelector("tr:has(td[data-test-id=programResult-number])");
                wait.until(ExpectedConditions.presenceOfElementLocated(rowLocator));

                // Tüm maç satırlarını bul
                List<WebElement> rows = driver.findElements(rowLocator);

                if (rows.isEmpty()) {
                    System.out.println("BİLGİ: Hafta " + pNo + " için veri satırı bulunamadı.");
                    continue;
                }

                for (WebElement row : rows) {
                    String macNo = row.findElement(By.cssSelector("td[data-test-id=programResult-number]")).getText();
                    String tarih = row.findElement(By.cssSelector("td[data-test-id=programResult-date]")).getText();
                    String karsilasma = row.findElement(By.cssSelector("td[data-test-id=programResult-name]")).getText();
                    String skor = row.findElement(By.cssSelector("td[data-test-id=programResult-score]")).getText();
                    String sonuc = row.findElement(By.cssSelector("td[data-test-id=programResult-result]")).getText();

                    String[] matchData = {
                            String.valueOf(pNo), macNo, tarih, karsilasma, skor, sonuc
                    };
                    allMatches.add(matchData);
                }

            } catch (Exception e) {
                System.err.println("HATA: Hafta " + pNo + " işlenirken bir hata oluştu: " + e.getMessage());
            }
        }

        // Tarayıcıyı tamamen kapat
        driver.quit();

        writeToExcel(allMatches);

        long endTime = System.currentTimeMillis();
        System.out.println("Toplam işlem süresi: " + (endTime - startTime) + " ms");
    }

    private static void writeToExcel(List<String[]> allMatches) {
        if (allMatches.isEmpty()) {
            System.out.println("Excel'e yazılacak veri bulunamadı.");
            return;
        }

        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fileOut = new FileOutputStream("Nesine_Mac_Sonuclari_Selenium_Headless.xlsx")) {

            Sheet sheet = workbook.createSheet("Maç Sonuçları");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Hafta No", "Maç No", "Tarih", "Karşılaşma", "Skor", "Sonuç"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            int rowNum = 1;
            for (String[] matchData : allMatches) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < matchData.length; i++) {
                    row.createCell(i).setCellValue(matchData[i]);
                }
            }
            workbook.write(fileOut);
            System.out.println("\nExcel dosyası başarıyla oluşturuldu: Nesine_Mac_Sonuclari_Selenium_Headless.xlsx");

        } catch (IOException e) {
            System.err.println("Excel dosyası yazılırken bir hata oluştu: " + e.getMessage());
        }
    }
}