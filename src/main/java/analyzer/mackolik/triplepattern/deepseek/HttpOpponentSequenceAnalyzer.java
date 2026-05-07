package analyzer.mackolik.triplepattern.deepseek;


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

public class HttpOpponentSequenceAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(HttpOpponentSequenceAnalyzer.class);
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2014;
    private static final int NUM_THREADS = 10;

    private static class TeamOpponentSequenceTask implements Callable<String> {
        private final int teamId;
        private final CloseableHttpClient httpClient;

        TeamOpponentSequenceTask(int teamId, CloseableHttpClient httpClient) {
            this.teamId = teamId;
            this.httpClient = httpClient;
        }

        @Override
        public String call() throws Exception {
            log.info("Rakip dizisi analizi başlıyor: Takım ID {}", teamId);
            try {
                CurrentOpponentSequence currentSeq =
                        HttpTripleScoreFetcher.findCurrentSeasonOpponentSequence(httpClient, teamId);

                StringBuilder teamOutput = new StringBuilder();
                boolean foundAny = false;

                for (int year = START_YEAR; year >= END_YEAR; year--) {
                    String season = year + "/" + (year + 1);
                    try {
                        List<OpponentSequenceMatch> matches =
                                HttpTripleScoreFetcher.findOpponentSequencePatterns(
                                        httpClient, teamId, season, currentSeq);

                        if (!matches.isEmpty()) {
                            foundAny = true;
                            teamOutput.append("\n").append(year).append(" Sezonu:\n");
                            for (OpponentSequenceMatch m : matches) {
                                teamOutput.append(m.toString()).append("\n\n");
                            }
                        }
                    } catch (IOException e) {
                        log.error("IO hatası takım {} sezon {}: {}", teamId, season, e.getMessage());
                    }
                }

                if (foundAny) {
                    String upcomingStr = currentSeq.upcomingHomeTeam + " vs " + currentSeq.upcomingAwayTeam;
                    String header = String.format(
                            "=== %s (ID: %d) ===\nYaklaşan maç: %s\n" +
                                    "Rakip Dizisi (Son 3 → Gelecek 3): %s\n",
                            currentSeq.teamName, teamId,
                            upcomingStr,
                            currentSeq.lastOpponents + " → " + currentSeq.nextOpponents);
                    return header + teamOutput;
                } else {
                    log.info("Takım {} için 2/1-1/2 comeback ve rakip dizisi eşleşmesi bulunamadı.", teamId);
                    return null;
                }

            } catch (Exception e) {
                log.error("Takım {} işlenirken hata: {}", teamId, e.getMessage());
                return null;
            }
        }
    }

    public static void main(String[] args) {
        List<String> teamIds = TeamIdsFetcher.fetchUnstartedTeamIds();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(NUM_THREADS + 5);
        cm.setDefaultMaxPerRoute(NUM_THREADS);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<String>> futures = new ArrayList<>();

        for (String idStr : teamIds) {
            try {
                int teamId = Integer.parseInt(idStr.trim());
                futures.add(executor.submit(new TeamOpponentSequenceTask(teamId, httpClient)));
            } catch (NumberFormatException e) {
                log.warn("Geçersiz takım ID atlandı: {}", idStr);
            }
        }

        log.info("Toplam {} görev havuzlandı.", futures.size());

        int foundCount = 0;
        for (Future<String> future : futures) {
            try {
                String result = future.get();
                if (result != null && !result.isEmpty()) {
                    System.out.println(result);
                    System.out.println("====================================\n");
                    foundCount++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Ana iş parçacığı kesildi.", e);
            } catch (ExecutionException e) {
                log.error("Görev hatası: {}", e.getCause().getMessage());
            }
        }

        log.info("Analiz tamamlandı. {} takımda eşleşen rakip dizisi ve comeback bulundu.", foundCount);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            httpClient.close();
        } catch (IOException e) {
            log.error("HttpClient kapatma hatası", e);
        }

        System.exit(0);
    }
}