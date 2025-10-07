package flashscore.weeklydatascraping.mackolik.patternfinder.newgen;

import flashscore.weeklydatascraping.mackolik.patternfinder.TeamIdFinder;
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

public class AdvancedVirtualThreadedAnalyzer2 {

    private static final Logger log = LoggerFactory.getLogger(AdvancedVirtualThreadedAnalyzer2.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2017;

    // =============================================
    // İç sınıf: Takım analiz görevini yapan callable
    // =============================================
    private static class AdvancedTeamProcessorTask implements Callable<String> {
        private final int teamId;
        private final RequestConfig requestConfig;

        public AdvancedTeamProcessorTask(int teamId, RequestConfig requestConfig) {
            this.teamId = teamId;
            this.requestConfig = requestConfig;
        }

        @Override
        public String call() {
            long start = System.currentTimeMillis();
            log.info("[START] Processing Team ID: {}", teamId);

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {

                // 1. Güncel skor desenini bul
                AdvancedMatchPattern currentPattern = AdvancedScoreScraper.findLastMatchPattern(httpClient, teamId);
                if (currentPattern == null) {
                    log.warn("[SKIP] No current pattern found for team ID: {}", teamId);
                    return null;
                }

                log.info("[PATTERN] Found pattern for team '{}' (ID: {})", currentPattern.teamName, teamId);
                Map<Integer, List<AdvancedMatchResult>> foundMatches = searchPastSeasons(httpClient, currentPattern);

                if (foundMatches.isEmpty()) {
                    log.info("[NO-MATCH] No historical matches found for team '{}'", currentPattern.teamName);
                    return null;
                } else {
                    int totalMatches = foundMatches.values().stream().mapToInt(List::size).sum();
                    log.info("[SUCCESS] Found {} matches for team '{}' (ID: {})",
                            totalMatches, currentPattern.teamName, teamId);
                    return formatResults(currentPattern, foundMatches);
                }

            } catch (Exception e) {
                log.error("[ERROR] Exception while processing team {}: {}", teamId, e.getMessage(), e);
                return null;
            } finally {
                long duration = System.currentTimeMillis() - start;
                log.info("[END] Team ID: {} processed in {} ms.", teamId, duration);
            }
        }

        private Map<Integer, List<AdvancedMatchResult>> searchPastSeasons(
                CloseableHttpClient httpClient,
                AdvancedMatchPattern pattern) {

            Map<Integer, List<AdvancedMatchResult>> found = new TreeMap<>();

            for (int year = START_YEAR; year >= END_YEAR; year--) {
                String season = year + "/" + (year + 1);
                log.debug("Searching team ID {} in season {}", teamId, season);
                try {
                    List<AdvancedMatchResult> matches =
                            AdvancedScoreScraper.findHistoricalMatchPatterns(httpClient, pattern, season, teamId);

                    if (matches != null && !matches.isEmpty()) {
                        log.info("Found {} matches for team {} in {}", matches.size(), teamId, season);
                        found.put(year, matches);
                    }
                } catch (IOException e) {
                    log.error("IOException in season {} for team {}: {}", season, teamId, e.getMessage());
                }
            }

            return found;
        }

        private String formatResults(AdvancedMatchPattern pattern,
                                     Map<Integer, List<AdvancedMatchResult>> allMatches) {

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== Team ID: %d (%s) ===\n", teamId, pattern.teamName));
            sb.append("Aranan maç paterni:\n").append(pattern.toString()).append("\n");

            for (Map.Entry<Integer, List<AdvancedMatchResult>> entry : allMatches.entrySet()) {
                sb.append("\n").append(entry.getKey()).append(" Sezonu Analizi:\n");
                sb.append("------------------------\n");
                String seasonResults = entry.getValue().stream()
                        .map(AdvancedMatchResult::toString)
                        .collect(Collectors.joining("\n\n"));
                sb.append(seasonResults).append("\n");
            }
            return sb.toString();
        }
    }

    // =============================================
    // Ana metod: Virtual Thread tabanlı çalıştırma
    // =============================================
    public static void main(String[] args) {
        List<String> teamIds;
        try {
            teamIds = readTeamIdsFromFile("team_ids.txt");
            if (teamIds.isEmpty()) {
                log.warn("No team IDs found. Exiting.");
                return;
            }
            log.info("Loaded {} team IDs for analysis.", teamIds.size());
        } catch (IOException e) {
            log.error("Failed to read team IDs: {}", e.getMessage(), e);
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
                completionService.submit(new AdvancedTeamProcessorTask(teamId, requestConfig));
                submitted++;
            } catch (NumberFormatException e) {
                log.warn("Skipping invalid team ID: {}", idStr);
            }
        }

        log.info("{} tasks submitted. Waiting for completion...", submitted);
        System.out.println("\n--- İşlem Başladı. Sonuçlar bulundukça gösterilecektir ---\n");

        int foundCount = 0;
        for (int i = 0; i < submitted; i++) {
            try {
                Future<String> completed = completionService.take();
                String result = completed.get();
                if (result != null && !result.isEmpty()) {
                    synchronized (System.out) {
                        System.out.println(result);
                        System.out.println("====================================\n");
                    }
                    foundCount++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Main thread interrupted.", e);
                break;
            } catch (ExecutionException e) {
                log.error("A task failed: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("--- Tüm işlemler tamamlandı ---");
        log.info("Processing complete in {} ms. Found patterns for {} out of {} teams.",
                duration, foundCount, submitted);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    // =============================================
    // Yardımcı: Team ID listesini oku
    // =============================================
    public static List<String> readTeamIdsFromFile(String filePath) throws IOException {
        return TeamIdFinder.readIdsFromFile();
    }
}
