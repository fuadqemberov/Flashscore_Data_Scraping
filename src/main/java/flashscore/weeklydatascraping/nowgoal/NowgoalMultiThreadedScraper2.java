package flashscore.weeklydatascraping.nowgoal;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class NowgoalMultiThreadedScraper2 {
    private static final List<List<String>> ALL_MATCHES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger PROCESSED_COUNT = new AtomicInteger(0);
    private static final int THREAD_POOL_SIZE = 8; // Thread pool size

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<?>> futures = new ArrayList<>();
        WebDriver initialDriver = initializeDriver();

        try {
            List<String> matchIds = getMatchIds(initialDriver);

            for (String id : matchIds) {
                futures.add(executor.submit(() -> {
                    WebDriver threadDriver = initializeDriver();
                    try {
                        processMatch(id, threadDriver);
                    } finally {
                        if (threadDriver != null) {
                            threadDriver.quit();
                        }
                    }
                }));
            }

            // Wait for all tasks to complete
            for (Future<?> future : futures) {
                try {
                    future.get(); // Wait for task to finish
                } catch (Exception e) {
                    System.err.println("Task error: " + e.getMessage());
                }
            }

            // Write results to Excel
            writeMatchesToExcel(ALL_MATCHES);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            if (initialDriver != null) {
                initialDriver.quit();
            }
            executor.shutdown();
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("Total time: %d ms%n", endTime - startTime);
        System.out.printf("Total processed matches: %d%n", PROCESSED_COUNT.get());
    }

    private static List<String> getMatchIds(WebDriver driver) {
        List<String> matchIds = new ArrayList<>();
        String baseUrl = "https://live16.nowgoal29.com//football/fixture?f=ft";
        for (int i = 1; i <= 7; i++) {
            String url = baseUrl + i;
            driver.get(url);

            List<WebElement> trElements = driver.findElements(By.xpath("//tr[not(@style) or @style='']"));
            for (WebElement element : trElements) {
                String matchId = element.getAttribute("matchid");
                if (matchId != null && !matchId.isEmpty()) {
                    matchIds.add(matchId);
                }
            }
        }
        return matchIds;
    }

    private static void processMatch(String id, WebDriver driver) {
        try {
            String matchUrl = "https://live16.nowgoal29.com//match/h2h-" + id;
            driver.get(matchUrl);

            // Necessary operations (same as your original code)
            driver.findElement(By.xpath("//*[@id='checkboxleague2']")).click();
            driver.findElement(By.xpath("//*[@id='checkboxleague1']")).click();

            Select dropdown1 = new Select(driver.findElement(By.id("selectMatchCount1")));
            dropdown1.selectByValue("3");

            Select dropdown2 = new Select(driver.findElement(By.id("selectMatchCount2")));
            dropdown2.selectByValue("3");

            List<String> homeMatches = new ArrayList<>();
            List<String> awayMatches = new ArrayList<>();

            String result = extractResult(driver);
            String liga = driver.findElement(By.xpath("//*[@id=\"fbheader\"]/div[1]/span[1]/span")).getText();

            extractMatches(driver, homeMatches, "//tr[starts-with(@id, 'tr1_') and (not(@style) or @style='')]");
            homeMatches.add(result);
            homeMatches.add(liga);

            extractMatches(driver, awayMatches, "//tr[starts-with(@id, 'tr2_') and (not(@style) or @style='')]");
            awayMatches.add(result);
            awayMatches.add(liga);

            // Add the results to the list
            ALL_MATCHES.add(new ArrayList<>(homeMatches));
            ALL_MATCHES.add(new ArrayList<>(awayMatches));

            // Progress info
            int processed = PROCESSED_COUNT.incrementAndGet();
            System.out.printf("%d/%d processed%n", processed, ALL_MATCHES.size());

        } catch (Exception e) {
            System.err.printf("Error processing match %s: %s%n", id, e.getMessage());
        }
    }

    private static String extractResult(WebDriver driver) {
        try {
            WebElement htElement = driver.findElement(By.xpath("//div[@id='mScore']//span[@title='Score 1st Half']"));
            WebElement ft1Element = driver.findElement(By.xpath("//div[@id='mScore']//div[@class='end']//div[@class='score'][1]"));
            WebElement ft2Element = driver.findElement(By.xpath("//div[@id='mScore']//div[@class='end']//div[@class='score'][2]"));

            String ht = htElement.getText();
            String ft1 = ft1Element.getText();
            String ft2 = ft2Element.getText();

            return (ht != null && !ht.isEmpty() && ft1 != null && !ft1.isEmpty() && ft2 != null && !ft2.isEmpty())
                    ? ht + " / " + ft1 + "-" + ft2
                    : "N/A";

        } catch (Exception e) {
            return "N/A";
        }
    }

    private static void extractMatches(WebDriver driver, List<String> matches, String xpath) {
        try {
            List<WebElement> elements = driver.findElements(By.xpath(xpath));
            for (WebElement element : elements) {
                String td = element.findElements(By.tagName("td")).get(3).getText();
                if (!td.isEmpty()) {
                    matches.add(td);
                }
            }
        } catch (Exception e) {
            matches.add("N/A");
        }
    }

    private static void writeMatchesToExcel(List<List<String>> allMatches) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Match 1");
        headerRow.createCell(1).setCellValue("Match 2");
        headerRow.createCell(2).setCellValue("Match 3");
        headerRow.createCell(3).setCellValue("Result ht / ft");
        headerRow.createCell(4).setCellValue("Liga");

        int rowNum = 1;
        for (List<String> matches : allMatches) {
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < matches.size(); i++) {
                row.createCell(i).setCellValue(matches.get(i));
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream("match_results.xlsx")) {
            workbook.write(fileOut);
            System.out.println("Excel file successfully created!");
        } catch (IOException e) {
            System.err.println("Error writing Excel file: " + e.getMessage());
        }
    }

    private static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        return new ChromeDriver(options);
    }
}