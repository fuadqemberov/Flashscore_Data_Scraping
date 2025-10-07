package flashscore.weeklydatascraping.mackolik.patternfinder;// HttpClient v4 importları (HttpScoreScraper ile uyumlu)
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HttpMultiVirtualThreadedAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMultiVirtualThreadedAnalyzer.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2017;
    private static final int CONNECTION_POOL_SIZE = 1000;

    private static class TeamProcessorTask implements Callable<String> {
        private final int teamId;
        private final CloseableHttpClient httpClient;

        public TeamProcessorTask(int teamId, CloseableHttpClient httpClient) {
            this.teamId = teamId;
            this.httpClient = httpClient;
        }

        /**
         * Bu metod, her bir takım için ne kadar süre harcandığını ölçmek ve loglamak için güncellendi.
         * Bu sayede yavaşlığın ağdan mı yoksa programdan mı kaynaklandığı görülebilir.
         */
        @Override
        public String call() {
            long taskStartTime = System.currentTimeMillis(); // Görev başlangıç zamanı
            LOGGER.info("[START] Processing Team ID: {}", teamId);
            try {
                MatchPattern currentPattern = HttpScoreScraper.findCurrentSeasonLastTwoMatches(httpClient, teamId);
                if (currentPattern == null) {
                    LOGGER.warn("[SKIP] Could not find a current pattern for team ID: {}", teamId);
                    return null;
                }

                LOGGER.info("[PATTERN] Found current pattern for team '{}' (ID: {})", currentPattern.teamName, teamId);
                Map<Integer, List<MatchResult>> allFoundMatches = searchPastSeasons(currentPattern);

                if (allFoundMatches.isEmpty()) {
                    LOGGER.info("[NO-MATCH] No matching patterns found for team '{}' (ID: {})", currentPattern.teamName, teamId);
                    return null;
                } else {
                    int totalMatches = allFoundMatches.values().stream().mapToInt(List::size).sum();
                    LOGGER.info("[SUCCESS] Found {} matches for team '{}' (ID: {})", totalMatches, currentPattern.teamName, teamId);
                    return formatResultsString(currentPattern, allFoundMatches);
                }
            } catch (Exception e) {
                LOGGER.error("[ERROR] An unexpected error occurred while processing team ID: {}", teamId, e);
                return null;
            } finally {
                // Bu blok, görev başarılı da olsa hata da alsa çalışır.
                long taskDuration = System.currentTimeMillis() - taskStartTime;
                LOGGER.info("[END] Team ID: {} processed in {} ms.", teamId, taskDuration);
            }
        }

        private Map<Integer, List<MatchResult>> searchPastSeasons(MatchPattern pattern) {
            Map<Integer, List<MatchResult>> foundMatchesBySeason = new TreeMap<>();
            for (int year = START_YEAR; year >= END_YEAR; year--) {
                String season = year + "/" + (year + 1);
                LOGGER.debug("Searching team ID {} in season {}", teamId, season);
                try {
                    List<MatchResult> seasonMatches = HttpScoreScraper.findScorePattern(httpClient, pattern, season, teamId);
                    if (seasonMatches != null && !seasonMatches.isEmpty()) {
                        LOGGER.info("Found {} matches for team ID {} in season {}", seasonMatches.size(), teamId, season);
                        foundMatchesBySeason.put(year, seasonMatches);
                    }
                } catch (IOException e) {
                    LOGGER.error("IOException while searching season {} for team ID {}", season, teamId, e);
                }
            }
            return foundMatchesBySeason;
        }

        private String formatResultsString(MatchPattern pattern, Map<Integer, List<MatchResult>> allMatches) {
            StringBuilder report = new StringBuilder();
            report.append(String.format("=== Team ID: %d (%s) ===\n", teamId, pattern.teamName));
            report.append("Aranan skor paterni:\n").append(pattern.toString()).append("\n");
            for (Map.Entry<Integer, List<MatchResult>> entry : allMatches.entrySet()) {
                report.append("\n").append(entry.getKey()).append(" Sezonu Analizi:\n");
                report.append("------------------------\n");
                String seasonResults = entry.getValue().stream()
                        .map(MatchResult::toString)
                        .collect(Collectors.joining("\n\n"));
                report.append(seasonResults).append("\n");
            }
            return report.toString();
        }
    }

    public static void main(String[] args) {
        List<String> teamIds;
        try {
            teamIds = TeamIdFinder.readIdsFromFile();
            if (teamIds.isEmpty()) {
                LOGGER.warn("Team IDs file is empty. Exiting.");
                return;
            }
            LOGGER.info("Loaded {} team IDs to process.", teamIds.size());
        } catch (IOException e) {
            LOGGER.error("Failed to read team IDs file.", e);
            return;
        }

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(CONNECTION_POOL_SIZE + 10);
        cm.setDefaultMaxPerRoute(CONNECTION_POOL_SIZE);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15000)
                .setSocketTimeout(30000)
                .setConnectionRequestTimeout(5000)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

            long startTime = System.currentTimeMillis();
            int submittedTasks = 0;

            for (String idStr : teamIds) {
                try {
                    int teamId = Integer.parseInt(idStr.trim());
                    completionService.submit(new TeamProcessorTask(teamId, httpClient));
                    submittedTasks++;
                } catch (NumberFormatException e) {
                    LOGGER.warn("Skipping invalid team ID format: {}", idStr);
                }
            }

            LOGGER.info("{} tasks submitted. Waiting for results...", submittedTasks);
            System.out.println("\n--- İşlem Başladı. Sonuçlar bulundukça gösterilecektir ---\n");

            int foundCount = 0;
            for (int i = 0; i < submittedTasks; i++) {
                try {
                    Future<String> completedFuture = completionService.take();
                    String result = completedFuture.get();

                    if (result != null && !result.isEmpty()) {
                        System.out.println(result);
                        System.out.println("====================================\n");
                        foundCount++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Main thread was interrupted.", e);
                    break;
                } catch (ExecutionException e) {
                    LOGGER.error("A task failed with an exception.", e.getCause());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("--- Tüm işlemler tamamlandı ---");
            LOGGER.info("Processing complete in {} ms. Found patterns for {} out of {} processed teams.",
                    duration, foundCount, submittedTasks);

            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }

        } catch (IOException e) {
            LOGGER.error("Error with HttpClient lifecycle.", e);
        }
    }
}