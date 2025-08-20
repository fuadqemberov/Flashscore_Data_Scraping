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
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MultithreadedSeleniumScraper {

    private static final String URL_FILE = "match_urls.txt";
    private static final String EXCEL_FILE = "Aiscore_Bet365_Odds_MultiSelenium.xlsx";
    // Adjust this number based on your machine's CPU cores and RAM.
    // A good starting point is the number of available processors.
    private static final int BROWSER_INSTANCE_COUNT = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        System.out.println("--- Multithreaded Selenium Scraper Started ---");
        System.out.println("Preparing to launch " + BROWSER_INSTANCE_COUNT + " parallel browser instances.");

        suppressAllWarnings();
        WebDriverManager.chromedriver().setup(); // Setup driver once for all instances

        MultithreadedSeleniumScraper scraper = new MultithreadedSeleniumScraper();
        List<String> urls = scraper.readUrlsFromFile();
        if (urls.isEmpty()) {
            System.out.println("No URLs to process. Ensure '" + URL_FILE + "' is populated.");
            return;
        }

        System.out.println("Found " + urls.size() + " URLs. Starting scraping process...");
        List<MatchData> results = scraper.scrapeInParallel(urls);

        if (!results.isEmpty()) {
            ExcelWriter.saveToExcel(results, EXCEL_FILE);
        } else {
            System.out.println("No data was successfully scraped.");
        }
        System.out.println("\n--- SCRAPING PROCESS COMPLETED ---");
    }

    private List<MatchData> scrapeInParallel(List<String> urls) {
        WebDriverPool pool = new WebDriverPool(BROWSER_INSTANCE_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(BROWSER_INSTANCE_COUNT);
        List<Future<MatchData>> futures = new ArrayList<>();

        for (String url : urls) {
            Callable<MatchData> task = new SeleniumScrapingTask(url, pool);
            futures.add(executor.submit(task));
        }

        List<MatchData> results = new ArrayList<>();
        int completedCount = 0;
        for (Future<MatchData> future : futures) {
            try {
                MatchData data = future.get(); // Blocks until the task is complete
                if (data != null) {
                    results.add(data);
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error processing a scraping task: " + e.getMessage());
            }
            completedCount++;
            System.out.print("\rProgress: " + completedCount + "/" + urls.size() + " URLs processed.");
        }
        System.out.println(); // New line after progress bar

        // CRITICAL: Shutdown pool and executor
        executor.shutdown();
        pool.shutdown();

        return results.stream().filter(Objects::nonNull).collect(Collectors.toList());
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
            System.err.println("Error reading '" + URL_FILE + "': " + e.getMessage());
        }
        return urls;
    }

    private static void suppressAllWarnings() {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
    }
}

// ---- Helper Classes ----

/**
 * Manages a pool of reusable WebDriver instances.
 */
class WebDriverPool {
    private final int capacity;
    private final BlockingQueue<WebDriver> pool;

    public WebDriverPool(int capacity) {
        this.capacity = capacity;
        this.pool = new LinkedBlockingQueue<>(capacity);
        initializePool();
    }

    private void initializePool() {
        for (int i = 0; i < capacity; i++) {
            pool.add(createDriver());
        }
    }

    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1920,1080", "--log-level=3");
        options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        return new ChromeDriver(options);
    }

    public WebDriver get() throws InterruptedException {
        return pool.take(); // Waits if necessary until a driver becomes available
    }

    public void release(WebDriver driver) {
        if (driver != null) {
            pool.add(driver);
        }
    }

    public void shutdown() {
        System.out.println("Shutting down all " + capacity + " browser instances...");
        for (WebDriver driver : pool) {
            driver.quit();
        }
    }
}

/**
 * A task representing the scraping of a single URL.
 */
class SeleniumScrapingTask implements Callable<MatchData> {
    private final String url;
    private final WebDriverPool pool;

    public SeleniumScrapingTask(String url, WebDriverPool pool) {
        this.url = url;
        this.pool = pool;
    }

