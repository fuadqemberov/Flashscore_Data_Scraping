package flashscore.weeklydatascraping.nowgoal;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NowgoalLastMatchesOptimizedFinal2 {
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final List<List<String>> allMatches = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);
    private static final BlockingQueue<WebDriver> driverPool = new LinkedBlockingQueue<>();

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        // Create driver pool
        System.out.println("Initializing " + THREAD_POOL_SIZE + " WebDrivers...");
        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            try {
                WebDriver driver = initializeDriver();
                driverPool.offer(driver);
            } catch (Exception e) {
                System.err.println("Failed to initialize driver: " + e.getMessage());
            }
        }

        if (driverPool.isEmpty()) {
            System.err.println("No drivers could be initialized. Exiting.");
            return;
        }

        System.out.println("Successfully initialized " + driverPool.size() + " WebDrivers");

        ExecutorService executor = Executors.newFixedThreadPool(driverPool.size());
        Queue<Long> idQueue = new ConcurrentLinkedQueue<>();

        // ID range
        long startId = 2738509;
        long endId = 2738511;

        for (long id = startId; id < endId; id++) {
            idQueue.add(id);
        }

        System.out.println("Starting to process " + (endId - startId) + " matches");

        // Progress tracking thread
        executor.submit(() -> {
            int totalMatches = (int)(endId - startId);
            int lastReported = 0;
            int lastFailed = 0;

            while (!idQueue.isEmpty() || (lastReported + lastFailed) < totalMatches) {
                int current = successCount.get();
                int failed = failCount.get();
                if (current > lastReported || failed > lastFailed) {
                    int processed = current + failed;
                    System.out.printf("Progress: %d/%d (%.1f%%) - Success: %d, Failed: %d%n",
                            processed, totalMatches,
                            (double)processed/totalMatches*100,
                            current, failed);
                    lastReported = current;
                    lastFailed = failed;
                }

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        // Worker threads
        for (int i = 0; i < driverPool.size(); i++) {
            executor.submit(() -> {
                WebDriver driver = null;
                try {
                    driver = driverPool.take();

                    while (!idQueue.isEmpty()) {
                        Long id = idQueue.poll();
                        if (id != null) {
                            try {
                                processMatch(driver, id);
                                successCount.incrementAndGet();
                            } catch (Exception e) {
                                failCount.incrementAndGet();
                                System.err.println("Error processing match " + id + ": " + e.getMessage());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (driver != null && !Thread.currentThread().isInterrupted()) {
                        driverPool.offer(driver);
                    } else if (driver != null) {
                        driver.quit();
                    }
                }
            });
        }

        // Shutdown
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.MINUTES)) {
                System.err.println("Executor timed out before completion");
                executor.shutdownNow();
            }

            writeMatchesToExcel(allMatches);

            for (WebDriver driver : driverPool) {
                try {
                    driver.quit();
                } catch (Exception e) {}
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Completed in " + (endTime - startTime) / 1000 + " seconds");
            System.out.println("Successfully processed " + successCount.get() + " matches");
            System.out.println("Failed to process " + failCount.get() + " matches");
            System.out.println("Results written to match_results.xlsx");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Process interrupted: " + e.getMessage());
            executor.shutdownNow();
        }
    }

    private static void processMatch(WebDriver driver, long matchId) {
        try {
            driver.get("https://live14.nowgoal25.com/match/h2h-" + matchId);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Checkboxes için bekle
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                    By.cssSelector("input[id^='checkboxleague']")));

            // DÜZELTME: Home takım için checkboxleague1 kullan
            clickElement(driver, By.id("checkboxleague1")); // Ev sahibi için
            clickElement(driver, By.id("checkboxleague2")); // Ev sahibi için
            // Seçimler için bekle
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("selectMatchCount1")));

            // Dropdown'ları seç
            Select homeDropdown = new Select(driver.findElement(By.id("selectMatchCount1")));
            homeDropdown.selectByValue("3");

            Select awayDropdown = new Select(driver.findElement(By.id("selectMatchCount2")));
            awayDropdown.selectByValue("3");

            // Veri yüklemesi için bekle
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//tr[starts-with(@id, 'tr1_')]")));

            Document doc = Jsoup.parse(driver.getPageSource());

            List<String> homeData = extractMatchData(doc, "tr1_");
            List<String> awayData = extractMatchData(doc, "tr2_");
            String result = extractResult(doc);
            String league = extractLeague(doc);

            if (league.equals("N/A") || homeData.isEmpty() || awayData.isEmpty()) return;

            synchronized (allMatches) {
                List<String> homeRow = new ArrayList<>(homeData);
                homeRow.add(result);
                homeRow.add(league);
                homeRow.add(String.valueOf(matchId));
                allMatches.add(homeRow);

                List<String> awayRow = new ArrayList<>(awayData);
                awayRow.add(result);
                awayRow.add(league);
                awayRow.add(String.valueOf(matchId));
                allMatches.add(awayRow);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing match " + matchId, e);
        }
    }

    // GELİŞTİRİLMİŞ clickElement metodu
    private static void clickElement(WebDriver driver, By locator) {
        try {
            WebElement element = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.elementToBeClickable(locator));

            try {
                element.click();
            } catch (WebDriverException e) {
                System.out.println("Normal click failed, using JS click");
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].click();", element);
            }

            // Tıklama sonrası bekleme
            Thread.sleep(500);
        } catch (Exception e) {
            System.err.println("Click error: " + e.getMessage());
        }
    }

    private static List<String> extractMatchData(Document doc, String rowPrefix) {
        List<String> matches = new ArrayList<>();
        Elements rows = doc.select("tr[id^=" + rowPrefix + "]:not([style*=display]), tr[id^=" + rowPrefix + "][style='']");

        for (Element row : rows) {
            Elements tds = row.select("td");
            if (tds.size() > 3) {
                String scoreText = tds.get(3).text();
                matches.add(scoreText.length() >= 3 ? scoreText.substring(0, 3) : scoreText);
            }
        }
        return matches;
    }

    private static String extractResult(Document doc) {
        Element ht = doc.selectFirst("div#mScore span[title='Score 1st Half']");
        Elements ft = doc.select("div#mScore div.end div.score");
        return (ht != null ? ht.text() : "N/A") + " / " +
                (ft.size() > 1 ? ft.get(0).text() + "-" + ft.get(1).text() : "N/A");
    }

    private static String extractLeague(Document doc) {
        Element league = doc.selectFirst("#fbheader > div:first-child > span:first-child > span");
        return league != null ? league.text() : "N/A";
    }

    private static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src/chromee/chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--disable-extensions", "--disable-notifications");
        options.addArguments("--window-size=1920,1080");
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return driver;
    }

    private static void writeMatchesToExcel(List<List<String>> data) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Match Results");
            createHeaderRow(sheet);

            int rowNum = 1;
            for (List<String> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < rowData.size(); i++) {
                    row.createCell(i).setCellValue(rowData.get(i));
                }
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream("match_results.xlsx")) {
                workbook.write(fos);
            }
        } catch (IOException e) {
            System.err.println("Excel write error: " + e.getMessage());
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {"Match 1", "Match 2", "Match 3", "Result", "League", "Match ID"};
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }
}