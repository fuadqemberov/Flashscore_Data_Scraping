package flashscore.weeklydatascraping.nowgoal_week_finder;

import io.github.bonigarcia.wdm.WebDriverManager;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class nowGoalFinalVersion {
    private static final Random RANDOM = new Random();
    private static final int PAGE_LOAD_TIMEOUT = 20;
    private static final String BASE_URL = "https://football.nowgoal25.com";
    private static final String CURRENT_SEASON = "2025";
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final List<String> LEAGUE_IDS = Arrays.asList(
            "10", "26", "22", "122", "15", "212", "127", "60", "1292", "166", "214"
    );

    private static final ConcurrentHashMap<String, List<List<String>>> results = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, String>> upcomingMatchesByTeam = new ConcurrentHashMap<>();
    private static final AtomicInteger completedTasks = new AtomicInteger(0);

    static class LeagueInfo {
        final String type;
        final String baseUrlSegment;
        final String currentSeasonUrlSegment;
        final boolean usesSingleYearSeason;

        LeagueInfo(String type, String baseUrlSegment, String currentSeasonUrlSegment, boolean usesSingleYearSeason) {
            this.type = type;
            this.baseUrlSegment = baseUrlSegment;
            this.currentSeasonUrlSegment = currentSeasonUrlSegment;
            this.usesSingleYearSeason = usesSingleYearSeason;
        }

        String getCurrentSeasonBaseUrl(String id) {
            return BASE_URL + baseUrlSegment + currentSeasonUrlSegment + id;
        }

        String getHistoricalSeasonUrl(String id, int year) {
            String historicalSeasonSegment;
            if (this.usesSingleYearSeason) {
                historicalSeasonSegment = year + "/";
            } else {
                historicalSeasonSegment = year + "-" + (year + 1) + "/";
            }
            if (type.equals("league")) {
                return BASE_URL + "/league/" + historicalSeasonSegment + id;
            }
            return BASE_URL + this.baseUrlSegment + historicalSeasonSegment + id;
        }

        String getScheduleHref(String id) {
            return baseUrlSegment + currentSeasonUrlSegment + id;
        }

        String getScoreStatsHref(String id) {
            // currentSeasonUrlSegment zaten doğru formatı içeriyor (2025/ veya 2025-2026/)
            return "/scorestats/" + currentSeasonUrlSegment + id;
        }
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        WebDriverManager.chromedriver().setup();

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        System.out.println("Scraping işlemi " + THREAD_POOL_SIZE + " thread ile " + LEAGUE_IDS.size() + " lig için başlatılıyor.");

        for (String leagueId : LEAGUE_IDS) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                WebDriver driver = initializeDriver();
                try {
                    LeagueInfo leagueInfo = determineLeagueInfo(driver, leagueId, CURRENT_SEASON);
                    if (leagueInfo != null) {
                        List<String> teamList = getTeamList(driver, leagueId, leagueInfo);
                        if (teamList.isEmpty()) {
                            System.err.println("ATLANDI: Lig " + leagueId + " için takım listesi alınamadı.");
                            return;
                        }
                        String week = findWeekNum(driver, leagueId, leagueInfo);
                        Map<String, String> upcomingMatches = getUpcomingMatches(driver, leagueId, leagueInfo);
                        upcomingMatchesByTeam.put(leagueId, upcomingMatches);
                        List<List<String>> leagueResults = scrapeLeagueData(driver, leagueId, teamList, week, upcomingMatches, leagueInfo);
                        results.put(leagueId, leagueResults);
                        int completed = completedTasks.incrementAndGet();
                        System.out.printf("TAMAMLANDI: Lig %s (%d/%d)%n", leagueId, completed, LEAGUE_IDS.size());
                    } else {
                        System.err.println("ATLANDI: Lig " + leagueId + " - Tüm denemelere rağmen lig tipi belirlenemedi.");
                    }
                } catch (Exception e) {
                    System.err.println("KRİTİK HATA: Lig " + leagueId + " işlenirken ana döngüde sorun oluştu: " + e.getMessage());
                } finally {
                    if (driver != null) {
                        driver.quit();
                    }
                }
            }, executorService);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();
        List<List<String>> allResults = combineResults();
        writeMatchesToExcel(allResults);

        long endTime = System.currentTimeMillis();
        System.out.printf("Toplam çalışma süresi: %.2f saniye%n", (endTime - startTime) / 1000.0);
    }

    static WebDriver initializeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1080", "--log-level=3");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
        return new ChromeDriver(options);
    }

    private static void randomSleep() {
        try {
            Thread.sleep(1500 + RANDOM.nextInt(2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void handleCookieBanner(WebDriver driver) {
        try {
            WebDriverWait bannerWait = new WebDriverWait(driver, Duration.ofSeconds(5));
            WebElement acceptButton = bannerWait.until(ExpectedConditions.elementToBeClickable(By.id("ez-accept-all")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", acceptButton);
            randomSleep();
        } catch (Exception e) {
            // Banner yoksa devam et.
        }
    }

    // SADECE BU ÖZELLİĞİ EKLEDİM: Sorry..We meet some problems in loading kontrolü
    private static LeagueInfo determineLeagueInfo(WebDriver driver, String leagueId, String currentSeason) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
        By leaguePageIdentifier = By.id("i_data");

        String doubleYearSeasonSegment = currentSeason + "-" + (Integer.parseInt(currentSeason) + 1) + "/";
        String singleYearSeasonSegment = currentSeason + "/";

        // Önce double year formatını test et, sonra single year formatını
        List<Map<String, Object>> testCases = Arrays.asList(
                Map.of("type", "subleague", "baseUrl", BASE_URL + "/subleague/" + doubleYearSeasonSegment + leagueId, "baseUrlSegment", "/subleague/", "seasonSegment", doubleYearSeasonSegment, "isSingleYear", false),
                Map.of("type", "subleague", "baseUrl", BASE_URL + "/subleague/" + singleYearSeasonSegment + leagueId, "baseUrlSegment", "/subleague/", "seasonSegment", singleYearSeasonSegment, "isSingleYear", true),
                Map.of("type", "league", "baseUrl", BASE_URL + "/league/" + doubleYearSeasonSegment + leagueId, "baseUrlSegment", "/league/", "seasonSegment", doubleYearSeasonSegment, "isSingleYear", false),
                Map.of("type", "league", "baseUrl", BASE_URL + "/league/" + singleYearSeasonSegment + leagueId, "baseUrlSegment", "/league/", "seasonSegment", singleYearSeasonSegment, "isSingleYear", true),
                Map.of("type", "league", "baseUrl", BASE_URL + "/league/" + leagueId, "baseUrlSegment", "/league/", "seasonSegment", "", "isSingleYear", false)
        );

        for (Map<String, Object> testCase : testCases) {
            String url = (String) testCase.get("baseUrl");
            try {
                System.out.println("Kontrol ediliyor: " + url);
                driver.get(url);
                handleCookieBanner(driver);
                wait.until(ExpectedConditions.presenceOfElementLocated(leaguePageIdentifier));

                // EKLENDİ: Sorry..We meet some problems in loading kontrolü
                List<WebElement> unusualTips = driver.findElements(By.cssSelector("#i_data .unusual-tips"));
                if (!unusualTips.isEmpty()) {
                    String tipText = unusualTips.get(0).getText();
                    if (tipText.contains("Sorry..We meet some problems in loading")) {
                        System.out.println("Hatalı segment: " + url + " -> Sorry..We meet some problems in loading");
                        continue; // Bu segmenti atla, diğerini dene
                    }
                }

                String type = (String) testCase.get("type");
                String baseUrlSegment = (String) testCase.get("baseUrlSegment");
                String seasonSegment = (String) testCase.get("seasonSegment");
                boolean isSingleYear = (boolean) testCase.get("isSingleYear");

                System.out.println("Tespit edildi: Lig " + leagueId + " -> Tip: " + type + ", Sezon: " + seasonSegment + ", Tek Yıl: " + isSingleYear);
                return new LeagueInfo(type, baseUrlSegment, seasonSegment, isSingleYear);

            } catch (Exception e) {
                System.out.println("Başarısız: " + url);
            }
        }

        return null;
    }

    // --- Diğer kodlar aynen kalacak ---
    private static Map<String, String> getUpcomingMatches(WebDriver driver, String leagueId, LeagueInfo leagueInfo) {
        Map<String, String> upcomingMatchesForTeam = new HashMap<>();
        try {
            driver.get(leagueInfo.getCurrentSeasonBaseUrl(leagueId));
            handleCookieBanner(driver);
            randomSleep();

            List<WebElement> matchRows = driver.findElements(By.xpath("//tr[contains(@id, 'tr_')]//div[contains(@class, 'point') and text()='-']/ancestor::tr"));
            for (WebElement row : matchRows) {
                try {
                    String homeTeam = row.findElement(By.xpath(".//td[3]/a")).getText();
                    String awayTeam = row.findElement(By.xpath(".//td[5]/a")).getText();
                    String matchup = homeTeam + " - " + awayTeam;
                    upcomingMatchesForTeam.put(homeTeam, matchup);
                    upcomingMatchesForTeam.put(awayTeam, matchup);
                } catch (Exception e) {
                    // Satır parse edilemezse atla
                }
            }
        } catch (Exception e) {
            System.err.println("Gelecek maçlar çekilirken hata (Lig " + leagueId + "): " + e.getMessage());
        }
        return upcomingMatchesForTeam;
    }

    private static List<List<String>> scrapeLeagueData(WebDriver driver, String leagueId,
                                                       List<String> teamList, String week,
                                                       Map<String, String> upcomingMatches, LeagueInfo leagueInfo) {
        List<List<String>> leagueResults = new ArrayList<>();
        try {
            for (int year = 2023; year > 2019; year--) {
                scrapeYearData(driver, leagueId, year, week, teamList, leagueResults, upcomingMatches, leagueInfo);
            }
        } catch (Exception e) {
            System.err.println("Lig verileri çekilirken hata (Lig " + leagueId + "): " + e.getMessage());
        }
        return leagueResults;
    }

    private static List<String> getTeamList(WebDriver driver, String id, LeagueInfo leagueInfo) {
        List<String> teamList = new ArrayList<>();
        try {
            String currentUrl = leagueInfo.getCurrentSeasonBaseUrl(id);
            System.out.println("Ana sayfa URL'si: " + currentUrl);
            driver.get(currentUrl);
            handleCookieBanner(driver);
            randomSleep();

            String scoreStatsHref = leagueInfo.getScoreStatsHref(id);
            System.out.println("Aranacak scorestats linki: " + scoreStatsHref);

            // Önce sayfada böyle bir link var mı kontrol et
            List<WebElement> possibleLinks = driver.findElements(By.xpath("//a[contains(@href, '/scorestats/')]"));
            System.out.println("Sayfada bulunan scorestats linkleri:");
            for (WebElement link : possibleLinks) {
                String href = link.getAttribute("href");
                System.out.println(" - " + href);
            }

            WebElement element = driver.findElement(By.cssSelector("a[href='" + scoreStatsHref + "']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            randomSleep();

            List<WebElement> teams = driver.findElements(By.xpath("//td[@align='left']/a"));
            for (WebElement team : teams) {
                String teamName = team.getText().replaceAll("\\d+", "").trim().replaceAll("\\s+$", "");
                if (!teamName.isEmpty()) {
                    teamList.add(teamName);
                }
            }
            clickScheduleLink(driver, id, leagueInfo);
        } catch (Exception e) {
            System.err.println("Takım listesi alınırken hata oluştu (Lig " + id + "): " + e.getMessage());
            e.printStackTrace();
        }
        return teamList;
    }

    private static void clickScheduleLink(WebDriver driver, String id, LeagueInfo leagueInfo) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[name='0']"))).click();
            randomSleep();
        } catch (Exception e) {
            System.err.println("Schedule linkine tıklanırken hata (Lig " + id + "): " + e.getMessage());
        }
    }

    private static void scrapeYearData(WebDriver driver, String id, int year,
                                       String week, List<String> teamList,
                                       List<List<String>> results,
                                       Map<String, String> upcomingMatches, LeagueInfo leagueInfo) {
        String link = leagueInfo.getHistoricalSeasonUrl(id, year);
        driver.get(link);
        handleCookieBanner(driver);
        randomSleep();

        try {
            driver.findElement(By.xpath("(//td[contains(@class,'lsm')][normalize-space(text())='" + week + "'])")).click();
            randomSleep();

            for (String teamName : teamList) {
                WebElement teamRow;
                try {
                    teamRow = driver.findElement(By.xpath("//a[contains(text(), \"" + teamName + "\")]/ancestor::tr"));
                } catch (Exception e) {
                    continue;
                }
                String upcomingMatch = upcomingMatches.getOrDefault(teamName, "N/A");
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
            System.err.println("Veri çekilemedi: Lig " + id + ", Yıl " + year + " (" + link + "): " + e.getMessage());
        }
    }

    private static String getMatchScore(WebElement element) {
        try { return element.findElement(By.cssSelector("div.point b.redf strong")).getText(); } catch (Exception e) { return "N/A"; }
    }
    private static String getHalfScore(WebElement element) {
        try { return element.findElement(By.cssSelector("td:last-child strong")).getText(); } catch (Exception e) { return "N/A"; }
    }
    private static String getFullTeamNames(WebElement element) {
        try {
            String homeTeam = element.findElement(By.cssSelector("td:nth-child(3) a")).getText();
            String awayTeam = element.findElement(By.cssSelector("td:nth-child(5) a")).getText();
            return homeTeam + " - " + awayTeam;
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static String findWeekNum(WebDriver driver, String id, LeagueInfo leagueInfo) {
        try {
            driver.get(leagueInfo.getCurrentSeasonBaseUrl(id));
            handleCookieBanner(driver);
            randomSleep();
            return driver.findElement(By.cssSelector("td.lsm2[style*='background-color']")).getText();
        } catch (Exception e) {
            System.err.println("Hafta numarası bulunamadı (Lig " + id + "): " + e.getMessage());
            return "1"; // Hata durumunda varsayılan bir hafta dön
        }
    }

    private static List<List<String>> combineResults() {
        List<List<String>> allResults = new ArrayList<>();
        results.values().forEach(allResults::addAll);
        return allResults;
    }

    private static void writeMatchesToExcel(List<List<String>> results) {
        if (results.isEmpty()) {
            System.out.println("Çekilecek veri bulunamadı. Excel dosyası oluşturulmadı.");
            return;
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Match Results");
            createHeaderRow(sheet);
            fillDataRows(sheet, results);
            try (FileOutputStream fileOut = new FileOutputStream("week.xlsx")) {
                workbook.write(fileOut);
                System.out.println("Excel dosyası başarıyla oluşturuldu!");
            }
        } catch (IOException e) {
            System.err.println("Excel dosyasına yazılırken hata: " + e.getMessage());
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