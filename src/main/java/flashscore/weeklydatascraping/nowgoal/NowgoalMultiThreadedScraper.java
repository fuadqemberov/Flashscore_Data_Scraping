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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class NowgoalMultiThreadedScraper {
    // Thread-safe collections
    private static final List<List<String>> ALL_MATCHES = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger PROCESSED_COUNT = new AtomicInteger(0);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        WebDriver driver = null;
        try {
            driver = initializeDriver();
            List<String> matchIds = getMatchIds(driver);

            for (String id : matchIds) {
                processMatch(driver, Long.parseLong(id));
            }

            // Write results to Excel
            writeMatchesToExcel(ALL_MATCHES);

            long endTime = System.currentTimeMillis();
            System.out.printf("Total processing time: %d ms%n", endTime - startTime);
            System.out.printf("Total matches processed: %d%n", PROCESSED_COUNT.get());

        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    public static List<String> getMatchIds(WebDriver driver) {
        List<String> matchIds2 = new ArrayList<>();
        String baseUrl = "https://live18.nowgoal29.com/football/fixture?f=ft";
        for (int i = 1; i < 8; i++) {
            String url = baseUrl + i;
            driver.get(url);
            List<WebElement> trElements = driver.findElements(By.xpath("//tr[not(@style) or @style='']"));
            for (WebElement element : trElements) {
                String matchId = element.getAttribute("matchid"); // Get the match ID
                if (matchId != null && !matchId.isEmpty()) { // Check for null or empty match ID
                    matchIds2.add(matchId); // Add the match ID to the list
                }
            }
        }
        return matchIds2;
    }

    private static synchronized void processMatch(WebDriver driver, long id) {
        String matchUrl = "https://live18.nowgoal29.com/match/h2h-";
        driver.get(matchUrl + id);

        try {
            // Perform match data extraction (similar to original method)
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

            extractHomeMatches(driver, homeMatches);
            homeMatches.add(result);
            homeMatches.add(liga);

            extractAwayMatches(driver, awayMatches);
            awayMatches.add(result);
            awayMatches.add(liga);

            // Synchronized addition to shared list
            synchronized (ALL_MATCHES) {
                ALL_MATCHES.add(new ArrayList<>(homeMatches));
                ALL_MATCHES.add(new ArrayList<>(awayMatches));
            }

            PROCESSED_COUNT.incrementAndGet();

        } catch (Exception e) {
            System.err.printf("Error processing match %d: %s%n", id, e.getMessage());
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

    private static void extractHomeMatches(WebDriver driver, List<String> homeMatches) {
        String xpath = "//tr[starts-with(@id, 'tr1_') and (not(@style) or @style='')]";
        try {
            List<WebElement> elements = driver.findElements(By.xpath(xpath));
            for (WebElement element : elements) {
                String td = element.findElements(By.tagName("td")).get(3).getText();
                if (!td.isEmpty()) {
                    homeMatches.add(td);
                }
            }
        } catch (Exception e) {
            homeMatches.add("N/A");
        }
    }

    private static void extractAwayMatches(WebDriver driver, List<String> awayMatches) {
        String xpath = "//tr[starts-with(@id, 'tr2_') and (not(@style) or @style='')]";
        try {
            List<WebElement> elements = driver.findElements(By.xpath(xpath));
            for (WebElement element : elements) {
                String td = element.findElements(By.tagName("td")).get(3).getText();
                if (!td.isEmpty()) {
                    awayMatches.add(td);
                }
            }
        } catch (Exception e) {
            awayMatches.add("N/A");
        }
    }

    public static void writeMatchesToExcel(List<List<String>> allmatches) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Match 1");
        headerRow.createCell(1).setCellValue("Match 2");
        headerRow.createCell(2).setCellValue("Match 3");
        headerRow.createCell(3).setCellValue("Result ht / ft");
        headerRow.createCell(4).setCellValue("Liga");

        int rowNum = 1;

        for (int i = 0; i < allmatches.size(); i++) {
            List<String> matches = allmatches.get(i);

            String match1 = matches.get(0).substring(0, 3);
            String match2 = matches.get(1).substring(0, 3);
            String match3 = matches.get(2).substring(0, 3);
            String liga = matches.get(3);
            String result = matches.get(4);

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(match1);
            row.createCell(1).setCellValue(match2);
            row.createCell(2).setCellValue(match3);
            row.createCell(3).setCellValue(liga);
            row.createCell(4).setCellValue(result);
        }
        try (FileOutputStream fileOut = new FileOutputStream("match_results.xlsx")) {
            workbook.write(fileOut);
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Excel file created successfully!");
    }

    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Başsız modda çalıştır
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        return driver;
    }
}
