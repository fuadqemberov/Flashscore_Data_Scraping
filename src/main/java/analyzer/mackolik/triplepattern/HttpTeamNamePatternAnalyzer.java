package analyzer.mackolik.triplepattern;

import analyzer.util.TeamIdsFetcher;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Analyzer that searches for TEAM NAME SEQUENCE patterns around unstarted matches.
 *
 * For each team:
 *   1. Finds the first unstarted match in the current season.
 *   2. Collects the last 3 opponent names (prev) and next 3 opponent names (next).
 *   3. Searches past seasons (2014–2024) for the same opponent sequence in various
 *      combinations (PREV3, PREV3+NEXT1, PREV2+NEXT2, etc.).
 *   4. Prints ONLY results where the historical target match had HT/FT = 1/2 or 2/1.
 */
public class HttpTeamNamePatternAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(HttpTeamNamePatternAnalyzer.class);

    private static final int START_YEAR = 2024;
    private static final int END_YEAR   = 2010;
    private static final int NUM_THREADS = 10;

    // -----------------------------------------------------------------------

    private static class TeamNamePatternTask implements Callable<String> {
        private final int teamId;
        private final CloseableHttpClient http;

        TeamNamePatternTask(int teamId, CloseableHttpClient http) {
            this.teamId = teamId;
            this.http   = http;
        }

        @Override
        public String call() {
            log.info("Processing team ID: {}", teamId);
            try {
                // Step 1: build current-season pattern
                TeamNamePattern pattern = HttpTeamNamePatternFetcher.buildCurrentPattern(http, teamId);
                if (pattern == null) {
                    log.warn("No pattern built for team {}", teamId);
                    return null;
                }

                // Must have at least 1 prev AND the target to be meaningful
                if (pattern.prevOpponents.isEmpty() && pattern.nextOpponents.isEmpty()) {
                    log.info("Skipping team {} – no surrounding opponents found", teamId);
                    return null;
                }

                StringBuilder output       = new StringBuilder();
                boolean       foundAnything = false;

                // Step 2: search past seasons
                for (int year = START_YEAR; year >= END_YEAR; year--) {
                    String season = year + "/" + (year + 1);
                    try {
                        List<TeamNameMatchResult> hits =
                                HttpTeamNamePatternFetcher.searchHistoricalSeason(http, pattern, season, teamId);

                        if (!hits.isEmpty()) {
                            foundAnything = true;
                            output.append(String.format("\n--- %s Sezonu ---\n", season));
                            for (TeamNameMatchResult hit : hits) {
                                output.append(hit).append("\n");
                            }
                        }
                    } catch (IOException e) {
                        log.error("IO error team {} season {}: {}", teamId, season, e.getMessage());
                    } catch (Exception e) {
                        log.error("Error team {} season {}: {}", teamId, season, e.getMessage(), e);
                    }
                }

                if (foundAnything) {
                    String header = String.format(
                            "╔══════════════════════════════════════════════╗\n" +
                                    "  Takım: %s (ID: %d)\n" +
                                    "  Mevcut Maç  : %s vs %s\n" +
                                    "  Son 3 Rakip : %s\n" +
                                    "  Sonraki 3   : %s\n" +
                                    "╚══════════════════════════════════════════════╝",
                            pattern.teamName, pattern.teamId,
                            pattern.targetHomeTeam, pattern.targetAwayTeam,
                            pattern.prevOpponents,
                            pattern.nextOpponents);
                    return header + "\n" + output;
                } else {
                    log.info("No HT/FT 1/2 or 2/1 pattern found for team {}", teamId);
                    return null;
                }

            } catch (Exception e) {
                log.error("Fatal error for team {}: {}", teamId, e.getMessage(), e);
                return null;
            }
        }
    }

    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        List<String> teamIds = TeamIdsFetcher.fetchUnstartedTeamIds();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(NUM_THREADS + 5);
        cm.setDefaultMaxPerRoute(NUM_THREADS);

        CloseableHttpClient http = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<String>> futures = new ArrayList<>();

        for (String idStr : teamIds) {
            try {
                int teamId = Integer.parseInt(idStr.trim());
                futures.add(executor.submit(new TeamNamePatternTask(teamId, http)));
            } catch (NumberFormatException e) {
                log.warn("Invalid team ID: {}", idStr);
            }
        }

        log.info("Submitted {} tasks.", futures.size());

        int total = 0, found = 0;
        for (Future<String> f : futures) {
            try {
                String result = f.get();
                total++;
                if (result != null && !result.isEmpty()) {
                    System.out.println(result);
                    System.out.println("================================================\n");
                    found++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted", e);
            } catch (ExecutionException e) {
                log.error("Task failed: {}", e.getCause().getMessage(), e.getCause());
            }
        }

        log.info("Done. {} teams processed, {} had HT/FT 1/2 or 2/1 pattern matches.", total, found);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            http.close();
        } catch (IOException e) {
            log.error("Error closing HttpClient", e);
        }

        System.exit(0);
    }
}