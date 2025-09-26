package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class SporTotoSonucCekmeWebDriverManager {

    public static void main(String[] args) {

        // WebDriverManager, Chrome sürümünüzle uyumlu olan ChromeDriver'ı
        // otomatik olarak indirir ve kurar.
        WebDriverManager.chromedriver().setup();

        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String url = "https://www.iddaa.com/spor-toto/sonuclar";

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Spor Toto Sonuçları");

        // Excel başlıklarını oluştur
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Maç Sırası", "Tarih", "Ev Sahibi - Konuk Takım", "Skor", "Sonuç"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        int rowNum = 1;

        try {
            driver.get(url);

            WebElement dropdownElement = driver.findElement(By.cssSelector("div[data-comp-name='sporToto-programSelect'] select"));
            Select select = new Select(dropdownElement);
            int optionsCount = select.getOptions().size();

            // Dropdown'daki her bir seçeneği sondan başa doğru işle
            for (int i = optionsCount - 1; i >= 0; i--) {
                // Her döngüde elementleri yeniden bularak "StaleElementReferenceException" hatasını önle
                dropdownElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[data-comp-name='sporToto-programSelect'] select")));
                select = new Select(dropdownElement);
                select.selectByIndex(i);

                // Verilerin yüklenmesini bekle
                wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("div[data-comp-name='sporToto-result-eventWrapper']"), Integer.valueOf(0)));

                List<WebElement> macSatirlari = driver.findElements(By.cssSelector("div[data-comp-name='sporToto-result-eventWrapper']"));

                for (WebElement satir : macSatirlari) {
                    List<WebElement> hucreler = satir.findElements(By.xpath("./div"));

                    String macSirasi = hucreler.get(0).getText();
                    String tarih = hucreler.get(1).getText();
                    String takimlar = hucreler.get(2).getText();
                    String skor = hucreler.get(3).getText();
                    String sonuc = hucreler.get(4).getText();

                    Row dataRow = sheet.createRow(rowNum++);
                    dataRow.createCell(0).setCellValue(macSirasi);
                    dataRow.createCell(1).setCellValue(tarih);
                    dataRow.createCell(2).setCellValue(takimlar);
                    dataRow.createCell(3).setCellValue(skor);
                    dataRow.createCell(4).setCellValue(sonuc);
                }
            }

            // Workbook'u bir dosyaya yaz
            try (FileOutputStream outputStream = new FileOutputStream("SporTotoSonuclari.xlsx")) {
                workbook.write(outputStream);
                System.out.println("Excel dosyası başarıyla oluşturuldu: SporTotoSonuclari.xlsx");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            driver.quit();
        }
    }
}