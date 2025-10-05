package flashscore.weeklydatascraping.mackolik.patternfinder.newgen;

import flashscore.weeklydatascraping.mackolik.patternfinder.TeamIdFinder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AdvancedMultiThreadedAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(AdvancedMultiThreadedAnalyzer.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2017;
    private static final int NUM_THREADS = 10;

    // İç içe sınıf olarak yeniden adlandırıldı
    private static class AdvancedTeamProcessorTask implements Callable<String> {
        private final int teamId;
        private final CloseableHttpClient httpClient;

        public AdvancedTeamProcessorTask(int teamId, CloseableHttpClient httpClient) {
            this.teamId = teamId;
            this.httpClient = httpClient;
        }

        @Override
        public String call() {
            log.info("Processing Team ID: {}", teamId);
            try {
                // 1. Yeni metoda göre son maç desenini bul
                AdvancedMatchPattern currentPattern = AdvancedScoreScraper.findLastMatchPattern(httpClient, teamId);
                if (currentPattern == null) {
                    log.warn("Could not find current pattern for team ID: {}", teamId);
                    return null;
                }

                StringBuilder teamResults = new StringBuilder();
                boolean foundMatchesForTeam = false;

                // 2. Geçmiş sezonlarda yeni deseni ara
                for (int year = START_YEAR; year >= END_YEAR; year--) {
                    String season = year + "/" + (year + 1);
                    try {
                        List<AdvancedMatchResult> seasonMatches = AdvancedScoreScraper.findHistoricalMatchPatterns(httpClient, currentPattern, season, teamId);

                        if (!seasonMatches.isEmpty()) {
                            foundMatchesForTeam = true;
                            teamResults.append("\n").append(year).append(" Sezonu Analizi:\n------------------------\n");
                            for (AdvancedMatchResult match : seasonMatches) {
                                teamResults.append(match).append("\n\n");
                            }
                        }
                    } catch (IOException e) {
                        log.error("IOException while searching team {} season {}: {}", teamId, season, e.getMessage());
                    }
                }

                // 3. Sonuçları formatla
                if (foundMatchesForTeam) {
                    String header = String.format("=== Takım ID: %d (%s) ===\nAranan Maç Paterni:\n%s\n",
                            teamId, currentPattern.teamName, currentPattern.toString());
                    return header + teamResults.toString();
                } else {
                    log.info("No matching patterns found for team ID: {}", teamId);
                    return null;
                }

            } catch (RuntimeException e) {
                log.error("Runtime error processing team ID {}: {}", teamId, e.getMessage());
                return null;
            } catch (Exception e) {
                log.error("General error processing team ID {}: {}", teamId, e.getMessage(), e);
                return null;
            }
        }
    }

    public static void main(String[] args) {
        List<String> teamIds;
        try {
            // TeamIdFinder yerine basit bir dosya okuma metodu
            teamIds = readTeamIdsFromFile("team_ids.txt");
            log.info("Loaded {} team IDs.", teamIds.size());
        } catch (IOException e) {
            log.error("Failed to read team IDs file: {}", e.getMessage(), e);
            return;
        }

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(NUM_THREADS + 5);
        cm.setDefaultMaxPerRoute(NUM_THREADS);

        try (CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build()) {
            ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
            List<Future<String>> futures = new ArrayList<>();

            for (String idStr : teamIds) {
                try {
                    int teamId = Integer.parseInt(idStr.trim());
                    Callable<String> task = new AdvancedTeamProcessorTask(teamId, httpClient);
                    futures.add(executor.submit(task));
                } catch (NumberFormatException e) {
                    log.warn("Skipping invalid team ID: {}", idStr);
                }
            }

            int foundCount = 0;
            for (Future<String> future : futures) {
                try {
                    String result = future.get();
                    if (result != null && !result.isEmpty()) {
                        System.out.println(result);
                        System.out.println("====================================\n");
                        foundCount++;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Task execution failed: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                }
            }

            log.info("Processing complete. {} teams had matching patterns.", foundCount);
            executor.shutdown();
        } catch (IOException e) {
            log.error("Error closing HttpClient: {}", e.getMessage(), e);
        }
    }

    // TeamIdFinder bağımlılığını kaldırmak için basit bir yardımcı method
    public static List<String> readTeamIdsFromFile(String filePath) throws IOException {
        return TeamIdFinder.readIdsFromFile();
    }
}