    @Override
    public MatchData call() {
        WebDriver driver = null;
        try {
            driver = pool.get(); // Borrow a driver from the pool

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            driver.get(url + "/odds");

            // Wait for the Bet365 logo to ensure the odds table is loaded
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//img[contains(@src, 'fe8aec51afeb2de633c9')]")));

            String league = safeGetText(driver, By.cssSelector("div.comp-name > a"));
            String round = safeGetText(driver, By.xpath("//div[@class='comp-name']/span[contains(text(), 'Round')]"));
            String homeTeam = safeGetText(driver, By.cssSelector("div.home-box a[itemprop='homeTeam']"));
            String awayTeam = safeGetText(driver, By.cssSelector("div.away-box a[itemprop='awayTeam']"));
            String homeScore = safeGetText(driver, By.cssSelector("div.home-score > span"));
            String awayScore = safeGetText(driver, By.cssSelector("div.away-score > span"));
            String htScore = safeGetText(driver, By.cssSelector("div.smallStatus")).replace("HT ", "");

            WebElement bet365Row = driver.findElement(By.xpath("//img[contains(@src, 'fe8aec51afeb2de633c9')]/ancestor::div[contains(@class, 'borderBottom')]"));

            // Get opening odds for the 1X2 market
            List<WebElement> openingOddsElements = bet365Row.findElements(By.cssSelector("div.flex.flex-1:first-child div.openingBg1 div.oddItems"));

            String opening1 = "N/A", openingX = "N/A", opening2 = "N/A";
            if (openingOddsElements.size() >= 3) {
                opening1 = openingOddsElements.get(0).getText().trim();
                openingX = openingOddsElements.get(1).getText().trim();
                opening2 = openingOddsElements.get(2).getText().trim();
            }

            return new MatchData(league, round, homeTeam, awayTeam, htScore, homeScore + " - " + awayScore, opening1, openingX, opening2);

        } catch (NoSuchElementException e) {
            System.err.println("\n  -> Data not found (likely no Bet365 odds or different page layout): " + url);
            return null;
        } catch (Exception e) {
            System.err.println("\n  -> Unexpected error for URL " + url + ": " + e.getMessage());
            return null;
        } finally {
            if (driver != null) {
                pool.release(driver); // CRITICAL: Always return the driver to the pool
            }
        }
    }

    private String safeGetText(WebDriver driver, By by) {
        try {
            return driver.findElement(by).getText().trim();
        } catch (NoSuchElementException e) {
            return "N/A";
        }
    }
}

// Data-holding record for clean code
record MatchData(
        String league, String round, String homeTeam, String awayTeam,
        String htScore, String ftScore,
        String opening1, String openingX, String opening2
) {}


// Utility class for writing to Excel (no changes needed here)
class ExcelWriter {
    public static void saveToExcel(List<MatchData> data, String fileName) {
        if (data == null || data.isEmpty()) {
            System.out.println("No data to write to Excel.");
            return;
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bet365 Oranları");
            createHeaderRow(sheet);

            int rowNum = 1;
            for (MatchData match : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(match.league());
                row.createCell(1).setCellValue(match.round());
                row.createCell(2).setCellValue(match.homeTeam());
                row.createCell(3).setCellValue(match.awayTeam());
                row.createCell(4).setCellValue(match.htScore());
                row.createCell(5).setCellValue(match.ftScore());
                row.createCell(6).setCellValue(match.opening1());
                row.createCell(7).setCellValue(match.openingX());
                row.createCell(8).setCellValue(match.opening2());
            }

            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
                System.out.println("\nAll " + data.size() + " results successfully written to '" + fileName + "'");
            }

        } catch (IOException e) {
            System.err.println("Error writing to Excel file: " + e.getMessage());
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] columns = {"Lig", "Hafta", "Ev Sahibi", "Deplasman", "İY Skoru", "MS Skoru", "Bet365 Açılış 1", "Bet365 Açılış X", "Bet365 Açılış 2"};
        for (int i = 0; i < columns.length; i++) {
            headerRow.createCell(i).setCellValue(columns[i]);
        }
    }
}