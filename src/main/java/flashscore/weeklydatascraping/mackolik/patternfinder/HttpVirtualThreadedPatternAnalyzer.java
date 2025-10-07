package flashscore.weeklydatascraping.mackolik.patternfinder;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Bu sınıf, her bir takım ID'sini virtual thread'lerde paralel olarak işleyerek
 * geçmiş sezonlardan skor paternlerini bulur.
 * Her thread kendi HttpClient'ini oluşturur — bu sayede tam paralellik sağlanır.
 */
public class HttpVirtualThreadedPatternAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpVirtualThreadedPatternAnalyzer.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2017;

    private static class TeamProcessorTask implements Callable<String> {
        private final int teamId;
        private final RequestConfig requestConfig;

        public TeamProcessorTask(int teamId, RequestConfig requestConfig) {
            this.teamId = teamId;
            this.requestConfig = requestConfig;
        }

        @Override
        public String call() {
            long taskStartTime = System.currentTimeMillis();
            LOGGER.info("[START] Processing Team ID: {}", teamId);

            // Her thread kendi HttpClient'ini kullanır
            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {

                MatchPattern currentPattern = HttpScoreScraper.findCurrentSeasonLastTwoMatches(httpClient, teamId);
                if (currentPattern == null) {
                    LOGGER.warn("[SKIP] No current pattern for team ID: {}", teamId);
                    return null;
                }

                LOGGER.info("[PATTERN] Found current pattern for team '{}' (ID: {})", currentPattern.teamName, teamId);
                Map<Integer, List<MatchResult>> allFoundMatches = searchPastSeasons(httpClient, currentPattern);

                if (allFoundMatches.isEmpty()) {
                    LOGGER.info("[NO-MATCH] No matching patterns found for team '{}' (ID: {})", currentPattern.teamName, teamId);
                    return null;
                } else {
                    int totalMatches = allFoundMatches.values().stream().mapToInt(List::size).sum();
                    LOGGER.info("[SUCCESS] Found {} matches for team '{}' (ID: {})", totalMatches, currentPattern.teamName, teamId);
                    return formatResultsString(currentPattern, allFoundMatches);
                }
            } catch (Exception e) {
                LOGGER.error("[ERROR] Exception while processing team ID: {}", teamId, e);
                return null;
            } finally {
                long duration = System.currentTimeMillis() - taskStartTime;
                LOGGER.info("[END] Team ID: {} processed in {} ms.", teamId, duration);
            }
        }

        private Map<Integer, List<MatchResult>> searchPastSeasons(CloseableHttpClient httpClient, MatchPattern pattern) {
            Map<Integer, List<MatchResult>> foundMatches = new TreeMap<>();
            for (int year = START_YEAR; year >= END_YEAR; year--) {
                String season = year + "/" + (year + 1);
                LOGGER.debug("Searching team ID {} in season {}", teamId, season);
                try {
                    List<MatchResult> matches = HttpScoreScraper.findScorePattern(httpClient, pattern, season, teamId);
                    if (matches != null && !matches.isEmpty()) {
                        LOGGER.info("Found {} matches for team ID {} in season {}", matches.size(), teamId, season);
                        foundMatches.put(year, matches);
                    }
                } catch (IOException e) {
                    LOGGER.error("IOException while searching season {} for team ID {}", season, teamId, e);
                }
            }
            return foundMatches;
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

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setSocketTimeout(20000)
                .setConnectionRequestTimeout(5000)
                .build();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

        long startTime = System.currentTimeMillis();
        int submitted = 0;

        for (String idStr : teamIds) {
            try {
                int teamId = Integer.parseInt(idStr.trim());
                completionService.submit(new TeamProcessorTask(teamId, requestConfig));
                submitted++;
            } catch (NumberFormatException e) {
                LOGGER.warn("Skipping invalid team ID format: {}", idStr);
            }
        }

        LOGGER.info("{} tasks submitted. Waiting for results...", submitted);
        System.out.println("\n--- İşlem Başladı. Sonuçlar bulundukça gösterilecektir ---\n");

        int foundCount = 0;
        for (int i = 0; i < submitted; i++) {
            try {
                Future<String> completedFuture = completionService.take();
                String result = completedFuture.get();

                if (result != null && !result.isEmpty()) {
                    synchronized (System.out) { // Çıktı karışmasını önle
                        System.out.println(result);
                        System.out.println("====================================\n");
                    }
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
        LOGGER.info("Processing complete in {} ms. Found patterns for {} out of {} teams.",
                duration, foundCount, submitted);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
         System.exit(0);
    }
}
