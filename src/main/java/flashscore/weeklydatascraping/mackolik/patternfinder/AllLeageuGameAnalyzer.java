package flashscore.weeklydatascraping.mackolik.patternfinder;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AllLeageuGameAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(AllLeageuGameAnalyzer.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2017;
    private static final int NUM_THREADS = 10;

    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
        System.setProperty("org.slf4j.simpleLogger.log.flashscore.weeklydatascraping.mackolik.patternfinder", "DEBUG");
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
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--log-level=3");
            options.addArguments("--silent");
            options.addArguments("--disable-logging");
            options.addArguments("--disable-dev-tools");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            // Headless mod için özel ayarlar
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);

            WebDriver driver = null;
            try {
                driver = new ChromeDriver(options);

                // 1. Find the current season's pattern
                MatchPattern currentPattern = SeleniumScoreScraperWithDateSort.findCurrentSeasonLastTwoMatches(driver, teamId);
                if (currentPattern == null) {
                    log.info("❌ Takım ID {} için mevcut pattern bulunamadı", teamId);
                    return null;
                }
                System.out.println(currentPattern.toString());

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

        try {
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

            // Tüm task'ların tamamlanmasını bekle
            for (Future<String> future : futures) {
                try {
                    String result = future.get(5, TimeUnit.MINUTES); // Timeout ekle
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

                } catch (TimeoutException e) {
                    log.warn("⏰ Task timeout, atlanıyor...");
                    successCount++;
                } catch (InterruptedException e) {
                    log.warn("⏹️ İşlem kesildi");
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    log.debug("❌ Task hatası: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    successCount++;
                }
            }

            log.info("🎉 İşlem tamamlandı! {} task başarılı, {} takımda eşleşme bulundu",
                    successCount, foundCount);

        } catch (Exception e) {
            log.error("💥 Beklenmeyen hata: {}", e.getMessage(), e);
        } finally {
            // Executor'ı kesinlikle kapat
            log.info("🔴 Executor kapatılıyor...");
            executor.shutdown();

            try {
                // Mevcut task'ların bitmesini bekle
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("⏳ Zaman aşımı, executor zorla kapatılıyor...");
                    executor.shutdownNow();

                    // Zorla kapatma için de bekle
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("💥 Executor kapatılamadı!");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            log.info("✅ Executor başarıyla kapatıldı");
        }

        log.info("👋 Program sonlandırılıyor...");
        System.exit(0); // Tüm thread'leri sonlandır
    }
}