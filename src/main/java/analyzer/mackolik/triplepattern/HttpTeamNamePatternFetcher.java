package analyzer.mackolik.triplepattern;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fetches and analyzes the TEAM NAME pattern surrounding an unstarted match.
 *
 * Rules:
 *  - Up to 3 opponents BEFORE and up to 3 opponents AFTER the first unstarted match.
 *  - Direction (home/away) is IGNORED; only the opponent name matters.
 *  - Historical match is accepted ONLY if HT/FT = 1/2 or 2/1.
 *
 * Combination types checked (must all appear IN ORDER, contiguous):
 *   PREV3           – prev[-3], prev[-2], prev[-1]
 *   PREV3+NEXT1     – prev[-3], prev[-2], prev[-1], TARGET, next[+1]
 *   PREV3+NEXT2     – prev[-3...-1], TARGET, next[+1], next[+2]
 *   PREV3+NEXT3     – prev[-3...-1], TARGET, next[+1..+3]
 *   PREV2+NEXT1     – prev[-2], prev[-1], TARGET, next[+1]
 *   PREV2+NEXT2     – prev[-2], prev[-1], TARGET, next[+1], next[+2]
 *   PREV2+NEXT3     – prev[-2], prev[-1], TARGET, next[+1..+3]
 *   PREV1+NEXT1     – prev[-1], TARGET, next[+1]
 *   PREV1+NEXT2     – prev[-1], TARGET, next[+1], next[+2]
 *   PREV1+NEXT3     – prev[-1], TARGET, next[+1..+3]
 *   NEXT3           – next[+1], next[+2], next[+3]
 */
