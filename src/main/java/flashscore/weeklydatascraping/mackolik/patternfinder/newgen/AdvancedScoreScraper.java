package flashscore.weeklydatascraping.mackolik.patternfinder.newgen;


import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AdvancedScoreScraper {

    private static final Logger log = LoggerFactory.getLogger(AdvancedScoreScraper.class);
    private static final String BASE_URL = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026";

    private static String fetchHtml(CloseableHttpClient httpClient, String url) throws IOException {
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        log.debug("Fetching URL: {}", url);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                return EntityUtils.toString(response.getEntity());
            } else {
                log.warn("Failed to fetch URL: {}. Status code: {}", url, statusCode);
                return null;
            }
        }
    }

    /**
     * Mevcut sezondaki son oynanmış maçı bulur ve bir desen olarak döndürür.
     */
    public static AdvancedMatchPattern findLastMatchPattern(CloseableHttpClient httpClient, int teamId) throws IOException, RuntimeException {
        String currentSeasonUrl = String.format(BASE_URL, teamId, CURRENT_SEASON);
        String html = fetchHtml(httpClient, currentSeasonUrl);
        if (html == null) throw new RuntimeException("Could not fetch current season page for team ID: " + teamId);

        Document doc = Jsoup.parse(html);
        Element tableBody = doc.selectFirst("#tblFixture > tbody");
        if (tableBody == null) throw new RuntimeException("Fixture table not found for team ID: " + teamId);

        List<String> scores = new ArrayList<>();
        List<String> homeTeams = new ArrayList<>();
        List<String> awayTeams = new ArrayList<>();
        String nextHomeTeam = null, nextAwayTeam = null;
        String teamName = doc.selectFirst("title").text().split("-")[0].trim();

        Elements rows = tableBody.select("tr");
        boolean foundUnplayed = false;

        for (Element row : rows) {
            if (row.hasClass("competition") || row.selectFirst("td[itemprop=sportsevent]") != null) continue;

            Element scoreElement = row.selectFirst("td:nth-child(5) b a");
            Element homeTeamElement = row.selectFirst("td:nth-child(3)");
            Element awayTeamElement = row.selectFirst("td:nth-child(7)");

            if (scoreElement != null && homeTeamElement != null && awayTeamElement != null) {
                String score = scoreElement.text().trim();
                String home = homeTeamElement.text().trim();
                String away = awayTeamElement.text().trim();

                if (!foundUnplayed && !score.isEmpty() && score.contains("-")) {
                    scores.add(score);
                    homeTeams.add(home);
                    awayTeams.add(away);
                } else if (!foundUnplayed) {
                    nextHomeTeam = home;
                    nextAwayTeam = away;
                    foundUnplayed = true;
                    // Sonraki maçı bulduktan sonra döngüden çıkabiliriz.
                }
            }
        }

        if (!scores.isEmpty()) {
            int lastIndex = scores.size() - 1;
            String lastScore = scores.get(lastIndex);
            String lastHome = homeTeams.get(lastIndex);
            String lastAway = awayTeams.get(lastIndex);

            log.info("Found last match for team ID {}: {} {} {}", teamId, lastHome, lastScore, lastAway);
            return new AdvancedMatchPattern(lastHome, lastAway, lastScore, teamName, nextHomeTeam, nextAwayTeam);
        } else {
            throw new RuntimeException("Hiç tamamlanmış maç bulunamadı for team ID: " + teamId);
        }
    }

    /**
     * Verilen deseni geçmiş sezonlarda arar.
     */
    public static List<AdvancedMatchResult> findHistoricalMatchPatterns(CloseableHttpClient httpClient, AdvancedMatchPattern pattern, String seasonYear, int teamId) throws IOException {
        List<AdvancedMatchResult> foundResults = new ArrayList<>();
        String seasonUrl = String.format(BASE_URL, teamId, seasonYear);
        String html = fetchHtml(httpClient, seasonUrl);
        if (html == null) return foundResults;

        Document doc = Jsoup.parse(html);
        Elements allTableRows = doc.select("#tblFixture > tbody > tr");

        List<Element> leagueMatches = new ArrayList<>();
        boolean collectRows = false;
        boolean isFirstLeague = true;
        // Sadece ilk ligin maçlarını topla
        for (Element row : allTableRows) {
            if (row.hasClass("competition")) {
                if (!isFirstLeague) break;
                isFirstLeague = false;
                collectRows = true;
                continue;
            }
            if (collectRows && row.selectFirst("td:nth-child(5) b a") != null) {
                leagueMatches.add(row);
            }
        }

        for (int i = 0; i < leagueMatches.size(); i++) {
            Element row = leagueMatches.get(i);
            try {
                String homeTeam = row.selectFirst("td:nth-child(3)").text().trim();
                String awayTeam = row.selectFirst("td:nth-child(7)").text().trim();
                String score = row.selectFirst("td:nth-child(5) b a").text().trim();

                // Desenle eşleşme kontrolü (takımlar ve skor aynı olmalı)
                boolean teamsMatch = (homeTeam.equals(pattern.homeTeam) && awayTeam.equals(pattern.awayTeam)) ||
                        (homeTeam.equals(pattern.awayTeam) && awayTeam.equals(pattern.homeTeam));

                if (score.equals(pattern.score) && teamsMatch) {
                    log.info("Pattern match found for team {}, season {} -> {} {} {}", teamId, seasonYear, homeTeam, score, awayTeam);

                    String htScore = row.selectFirst("td:nth-child(9)").text().trim();
                    AdvancedMatchResult result = new AdvancedMatchResult(homeTeam, awayTeam, score, htScore, seasonYear, pattern);

                    // Önceki maçı al
                    if (i > 0) {
                        result.previousMatchInfo = getMatchInfo(leagueMatches.get(i - 1));
                    } else {
                        result.previousMatchInfo = "Bilgi Yok (Sezonun İlk Maçı)";
                    }

                    // Sonraki maçı al
                    if (i + 1 < leagueMatches.size()) {
                        result.nextMatchInfo = getMatchInfo(leagueMatches.get(i + 1));
                    } else {
                        result.nextMatchInfo = "Bilgi Yok (Sezonun Son Maçı)";
                    }

                    foundResults.add(result);
                }
            } catch (Exception e) {
                log.error("Error analyzing match row at index {} for team {}, season {}: {}", i, teamId, seasonYear, e.getMessage());
            }
        }
        return foundResults;
    }

    // Helper method to get match info from a row
    private static String getMatchInfo(Element row) {
        try {
            String home = row.selectFirst("td:nth-child(3)").text().trim();
            String away = row.selectFirst("td:nth-child(7)").text().trim();
            String score = row.selectFirst("td:nth-child(5) b a").text().trim();
            String ht = row.selectFirst("td:nth-child(9)").text().trim();
            return String.format("%s %s %s (HT: %s)", home, score, away, ht.isEmpty() ? "N/A" : ht);
        } catch (Exception e) {
            return "Hata (Okunamadı)";
        }
    }
}