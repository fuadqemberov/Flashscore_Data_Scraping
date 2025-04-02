package flashscore.weeklydatascraping.nowgoal;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class nowgoalV2 {
    private static final int WAIT_TIME = 2000;
    private static final int PAGE_LOAD_TIMEOUT = 10;
    private static final String BASE_URL = "https://football.nowgoal.com";
    private static final String CURRENT_SEASON = "2024-2025";
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors(); // CPU çekirdek sayısı kadar thread
    private static final List<String> LEAGUE_IDS = Arrays.asList("36","37");

    private static final ConcurrentHashMap<String, List<List<String>>> results = new ConcurrentHashMap<>();
    private static final AtomicInteger completedTasks = new AtomicInteger(0);

    static class LeagueInfo {
        final String type;
        final String baseUrlSegment;
        final String currentSeasonUrlSegment;
        final boolean needsSeasonInHistoricalPath;

        LeagueInfo(String type, String baseUrlSegment, String currentSeasonUrlSegment, boolean needsSeasonInHistoricalPath) {
            this.type = type;
            this.baseUrlSegment = baseUrlSegment;
            this.currentSeasonUrlSegment = currentSeasonUrlSegment;
            this.needsSeasonInHistoricalPath = needsSeasonInHistoricalPath;
        }

        String getCurrentSeasonBaseUrl(String id) {
            return BASE_URL + baseUrlSegment + currentSeasonUrlSegment + id;
        }

        String getHistoricalSeasonUrl(String id, int year) {
            return BASE_URL + baseUrlSegment + year + "-" + (year + 1) + "/" + id;
        }

        String getCurrentSeasonPageUrl(String pagePath, String id) {
            return BASE_URL + pagePath + currentSeasonUrlSegment + id;
        }

        String getScheduleHref(String id) {
            return baseUrlSegment + currentSeasonUrlSegment + id;
        }

        String getScoreStatsHref(String id) {
            return "/scorestats/" + currentSeasonUrlSegment + id;
        }
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        System.out.println("Starting scraping with " + THREAD_POOL_SIZE + " threads...");

        // Her lig için ayrı bir task oluştur
        for (String leagueId : LEAGUE_IDS) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    WebDriver driver = initializeDriver();
                    try {
                        LeagueInfo leagueInfo = determineLeagueInfo(driver, leagueId, CURRENT_SEASON);
                        if (leagueInfo != null) {
                            List<List<String>> leagueResults = scrapeLeagueData(driver, leagueId, leagueInfo);
                            results.put(leagueId, leagueResults);

                            int completed = completedTasks.incrementAndGet();
                            System.out.printf("Completed league %s (%d/%d)%n",
                                    leagueId, completed, LEAGUE_IDS.size());
                        } else {
                            System.err.println("Skipping league " + leagueId + " - Could not determine league type.");
                        }
                    } finally {
                        driver.quit();
                    }
                } catch (Exception e) {
                    System.err.println("Error processing league " + leagueId + ": " + e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        // Tüm taskların tamamlanmasını bekle
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Thread havuzunu kapat
        executorService.shutdown();

        // Sonuçları birleştir ve Excel'e yaz
        List<List<String>> allResults = combineResults();
        writeMatchesToExcel(allResults);

        long endTime = System.currentTimeMillis();
        System.out.printf("Total execution time: %.2f seconds%n", (endTime - startTime) / 1000.0);
    }

    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chrome\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Başsız modda çalıştır
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
        return driver;
    }

    private static LeagueInfo determineLeagueInfo(WebDriver driver, String leagueId, String currentSeason) {
        String seasonSlug = currentSeason + "/";
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT));

        // 1. Subleague formatını dene
        String subleagueTestUrl = BASE_URL + "/subleague/" + seasonSlug + leagueId;
        String subleagueScheduleHref = "/subleague/" + seasonSlug + leagueId;
        try {
            System.out.println("Checking subleague type for " + leagueId + " at " + subleagueTestUrl);
            driver.get(subleagueTestUrl);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[name='0'][href='" + subleagueScheduleHref + "']")));
            System.out.println("League " + leagueId + " identified as: subleague");
            return new LeagueInfo("subleague", "/subleague/", seasonSlug, true);
        } catch (Exception e) {
            System.err.println("Error checking subleague type for " + leagueId + ": " + e.getMessage());
        }

        // 2. League formatını dene
        String leagueTestUrl = BASE_URL + "/league/" + leagueId;
        String leagueScheduleHref = "/league/" + leagueId;
        try {
            System.out.println("Checking league type for " + leagueId + " at " + leagueTestUrl);
            driver.get(leagueTestUrl);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[name='0'][href='" + leagueScheduleHref + "']")));
            System.out.println("League " + leagueId + " identified as: league");
            return new LeagueInfo("league", "/league/", "", true);
        } catch (Exception e) {
            System.err.println("Error checking league type for " + leagueId + ": " + e.getMessage());
        }

        return null;
    }

    private static List<List<String>> scrapeLeagueData(WebDriver driver, String leagueId, LeagueInfo leagueInfo) {
        List<List<String>> leagueResults = new ArrayList<>();
        try {
            List<String> teamList = getTeamList(driver, leagueId, leagueInfo);
            String week = findWeekNum(driver, leagueId, leagueInfo);

            for (int year = 2023; year > 2019; year--) {
                scrapeYearData(driver, leagueId, year, week, teamList, leagueResults, leagueInfo);
            }
        } catch (Exception e) {
            System.err.println("Error scraping league " + leagueId + ": " + e.getMessage());
        }
        return leagueResults;
    }

    private static List<String> getTeamList(WebDriver driver, String id, LeagueInfo leagueInfo) throws InterruptedException {
        List<String> teamList = new ArrayList<>();
        driver.get(leagueInfo.getCurrentSeasonBaseUrl(id));
        Thread.sleep(WAIT_TIME);

        WebElement element = driver.findElement(By.cssSelector("a[href='" + leagueInfo.getScoreStatsHref(id) + "']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        Thread.sleep(WAIT_TIME);

        List<WebElement> teams = driver.findElements(By.xpath("//td[@align='left']/a"));
        for (WebElement team : teams) {
            String teamName = team.getText().replaceAll("\\d+", "").trim().replaceAll("\\s+$", "");
            teamList.add(teamName);
        }

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
        clickScheduleLink(wait, id, leagueInfo);

        return teamList;
    }

    private static void clickScheduleLink(WebDriverWait wait, String id, LeagueInfo leagueInfo) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("a[name='0'][href='" + leagueInfo.getScheduleHref(id) + "']")
            )).click();
        } catch (Exception e) {
            wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("a[name='0'][href='" + leagueInfo.getScheduleHref(id) + "']")
            )).click();
        }
    }

    private static void scrapeYearData(WebDriver driver, String id, int year,
                                       String week, List<String> teamList,
                                       List<List<String>> results, LeagueInfo leagueInfo) throws InterruptedException {
        String link = leagueInfo.getHistoricalSeasonUrl(id, year);
        driver.get(link);
        Thread.sleep(WAIT_TIME);

        try {
            driver.findElement(By.xpath("(//td[@class='lsm2' or @class='lsm1'])[" + week + "]")).click();
            Thread.sleep(WAIT_TIME);

            for (String teamName : teamList) {
                WebElement teamRow;
                try {
                    teamRow = driver.findElement(
                            By.xpath("//a[contains(text(), '" + teamName + "')]/ancestor::tr")
                    );
                }
                catch (Exception e) {
                    continue;
                }
                List<String> matchData = Arrays.asList(
                        String.valueOf(year),
                        teamName,
                        getHalfScore(teamRow) + " / " + getMatchScore(teamRow),
                        week,
                        getFullTeamNames(teamRow)
                );
                results.add(matchData);

            }
        } catch (Exception e) {
            System.err.println("Error scraping data for year " + year + ": " + e.getMessage());
        }
    }

    private static String getMatchScore(WebElement element) {
        try {
            return element.findElement(By.cssSelector("div.point b.redf strong")).getText();
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static String getHalfScore(WebElement element) {
        try {
            return element.findElement(By.cssSelector("td:last-child strong")).getText();
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static String getFullTeamNames(WebElement element) {
        String homeTeam = element.findElement(By.cssSelector("td:nth-child(3) a")).getText();
        String awayTeam = element.findElement(By.cssSelector("td:nth-child(5) a")).getText();
        return homeTeam + " - " + awayTeam;
    }

    private static String findWeekNum(WebDriver driver, String id, LeagueInfo leagueInfo) throws InterruptedException {
        driver.get(leagueInfo.getCurrentSeasonBaseUrl(id));
        Thread.sleep(WAIT_TIME);
        return driver.findElement(By.cssSelector("td.lsm2[style*='background-color']")).getText();
    }

    private static List<List<String>> combineResults() {
        List<List<String>> allResults = new ArrayList<>();
        results.values().forEach(allResults::addAll);
        return allResults;
    }

    private static void writeMatchesToExcel(List<List<String>> results) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Match Results");
            createHeaderRow(sheet);
            fillDataRows(sheet, results);

            try (FileOutputStream fileOut = new FileOutputStream("week.xlsx")) {
                workbook.write(fileOut);
                System.out.println("Excel file created successfully!");
            }
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Year", "Main Team", "Result ht / ft", "Week", "Teams"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private static void fillDataRows(Sheet sheet, List<List<String>> results) {
        int rowNum = 1;
        for (List<String> match : results) {
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < match.size(); i++) {
                row.createCell(i).setCellValue(match.get(i));
            }
        }
    }
}