public class HttpTeamNamePatternFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpTeamNamePatternFetcher.class);
    private static final String BASE_URL      = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026";

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Step 1: Build the current-season TeamNamePattern for a team
     *         (opponents around the FIRST unstarted match).
     */
    public static TeamNamePattern buildCurrentPattern(CloseableHttpClient http, int teamId) throws IOException {

        String html = fetchHtml(http, String.format(BASE_URL, teamId, CURRENT_SEASON));
        if (html == null) throw new RuntimeException("Cannot fetch current season for team " + teamId);

        Document doc = Jsoup.parse(html);
        String teamName = extractTeamName(doc);

        Element tableBody = doc.selectFirst("#tblFixture > tbody");
        if (tableBody == null) {
            log.warn("No fixture table for team {}", teamId);
            return null;
        }

        // Collect ALL rows in order
        List<Element> rows = collectLeagueRows(tableBody);

        // Split into played / unstarted
        int unstartedIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");

            if (scoreEl == null) {
                // No anchor = likely upcoming (time shown instead of score)
                String cell5 = extractCell(row, "td:nth-child(5)");
                String home  = extractCell(row, "td:nth-child(3)");
                String away  = extractCell(row, "td:nth-child(7)");
                if (home != null && !home.isEmpty() && away != null && !away.isEmpty()) {
                    unstartedIdx = i;
                    break;
                }
                continue;
            }

            String score = scoreEl.text().trim();
            // "v" or empty or non-numeric = unplayed
            if (score.isEmpty() || score.equalsIgnoreCase("v")) {
                unstartedIdx = i;
                break;
            }
            // Normalize spaces: "3 - 1" → "3-1"
            String normalized = score.replaceAll("\\s*-\\s*", "-");
            String[] parts = normalized.split("-");
            if (parts.length != 2) { unstartedIdx = i; break; }
            try {
                Integer.parseInt(parts[0].trim());
                Integer.parseInt(parts[1].trim());
                // It's a valid score → played, continue
            } catch (NumberFormatException e) {
                unstartedIdx = i;
                break;
            }
        }

        if (unstartedIdx < 0) {
            log.warn("No unstarted match found for team {}", teamId);
            return null;
        }

        // Extract opponent names for played matches
        // prev: up to 3 matches BEFORE unstartedIdx (oldest first)
        List<String> prevOpponents = new ArrayList<>();
        for (int i = Math.max(0, unstartedIdx - 3); i < unstartedIdx; i++) {
            String opp = extractOpponent(rows.get(i), teamName);
            if (opp != null) prevOpponents.add(opp);
        }

        // next: up to 3 matches AFTER unstartedIdx (these may also be unplayed - extract team names anyway)
        List<String> nextOpponents = new ArrayList<>();
        for (int i = unstartedIdx + 1; i < Math.min(rows.size(), unstartedIdx + 4); i++) {
            Element row  = rows.get(i);
            String  home = extractCell(row, "td:nth-child(3)");
            String  away = extractCell(row, "td:nth-child(7)");
            if (home == null || away == null) continue;
            // Try to figure out which side is "our" team
            String norm = normalize(teamName);
            String opp;
            if (normalize(home).contains(norm) || norm.contains(normalize(home))) {
                opp = away;
            } else if (normalize(away).contains(norm) || norm.contains(normalize(away))) {
                opp = home;
            } else {
                // Fallback: pick whichever is non-empty
                opp = (!away.isEmpty()) ? away : home;
            }
            if (opp != null && !opp.isEmpty()) nextOpponents.add(opp);
        }

        // Target match teams
        String targetHome = extractCell(rows.get(unstartedIdx), "td:nth-child(3)");
        String targetAway = extractCell(rows.get(unstartedIdx), "td:nth-child(7)");

        log.info("Team {} ({}) | prev={} | target={} vs {} | next={}",
                teamId, teamName, prevOpponents, targetHome, targetAway, nextOpponents);

        return new TeamNamePattern(teamId, teamName, prevOpponents, nextOpponents, targetHome, targetAway);
    }

    /**
     * Step 2: Search a historical season for the same team-name sequence.
     *         Returns all matches that satisfy HT/FT = 1/2 or 2/1.
     */
    public static List<TeamNameMatchResult> searchHistoricalSeason(
            CloseableHttpClient http,
            TeamNamePattern pattern,
            String seasonYear,
            int teamId) throws IOException {

        List<TeamNameMatchResult> results = new ArrayList<>();
        String html = fetchHtml(http, String.format(BASE_URL, teamId, seasonYear));
        if (html == null) return results;

        Document doc  = Jsoup.parse(html);
        Element tbody = doc.selectFirst("#tblFixture > tbody");
        if (tbody == null) return results;

        List<Element> rows = collectLeagueRows(tbody);

        // Build a flat list of match data for the season
        List<MatchData> matches = new ArrayList<>();
        for (Element row : rows) {
            MatchData md = parseMatchData(row);
            if (md != null) matches.add(md);
        }

        log.debug("Season {} – {} parsed matches for team {}", seasonYear, matches.size(), teamId);

        // For every match index i (potential "target"), check all combinations
        for (int i = 0; i < matches.size(); i++) {
            MatchData target = matches.get(i);

            // Filter: only matches with HT/FT 1/2 or 2/1
            String htFt = computeHtFt(target.ftScore, target.htScore);
            if (htFt == null) continue;

            // Build surrounding opponent lists for this historical index
            List<String> histPrev = new ArrayList<>();
            for (int k = Math.max(0, i - 3); k < i; k++) {
                histPrev.add(opponentOf(matches.get(k), pattern.teamName));
            }
            List<String> histNext = new ArrayList<>();
            for (int k = i + 1; k < Math.min(matches.size(), i + 4); k++) {
                histNext.add(opponentOf(matches.get(k), pattern.teamName));
            }

            // Try every combination
            for (CombinationDef combo : COMBINATIONS) {
                if (combo.matches(pattern.prevOpponents, pattern.nextOpponents, histPrev, histNext)) {
                    TeamNameMatchResult res = new TeamNameMatchResult(
                            teamId, pattern.teamName, seasonYear,
                            combo.label,
                            subList(histPrev, combo.prevCount),
                            subList(histNext, combo.nextCount),
                            target.homeTeam, target.awayTeam,
                            target.ftScore, target.htScore,
                            htFt,
                            pattern.targetHomeTeam, pattern.targetAwayTeam);
                    results.add(res);
                    log.info("MATCH [{}] team={} season={} htFt={}", combo.label, pattern.teamName, seasonYear, htFt);
                }
            }
        }
        return results;
    }

    // -----------------------------------------------------------------------
    // Combination definitions
    // -----------------------------------------------------------------------

    private static final List<CombinationDef> COMBINATIONS = Arrays.asList(
            // Only prev sequences (no target in the sequence – we just check surroundings)
            new CombinationDef("PREV3",        3, 0),
            new CombinationDef("NEXT3",        0, 3),
            // Mixed – target is always the pivot
            new CombinationDef("PREV3+NEXT1",  3, 1),
            new CombinationDef("PREV3+NEXT2",  3, 2),
            new CombinationDef("PREV3+NEXT3",  3, 3),
            new CombinationDef("PREV2+NEXT1",  2, 1),
            new CombinationDef("PREV2+NEXT2",  2, 2),
            new CombinationDef("PREV2+NEXT3",  2, 3),
            new CombinationDef("PREV1+NEXT1",  1, 1),
            new CombinationDef("PREV1+NEXT2",  1, 2),
            new CombinationDef("PREV1+NEXT3",  1, 3)
    );

    /**
     * A combination definition: prevCount prev-opponents + nextCount next-opponents
     * must all appear IN ORDER in the historical data.
     */
    private static class CombinationDef {
        final String label;
        final int    prevCount;
        final int    nextCount;

        CombinationDef(String label, int prevCount, int nextCount) {
            this.label     = label;
            this.prevCount = prevCount;
            this.nextCount = nextCount;
        }

        boolean matches(List<String> curPrev, List<String> curNext,
                        List<String> histPrev, List<String> histNext) {
            // We need at least prevCount + nextCount opponents to match
            if (histPrev.size() < prevCount || histNext.size() < nextCount) return false;
            if (curPrev.size()  < prevCount || curNext.size()  < nextCount) return false;

            // Compare last `prevCount` of prev lists (in order, oldest→newest)
            for (int i = 0; i < prevCount; i++) {
                String cur  = normalize(curPrev.get(curPrev.size()   - prevCount + i));
                String hist = normalize(histPrev.get(histPrev.size() - prevCount + i));
                if (!cur.equals(hist)) return false;
            }
            // Compare first `nextCount` of next lists (in order)
            for (int i = 0; i < nextCount; i++) {
                String cur  = normalize(curNext.get(i));
                String hist = normalize(histNext.get(i));
                if (!cur.equals(hist)) return false;
            }
            return true;
        }
    }

    // -----------------------------------------------------------------------
    // MatchData helpers
    // -----------------------------------------------------------------------

    private static class MatchData {
        String homeTeam;
        String awayTeam;
        String ftScore;   // "2-1"
        String htScore;   // "1-0" or null
    }

    private static MatchData parseMatchData(Element row) {
        try {
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");
            if (scoreEl == null) return null;
            String score = scoreEl.text().trim();
            if (score.isEmpty() || !score.contains("-")) return null;
            // Skip unplayed markers like "v" or "- -"
            if (score.equalsIgnoreCase("v")) return null;

            // Normalize "3 - 1" → "3-1"
            String normalizedScore = score.replaceAll("\\s*-\\s*", "-");

            // Validate it's actually a score: "N-N"
            String[] parts = normalizedScore.split("-");
            if (parts.length != 2) return null;
            Integer.parseInt(parts[0].trim());
            Integer.parseInt(parts[1].trim());

            MatchData md = new MatchData();
            md.homeTeam = extractCell(row, "td:nth-child(3)");
            md.awayTeam = extractCell(row, "td:nth-child(7)");
            md.ftScore  = normalizedScore;
            String ht   = extractCell(row, "td:nth-child(9)");
            // Normalize HT score too
            if (ht != null && !ht.isEmpty()) {
                ht = ht.replaceAll("\\s*-\\s*", "-");
                md.htScore = ht;
            }
            return md;
        } catch (NumberFormatException e) {
            return null; // not a real score
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determines HT/FT type:
     * - "1/2" if home team won at HT but away team won at FT
     * - "2/1" if away team won at HT but home team won at FT
     * - null otherwise
     */
    static String computeHtFt(String ftScore, String htScore) {
        if (ftScore == null || htScore == null) return null;
        try {
            // Normalize "3 - 1" → "3-1"
            String ft = ftScore.replaceAll("\\s*-\\s*", "-");
            String ht = htScore.replaceAll("\\s*-\\s*", "-");

            String[] ftParts = ft.split("-");
            String[] htParts = ht.split("-");
            if (ftParts.length != 2 || htParts.length != 2) return null;

            int ftH = Integer.parseInt(ftParts[0].trim());
            int ftA = Integer.parseInt(ftParts[1].trim());
            int htH = Integer.parseInt(htParts[0].trim());
            int htA = Integer.parseInt(htParts[1].trim());

            boolean homeWinsHT = htH > htA;
            boolean awayWinsFT = ftA > ftH;
            boolean awayWinsHT = htA > htH;
            boolean homeWinsFT = ftH > ftA;

            if (homeWinsHT && awayWinsFT) return "1/2";
            if (awayWinsHT && homeWinsFT) return "2/1";
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns the opponent of the given team in a match. Falls back to home or away. */
    private static String opponentOf(MatchData md, String teamName) {
        if (md == null) return "?";
        String norm = normalize(teamName);
        if (normalize(md.homeTeam).contains(norm) || norm.contains(normalize(md.homeTeam))) {
            return md.awayTeam;
        }
        if (normalize(md.awayTeam).contains(norm) || norm.contains(normalize(md.awayTeam))) {
            return md.homeTeam;
        }
        // Can't tell – return both (this shouldn't normally happen)
        return md.homeTeam + "/" + md.awayTeam;
    }

    // -----------------------------------------------------------------------
    // HTML / parsing utilities
    // -----------------------------------------------------------------------

    private static List<Element> collectLeagueRows(Element tableBody) {
        List<Element> rows = new ArrayList<>();
        boolean firstLeague = false;

        for (Element row : tableBody.select("tr")) {
            if (row.hasClass("competition")) {
                if (!firstLeague) { firstLeague = true; continue; }
                else break; // stop at second competition header
            }
            if (firstLeague) rows.add(row);
        }

        // Fallback: if nothing collected via competition class, take all non-header rows
        if (rows.isEmpty()) {
            rows.addAll(tableBody.select("tr:not(.competition)"));
        }
        return rows;
    }

    private static String extractOpponent(Element row, String teamName) {
        String home = extractCell(row, "td:nth-child(3)");
        String away = extractCell(row, "td:nth-child(7)");
        if (home == null || away == null) return null;
        String norm = normalize(teamName);
        if (normalize(home).contains(norm) || norm.contains(normalize(home))) return away;
        if (normalize(away).contains(norm) || norm.contains(normalize(away))) return home;
        return away; // default
    }

    private static String extractOpponentAny(Element row) {
        // For upcoming matches we don't know which side is "us" – just return away team name
        String home = extractCell(row, "td:nth-child(3)");
        String away = extractCell(row, "td:nth-child(7)");
        if (home == null && away == null) return null;
        return (away != null && !away.isEmpty()) ? away : home;
    }

    private static String extractCell(Element row, String cssSelector) {
        Element el = row.selectFirst(cssSelector);
        return el != null ? el.text().trim() : null;
    }

    private static String extractTeamName(Document doc) {
        try {
            Element title = doc.selectFirst("title");
            if (title != null) return title.text().split("-")[0].trim();
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private static String fetchHtml(CloseableHttpClient http, String url) throws IOException {
        HttpGet req = new HttpGet(url);
        req.addHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/91.0 Safari/537.36");
        log.debug("GET {}", url);
        try (CloseableHttpResponse resp = http.execute(req)) {
            int code = resp.getStatusLine().getStatusCode();
            if (code == 200) return EntityUtils.toString(resp.getEntity());
            log.warn("HTTP {} for {}", code, url);
            return null;
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9]", ""); // remove spaces, dots, special chars
    }

    private static List<String> subList(List<String> list, int count) {
        int from = Math.max(0, list.size() - count);
        return new ArrayList<>(list.subList(from, list.size()));
    }
}