package analyzer.mackolik.triplepattern;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Fetches and analyzes the TEAM NAME pattern surrounding an unstarted match.
 *
 * FIX SUMMARY (v3):
 *  1. detectTeamNameFromRows() — team name is detected from the fixture table itself
 *     (most frequent name in home/away columns), NOT from the page title.
 *     Fixes: title says "Polonia Warszawa U19" but table says "P.Warszawa U19".
 *  2. searchHistoricalSeason() uses detectTeamNameFromRows() per season so the
 *     correct abbreviation is used for every historical season too.
 *  3. teamsMatch() — multi-strategy token-level matching:
 *     exact → contains → shared token → prefix abbreviation.
 *     Fixes: "S.Bratislava" vs "Slovan Bratislava", "Atl." vs "Atletico", etc.
 *  4. collectLeagueRows() — robust fallback when competition CSS class is absent.
 */
public class HttpTeamNamePatternFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpTeamNamePatternFetcher.class);
    private static final String BASE_URL       = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026";

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Step 1: Build the current-season TeamNamePattern for a team.
     */
    public static TeamNamePattern buildCurrentPattern(CloseableHttpClient http, int teamId) throws IOException {

        System.out.println("       [ID:" + teamId + "] Mevcut sezon (2025/2026) fetch ediliyor...");
        String html = fetchHtml(http, String.format(BASE_URL, teamId, CURRENT_SEASON));
        if (html == null) throw new RuntimeException("Cannot fetch current season for team " + teamId);

        Document doc = Jsoup.parse(html);

        Element tableBody = doc.selectFirst("#tblFixture > tbody");
        if (tableBody == null) {
            log.warn("No fixture table for team {}", teamId);
            return null;
        }

        List<Element> rows = collectLeagueRows(tableBody);

        // ── Detect team name FROM THE ROWS (not the page title) ─────────────
        // The page title may use the full official name ("Polonia Warszawa U19")
        // while the fixture table uses an abbreviation ("P.Warszawa U19").
        // We count how often each name appears in home/away columns — our team
        // appears in every match, so it will have the highest frequency.
        String titleFallback = extractTeamName(doc);
        String teamName = detectTeamNameFromRows(rows, titleFallback);
        System.out.println("       [ID:" + teamId + "] Takım adı (tablodan): '" + teamName + "'");

        // ── Find first unstarted match ───────────────────────────────────────
        int unstartedIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            Element row     = rows.get(i);
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");

            if (scoreEl == null) {
                String home = extractCell(row, "td:nth-child(3)");
                String away = extractCell(row, "td:nth-child(7)");
                if (home != null && !home.isEmpty() && away != null && !away.isEmpty()) {
                    unstartedIdx = i;
                    break;
                }
                continue;
            }

            String score      = scoreEl.text().trim();
            String normalized = score.replaceAll("\\s*-\\s*", "-");
            String[] parts    = normalized.split("-");

            if (score.isEmpty() || score.equalsIgnoreCase("v") || parts.length != 2) {
                unstartedIdx = i;
                break;
            }
            try {
                Integer.parseInt(parts[0].trim());
                Integer.parseInt(parts[1].trim());
                // valid score → played, continue
            } catch (NumberFormatException e) {
                unstartedIdx = i;
                break;
            }
        }

        if (unstartedIdx < 0) {
            System.out.println("       ⚠️  Başlamamış maç bulunamadı");
            log.warn("No unstarted match found for team {}", teamId);
            return null;
        }

        // ── Collect prev opponents (up to 3, oldest first) ──────────────────
        List<String> prevOpponents = new ArrayList<>();
        for (int i = Math.max(0, unstartedIdx - 3); i < unstartedIdx; i++) {
            String opp = extractOpponent(rows.get(i), teamName);
            if (opp != null && !opp.isEmpty()) prevOpponents.add(opp);
        }

        // ── Collect next opponents (up to 3) ────────────────────────────────
        List<String> nextOpponents = new ArrayList<>();
        for (int i = unstartedIdx + 1; i < Math.min(rows.size(), unstartedIdx + 4); i++) {
            Element row  = rows.get(i);
            String  home = extractCell(row, "td:nth-child(3)");
            String  away = extractCell(row, "td:nth-child(7)");
            if (home == null || away == null) continue;
            String opp;
            if (teamsMatch(home, teamName)) {
                opp = away;
            } else if (teamsMatch(away, teamName)) {
                opp = home;
            } else {
                opp = (!away.isEmpty()) ? away : home;
            }
            if (opp != null && !opp.isEmpty()) nextOpponents.add(opp);
        }

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

        Document doc   = Jsoup.parse(html);
        Element  tbody = doc.selectFirst("#tblFixture > tbody");
        if (tbody == null) return results;

        List<Element> rows = collectLeagueRows(tbody);

        // ── Detect team name as it appears IN THIS SEASON'S rows ─────────────
        // The same team may be abbreviated differently across seasons.
        // Always detect from the current page's rows, not from pattern.teamName.
        String histTeamName = detectTeamNameFromRows(rows, pattern.teamName);
        log.debug("Season {} histTeamName='{}' (pattern='{}')", seasonYear, histTeamName, pattern.teamName);

        List<MatchData> matches = new ArrayList<>();
        for (Element row : rows) {
            MatchData md = parseMatchData(row);
            if (md != null) matches.add(md);
        }

        if (matches.isEmpty()) return results;

        for (int i = 0; i < matches.size(); i++) {
            MatchData target = matches.get(i);

            // ── HT/FT filter: only 1/2 or 2/1 ──────────────────────────────
            String htFt = computeHtFt(target.ftScore, target.htScore);
            if (htFt == null) continue;

            // ── Build historical surrounding opponent lists ───────────────
            List<String> histPrev = new ArrayList<>();
            for (int k = Math.max(0, i - 3); k < i; k++) {
                histPrev.add(opponentOf(matches.get(k), histTeamName));
            }
            List<String> histNext = new ArrayList<>();
            for (int k = i + 1; k < Math.min(matches.size(), i + 4); k++) {
                histNext.add(opponentOf(matches.get(k), histTeamName));
            }

            // ── Try every combination ────────────────────────────────────
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
            new CombinationDef("PREV3",       3, 0),
            new CombinationDef("NEXT3",       0, 3),
            new CombinationDef("PREV3+NEXT1", 3, 1),
            new CombinationDef("PREV3+NEXT2", 3, 2),
            new CombinationDef("PREV3+NEXT3", 3, 3),
            new CombinationDef("PREV2+NEXT1", 2, 1),
            new CombinationDef("PREV2+NEXT2", 2, 2),
            new CombinationDef("PREV2+NEXT3", 2, 3),
            new CombinationDef("PREV1+NEXT1", 1, 1),
            new CombinationDef("PREV1+NEXT2", 1, 2),
            new CombinationDef("PREV1+NEXT3", 1, 3)
    );

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
            if (histPrev.size() < prevCount || histNext.size() < nextCount) return false;
            if (curPrev.size()  < prevCount || curNext.size()  < nextCount) return false;

            for (int i = 0; i < prevCount; i++) {
                String cur  = curPrev.get(curPrev.size()   - prevCount + i);
                String hist = histPrev.get(histPrev.size() - prevCount + i);
                if (!teamsMatch(cur, hist)) return false;
            }
            for (int i = 0; i < nextCount; i++) {
                String cur  = curNext.get(i);
                String hist = histNext.get(i);
                if (!teamsMatch(cur, hist)) return false;
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
        String ftScore;
        String htScore;
    }

    private static MatchData parseMatchData(Element row) {
        try {
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");
            if (scoreEl == null) return null;
            String score = scoreEl.text().trim();
            if (score.isEmpty() || !score.contains("-")) return null;
            if (score.equalsIgnoreCase("v")) return null;

            String normalizedScore = score.replaceAll("\\s*-\\s*", "-");
            String[] parts = normalizedScore.split("-");
            if (parts.length != 2) return null;
            Integer.parseInt(parts[0].trim());
            Integer.parseInt(parts[1].trim());

            MatchData md = new MatchData();
            md.homeTeam = extractCell(row, "td:nth-child(3)");
            md.awayTeam = extractCell(row, "td:nth-child(7)");
            md.ftScore  = normalizedScore;
            String ht   = extractCell(row, "td:nth-child(9)");
            if (ht != null && !ht.isEmpty()) {
                md.htScore = ht.replaceAll("\\s*-\\s*", "-");
            }
            return md;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    static String computeHtFt(String ftScore, String htScore) {
        if (ftScore == null || htScore == null) return null;
        try {
            String ft = ftScore.replaceAll("\\s*-\\s*", "-");
            String ht = htScore.replaceAll("\\s*-\\s*", "-");

            String[] ftParts = ft.split("-");
            String[] htParts = ht.split("-");
            if (ftParts.length != 2 || htParts.length != 2) return null;

            int ftH = Integer.parseInt(ftParts[0].trim());
            int ftA = Integer.parseInt(ftParts[1].trim());
            int htH = Integer.parseInt(htParts[0].trim());
            int htA = Integer.parseInt(htParts[1].trim());

            if (htH > htA && ftA > ftH) return "1/2";
            if (htA > htH && ftH > ftA) return "2/1";
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns the opponent of the given team in a match. */
    private static String opponentOf(MatchData md, String teamName) {
        if (md == null) return "?";
        if (teamsMatch(md.homeTeam, teamName)) return md.awayTeam;
        if (teamsMatch(md.awayTeam, teamName)) return md.homeTeam;
        // Could not identify our team's side — return combined (signals a mismatch)
        return md.homeTeam + "/" + md.awayTeam;
    }

    // -----------------------------------------------------------------------
    // HTML / parsing utilities
    // -----------------------------------------------------------------------

    /**
     * Detects the team name exactly as it appears in the fixture rows.
     *
     * Counts occurrences of every home/away name across all rows.
     * Our team appears in every match, so it will have the highest frequency.
     * Falls back to titleFallback if detection fails.
     */
    private static String detectTeamNameFromRows(List<Element> rows, String titleFallback) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (Element row : rows) {
            String home = extractCell(row, "td:nth-child(3)");
            String away = extractCell(row, "td:nth-child(7)");
            if (home != null && !home.isEmpty()) freq.merge(home, 1, Integer::sum);
            if (away != null && !away.isEmpty()) freq.merge(away, 1, Integer::sum);
        }

        if (freq.isEmpty()) return titleFallback;

        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }

        // Our team should appear in at least half the rows
        if (best != null && bestCount >= Math.max(1, rows.size() / 2)) {
            log.debug("detectTeamNameFromRows → '{}' (count={}/{})", best, bestCount, rows.size());
            return best;
        }

        return titleFallback;
    }

    private static List<Element> collectLeagueRows(Element tableBody) {
        List<Element> rows = new ArrayList<>();
        boolean inFirstLeague = false;

        for (Element row : tableBody.select("tr")) {
            if (row.hasClass("competition")) {
                if (!inFirstLeague) {
                    inFirstLeague = true;
                    continue;
                } else {
                    break;
                }
            }
            if (inFirstLeague) rows.add(row);
        }

        // Fallback: no competition CSS class — collect all rows with a team name
        if (rows.isEmpty()) {
            log.debug("competition-class detection found nothing, using fallback row collector");
            for (Element row : tableBody.select("tr")) {
                String home = extractCell(row, "td:nth-child(3)");
                if (home != null && !home.isEmpty()) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static String extractOpponent(Element row, String teamName) {
        String home = extractCell(row, "td:nth-child(3)");
        String away = extractCell(row, "td:nth-child(7)");
        if (home == null || away == null) return null;
        if (teamsMatch(home, teamName)) return away;
        if (teamsMatch(away, teamName)) return home;
        return away; // default fallback
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

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setConnectionRequestTimeout(10000)
                .setSocketTimeout(15000)
                .build();
        req.setConfig(config);

        log.debug("GET {}", url);
        try (CloseableHttpResponse resp = http.execute(req)) {
            int code = resp.getStatusLine().getStatusCode();
            if (code == 200) return EntityUtils.toString(resp.getEntity());
            log.warn("HTTP {} for {}", code, url);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Team name matching
    // -----------------------------------------------------------------------

    /**
     * Normalizes a team name: lowercase, accent removal, strip non-alphanumeric.
     */
    private static String normalize(String s) {
        if (s == null) return "";
        String ascii = s
                .replace("ı", "i").replace("İ", "i")
                .replace("ğ", "g").replace("Ğ", "g")
                .replace("ş", "s").replace("Ş", "s")
                .replace("ç", "c").replace("Ç", "c")
                .replace("ö", "o").replace("Ö", "o")
                .replace("ü", "u").replace("Ü", "u")
                .replace("é", "e").replace("á", "a")
                .replace("ó", "o").replace("ú", "u")
                .replace("ñ", "n").replace("ã", "a");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Returns true if teamA and teamB refer to the same club.
     *
     * Strategy:
     *  1. Exact normalized match
     *  2. One normalized string contains the other
     *  3. Any token of A (len>=3) exactly matches any token of B
     *  4. Any token of A is a prefix of any token of B (len>=4), or vice versa
     */
    static boolean teamsMatch(String teamA, String teamB) {
        if (teamA == null || teamB == null) return false;
        String a = normalize(teamA);
        String b = normalize(teamB);
        if (a.isEmpty() || b.isEmpty()) return false;

        // 1. Exact
        if (a.equals(b)) return true;

        // 2. One contains the other
        if (a.contains(b) || b.contains(a)) return true;

        // 3 & 4. Token-level matching
        List<String> tokensA = tokens(teamA);
        List<String> tokensB = tokens(teamB);

        for (String tA : tokensA) {
            String nA = normalize(tA);
            if (nA.length() < 3) continue;
            for (String tB : tokensB) {
                String nB = normalize(tB);
                if (nB.length() < 3) continue;
                if (nA.equals(nB)) return true;
                if (nA.length() >= 4 && nB.startsWith(nA)) return true;
                if (nB.length() >= 4 && nA.startsWith(nB)) return true;
            }
        }

        return false;
    }

    /** Splits a name into whitespace-delimited tokens, stripping punctuation. */
    private static List<String> tokens(String name) {
        List<String> result = new ArrayList<>();
        if (name == null) return result;
        for (String part : name.trim().split("\\s+")) {
            String clean = part.replaceAll("[^a-zA-Z0-9]", "");
            if (!clean.isEmpty()) result.add(clean);
        }
        return result;
    }

    private static List<String> subList(List<String> list, int count) {
        int from = Math.max(0, list.size() - count);
        return new ArrayList<>(list.subList(from, list.size()));
    }
}