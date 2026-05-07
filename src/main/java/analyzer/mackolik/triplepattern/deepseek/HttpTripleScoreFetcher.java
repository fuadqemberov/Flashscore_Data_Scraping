package analyzer.mackolik.triplepattern.deepseek;


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
import java.util.Arrays;
import java.util.List;

public class HttpTripleScoreFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpTripleScoreFetcher.class);
    private static final String BASE_URL = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026";

    // -----------------------------------------------------------------------
    // Ortak HTTP ve yardımcı metotlar
    // -----------------------------------------------------------------------
    private static String fetchHtml(CloseableHttpClient httpClient, String url) throws IOException {
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        log.debug("Fetching URL: {}", url);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                return EntityUtils.toString(response.getEntity());
            } else {
                log.warn("Non-200 status for {}: {}", url, statusCode);
                return null;
            }
        }
    }

    private static String normalizeScore(String score) {
        String[] parts = score.split("-");
        if (parts.length != 2) return score;
        try {
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            return Math.min(a, b) + "-" + Math.max(a, b);
        } catch (NumberFormatException e) {
            return score;
        }
    }

    private static boolean matchesTriplePatternUnordered(String s1, String s2, String s3,
                                                         ThreeMatchPattern pattern) {
        String c1 = normalizeScore(s1);
        String c2 = normalizeScore(s2);
        String c3 = normalizeScore(s3);
        String p1 = normalizeScore(pattern.score1);
        String p2 = normalizeScore(pattern.score2);
        String p3 = normalizeScore(pattern.score3);
        List<String> candidates = new ArrayList<>(Arrays.asList(c1, c2, c3));
        for (String ps : Arrays.asList(p1, p2, p3)) {
            boolean found = false;
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).equals(ps)) {
                    candidates.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    // =======================================================================
    // ESKİ ÖZELLİK 1: Mevcut sezon son 3 maç skoru desenini bulma
    // =======================================================================
    public static ThreeMatchPattern findCurrentSeasonLastThreeMatches(
            CloseableHttpClient httpClient, int teamId) throws IOException {

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

        List<String> allScores     = new ArrayList<>();
        List<String> allHomeTeams  = new ArrayList<>();
        List<String> allAwayTeams  = new ArrayList<>();
        String nextHomeTeam = null;
        String nextAwayTeam = null;
        String teamName = "Unknown";

        try {
            Element titleEl = doc.selectFirst("title");
            if (titleEl != null) {
                teamName = titleEl.text().split("-")[0].trim();
            }
        } catch (Exception e) {
            log.warn("Could not extract team name for ID {}", teamId);
        }

        Elements rows = tableBody.select("tr");
        boolean foundUnplayed = false;

        for (Element row : rows) {
            if (row.selectFirst("td[itemprop=sportsevent]") != null) break;
            if (row.hasClass("competition")) continue;

            try {
                Element scoreElement    = row.selectFirst("td:nth-child(5) b a");
                Element homeTeamElement = row.selectFirst("td:nth-child(3)");
                Element awayTeamElement = row.selectFirst("td:nth-child(7)");

                if (scoreElement != null && homeTeamElement != null && awayTeamElement != null) {
                    String score    = scoreElement.text().trim();
                    String homeTeam = homeTeamElement.text().trim();
                    String awayTeam = awayTeamElement.text().trim();

                    if (!foundUnplayed && !score.isEmpty() && !score.equalsIgnoreCase("v") && score.contains("-")) {
                        allScores.add(score);
                        allHomeTeams.add(homeTeam);
                        allAwayTeams.add(awayTeam);
                    } else if (!foundUnplayed) {
                        nextHomeTeam = homeTeam;
                        nextAwayTeam = awayTeam;
                        foundUnplayed = true;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Error parsing a match row for team {}: {}", teamId, e.getMessage());
            }
        }

        if (allScores.size() >= 3) {
            int last = allScores.size() - 1;
            return new ThreeMatchPattern(
                    allScores.get(last - 2), allScores.get(last - 1), allScores.get(last),
                    allHomeTeams.get(last - 2), allAwayTeams.get(last - 2),
                    allHomeTeams.get(last - 1), allAwayTeams.get(last - 1),
                    allHomeTeams.get(last),     allAwayTeams.get(last),
                    teamName,
                    nextHomeTeam, nextAwayTeam);
        } else {
            throw new RuntimeException(
                    "Son üç maç skoru bulunamadı for team ID: " + teamId + "! Found: " + allScores.size());
        }
    }

    // =======================================================================
    // ESKİ ÖZELLİK 2: Geçmiş sezonda üçlü skor deseni arama
    // =======================================================================
    public static List<TripleMatchResult> findTripleScorePattern(
            CloseableHttpClient httpClient,
            ThreeMatchPattern pattern,
            String seasonYear,
            int teamId) throws IOException {

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
        boolean collectRows   = false;

        for (Element row : allTableRows) {
            if (row.hasClass("competition")) {
                if (!isFirstLeague) break;
                isFirstLeague = false;
                collectRows   = true;
                continue;
            }
            if (collectRows && row.selectFirst("td:nth-child(5) b a") != null) {
                leagueMatches.add(row);
            }
        }

        log.debug("Collected {} potential league match rows for season {}", leagueMatches.size(), seasonYear);

        for (int i = 0; i < leagueMatches.size() - 2; i++) {
            Element row1 = leagueMatches.get(i);
            Element row2 = leagueMatches.get(i + 1);
            Element row3 = leagueMatches.get(i + 2);

            try {
                String score1 = row1.selectFirst("td:nth-child(5) b a").text().trim();
                String score2 = row2.selectFirst("td:nth-child(5) b a").text().trim();
                String score3 = row3.selectFirst("td:nth-child(5) b a").text().trim();

                if (!matchesTriplePatternUnordered(score1, score2, score3, pattern)) continue;

                log.debug("Triple pattern match found for season {} at index {}", seasonYear, i);

                String homeTeam1 = row1.selectFirst("td:nth-child(3)").text().trim();
                String awayTeam1 = row1.selectFirst("td:nth-child(7)").text().trim();
                String htScore1  = row1.selectFirst("td:nth-child(9)").text().trim();

                String homeTeam2 = row2.selectFirst("td:nth-child(3)").text().trim();
                String awayTeam2 = row2.selectFirst("td:nth-child(7)").text().trim();
                String htScore2  = row2.selectFirst("td:nth-child(9)").text().trim();

                String homeTeam3 = row3.selectFirst("td:nth-child(3)").text().trim();
                String awayTeam3 = row3.selectFirst("td:nth-child(7)").text().trim();
                String htScore3  = row3.selectFirst("td:nth-child(9)").text().trim();

                TripleMatchResult result = new TripleMatchResult(
                        homeTeam1, awayTeam1, score1,
                        homeTeam2, awayTeam2, score2,
                        homeTeam3, awayTeam3, score3,
                        seasonYear, pattern);

                result.htScore1 = htScore1.isEmpty() ? null : htScore1;
                result.htScore2 = htScore2.isEmpty() ? null : htScore2;
                result.htScore3 = htScore3.isEmpty() ? null : htScore3;

                // Önceki maç
                if (i > 0) {
                    Element prev = leagueMatches.get(i - 1);
                    try {
                        String prevScore  = prev.selectFirst("td:nth-child(5) b a").text().trim();
                        String prevHT     = prev.selectFirst("td:nth-child(9)").text().trim();
                        String prevHome   = prev.selectFirst("td:nth-child(3)").text().trim();
                        String prevAway   = prev.selectFirst("td:nth-child(7)").text().trim();
                        result.previousMatchScore = prevHome + " " + prevScore + " " + prevAway;
                        result.previousHTScore    = prevHT.isEmpty() ? null : prevHT;
                    } catch (Exception e) {
                        log.warn("Could not parse preceding match at index {} for season {}", i - 1, seasonYear);
                        result.previousMatchScore = "Hata (Önceki)";
                    }
                } else {
                    result.previousMatchScore = "Bilgi Yok (İlk Maç)";
                }

                // Sonraki maç
                if (i + 3 < leagueMatches.size()) {
                    Element next = leagueMatches.get(i + 3);
                    try {
                        String nextScore = next.selectFirst("td:nth-child(5) b a").text().trim();
                        String nextHT    = next.selectFirst("td:nth-child(9)").text().trim();
                        String nextHome  = next.selectFirst("td:nth-child(3)").text().trim();
                        String nextAway  = next.selectFirst("td:nth-child(7)").text().trim();
                        result.nextMatchScore = nextHome + " " + nextScore + " " + nextAway;
                        result.nextHTScore    = nextHT.isEmpty() ? null : nextHT;
                    } catch (Exception e) {
                        log.warn("Could not parse following match at index {} for season {}", i + 3, seasonYear);
                        result.nextMatchScore = "Hata (Sonraki)";
                    }
                } else {
                    result.nextMatchScore = "Bilgi Yok (Son Maç)";
                }

                foundResults.add(result);
                log.info("Triple pattern match added for team {}, season {}", teamId, seasonYear);

            } catch (Exception e) {
                log.error("Error analyzing triple match at index {} for team {}, season {}: {}",
                        i, teamId, seasonYear, e.getMessage());
            }
        }

        return foundResults;
    }

    // =======================================================================
    // YENİ ÖZELLİK: RAKİP DİZİSİ + HT/FT COMEBACK (2/1 – 1/2)
    // =======================================================================

    /**
     * HT/FT olarak 2/1 veya 1/2 (comeback) olup olmadığını kontrol eder.
     */
    private static boolean isComeback(String htScore, String ftScore) {
        if (htScore == null || ftScore == null) return false;
        String[] htParts = htScore.split("-");
        String[] ftParts = ftScore.split("-");
        if (htParts.length != 2 || ftParts.length != 2) return false;
        try {
            int htHome = Integer.parseInt(htParts[0].trim());
            int htAway = Integer.parseInt(htParts[1].trim());
            int ftHome = Integer.parseInt(ftParts[0].trim());
            int ftAway = Integer.parseInt(ftParts[1].trim());

            boolean awayLeadingHT = htHome < htAway;
            boolean homeWinsFT    = ftHome > ftAway;
            boolean homeLeadingHT = htHome > htAway;
            boolean awayWinsFT    = ftHome < ftAway;

            return (awayLeadingHT && homeWinsFT) || (homeLeadingHT && awayWinsFT);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Mevcut sezonda takımın oynanmamış ilk maçını ve etrafındaki rakipleri çıkarır.
     */
    public static CurrentOpponentSequence findCurrentSeasonOpponentSequence(
            CloseableHttpClient httpClient, int teamId) throws IOException {

        String currentSeasonUrl = String.format(BASE_URL, teamId, CURRENT_SEASON);
        String html = fetchHtml(httpClient, currentSeasonUrl);
        if (html == null) throw new RuntimeException("Sayfa alınamadı: " + currentSeasonUrl);

        Document doc = Jsoup.parse(html);
        Element tableBody = doc.selectFirst("#tblFixture > tbody");
        if (tableBody == null) throw new RuntimeException("Fikstür tablosu bulunamadı: " + teamId);

        String teamName = "Unknown";
        try {
            Element titleEl = doc.selectFirst("title");
            if (titleEl != null) teamName = titleEl.text().split("-")[0].trim();
        } catch (Exception e) { /* ignore */ }

        List<String> lastOpponents = new ArrayList<>();
        String upcomingHome = null;
        String upcomingAway = null;
        List<String> nextOpponents = new ArrayList<>();

        Elements rows = tableBody.select("tr");
        boolean foundUpcoming = false;

        for (Element row : rows) {
            if (row.selectFirst("td[itemprop=sportsevent]") != null) break;
            if (row.hasClass("competition")) continue;

            Element homeEl = row.selectFirst("td:nth-child(3)");
            Element awayEl = row.selectFirst("td:nth-child(7)");
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");
            if (homeEl == null || awayEl == null) continue;

            String home = homeEl.text().trim();
            String away = awayEl.text().trim();
            String score = (scoreEl != null) ? scoreEl.text().trim() : "";

            boolean isHome = home.equalsIgnoreCase(teamName);
            boolean isAway = away.equalsIgnoreCase(teamName);
            String opponent = isHome ? away : (isAway ? home : null);

            if (!foundUpcoming) {
                // Skor boş, "v" veya içinde tire yoksa → oynanmamış
                if (score.isEmpty() || score.equalsIgnoreCase("v") || !score.contains("-")) {
                    foundUpcoming = true;
                    upcomingHome = home;
                    upcomingAway = away;
                } else {
                    if (opponent != null) {
                        lastOpponents.add(opponent);
                        if (lastOpponents.size() > 3) {
                            lastOpponents.remove(0);
                        }
                    }
                }
            } else {
                if (opponent != null && nextOpponents.size() < 3) {
                    nextOpponents.add(opponent);
                }
                if (nextOpponents.size() >= 3) break;
            }
        }

        if (upcomingHome == null) {
            throw new RuntimeException("Oynanmamış maç bulunamadı: " + teamId);
        }

        return new CurrentOpponentSequence(teamName, upcomingHome, upcomingAway,
                new ArrayList<>(lastOpponents), new ArrayList<>(nextOpponents));
    }

    /**
     * Bir sezonu tarar, mevcut rakip dizisi patternleriyle eşleşen ve
     * HT/FT 2/1 – 1/2 olan geçmiş maçları döndürür.
     */
    public static List<OpponentSequenceMatch> findOpponentSequencePatterns(
            CloseableHttpClient httpClient, int teamId, String seasonYear,
            CurrentOpponentSequence currentSeq) throws IOException {

        List<OpponentSequenceMatch> results = new ArrayList<>();
        String seasonUrl = String.format(BASE_URL, teamId, seasonYear);
        String html = fetchHtml(httpClient, seasonUrl);
        if (html == null) return results;

        Document doc = Jsoup.parse(html);
        Elements allTableRows = doc.select("table tbody tr");

        // İlk lig tablosundaki maçları topla
        List<Element> leagueRows = new ArrayList<>();
        boolean isFirstLeague = true;
        boolean collect = false;
        for (Element row : allTableRows) {
            if (row.hasClass("competition")) {
                if (!isFirstLeague) break;
                isFirstLeague = false;
                collect = true;
                continue;
            }
            if (collect && row.selectFirst("td:nth-child(5) b a") != null) {
                leagueRows.add(row);
            }
        }

        // Tüm maçları MatchRecord listesine çevir
        List<MatchRecord> matches = new ArrayList<>();
        for (Element row : leagueRows) {
            String home = row.selectFirst("td:nth-child(3)").text().trim();
            String away = row.selectFirst("td:nth-child(7)").text().trim();
            String ftScore = row.selectFirst("td:nth-child(5) b a").text().trim();
            String htScore = row.selectFirst("td:nth-child(9)").text().trim();
            if (ftScore.isEmpty() || !ftScore.contains("-")) continue;
            matches.add(new MatchRecord(home, away, ftScore, htScore.isEmpty() ? null : htScore));
        }

        // Kalıp oluşturma: Tüm dizi [son rakipler..., upcoming, gelecek rakipler...]
        List<String> fullSequence = new ArrayList<>();
        fullSequence.addAll(currentSeq.lastOpponents);
        fullSequence.add("TARGET");  // upcoming maçın rakibi buraya gelecek
        fullSequence.addAll(currentSeq.nextOpponents);
        int targetIndex = currentSeq.lastOpponents.size();

        // Bitişik alt diziler (boyut >= 3, TARGET'i içeren)
        List<SequencePattern> patterns = new ArrayList<>();
        int n = fullSequence.size();
        for (int len = 3; len <= n; len++) {
            for (int start = 0; start <= n - len; start++) {
                int end = start + len - 1;
                if (start <= targetIndex && targetIndex <= end) {
                    List<String> sub = fullSequence.subList(start, end + 1);
                    int localTargetIdx = targetIndex - start;
                    patterns.add(new SequencePattern(new ArrayList<>(sub), localTargetIdx));
                }
            }
        }

        String teamName = currentSeq.teamName;

        // Her maçı potansiyel hedef maç olarak dene
        for (int i = 0; i < matches.size(); i++) {
            MatchRecord targetMatch = matches.get(i);
            if (!isComeback(targetMatch.htScore, targetMatch.ftScore)) continue;

            // Hedef maçtaki rakip (takımın karşısındaki)
            String targetOpponent;
            if (targetMatch.home.equalsIgnoreCase(teamName)) {
                targetOpponent = targetMatch.away;
            } else if (targetMatch.away.equalsIgnoreCase(teamName)) {
                targetOpponent = targetMatch.home;
            } else {
                continue;
            }

            // Kalıpları dene (her seferinde temiz kopya kullan)
            for (SequencePattern pattern : patterns) {
                List<String> patternOpponents = new ArrayList<>(pattern.opponents);
                // TARGET yerine bu maçın rakibini koy
                if (patternOpponents.get(pattern.targetIndex).equals("TARGET")) {
                    patternOpponents.set(pattern.targetIndex, targetOpponent);
                }

                int neededBefore = pattern.targetIndex;
                int neededAfter = patternOpponents.size() - 1 - pattern.targetIndex;
                if (i - neededBefore < 0 || i + neededAfter >= matches.size()) continue;

                boolean match = true;
                StringBuilder seqBuilder = new StringBuilder();
                for (int j = 0; j < patternOpponents.size(); j++) {
                    int idx = i - pattern.targetIndex + j;
                    MatchRecord rec = matches.get(idx);
                    String recOpp;
                    if (rec.home.equalsIgnoreCase(teamName)) recOpp = rec.away;
                    else if (rec.away.equalsIgnoreCase(teamName)) recOpp = rec.home;
                    else { match = false; break; }

                    if (!recOpp.equalsIgnoreCase(patternOpponents.get(j))) {
                        match = false;
                        break;
                    }
                    if (j > 0) seqBuilder.append(" → ");
                    seqBuilder.append(recOpp);
                }

                if (match) {
                    String prevSum = null, nextSum = null;
                    if (i > 0) {
                        MatchRecord prv = matches.get(i - 1);
                        prevSum = prv.home + " " + prv.ftScore + " " + prv.away
                                + (prv.htScore != null ? " (HT " + prv.htScore + ")" : "");
                    }
                    if (i + 1 < matches.size()) {
                        MatchRecord nxt = matches.get(i + 1);
                        nextSum = nxt.home + " " + nxt.ftScore + " " + nxt.away
                                + (nxt.htScore != null ? " (HT " + nxt.htScore + ")" : "");
                    }

                    results.add(new OpponentSequenceMatch(
                            seasonYear,
                            targetMatch.home, targetMatch.away,
                            targetMatch.ftScore, targetMatch.htScore,
                            List.of(seqBuilder.toString().split(" → ")),
                            prevSum, nextSum
                    ));
                }
            }
        }

        return results;
    }

    // -----------------------------------------------------------------------
    // İç yardımcı sınıflar
    // -----------------------------------------------------------------------
    private static class MatchRecord {
        String home, away, ftScore, htScore;
        MatchRecord(String home, String away, String ftScore, String htScore) {
            this.home = home;
            this.away = away;
            this.ftScore = ftScore;
            this.htScore = htScore;
        }
    }

    private static class SequencePattern {
        List<String> opponents;
        int targetIndex;
        SequencePattern(List<String> opponents, int targetIndex) {
            this.opponents = opponents;
            this.targetIndex = targetIndex;
        }
    }
}