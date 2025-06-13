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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties; // Added import
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class nowgaolFinal {
    private static final Logger logger = LoggerFactory.getLogger(nowgaolFinal.class); // Added logger
    private static final String CONFIG_FILE = "nowgoal_config.properties"; // Added config file name
    private static Properties properties = new Properties(); // Added properties object

    // Commented out old static finals, will be replaced by config values
    // private static final int WAIT_TIME = 2000;
    // private static final int PAGE_LOAD_TIMEOUT = 10;
    // private static final String BASE_URL = "https://football.nowgoal.com";
    // private static final String CURRENT_SEASON = "2024-2025";
    // private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    // private static final List<String> LEAGUE_IDS = Arrays.asList(...);

    // New static fields for configuration values
    private static int WAIT_TIME_MS;
    private static int PAGE_LOAD_TIMEOUT_SECONDS;
    private static String CURRENT_SEASON_CONFIG;
    private static int THREAD_POOL_SIZE_CONFIG;
    private static List<String> LEAGUE_IDS_CONFIG;
    private static String WEBDRIVER_PATH_CONFIG; // Will be used by initializeDriver
    private static String OUTPUT_EXCEL_FILE_CONFIG; // Will be used by writeMatchesToExcel
    private static int START_YEAR_FOR_HISTORY_CONFIG; // Will be used by scrapeLeagueData
    private static String NOWGOAL_BASE_URL_CONFIG; // Will be used by LeagueInfo and determineLeagueInfo

    private static final ConcurrentHashMap<String, List<List<String>>> results = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, String>> upcomingMatchesByTeam = new ConcurrentHashMap<>();
    private static final AtomicInteger completedTasks = new AtomicInteger(0);

    static class LeagueInfo {
        final String type;
        final String baseUrlSegment;
        final String currentSeasonUrlSegment;
        final boolean needsSeasonInHistoricalPath;
        final String nowgoalBaseUrl; // Added field

        LeagueInfo(String type, String baseUrlSegment, String currentSeasonUrlSegment, boolean needsSeasonInHistoricalPath, String nowgoalBaseUrl) { // Added param
            this.type = type;
            this.baseUrlSegment = baseUrlSegment;
            this.currentSeasonUrlSegment = currentSeasonUrlSegment;
            this.needsSeasonInHistoricalPath = needsSeasonInHistoricalPath;
            this.nowgoalBaseUrl = nowgoalBaseUrl; // Set field
        }

        String getCurrentSeasonBaseUrl(String id) {
            // return BASE_URL + baseUrlSegment + currentSeasonUrlSegment + id; // Old
            return nowgoalBaseUrl + baseUrlSegment + currentSeasonUrlSegment + id; // Use field
        }

        String getHistoricalSeasonUrl(String id, int year) {
            // return BASE_URL + baseUrlSegment + year + "-" + (year + 1) + "/" + id; // Old
            return nowgoalBaseUrl + baseUrlSegment + year + "-" + (year + 1) + "/" + id; // Use field
        }

        String getCurrentSeasonPageUrl(String pagePath, String id) {
            // return BASE_URL + pagePath + currentSeasonUrlSegment + id; // Old
            return nowgoalBaseUrl + pagePath + currentSeasonUrlSegment + id; // Use field
        }

        String getScheduleHref(String id) {
            return baseUrlSegment + currentSeasonUrlSegment + id;
        }

        String getScoreStatsHref(String id) {
            return "/scorestats/" + currentSeasonUrlSegment + id;
        }
    }

    public static void main(String[] args) {
        // Load properties from config file
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            properties.load(fis);
            logger.info("{} configuration file loaded successfully.", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Error loading {} configuration file. Default values might be used or application may terminate.", CONFIG_FILE, e);
            // Depending on criticality, you might want to throw a RuntimeException here
        }

        // Assign configuration values to static fields
        WAIT_TIME_MS = Integer.parseInt(properties.getProperty("wait_time_ms", "2000"));
        PAGE_LOAD_TIMEOUT_SECONDS = Integer.parseInt(properties.getProperty("page_load_timeout_seconds", "10"));
        CURRENT_SEASON_CONFIG = properties.getProperty("current_season", "2024-2025");
        WEBDRIVER_PATH_CONFIG = properties.getProperty("webdriver_path", "src\\chrome\\chromedriver.exe");
        OUTPUT_EXCEL_FILE_CONFIG = properties.getProperty("output_excel_file", "week.xlsx");
        START_YEAR_FOR_HISTORY_CONFIG = Integer.parseInt(properties.getProperty("start_year_for_history", "2023"));
        NOWGOAL_BASE_URL_CONFIG = properties.getProperty("nowgoal_base_url", "https://football.nowgoal.com");

        String leagueIdsStr = properties.getProperty("league_ids", "36"); // Default to a single league if not specified
        LEAGUE_IDS_CONFIG = Arrays.asList(leagueIdsStr.split(",\\s*"));

        int configuredPoolSize = Integer.parseInt(properties.getProperty("thread_pool_size", "0"));
        if (configuredPoolSize <= 0) {
            THREAD_POOL_SIZE_CONFIG = Runtime.getRuntime().availableProcessors();
        } else {
            THREAD_POOL_SIZE_CONFIG = configuredPoolSize;
        }

        long startTime = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE_CONFIG); // Use configured pool size
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // System.out.println("Starting scraping with " + THREAD_POOL_SIZE + " threads..."); // Old
        logger.info("Starting scraping with {} threads for {} leagues...", THREAD_POOL_SIZE_CONFIG, LEAGUE_IDS_CONFIG.size()); // New

        // Her lig için ayrı bir task oluştur
        for (String leagueId : LEAGUE_IDS_CONFIG) { // Use configured league IDs
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                WebDriver driver = null; // Initialize driver to null for finally block
                try {
                    driver = initializeDriver(); // initializeDriver will use static config fields
                    try {
                        // Pass CURRENT_SEASON_CONFIG and NOWGOAL_BASE_URL_CONFIG to determineLeagueInfo
                        LeagueInfo leagueInfo = determineLeagueInfo(driver, leagueId, CURRENT_SEASON_CONFIG, NOWGOAL_BASE_URL_CONFIG); // Pass NOWGOAL_BASE_URL_CONFIG (already done in previous step, ensuring it's correct)
                        if (leagueInfo != null) {
                            // Önce takım listesini ve hafta numarasını al
                            List<String> teamList = getTeamList(driver, leagueId, leagueInfo);
                            String week = findWeekNum(driver, leagueId, leagueInfo);

                            // Oynanacak maçları topla
                            Map<String, String> upcomingMatches = getUpcomingMatches(driver, leagueId, leagueInfo);
                            upcomingMatchesByTeam.put(leagueId, upcomingMatches);

                            // Geçmiş maç verilerini topla
                            List<List<String>> leagueResults = scrapeLeagueData(driver, leagueId, teamList, week, upcomingMatches, leagueInfo, START_YEAR_FOR_HISTORY_CONFIG); // Pass START_YEAR_FOR_HISTORY_CONFIG
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
        writeMatchesToExcel(allResults, OUTPUT_EXCEL_FILE_CONFIG); // Pass configured output file name

        long endTime = System.currentTimeMillis();
        // System.out.printf("Total execution time: %.2f seconds%n", (endTime - startTime) / 1000.0); // Use logger
        logger.info("Total execution time: {} seconds", String.format("%.2f", (endTime - startTime) / 1000.0)); // Use logger
    }

    static WebDriver initializeDriver() {
        // System.setProperty("webdriver.chrome.driver", "src\\chrome\\chromedriver.exe"); // Old
        System.setProperty("webdriver.chrome.driver", WEBDRIVER_PATH_CONFIG); // Use config
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        // driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT)); // Old
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS)); // Use config
        return driver;
    }

    // determineLeagueInfo updated to accept nowgoalBaseUrl and use logger
    private static LeagueInfo determineLeagueInfo(WebDriver driver, String leagueId, String currentSeason, String nowgoalBaseUrl) {
        String seasonSlug = currentSeason + "/";
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS)); // Use config

        // 1. Subleague formatını dene
        String subleagueTestUrl = nowgoalBaseUrl + "/subleague/" + seasonSlug + leagueId; // Use param
        String subleagueScheduleHref = "/subleague/" + seasonSlug + leagueId;
        try {
            logger.debug("Checking subleague type for {} at {}", leagueId, subleagueTestUrl);
            driver.get(subleagueTestUrl);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[name='0'][href='" + subleagueScheduleHref + "']")));
            logger.info("League {} identified as: subleague", leagueId);
            return new LeagueInfo("subleague", "/subleague/", seasonSlug, true, nowgoalBaseUrl); // Pass nowgoalBaseUrl
        } catch (Exception e) {
            logger.warn("League {} check subleague failed. Trying league type. Error: {}", leagueId, e.getMessage());
        }

        // 2. League formatını dene
        String leagueTestUrl = nowgoalBaseUrl + "/league/" + leagueId; // Use param
        String leagueScheduleHref = "/league/" + leagueId;
        try {
            logger.debug("Checking league type for {} at {}", leagueId, leagueTestUrl);
            driver.get(leagueTestUrl);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[name='0'][href='" + leagueScheduleHref + "']")));
            logger.info("League {} identified as: league", leagueId);
            return new LeagueInfo("league", "/league/", "", true, nowgoalBaseUrl); // Pass nowgoalBaseUrl
        } catch (Exception e) {
            logger.error("Error checking league type for {}: {}", leagueId, e.getMessage(), e);
        }
        logger.error("Lig {} için uygun lig tipi belirlenemedi. (determineLeagueInfo)", leagueId);
        return null;
    }

    // getUpcomingMatches updated for better error handling and local map
    private static Map<String, String> getUpcomingMatches(WebDriver driver, String leagueId, LeagueInfo leagueInfo) {
        Map<String, String> upcomingMatchesMap = new HashMap<>(); // Use local map
        try {
            driver.get(leagueInfo.getCurrentSeasonBaseUrl(leagueId));
            Thread.sleep(WAIT_TIME_MS);

            List<WebElement> matchRows = driver.findElements(By.xpath("//tr[contains(@id, '') and .//div[contains(@class, 'point') and text()='-']]"));
            logger.debug("Lig {} için {} potansiyel yaklaşan maç satırı bulundu.", leagueId, matchRows.size());

            for (WebElement row : matchRows) {
                try {
                    String homeTeam = row.findElement(By.xpath(".//td[3]/a")).getText();
                    String awayTeam = row.findElement(By.xpath(".//td[5]/a")).getText();
                    if (homeTeam.isEmpty() || awayTeam.isEmpty()) { // Skip if team names are empty
                        logger.warn("Lig {} için yaklaşan maçta boş takım adı bulundu. Satır: {}", leagueId, row.getText());
                        continue;
                    }
                    String matchup = homeTeam + " - " + awayTeam;

                    upcomingMatchesMap.put(homeTeam, matchup); // Add to local map
                    upcomingMatchesMap.put(awayTeam, matchup); // Add to local map

                    logger.debug("Yaklaşan maç eklendi (Lig: {}): {}", leagueId, matchup);
                } catch (org.openqa.selenium.NoSuchElementException e) { // More specific exception
                    logger.warn("Lig {} için yaklaşan maç satırı işlenirken element bulunamadı. Satır atlanıyor. Hata: {}", leagueId, e.getMessage());
                } catch (Exception e) { // Catch other potential exceptions for a single row
                    logger.error("Lig {} için yaklaşan maç satırı işlenirken beklenmedik hata. Satır atlanıyor.", leagueId, e);
                }
            }
        } catch (InterruptedException e) {
            logger.warn("getUpcomingMatches for league {} was interrupted.", leagueId, e);
            Thread.currentThread().interrupt();
        } catch (org.openqa.selenium.TimeoutException e) {
            logger.error("getUpcomingMatches for league {} sayfa yüklenirken zaman aşımına uğradı: {}", leagueId, e.getMessage());
        } catch (org.openqa.selenium.WebDriverException e) {
            logger.error("getUpcomingMatches for league {} sırasında WebDriver hatası: {}", leagueId, e.getMessage());
        } catch (Exception e) { // Catch any other top-level exceptions
            logger.error("Error fetching upcoming matches for league {}: {}", leagueId, e.getMessage(), e);
        }
        return upcomingMatchesMap; // Return local map
    }

    private static List<List<String>> scrapeLeagueData(WebDriver driver, String leagueId,
                                                       List<String> teamList, String week,
                                                       Map<String, String> upcomingMatches, LeagueInfo leagueInfo, int startYearForHistory) { // Added startYearForHistory
        List<List<String>> leagueResults = new ArrayList<>();
        try {
            for (int year = startYearForHistory; year > 2019; year--) { // Use startYearForHistory parameter
                scrapeYearData(driver, leagueId, year, week, teamList, leagueResults, upcomingMatches, leagueInfo, WAIT_TIME_MS); // Pass WAIT_TIME_MS
            }
        } catch (Exception e) {
            logger.error("Error scraping league {}: {}", leagueId, e.getMessage(), e); // Use logger
        }
        return leagueResults;
    }

    private static List<String> getTeamList(WebDriver driver, String id, LeagueInfo leagueInfo) { // Removed throws InterruptedException
        List<String> teamList = new ArrayList<>();
        try {
            driver.get(leagueInfo.getCurrentSeasonBaseUrl(id));
            Thread.sleep(WAIT_TIME_MS);

            WebElement element = driver.findElement(By.cssSelector("a[href='" + leagueInfo.getScoreStatsHref(id) + "']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            Thread.sleep(WAIT_TIME_MS);

            List<WebElement> teams = driver.findElements(By.xpath("//td[@align='left']/a"));
            for (WebElement team : teams) {
                try {
                    String teamName = team.getText().replaceAll("\\d+", "").trim().replaceAll("\\s+$", "");
                    if (!teamName.isEmpty()) {
                        teamList.add(teamName);
                        logger.trace("Lig {} için takım eklendi: {}", id, teamName);
                    }
                } catch (Exception e) {
                    logger.warn("Lig {} için takım adı alınırken hata. Takım atlanıyor: {}. Element: {}", id, e.getMessage(), team.toString());
                }
            }

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
            clickScheduleLink(wait, id, leagueInfo); // This might also throw exceptions

        } catch (TimeoutException e) {
            logger.error("Lig {} için takım listesi alınırken sayfa yükleme zaman aşımı: {}. URL: {}", id, e.getMessage(), driver.getCurrentUrl());
        } catch (org.openqa.selenium.NoSuchElementException e) { // More specific
            logger.error("Lig {} için takım listesi alınırken element bulunamadı: {}. URL: {}", id, e.getMessage(), driver.getCurrentUrl());
        } catch (InterruptedException e) {
            logger.warn("Lig {} için takım listesi alınırken thread kesintiye uğradı.", id, e);
            Thread.currentThread().interrupt();
        } catch (WebDriverException e) { // General WebDriver exception
            logger.error("Lig {} için takım listesi alınırken WebDriver hatası: {}. URL: {}", id, e.getMessage(), driver.getCurrentUrl());
        } catch (Exception e) { // Catch-all for any other unexpected exceptions
            logger.error("Lig {} için takım listesi alınırken beklenmedik genel bir hata oluştu: {}", id, e.getMessage(), e);
        }
        logger.debug("Lig {} için {} takım bulundu.", id, teamList.size());
        return teamList;
    }

    private static void clickScheduleLink(WebDriverWait wait, String id, LeagueInfo leagueInfo) { // This can also throw
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
                                       List<List<String>> results,
                                       Map<String, String> upcomingMatches, LeagueInfo leagueInfo, int waitTimeMs) { // Removed throws InterruptedException
        String link = leagueInfo.getHistoricalSeasonUrl(id, year);
        try {
            driver.get(link);
            Thread.sleep(waitTimeMs);

            if (week == null || week.trim().isEmpty() || "N/A".equals(week)) {
                logger.warn("Lig {}, Yıl {} için geçersiz veya bulunamayan hafta '{}'. Bu yıl atlanıyor.", id, year, week);
                return; // Skip this year if week is invalid
            }

            // Click on the specific week tab
            try {
                driver.findElement(By.xpath("(//td[@class='lsm2' or @class='lsm1'])[" + week + "]")).click();
                Thread.sleep(waitTimeMs);
            } catch (org.openqa.selenium.NoSuchElementException e) {
                logger.warn("Lig {}, Yıl {} için '{}'. hafta sekmelerden seçilemedi. Sayfa olduğu gibi işlenecek. Hata: {}", id, year, week, e.getMessage());
                // Continue processing with the current page content if week tab is not found/clickable
            }


            for (String teamName : teamList) {
                WebElement teamRow = null; // Initialize to null
                try {
                    teamRow = driver.findElement(
                            By.xpath("//a[contains(text(), '" + teamName + "')]/ancestor::tr")
                    );
                } catch (org.openqa.selenium.NoSuchElementException e) {
                    logger.warn("Lig {}, Yıl {}, Takım '{}' satırı bulunamadı. Bu takım atlanıyor.", id, year, teamName);
                    continue; // Skip this team for this year if its row isn't found
                }

                String upcomingMatch = upcomingMatches.getOrDefault(teamName, "");

                List<String> matchData = Arrays.asList(
                        String.valueOf(year),
                        teamName,
                        getHalfScore(teamRow) + " / " + getMatchScore(teamRow),
                        week,
                        getFullTeamNames(teamRow),
                        upcomingMatch
                );
                results.add(matchData);
            }
        } catch (Exception e) {
            logger.warn("Error scraping data for league {}, year {}: {}", id, year, e.getMessage()); // Use logger
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

    // findWeekNum will be updated later to use WAIT_TIME_MS
    private static String findWeekNum(WebDriver driver, String id, LeagueInfo leagueInfo) throws InterruptedException {
        driver.get(leagueInfo.getCurrentSeasonBaseUrl(id)); // LeagueInfo now uses NOWGOAL_BASE_URL_CONFIG
        Thread.sleep(WAIT_TIME_MS); // Use configured WAIT_TIME_MS
        return driver.findElement(By.cssSelector("td.lsm2[style*='background-color']")).getText();
    }

    private static List<List<String>> combineResults() {
        List<List<String>> allResults = new ArrayList<>();
        results.values().forEach(allResults::addAll);
        return allResults;
    }

    // Updated to take outputExcelFile parameter and use logger
    private static void writeMatchesToExcel(List<List<String>> results, String outputExcelFile) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Match Results");
            createHeaderRow(sheet);
            fillDataRows(sheet, results);

            try (FileOutputStream fileOut = new FileOutputStream(outputExcelFile)) { // Use parameter
                workbook.write(fileOut);
                logger.info("Excel file created successfully: {}", outputExcelFile); // Use logger with param
            }
        } catch (IOException e) {
            logger.error("Error while writing to Excel file {}: {}", outputExcelFile, e.getMessage(), e); // Use logger with param
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Year", "Main Team", "Result ht / ft", "Week", "Teams", "Upcoming Match"};
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