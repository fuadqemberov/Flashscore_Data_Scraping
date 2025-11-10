package flashscore.weeklydatascraping.mackolik.patternfinder;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;

public class AllLeageuGameAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(AllLeageuGameAnalyzer.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2017;
    private static final int NUM_THREADS = 10;

    static {
        // TÜM Selenium loglarını COMPLETELY KAPAT
        System.setProperty("webdriver.chrome.silentOutput", "true");
        System.setProperty("webdriver.http.factory", "jdk-http-client");
        System.setProperty("webdriver.chrome.verboseLogging", "false");

        // Java util logging'i komple kapat
        java.util.logging.Logger.getLogger("").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache").setLevel(Level.OFF);
    }

    private static class TeamProcessorTask implements Callable<String> {
        private final int teamId;

        public TeamProcessorTask(int teamId) {
            this.teamId = teamId;
        }

        @Override
        public String call() throws Exception {
            log.info("🔍 Takım ID işleniyor: {}", teamId);

            ChromeOptions options = new ChromeOptions();
//            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--log-level=3");
            options.addArguments("--silent");
            options.addArguments("--disable-logging");
            options.addArguments("--disable-dev-tools");

            // TÜM logging'i devre dışı bırak
            LoggingPreferences logs = new LoggingPreferences();
            logs.enable(LogType.BROWSER, Level.OFF);
            logs.enable(LogType.DRIVER, Level.OFF);
            logs.enable(LogType.PERFORMANCE, Level.OFF);
            logs.enable(LogType.CLIENT, Level.OFF);
            logs.enable(LogType.SERVER, Level.OFF);
            options.setCapability("goog:loggingPrefs", logs);

            // Ek capability'ler
            options.setCapability("goog:chromeOptions", new java.util.HashMap<String, Object>() {{
                put("excludeSwitches", new String[]{"enable-logging"});
            }});

            WebDriver driver = null;
            try {
                driver = new ChromeDriver(options);

                // 1. Find the current season's pattern
                MatchPattern currentPattern = flashscore.weeklydatascraping.mackolik.patternfinder.SeleniumScoreScraperWithDateSort.findCurrentSeasonLastTwoMatches(driver, teamId);
                if (currentPattern == null) {
                    log.info("❌ Takım ID {} için mevcut pattern bulunamadı", teamId);
                    return null;
                }

                StringBuilder teamResults = new StringBuilder();
                boolean foundMatchesForTeam = false;

                // 2. Search past seasons for the pattern
                for (int year = START_YEAR; year >= END_YEAR; year--) {
                    String season = year + "/" + (year + 1);

                    try {
                        List<MatchResult> seasonMatches = SeleniumScoreScraperWithDateSort.findScorePattern(driver, currentPattern, season, teamId);
                        if (!seasonMatches.isEmpty()) {
                            foundMatchesForTeam = true;
                            teamResults.append("\n").append(year).append(" Sezonu Analizi:\n------------------------\n");
                            for (MatchResult match : seasonMatches) {
                                teamResults.append(match).append("\n\n");
                            }
                            if (teamResults.length() > 1) {
                                teamResults.setLength(teamResults.length() - 2);
                            }
                        }
                    } catch (Exception e) {
                        // Sessizce devam et
                    }
                }

                // 3. Format output if matches were found
                if (foundMatchesForTeam) {
                    String header = String.format(
                            "=== Team ID: %d (%s) ===\nAranan skor paterni:\n%s\n",
                            teamId, currentPattern.teamName, currentPattern
                    );
                    log.info("✅ Takım ID {} için analiz tamamlandı - Eşleşme BULUNDU", teamId);
                    return header + teamResults.toString();
                } else {
                    log.info("ℹ️  Takım ID {} için eşleşme bulunamadı", teamId);
                    return null;
                }
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("maç skoru bulunamadı")) {
                    log.info("⏭️  Takım {} atlanıyor - yeterli maç skoru yok", teamId);
                }
                return null;
            } catch (Exception e) {
                return null;
            } finally {
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception e) {
                        // Görmezden gel
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        // TÜM system property'leri log kapatmak için
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "false");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "false");

        // TÜM diğer logları kapat
        System.setProperty("org.slf4j.simpleLogger.log.org.apache", "OFF");
        System.setProperty("org.slf4j.simpleLogger.log.org.openqa", "OFF");
        System.setProperty("org.slf4j.simpleLogger.log.com.gargoylesoftware", "OFF");
        System.setProperty("org.slf4j.simpleLogger.log.io.github.bonigarcia", "OFF");
        System.setProperty("org.slf4j.simpleLogger.log.io.netty", "OFF");
        System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "OFF");

        log.info("🚀 Çoklu thread analiz başlatılıyor...");

        List<String> teamIds;
        try {
            teamIds = TeamIdFinder.readIdsFromFile();
            log.info("📋 {} takım ID yüklendi", teamIds.size());
        } catch (IOException e) {
            log.error("❌ Takım ID dosyası okunamadı");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<String>> futures = new ArrayList<>();

        for (String idStr : teamIds) {
            try {
                int teamId = Integer.parseInt(idStr.trim());
                Callable<String> task = new TeamProcessorTask(teamId);
                futures.add(executor.submit(task));
            } catch (NumberFormatException e) {
                // Görmezden gel
            }
        }

        log.info("📤 {} task thread pool'a gönderildi", futures.size());

        int successCount = 0;
        int foundCount = 0;

        for (Future<String> future : futures) {
            try {
                String result = future.get();
                if (result != null && !result.isEmpty()) {
                    System.out.println(result);
                    System.out.println("====================================\n");
                    foundCount++;
                }
                successCount++;

                if (successCount % 10 == 0) {
                    log.info("📊 İlerleme: {}/{} tamamlandı, {} eşleşme bulundu",
                            successCount, futures.size(), foundCount);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // Görmezden gel
            }
        }

        log.info("🎉 İşlem tamamlandı! {} task başarılı, {} takımda eşleşme bulundu",
                successCount, foundCount);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("👋 Program sonlandırılıyor...");
    }
}