package flashscore.weeklydatascraping.mackolik.patternfinder;

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

public class OnlyLeagueScraper {

    private static final Logger log = LoggerFactory.getLogger(OnlyLeagueScraper.class);
    private static final String BASE_URL = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026"; // Assuming this is the current season year format

    // Helper method to fetch HTML content
    private static String fetchHtml(CloseableHttpClient httpClient, String url) throws IOException {
        HttpGet request = new HttpGet(url);
        // Optional: Add headers like User-Agent if needed
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        log.debug("Fetching URL: {}", url);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                return EntityUtils.toString(response.getEntity());
            } else {
                log.warn("Failed to fetch URL: {}. Status code: {}", url, statusCode);
                return null; // Or throw exception
            }
        } catch (IOException e) {
            log.error("IOException while fetching URL: {}", url, e);
            throw e; // Re-throw to indicate failure
        }
    }

    // --- Refactored findCurrentSeasonLastTwoMatches ---
    public static MatchPattern findCurrentSeasonLastTwoMatches(CloseableHttpClient httpClient, int teamId) throws IOException, RuntimeException {
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
                } else {
                    log.trace("Skipping row without expected match data elements: {}", row.html());
                }
            } catch (Exception e) {
                log.warn("Error parsing a match row for team {}: {}. Row HTML: {}", teamId, e.getMessage(), row.html());
            }
        }

        if (allScores.size() >= 2) {
            int lastIndex = allScores.size() - 1;
            String score1 = allScores.get(lastIndex - 1);
            String score2 = allScores.get(lastIndex);
            String home1 = allHomeTeams.get(lastIndex - 1);
            String away1 = allAwayTeams.get(lastIndex - 1);
            String home2 = allHomeTeams.get(lastIndex);
            String away2 = allAwayTeams.get(lastIndex);

            log.info("Found last two matches for team ID {}: {} vs {} ({}), {} vs {} ({})", teamId, home1, away1, score1, home2, away2, score2);
            return new MatchPattern(score1, score2, home1, away1, home2, away2, teamName, nextHomeTeam, nextAwayTeam);
        } else {
            log.warn("Could not find at least two completed match scores for team ID {} in season {}", teamId, CURRENT_SEASON);
            throw new RuntimeException("Son iki maç skoru bulunamadı for team ID: " + teamId + "! Found: " + allScores.size());
        }
    }


    // --- Refactored findScorePattern ---
    public static List<MatchResult> findScorePattern(CloseableHttpClient httpClient, MatchPattern pattern, String seasonYear, int teamId) throws IOException {
        List<MatchResult> foundResults = new ArrayList<>();
        String seasonUrl = String.format(BASE_URL, teamId, seasonYear);
        String html = fetchHtml(httpClient, seasonUrl);

        if (html == null) {
            log.warn("Could not fetch page for team ID {} and season {}", teamId, seasonYear);
            return foundResults; // Return empty list if page fetch fails
        }

        Document doc = Jsoup.parse(html);
        // Select all rows within any tbody inside a table - adjust if structure is more specific
        Elements allTableRows = doc.select("table tbody tr");

        List<Element> leagueMatches = new ArrayList<>();
        boolean isFirstLeague = true;
        boolean collectRows = false;

        for (Element row : allTableRows) {
            if (row.hasClass("competition")) {
                if (!isFirstLeague) {
                    log.debug("Found second competition header, stopping row collection for season {}", seasonYear);
                    break; // Stop after the first league's matches
                }
                isFirstLeague = false;
                collectRows = true; // Start collecting rows after the first competition header
                log.debug("Found first competition header for season {}", seasonYear);
                continue; // Skip the header row itself
            }

            // Collect rows only if they belong to the first league section and have the 'row' class (or similar marker)
            // Also check for essential elements to avoid adding empty/irrelevant rows
            if (collectRows && row.selectFirst("td:nth-child(5) b a") != null) {
                leagueMatches.add(row);
            } else if (collectRows) {
                log.trace("Skipping row in league section (missing score link or not a match row?): {}", row.html());
            }
        }

        log.debug("Collected {} potential league match rows for season {}", leagueMatches.size(), seasonYear);


        for (int i = 0; i < leagueMatches.size() - 1; i++) {
            Element currentRow = leagueMatches.get(i);
            Element nextRow = leagueMatches.get(i + 1);

            try {
                // Extract scores using Jsoup selectors
                String currentScore = currentRow.selectFirst("td:nth-child(5) b a").text().trim();
                String nextScore = nextRow.selectFirst("td:nth-child(5) b a").text().trim();

                // Check pattern match (both orders)
                boolean matchOrder1 = currentScore.equals(pattern.score1) && nextScore.equals(pattern.score2);
                boolean matchOrder2 = currentScore.equals(pattern.score2) && nextScore.equals(pattern.score1);

                if (matchOrder1 || matchOrder2) {
                    log.debug("Pattern match found for season {} at index {}", seasonYear, i);

                    // Extract details for the two matched rows
                    String homeTeam = currentRow.selectFirst("td:nth-child(3)").text().trim();
                    String awayTeam = currentRow.selectFirst("td:nth-child(7)").text().trim();
                    String currentHTScore = currentRow.selectFirst("td:nth-child(9)").text().trim();

                    String secondMatchHomeTeam = nextRow.selectFirst("td:nth-child(3)").text().trim();
                    String secondMatchAwayTeam = nextRow.selectFirst("td:nth-child(7)").text().trim();
                    String secondMatchScore = nextScore; // Already have this
                    String secondMatchHTScore = nextRow.selectFirst("td:nth-child(9)").text().trim();

                    // Create MatchResult object, passing the original pattern
                    MatchResult result = new MatchResult(homeTeam, awayTeam, currentScore, seasonYear, pattern);
                    result.firstMatchHTScore = currentHTScore.isEmpty() ? null : currentHTScore;
                    result.secondMatchHomeTeam = secondMatchHomeTeam;
                    result.secondMatchScore = secondMatchScore;
                    result.secondMatchAwayTeam = secondMatchAwayTeam;
                    result.secondMatchHTScore = secondMatchHTScore.isEmpty() ? null : secondMatchHTScore;


                    // Extract preceding match details (if exists)
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

                    // Extract following match details (if exists)
                    if (i + 2 < leagueMatches.size()) {
                        Element followingRow = leagueMatches.get(i + 2);
                        try {
                            String followingScore = followingRow.selectFirst("td:nth-child(5) b a").text().trim();
                            String followingHTScore = followingRow.selectFirst("td:nth-child(9)").text().trim();
                            String followingHome = followingRow.selectFirst("td:nth-child(3)").text().trim();
                            String followingAway = followingRow.selectFirst("td:nth-child(7)").text().trim();
                            result.nextMatchScore = followingHome + " " + followingScore + " " + followingAway;
                            result.nextHTScore = followingHTScore.isEmpty() ? null : followingHTScore;
                        } catch(Exception e) {
                            log.warn("Could not parse following match at index {} for season {}", i + 2, seasonYear, e);
                            result.nextMatchScore = "Hata (Sonraki)";
                        }
                    } else {
                        result.nextMatchScore = "Bilgi Yok (Son Maç)";
                        result.nextHTScore = null;
                    }

                    // Apply the filter: Only add if it involves original teams playing same opponent
                    if (result.containsOriginalTeamVsOpponent()) {
                        foundResults.add(result);
                        log.info("Filtered pattern match added for team {}, season {}", teamId, seasonYear);
                    } else {
                        log.debug("Pattern match found but filtered out (opponent mismatch) for team {}, season {}", teamId, seasonYear);
                    }
                }
            } catch (Exception e) {
                // Log error for this pair of rows but continue checking others
                log.error("Error analyzing match pair at index {} for team {}, season {}: {}", i, teamId, seasonYear, e.getMessage());
                // Consider adding more details like row HTML if helpful for debugging
                // log.error("Row 1 HTML: {}", currentRow.html());
                // log.error("Row 2 HTML: {}", nextRow.html());
            }
        }

        return foundResults;
    }
}