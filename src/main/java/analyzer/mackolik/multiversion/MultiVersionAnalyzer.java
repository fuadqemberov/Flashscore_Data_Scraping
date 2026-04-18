package analyzer.mackolik.multiversion;

import analyzer.mackolik.patternfinder.TeamIdFinder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MultiVersionAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(MultiVersionAnalyzer.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2010;
    private static final int[] ANALYSIS_VERSIONS = {1, 2, 3, 4};

    // =============================================
    // İç sınıf: Takım analiz görevini yapan callable
    // =============================================
    private static class MultiVersionTeamProcessorTask implements Callable<MultiVersionResult> {
        private final int teamId;
        private final RequestConfig requestConfig;

        public MultiVersionTeamProcessorTask(int teamId, RequestConfig requestConfig) {
            this.teamId = teamId;
            this.requestConfig = requestConfig;
        }

        @Override
        public MultiVersionResult call() {
            long start = System.currentTimeMillis();
            log.info("[START] Processing Team ID: {} with {} versions", teamId, ANALYSIS_VERSIONS.length);

            MultiVersionResult multiResult = new MultiVersionResult(teamId);

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {

                // Her versiyon için analiz yap
                for (int version : ANALYSIS_VERSIONS) {
                    try {
                        log.info("[VERSION-{}] Analyzing team ID: {} (sondan {}. maç)", version, teamId, version);

                        // Güncel deseni bul (sondan version. maç)
                        AdvancedMatchPattern currentPattern = AdvancedScoreScraper.findLastMatchPattern(
                                httpClient, teamId, version);

                        if (currentPattern == null) {
                            log.warn("[VERSION-{}] No pattern found for team ID: {}", version, teamId);
                            continue;
                        }

                        log.info("[VERSION-{}] Pattern: {} {} {} (Team: {})",
                                version, currentPattern.homeTeam, currentPattern.score,
                                currentPattern.awayTeam, currentPattern.teamName);

                        // Geçmiş sezonlarda ara (version kadar maç sonrasını göster)
                        Map<Integer, List<AdvancedMatchResult>> foundMatches =
                                searchPastSeasons(httpClient, currentPattern, version);

                        if (!foundMatches.isEmpty()) {
                            int totalMatches = foundMatches.values().stream().mapToInt(List::size).sum();
                            log.info("[VERSION-{}] Found {} matches for team '{}'",
                                    version, totalMatches, currentPattern.teamName);

                            VersionResult versionResult = new VersionResult(version, currentPattern, foundMatches);
                            multiResult.addVersionResult(versionResult);
                        } else {
                            log.info("[VERSION-{}] No historical matches found", version);
                        }

                    } catch (Exception e) {
                        log.error("[VERSION-{}] Error processing team {}: {}", version, teamId, e.getMessage());
                    }
                }

                return multiResult.hasAnyResults() ? multiResult : null;

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
                AdvancedMatchPattern pattern,
                int matchesForward) {

            Map<Integer, List<AdvancedMatchResult>> found = new TreeMap<>();

            for (int year = START_YEAR; year >= END_YEAR; year--) {
                String season = year + "/" + (year + 1);
                try {
                    List<AdvancedMatchResult> matches =
                            AdvancedScoreScraper.findHistoricalMatchPatterns(
                                    httpClient, pattern, season, teamId, matchesForward);

                    if (matches != null && !matches.isEmpty()) {
                        log.debug("Found {} matches in {}", matches.size(), season);
                        found.put(year, matches);
                    }
                } catch (IOException e) {
                    log.error("IOException in season {} for team {}: {}", season, teamId, e.getMessage());
                }
            }

            return found;
        }
    }

    // =============================================
    // Sonuç sınıfları
    // =============================================
    static class VersionResult {
        int version;
        AdvancedMatchPattern pattern;
        Map<Integer, List<AdvancedMatchResult>> matches;

        public VersionResult(int version, AdvancedMatchPattern pattern,
                             Map<Integer, List<AdvancedMatchResult>> matches) {
            this.version = version;
            this.pattern = pattern;
            this.matches = matches;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("\n╔══════════════════════════════════════════════════════════════╗\n"));
            sb.append(String.format("║ VERSİYON %d: Sondan %d. Maç Baz Alındı, %d Maç Sonrası      ║\n",
                    version, version, version));
            sb.append(String.format("╚══════════════════════════════════════════════════════════════╝\n"));
            sb.append("Aranan Pattern: ").append(pattern.toString()).append("\n");

            for (Map.Entry<Integer, List<AdvancedMatchResult>> entry : matches.entrySet()) {
                sb.append("\n").append(entry.getKey()).append(" Sezonu:\n");
                sb.append("─────────────────────────────────────────────\n");
                String seasonResults = entry.getValue().stream()
                        .map(AdvancedMatchResult::toString)
                        .collect(Collectors.joining("\n\n"));
                sb.append(seasonResults).append("\n");
            }
            return sb.toString();
        }
    }

    static class MultiVersionResult {
        int teamId;
        List<VersionResult> versionResults = new ArrayList<>();

        public MultiVersionResult(int teamId) {
            this.teamId = teamId;
        }

        public void addVersionResult(VersionResult result) {
            versionResults.add(result);
        }

        public boolean hasAnyResults() {
            return !versionResults.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            sb.append("═══════════════════════════════════════════════════════════════════════\n");
            sb.append(String.format("  TAKIM ID: %d - %d VERSİYON ANALİZİ\n",
                    teamId, versionResults.size()));

            if (!versionResults.isEmpty()) {
                sb.append(String.format("  Takım: %s\n", versionResults.get(0).pattern.teamName));
            }
            sb.append("═══════════════════════════════════════════════════════════════════════\n");

            for (VersionResult vr : versionResults) {
                sb.append(vr.toString());
            }

            sb.append("\n═══════════════════════════════════════════════════════════════════════\n");
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
            log.info("Loaded {} team IDs for multi-version analysis.", teamIds.size());
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
        CompletionService<MultiVersionResult> completionService = new ExecutorCompletionService<>(executor);

        long startTime = System.currentTimeMillis();
        int submitted = 0;

        for (String idStr : teamIds) {
            try {
                int teamId = Integer.parseInt(idStr.trim());
                completionService.submit(new MultiVersionTeamProcessorTask(teamId, requestConfig));
                submitted++;
            } catch (NumberFormatException e) {
                log.warn("Skipping invalid team ID: {}", idStr);
            }
        }

        log.info("{} tasks submitted. Waiting for completion...", submitted);
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  MULTI-VERSION PATTERN ANALYZER                               ║");
        System.out.println("║  Versions: 1,2,3,4 (Sondan 1.,2.,3.,4. maçlar)              ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        int foundCount = 0;
        for (int i = 0; i < submitted; i++) {
            try {
                Future<MultiVersionResult> completed = completionService.take();
                MultiVersionResult result = completed.get();
                if (result != null && result.hasAnyResults()) {
                    synchronized (System.out) {
                        System.out.println(result);
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
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  İŞLEM TAMAMLANDI                                             ║");
        System.out.println(String.format("║  Süre: %d ms | Bulunan: %d/%d takım                      ║",
                duration, foundCount, submitted));
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

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

    public static List<String> readTeamIdsFromFile(String filePath) throws IOException {
        return TeamIdFinder.readIdsFromFile();
    }
}