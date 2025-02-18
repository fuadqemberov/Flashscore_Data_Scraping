package flashscore.weeklydatascraping.datepickermackolik;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class DatePickerAutomationDeepSeek {

    private static List<String> links = Collections.synchronizedList(new ArrayList<>());
    private static CopyOnWriteArrayList<List<String>> allData = new CopyOnWriteArrayList<>();
    private static final int MAX_THREADS = 5; // Sabit thread sayısı
    private static final Object fileLock = new Object();
    private static final int MAX_RETRIES = 3;
    private static final Semaphore driverSemaphore = new Semaphore(5); // Aynı anda maksimum 2 Chrome penceresi

    public static void main(String[] args) throws InterruptedException, IOException {
        long startTime = System.currentTimeMillis();

        // Step 1: Get all match links
        WebDriver driver = getChromeDriver();
//        try {
//            getMatchesByDateRange(driver, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));
//        } finally {
//
//        }

        // Step 2: Process links in parallel
            List<String> linksToProcess = readFile();
            processLinksInParallel(linksToProcess);


        driver.quit();
        // Step 3: Write results to Excel
        writeMatchesToExcel();

        long endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) / 1000 + " seconds");
    }

    private static void processLinksInParallel(List<String> linksToProcess) throws InterruptedException {
        System.out.println("Processing " + linksToProcess.size() + " links using " + MAX_THREADS + " threads...");

        ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
        CountDownLatch latch = new CountDownLatch(linksToProcess.size());
        AtomicInteger processedCount = new AtomicInteger(0);

        for (String link : linksToProcess) {
            executorService.submit(() -> {
                WebDriver threadDriver = null;
                try {
                    driverSemaphore.acquire(); // İzin alana kadar bekler
                    threadDriver = getChromeDriver();

                    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                            getData(link, threadDriver);
                            break; // Success, exit retry loop
                        } catch (Exception e) {
                            if (attempt == MAX_RETRIES) {
                                System.err.println("Final attempt failed for link " + link + ": " + e.getMessage());
                            } else {
                                System.out.println("Attempt " + attempt + " failed for link " + link + ". Retrying...");
                                threadDriver.manage().deleteAllCookies();
                                threadDriver.navigate().refresh();
                                Thread.sleep(1000 * attempt); // Exponential backoff
                            }
                        }
                    }

                    int count = processedCount.incrementAndGet();
                    if (count % 5 == 0 || count == linksToProcess.size()) {
                        System.out.println("Processed " + count + " out of " + linksToProcess.size() + " links");
                    }
                } catch (Exception e) {
                    System.err.println("Error in thread processing link " + link + ": " + e.getMessage());
                } finally {
                    if (threadDriver != null) {
                        threadDriver.quit();
                    }
                    driverSemaphore.release(); // İzni serbest bırak
                    latch.countDown();
                }
            });
        }

        latch.await(); // Wait for all threads to complete
        executorService.shutdown();
        System.out.println("All links processed successfully!");
    }

    private static void getData(String link, WebDriver driver) throws InterruptedException {
        driver.get(link);
        Thread.sleep(1500); // Give page time to load initially

        try {
            Wait<WebDriver> fluentWait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(10))
                    .pollingEvery(Duration.ofMillis(500))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class);

            WebElement karsilastirmaButton = fluentWait.until(x -> x.findElement(By.xpath("//a[span[text()='Karşılaştırma']]")));

            Actions actions = new Actions(driver);
            actions.moveToElement(karsilastirmaButton).click().perform();
            Thread.sleep(3000); // Increased wait time after clicking the button
        } catch (Exception ex) {
            System.out.println("Karşılaştırma butonu bulunamadı veya tıklanamadı: " + ex.getMessage());
            throw new RuntimeException("Failed to find or click comparison button", ex);
        }

        String htFt = extractFormattedScore(link, driver);

        for (int j = 1; j < 3; j++) {
            List<String> teamList = new ArrayList<>();
            boolean hasData = false;

            for (int i = 3; i < 7; i++) {
                try {
                    Wait<WebDriver> wait = new FluentWait<>(driver)
                            .withTimeout(Duration.ofSeconds(5))
                            .pollingEvery(Duration.ofMillis(300))
                            .ignoring(NoSuchElementException.class)
                            .ignoring(StaleElementReferenceException.class);

                    String xpathHome = "/html/body/div[5]/div[2]/main/div[1]/div[2]/div/div[3]/div/div/div[2]/div[" + j + "]/div[" + i + "]/div/a[1]/div/span";
                    String xpathScore = "/html/body/div[5]/div[2]/main/div[1]/div[2]/div/div[3]/div/div/div[2]/div[" + j + "]/div[" + i + "]/div/a[2]/div";
                    String xpathAway = "/html/body/div[5]/div[2]/main/div[1]/div[2]/div/div[3]/div/div/div[2]/div[" + j + "]/div[" + i + "]/div/a[3]/div/span[2]";

                    WebElement homeElement = wait.until(x -> x.findElement(By.xpath(xpathHome)));
                    WebElement scoreElement = wait.until(x -> x.findElement(By.xpath(xpathScore)));
                    WebElement awayElement = wait.until(x -> x.findElement(By.xpath(xpathAway)));

                    String home = homeElement.getText();
                    String score = scoreElement.getText();
                    String away = awayElement.getText();

                    if (!home.isEmpty() && !score.isEmpty() && !away.isEmpty()) {
                        teamList.add(home);
                        teamList.add(score);
                        teamList.add(away);
                        hasData = true;
                    } else {
                        teamList.add("N/A");
                        teamList.add("N/A");
                        teamList.add("N/A");
                    }

                    if (i == 6) {
                        teamList.add(htFt);
                    }
                } catch (Exception ex) {
                    System.out.println("Veri alınamadı (j=" + j + ", i=" + i + "): " + ex.getMessage());
                    teamList.add("N/A");
                    teamList.add("N/A");
                    teamList.add("N/A");
                    if (i == 6) {
                        teamList.add("N/A");
                    }
                }
            }

            if (hasData) {
                allData.add(new ArrayList<>(teamList));
            }
        }
    }

    public static String extractFormattedScore(String url, WebDriver driver) {
        String formattedScore = "N/A";

        try {
            Wait<WebDriver> wait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(8))
                    .pollingEvery(Duration.ofMillis(500))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class);

            WebElement homeScoreElement = wait.until(x -> x.findElement(By.cssSelector(".p0c-soccer-match-details-header__score-home")));
            String homeScore = homeScoreElement.getText().trim();
            String awayScore = driver.findElement(By.cssSelector(".p0c-soccer-match-details-header__score-away")).getText().trim();

            try {
                WebElement detailedScoreElement = driver.findElement(By.className("p0c-soccer-match-details-header__detailed-score"));
                String detailedScore = detailedScoreElement.getText().trim();

                String halfTimeScore = "0-0";
                if (detailedScore.contains("İY")) {
                    String htScoreText = detailedScore.replace("(İY", "").replace(")", "").trim();
                    if (!htScoreText.isEmpty()) {
                        halfTimeScore = htScoreText;
                    }
                }

                formattedScore = halfTimeScore + "/" + homeScore + "-" + awayScore;
            } catch (Exception e) {
                formattedScore = "?-?/" + homeScore + "-" + awayScore;
            }

        } catch (Exception e) {
            System.err.println("Error extracting score data: " + e.getMessage());
        }

        return formattedScore;
    }

    public static List<String> readFile() throws IOException {
        List<String> lines = new ArrayList<>();
        File file = new File("mackolik.txt");

        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static void getMatchesByDateRange(WebDriver driver, LocalDate startDate, LocalDate endDate)
            throws InterruptedException, IOException {
        driver.get("https://www.mackolik.com/canli-sonuclar");
        Thread.sleep(3000);

        FileWriter writer = null;
        try {
            writer = new FileWriter("mackolik.txt");

            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                String dateStr = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                try {
                    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                            driver.findElement(By.className("widget-dateslider__datepicker-toggle")).click();
                            Thread.sleep(1000);
                            break;
                        } catch (Exception e) {
                            if (attempt == MAX_RETRIES) throw e;
                            System.out.println("Failed to click date picker. Retrying...");
                            Thread.sleep(1000);
                        }
                    }

                    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                            WebElement yearMonthSelector = driver.findElement(By.xpath("//div[@class='widget-datepicker__header']"));
                            yearMonthSelector.click();
                            Thread.sleep(1000);
                            break;
                        } catch (Exception e) {
                            if (attempt == MAX_RETRIES) throw e;
                            System.out.println("Failed to click year/month selector. Retrying...");
                            Thread.sleep(1000);
                        }
                    }

                    adjustYear(driver, currentDate.getYear());
                    adjustMonth(driver, currentDate.getMonthValue());

                    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                            driver.findElement(By.xpath("//td[@data-date='" + dateStr + "']")).click();
                            Thread.sleep(3000);
                            break;
                        } catch (Exception e) {
                            if (attempt == MAX_RETRIES) throw e;
                            System.out.println("Failed to click date. Retrying...");
                            Thread.sleep(1000);
                        }
                    }

                    List<WebElement> elementList = driver.findElements(By.xpath("//a[contains(@class, 'match-row__score')]"));

                    for (WebElement element : elementList) {
                        try {
                            String href = element.getAttribute("href");
                            if (href != null && !href.contains("basketbol")) {
                                links.add(href);
                                writer.write(href + System.lineSeparator());
                                writer.flush();
                            }
                        } catch (Exception ex) {
                            System.out.println("Error processing element on " + dateStr + ": " + ex.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to process date " + dateStr + ": " + e.getMessage());
                }

                currentDate = currentDate.plusDays(1);
                Thread.sleep(1000);
            }
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void adjustYear(WebDriver driver, int targetYear) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                while (true) {
                    WebElement yearElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__value']"));
                    int currentYear = Integer.parseInt(yearElement.getText());
                    if (currentYear == targetYear) {
                        break;
                    } else if (currentYear > targetYear) {
                        driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__nav widget-datepicker__nav--previous']")).click();
                    } else {
                        driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__nav widget-datepicker__nav--next']")).click();
                    }
                    Thread.sleep(500);
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                System.out.println("Failed to adjust year. Retrying...");
                Thread.sleep(1000);
            }
        }
    }

    private static void adjustMonth(WebDriver driver, int targetMonth) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                while (true) {
                    WebElement monthElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__value']"));
                    int currentMonth = convertMonthNameToNumber(monthElement.getText());
                    if (currentMonth == targetMonth) {
                        break;
                    } else if (currentMonth > targetMonth) {
                        driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__nav widget-datepicker__nav--previous']")).click();
                    } else {
                        driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__nav widget-datepicker__nav--next']")).click();
                    }
                    Thread.sleep(500);
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                System.out.println("Failed to adjust month. Retrying...");
                Thread.sleep(1000);
            }
        }
    }

    private static int convertMonthNameToNumber(String monthName) {
        switch (monthName.toLowerCase()) {
            case "oca":
                return 1;
            case "şub":
                return 2;
            case "mar":
                return 3;
            case "nis":
                return 4;
            case "may":
                return 5;
            case "haz":
                return 6;
            case "tem":
                return 7;
            case "ağu":
                return 8;
            case "eyl":
                return 9;
            case "eki":
                return 10;
            case "kas":
                return 11;
            case "ara":
                return 12;
            default:
                throw new IllegalArgumentException("Unknown month: " + monthName);
        }
    }

    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--headless=new");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-extensions");
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
        return driver;
    }
    public static void writeMatchesToExcel() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Home-4");
        headerRow.createCell(1).setCellValue("Score-4");
        headerRow.createCell(2).setCellValue("Away-4");
        headerRow.createCell(3).setCellValue("Home-3");
        headerRow.createCell(4).setCellValue("Score-3");
        headerRow.createCell(5).setCellValue("Away-3");
        headerRow.createCell(6).setCellValue("Home-2");
        headerRow.createCell(7).setCellValue("Score-2");
        headerRow.createCell(8).setCellValue("Away-2");
        headerRow.createCell(9).setCellValue("Home-1");
        headerRow.createCell(10).setCellValue("Score-1");
        headerRow.createCell(11).setCellValue("Away-1");
        headerRow.createCell(12).setCellValue("HT / FT");

        int rowNum = 1;
        for (List<String> teamMatches : allData) {
            Row row = sheet.createRow(rowNum++);

            if (teamMatches.size() >= 13) {  // Ensure we have at least 4 matches
                for (int i = 0; i < 13; i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
            } else {
                System.out.println("Warning: Not enough match data for row " + rowNum);
                // Fill available data
                for (int i = 0; i < teamMatches.size(); i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
                // Fill remaining cells with N/A
                for (int i = teamMatches.size(); i < 13; i++) {
                    row.createCell(i).setCellValue("N/A");
                }
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream("mackolik_results.xlsx")) {
            workbook.write(fileOut);
            System.out.println("Excel file created successfully with " + allData.size() + " rows!");
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}