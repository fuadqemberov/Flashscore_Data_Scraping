package flashscore.weeklydatascraping;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.ArrayList;

public class IddaaSporTotoScraper {
    private WebDriver driver;
    private WebDriverWait wait;
    private Workbook workbook;
    private Sheet sheet;
    private int rowNum = 0;
    private final Object excelLock = new Object();
    private ExecutorService executorService;

    public IddaaSporTotoScraper() {
        // WebDriverManager setup
        WebDriverManager.chromedriver().setup();

        // High performance ChromeOptions
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Headless mode for speed
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-web-security");
        options.addArguments("--disable-features=VizDisplayCompositor");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images"); // Don't load images for speed
        options.addArguments("--disable-javascript"); // Disable JS if not needed
        options.addArguments("--disable-css"); // Disable CSS loading
        options.addArguments("--aggressive-cache-discard");
        options.addArguments("--memory-pressure-off");
        options.addArguments("--max_old_space_size=4096");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--disable-backgrounding-occluded-windows");

        // Page load strategy for faster loading
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(10));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));

        wait = new WebDriverWait(driver, Duration.ofSeconds(8));

        // Excel setup
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet("Spor Toto Sonuçları");
        createHeaderRow();

        // Thread pool for parallel processing
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    private void createHeaderRow() {
        Row headerRow = sheet.createRow(rowNum++);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] headers = {"Dönem", "Maç Sırası", "Tarih", "Ev Sahibi - Konuk Takım", "Skor", "Sonuç"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    public void scrapeAllPeriods() {
        try {
            driver.get("https://www.iddaa.com/spor-toto/sonuclar");

            // Minimal wait - just until select is present
            WebElement selectElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("select")));

            Select select = new Select(selectElement);
            List<WebElement> options = select.getOptions();

            System.out.println("Toplam " + options.size() + " dönem bulundu. Hızlı işlem başlıyor...");

            List<Future<Void>> futures = new ArrayList<>();

            // Process periods in batches for maximum speed
            for (int i = 0; i < options.size(); i++) {
                final int index = i;
                Future<Void> future = executorService.submit(() -> {
                    processPeriod(index);
                    return null;
                });
                futures.add(future);

                // Process in batches of 5 to avoid overwhelming the server
                if ((i + 1) % 5 == 0 || i == options.size() - 1) {
                    // Wait for current batch to complete
                    for (Future<Void> f : futures) {
                        try {
                            f.get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            System.err.println("Batch processing error: " + e.getMessage());
                        }
                    }
                    futures.clear();
                    Thread.sleep(100); // Brief pause between batches
                }
            }

        } catch (Exception e) {
            System.err.println("Main process error: " + e.getMessage());
        }
    }

    private void processPeriod(int periodIndex) {
        try {
            // Re-find select element to avoid stale reference
            WebElement selectElement = driver.findElement(By.cssSelector("select"));
            Select select = new Select(selectElement);

            if (periodIndex >= select.getOptions().size()) return;

            WebElement option = select.getOptions().get(periodIndex);
            String periodText = option.getText();

            // Quick selection without waiting
            ((JavascriptExecutor) driver).executeScript("arguments[0].selectedIndex = arguments[1];", selectElement, periodIndex);
            ((JavascriptExecutor) driver).executeScript("arguments[0].dispatchEvent(new Event('change'));", selectElement);

            // Minimal wait for content update
            Thread.sleep(500);

            scrapeCurrentPeriodData(periodText);

        } catch (Exception e) {
            System.err.println("Period processing error: " + e.getMessage());
        }
    }

    private void scrapeCurrentPeriodData(String period) {
        try {
            // Fast element finding with JavaScript
            List<WebElement> matchRows = (List<WebElement>) ((JavascriptExecutor) driver).executeScript(
                    "return Array.from(document.querySelectorAll('div[data-comp-name=\"sporToto-result-eventWrapper\"]'));"
            );

            if (matchRows.isEmpty()) {
                // Fallback method
                matchRows = driver.findElements(By.cssSelector("div[data-comp-name='sporToto-result-eventWrapper']"));
            }

            List<MatchData> matchDataList = new ArrayList<>();

            for (WebElement matchRow : matchRows) {
                try {
                    MatchData matchData = extractMatchDataFast(matchRow, period);
                    if (matchData != null) {
                        matchDataList.add(matchData);
                    }
                } catch (Exception e) {
                    continue; // Skip problematic rows
                }
            }

            // Bulk add to Excel for better performance
            addBulkDataToExcel(matchDataList);

        } catch (Exception e) {
            System.err.println("Data scraping error for period " + period + ": " + e.getMessage());
        }
    }

    private MatchData extractMatchDataFast(WebElement matchRow, String period) {
        try {
            // Use JavaScript for faster data extraction
            String script =
                    "var row = arguments[0];" +
                            "var cells = row.querySelectorAll('div');" +
                            "return {" +
                            "  macSirasi: cells[0] ? cells[0].textContent.trim() : ''," +
                            "  tarih: cells[1] ? cells[1].textContent.trim() : ''," +
                            "  takimlar: cells[2] ? cells[2].textContent.trim() : ''," +
                            "  skor: cells[3] ? cells[3].textContent.trim() : ''," +
                            "  sonuc: cells[4] ? cells[4].textContent.trim() : ''" +
                            "};";

            Object result = ((JavascriptExecutor) driver).executeScript(script, matchRow);

            if (result instanceof java.util.Map) {
                java.util.Map<String, String> data = (java.util.Map<String, String>) result;
                return new MatchData(
                        period,
                        data.get("macSirasi"),
                        data.get("tarih"),
                        data.get("takimlar"),
                        data.get("skor"),
                        data.get("sonuc")
                );
            }

        } catch (Exception e) {
            // Fallback to Selenium method
            try {
                List<WebElement> cells = matchRow.findElements(By.cssSelector("div"));
                if (cells.size() >= 5) {
                    return new MatchData(
                            period,
                            cells.get(0).getText().trim(),
                            cells.get(1).getText().trim(),
                            cells.get(2).getText().trim(),
                            cells.get(3).getText().trim(),
                            cells.get(4).getText().trim()
                    );
                }
            } catch (Exception ex) {
                // Skip this row
            }
        }
        return null;
    }

    private void addBulkDataToExcel(List<MatchData> matchDataList) {
        synchronized (excelLock) {
            for (MatchData matchData : matchDataList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(matchData.period);
                row.createCell(1).setCellValue(matchData.macSirasi);
                row.createCell(2).setCellValue(matchData.tarih);
                row.createCell(3).setCellValue(matchData.takimlar);
                row.createCell(4).setCellValue(matchData.skor);
                row.createCell(5).setCellValue(matchData.sonuc);
            }
            System.out.println("Bulk added " + matchDataList.size() + " matches for period: " +
                    (matchDataList.isEmpty() ? "N/A" : matchDataList.get(0).period));
        }
    }

    // Data class for match information
    private static class MatchData {
        String period, macSirasi, tarih, takimlar, skor, sonuc;

        MatchData(String period, String macSirasi, String tarih, String takimlar, String skor, String sonuc) {
            this.period = period;
            this.macSirasi = macSirasi;
            this.tarih = tarih;
            this.takimlar = takimlar;
            this.skor = skor;
            this.sonuc = sonuc;
        }
    }

    public void saveExcel(String fileName) {
        try {
            // Auto-size columns efficiently
            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            FileOutputStream fileOut = new FileOutputStream(fileName);
            workbook.write(fileOut);
            fileOut.close();

            System.out.println("Excel file saved: " + fileName + " with " + (rowNum-1) + " data rows");

        } catch (IOException e) {
            System.err.println("Excel save error: " + e.getMessage());
        }
    }

    public void cleanup() {
        try {
            if (executorService != null) {
                executorService.shutdown();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            }
            if (driver != null) {
                driver.quit();
            }
            if (workbook != null) {
                workbook.close();
            }
        } catch (Exception e) {
            System.err.println("Cleanup error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        IddaaSporTotoScraper scraper = new IddaaSporTotoScraper();

        try {
            System.out.println("High-speed Spor Toto scraping started...");
            scraper.scrapeAllPeriods();
            scraper.saveExcel("spor_toto_sonuclari_fast.xlsx");

            long endTime = System.currentTimeMillis();
            System.out.println("Process completed in " + (endTime - startTime) / 1000.0 + " seconds!");

        } catch (Exception e) {
            System.err.println("Main error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scraper.cleanup();
        }
    }
}