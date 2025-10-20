package flashscore.weeklydatascraping.mackolik.patternfinder.triplepattern;

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

public class HttpTripleScoreFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpTripleScoreFetcher.class);
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
        } catch (IOException e) {
            log.error("IOException while fetching URL: {}", url, e);
            throw e;
        }
    }

    public static ThreeMatchPattern findCurrentSeasonLastThreeMatches(CloseableHttpClient httpClient, int teamId) throws IOException, RuntimeException {
        String currentSeasonUrl = String.format(BASE_URL, teamId, CURRENT_SEASON);
        String html = fetchHtml(httpClient, currentSeasonUrl);

        if (html == null) {
            throw new RuntimeException("Could not fetch current season page for team ID: " + teamId);
        }

        Document doc = Jsoup.parse(html);
        Element tableBody = doc.selectFirst("#tblFixture > tbody");

        if (tableBody == null) {
            log.warn("Fixture table body not found for team ID {} in season {}", teamId, CURRENT_SEASON);
            throw new RuntimeException("Fixture table not found for team ID: " + teamId);
        }

        List<String> allScores = new ArrayList<>();
        List<String> allHomeTeams = new ArrayList<>();
        List<String> allAwayTeams = new ArrayList<>();
        String nextHomeTeam = null;
        String nextAwayTeam = null;
        String teamName = "Unknown";

        try {
            Element teamNameElement = doc.selectFirst("title");
            if (teamNameElement != null) {
                String title = teamNameElement.text();
                teamName = title.split("-")[0].trim();
            }
        } catch (Exception e) {
            log.warn("Could not extract team name for ID {}", teamId);
        }

        Elements rows = tableBody.select("tr");
        boolean foundUnplayed = false;

        for (Element row : rows) {
            if (row.selectFirst("td[itemprop=sportsevent]") != null) {
                log.debug("Found 'sportsevent' row, stopping processing for team {}", teamId);
                break;
            }
            if (row.hasClass("competition")) {
                continue;
            }

            try {
                Element scoreElement = row.selectFirst("td:nth-child(5) b a");
                Element homeTeamElement = row.selectFirst("td:nth-child(3)");
                Element awayTeamElement = row.selectFirst("td:nth-child(7)");

                if (scoreElement != null && homeTeamElement != null && awayTeamElement != null) {
                    String score = scoreElement.text().trim();
                    String homeTeam = homeTeamElement.text().trim();
                    String awayTeam = awayTeamElement.text().trim();

                    if (!foundUnplayed && !score.isEmpty() && !score.equalsIgnoreCase("v") && score.contains("-")) {
                        allScores.add(score);
                        allHomeTeams.add(homeTeam);
                        allAwayTeams.add(awayTeam);
                        log.trace("Added match: {} {} {}", homeTeam, score, awayTeam);
                    } else if (!foundUnplayed) {
                        nextHomeTeam = homeTeam;
                        nextAwayTeam = awayTeam;
                        foundUnplayed = true;
                        log.debug("Found next unplayed match: {} vs {}", nextHomeTeam, nextAwayTeam);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Error parsing a match row for team {}: {}. Row HTML: {}", teamId, e.getMessage(), row.html());
            }
        }

        if (allScores.size() >= 3) {
            int lastIndex = allScores.size() - 1;
            String score1 = allScores.get(lastIndex - 2);
            String score2 = allScores.get(lastIndex - 1);
            String score3 = allScores.get(lastIndex);
            String home1 = allHomeTeams.get(lastIndex - 2);
            String away1 = allAwayTeams.get(lastIndex - 2);
            String home2 = allHomeTeams.get(lastIndex - 1);
            String away2 = allAwayTeams.get(lastIndex - 1);
            String home3 = allHomeTeams.get(lastIndex);
            String away3 = allAwayTeams.get(lastIndex);

            log.info("Found last three matches for team ID {}: {}, {}, {}", teamId, score1, score2, score3);
            return new ThreeMatchPattern(score1, score2, score3, home1, away1, home2, away2, home3, away3, teamName, nextHomeTeam, nextAwayTeam);
        } else {
            log.warn("Could not find at least three completed match scores for team ID {} in season {}", teamId, CURRENT_SEASON);
            throw new RuntimeException("Son üç maç skoru bulunamadı for team ID: " + teamId + "! Found: " + allScores.size());
        }
    }

    public static List<TripleMatchResult> findTripleScorePattern(CloseableHttpClient httpClient, ThreeMatchPattern pattern, String seasonYear, int teamId) throws IOException {
        List<TripleMatchResult> foundResults = new ArrayList<>();
        String seasonUrl = String.format(BASE_URL, teamId, seasonYear);
        String html = fetchHtml(httpClient, seasonUrl);

        if (html == null) {
            log.warn("Could not fetch page for team ID {} and season {}", teamId, seasonYear);
            return foundResults;
        }

        Document doc = Jsoup.parse(html);
        Elements allTableRows = doc.select("table tbody tr");

        List<Element> leagueMatches = new ArrayList<>();
        boolean isFirstLeague = true;
        boolean collectRows = false;

        for (Element row : allTableRows) {
            if (row.hasClass("competition")) {
                if (!isFirstLeague) {
                    log.debug("Found second competition header, stopping row collection for season {}", seasonYear);
                    break;
                }
                isFirstLeague = false;
                collectRows = true;
                log.debug("Found first competition header for season {}", seasonYear);
                continue;
            }

            if (collectRows && row.selectFirst("td:nth-child(5) b a") != null) {
                leagueMatches.add(row);
            }
        }

        log.debug("Collected {} potential league match rows for season {}", leagueMatches.size(), seasonYear);

        // Check for three consecutive matches
        for (int i = 0; i < leagueMatches.size() - 2; i++) {
            Element row1 = leagueMatches.get(i);
            Element row2 = leagueMatches.get(i + 1);
            Element row3 = leagueMatches.get(i + 2);

            try {
                String score1 = row1.selectFirst("td:nth-child(5) b a").text().trim();
                String score2 = row2.selectFirst("td:nth-child(5) b a").text().trim();
                String score3 = row3.selectFirst("td:nth-child(5) b a").text().trim();

                // Check if pattern matches (exact or reversed scores)
                if (matchesTriplePattern(score1, score2, score3, pattern)) {
                    log.debug("Triple pattern match found for season {} at index {}", seasonYear, i);

                    // Extract match details
                    String homeTeam1 = row1.selectFirst("td:nth-child(3)").text().trim();
                    String awayTeam1 = row1.selectFirst("td:nth-child(7)").text().trim();
                    String htScore1 = row1.selectFirst("td:nth-child(9)").text().trim();

                    String homeTeam2 = row2.selectFirst("td:nth-child(3)").text().trim();
                    String awayTeam2 = row2.selectFirst("td:nth-child(7)").text().trim();
                    String htScore2 = row2.selectFirst("td:nth-child(9)").text().trim();

                    String homeTeam3 = row3.selectFirst("td:nth-child(3)").text().trim();
                    String awayTeam3 = row3.selectFirst("td:nth-child(7)").text().trim();
                    String htScore3 = row3.selectFirst("td:nth-child(9)").text().trim();

                    TripleMatchResult result = new TripleMatchResult(
                            homeTeam1, awayTeam1, score1,
                            homeTeam2, awayTeam2, score2,
                            homeTeam3, awayTeam3, score3,
                            seasonYear, pattern);

                    result.htScore1 = htScore1.isEmpty() ? null : htScore1;
                    result.htScore2 = htScore2.isEmpty() ? null : htScore2;
                    result.htScore3 = htScore3.isEmpty() ? null : htScore3;

                    // Extract preceding match (if exists)
                    if (i > 0) {
                        Element precedingRow = leagueMatches.get(i - 1);
                        try {
                            String precedingScore = precedingRow.selectFirst("td:nth-child(5) b a").text().trim();
                            String precedingHTScore = precedingRow.selectFirst("td:nth-child(9)").text().trim();
                            String precedingHome = precedingRow.selectFirst("td:nth-child(3)").text().trim();
                            String precedingAway = precedingRow.selectFirst("td:nth-child(7)").text().trim();
                            result.previousMatchScore = precedingHome + " " + precedingScore + " " + precedingAway;
                            result.previousHTScore = precedingHTScore.isEmpty() ? null : precedingHTScore;
                        } catch (Exception e) {
                            log.warn("Could not parse preceding match at index {} for season {}", i - 1, seasonYear, e);
                            result.previousMatchScore = "Hata (Önceki)";
                        }
                    } else {
                        result.previousMatchScore = "Bilgi Yok (İlk Maç)";
                        result.previousHTScore = null;
                    }

                    // Extract following match (if exists)
                    if (i + 3 < leagueMatches.size()) {
                        Element followingRow = leagueMatches.get(i + 3);
                        try {
                            String followingScore = followingRow.selectFirst("td:nth-child(5) b a").text().trim();
                            String followingHTScore = followingRow.selectFirst("td:nth-child(9)").text().trim();
                            String followingHome = followingRow.selectFirst("td:nth-child(3)").text().trim();
                            String followingAway = followingRow.selectFirst("td:nth-child(7)").text().trim();
                            result.nextMatchScore = followingHome + " " + followingScore + " " + followingAway;
                            result.nextHTScore = followingHTScore.isEmpty() ? null : followingHTScore;
                        } catch (Exception e) {
                            log.warn("Could not parse following match at index {} for season {}", i + 3, seasonYear, e);
                            result.nextMatchScore = "Hata (Sonraki)";
                        }
                    } else {
                        result.nextMatchScore = "Bilgi Yok (Son Maç)";
                        result.nextHTScore = null;
                    }

                    foundResults.add(result);
                    log.info("Triple pattern match added for team {}, season {}", teamId, seasonYear);
                }
            } catch (Exception e) {
                log.error("Error analyzing triple match at index {} for team {}, season {}: {}", i, teamId, seasonYear, e.getMessage());
            }
        }

        return foundResults;
    }

    // Check if scores match the pattern (exact or reversed)
    private static boolean matchesTriplePattern(String s1, String s2, String s3, ThreeMatchPattern pattern) {
        // Exact match
        if (scoreEquals(s1, pattern.score1) && scoreEquals(s2, pattern.score2) && scoreEquals(s3, pattern.score3)) {
            return true;
        }
        // All reversed
        if (scoreEquals(s1, reverseScore(pattern.score1)) &&
            scoreEquals(s2, reverseScore(pattern.score2)) &&
            scoreEquals(s3, reverseScore(pattern.score3))) {
            return true;
        }
        return false;
    }

    private static boolean scoreEquals(String score1, String score2) {
        return score1.equals(score2) || score1.equals(reverseScore(score2));
    }

    private static String reverseScore(String score) {
        String[] parts = score.split("-");
        if (parts.length == 2) {
            return parts[1] + "-" + parts[0];
        }
        return score;
    }
}