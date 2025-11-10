package flashscore.weeklydatascraping.mackolik.patternfinder;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class OnlyLeagueAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(OnlyLeagueAnalyzer.class);
    private static final int START_YEAR = 2024; // Inclusive
    private static final int END_YEAR = 2017;   // Inclusive
    private static final int NUM_THREADS = 10; // Adjust based on your system and network tolerance

    // Task to process a single team ID
    private static class TeamProcessorTask implements Callable<String> {
        private final int teamId;
        private final CloseableHttpClient httpClient; // Share the client

        public TeamProcessorTask(int teamId, CloseableHttpClient httpClient) {
            this.teamId = teamId;
            this.httpClient = httpClient;
        }

        @Override
        public String call() throws Exception {
            log.info("Processing Team ID: {}", teamId);
            try {
                // 1. Find the current season's pattern
                MatchPattern currentPattern = OnlyLeagueScraper.findCurrentSeasonLastTwoMatches(httpClient, teamId);
                if (currentPattern == null) {
                    log.warn("Could not find  current pattern for team ID: {}", teamId);
                    return null; // Skip if no current pattern
                }

                StringBuilder teamResults = new StringBuilder();
                boolean foundMatchesForTeam = false;

                // 2. Search past seasons for the pattern
                for (int year = START_YEAR; year >= END_YEAR; year--) {
                    String season = year + "/" + (year + 1);
                    log.debug("Searching team ID {} in season {}", teamId, season);
                    try {
                        // Optional small delay between requests for the same team
                        // Thread.sleep(100); // 100ms delay

                        List<MatchResult> seasonMatches = OnlyLeagueScraper.findScorePattern(httpClient, currentPattern, season, teamId);

                        if (!seasonMatches.isEmpty()) {
                            foundMatchesForTeam = true;
                            teamResults.append("\n").append(year).append(" Sezonu Analizi:\n------------------------\n");
                            for (MatchResult match : seasonMatches) {
                                teamResults.append(match).append("\n\n");
                            }
                            // Remove the trailing newlines
                            if (teamResults.length() > 1) {
                                teamResults.setLength(teamResults.length() - 2);
                            }
                        }
                    } catch (IOException e) {
                        log.error("IOException while searching team {} season {}: {}", teamId, season, e.getMessage());
                        // Decide whether to continue with other seasons or stop for this team
                    } catch (Exception e) {
                        log.error("Unexpected error processing team {} season {}: {}", teamId, season, e.getMessage(), e);
                    }
                }

                // 3. Format output if matches were found
                if (foundMatchesForTeam) {
                    String header = String.format("=== Team ID: %d (%s) ===\nAranan skor paterni:\n%s\n",
                            teamId, currentPattern.teamName, currentPattern.toString());
                    return header + teamResults.toString();
                } else {
                    log.info("No matching patterns found for team ID: {}", teamId);
                    return null; // No matches found for this team
                }

            } catch (RuntimeException e) {
                log.error("Runtime error processing team ID {}: {}", teamId, e.getMessage());
                // Log stack trace for runtime exceptions originating from findCurrentSeason...
                if (e.getMessage() != null && e.getMessage().contains("maç skoru bulunamadı")) {
                    log.warn("Skipping team {} due to missing initial scores.", teamId);
                } else {
                    log.error("Detailed error for team " + teamId, e);
                }
                return null; // Indicate failure for this team
            } catch (Exception e) {
                log.error("General error processing team ID {}: {}", teamId, e.getMessage(), e);
                return null; // Indicate failure
            }
        }
    }

    public static void main(String[] args) {
        List<String> teamIds;
        try {
            // Ensure TeamIdFinder provides the correct path
            teamIds = TeamIdFinder.readIdsFromFile(); // Use your existing method
            log.info("Loaded {} team IDs.", teamIds.size());
        } catch (IOException e) {
            log.error("Failed to read team IDs file: {}", e.getMessage(), e);
            return;
        }

        // Configure connection pooling for HttpClient
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(NUM_THREADS + 5); // Max total connections
        cm.setDefaultMaxPerRoute(NUM_THREADS); // Max connections per host

        // Create a shared HttpClient instance using the connection manager
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<String>> futures = new ArrayList<>();

        // Submit tasks for each team ID
        for (String idStr : teamIds) {
            try {
                int teamId = Integer.parseInt(idStr.trim());
                Callable<String> task = new TeamProcessorTask(teamId, httpClient);
                futures.add(executor.submit(task));
            } catch (NumberFormatException e) {
                log.warn("Skipping invalid team ID: {}", idStr);
            }
        }

        log.info("Submitted {} tasks to thread pool.", futures.size());

        // Collect and print results
        int successCount = 0;
        int foundCount = 0;
        for (Future<String> future : futures) {
            try {
                String result = future.get(); // Wait for task completion
                if (result != null && !result.isEmpty()) {
                    System.out.println(result);
                    System.out.println("====================================\n"); // Separator
                    foundCount++;
                }
                successCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Main thread interrupted while waiting for task.", e);
            } catch (ExecutionException e) {
                log.error("Task execution failed: {}", e.getCause().getMessage(), e.getCause());
            }
        }

        log.info("Processing complete. {} tasks succeeded, {} teams had matching patterns.", successCount, foundCount);

        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close HttpClient
        try {
            httpClient.close();
            log.info("HttpClient closed.");
        } catch (IOException e) {
            log.error("Error closing HttpClient: {}", e.getMessage(), e);
        }

        System.exit(0); // Explicit exit if needed
    }
}