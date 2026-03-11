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
import java.util.Set;

public class OnlyLeagueScraper {

    private static final Logger log = LoggerFactory.getLogger(OnlyLeagueScraper.class);
    private static final String BASE_URL = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026";
    private static final Set<String> COMEBACK_RESULTS = Set.of("2/1", "1/2");

    private static String fetchHtml(CloseableHttpClient httpClient, String url) throws IOException {
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) return EntityUtils.toString(response.getEntity());
            return null;
        }
    }

    public static MatchPattern findCurrentSeasonLastTwoMatches(CloseableHttpClient httpClient, int teamId)
            throws IOException {

        String html = fetchHtml(httpClient, String.format(BASE_URL, teamId, CURRENT_SEASON));
        if (html == null) throw new RuntimeException("Sayfa alinamadi: " + teamId);

        Document doc = Jsoup.parse(html);
        Element tableBody = doc.selectFirst("#tblFixture > tbody");
        if (tableBody == null) throw new RuntimeException("Fikstür tablosu bulunamadi: " + teamId);

        String teamName = "Unknown";
        try {
            Element title = doc.selectFirst("title");
            if (title != null) teamName = title.text().split("-")[0].trim();
        } catch (Exception ignored) {}

        List<String> scores    = new ArrayList<>();
        List<String> homeTeams = new ArrayList<>();
        List<String> awayTeams = new ArrayList<>();
        List<Boolean> played   = new ArrayList<>();

        for (Element row : tableBody.select("tr")) {
            if (row.selectFirst("td[itemprop=sportsevent]") != null) break;
            if (row.hasClass("competition")) continue;
            Element homeEl = row.selectFirst("td:nth-child(3)");
            Element awayEl = row.selectFirst("td:nth-child(7)");
            if (homeEl == null || awayEl == null) continue;
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");
            String score = scoreEl != null ? scoreEl.text().trim() : "";
            homeTeams.add(homeEl.text().trim());
            awayTeams.add(awayEl.text().trim());
            scores.add(score);
            played.add(score.contains("-") && !score.equalsIgnoreCase("v"));
        }

        int lastIdx = -1;
        for (int i = scores.size() - 1; i >= 0; i--) {
            if (played.get(i)) { lastIdx = i; break; }
        }

        if (lastIdx < 0)           throw new RuntimeException("Oynanan mac yok: " + teamId);
        if (lastIdx + 2 >= scores.size()) throw new RuntimeException("Sonraki 2 mac bulunamadi: " + teamId);

        String score1  = scores.get(lastIdx);
        String home1   = homeTeams.get(lastIdx);
        String away1   = awayTeams.get(lastIdx);
        String midHome = homeTeams.get(lastIdx + 1);
        String midAway = awayTeams.get(lastIdx + 1);
        String home2   = homeTeams.get(lastIdx + 2);
        String away2   = awayTeams.get(lastIdx + 2);

        // Mevcut pattern ekrana bas
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.printf ("║  MEVCUT PATTERN — %-33s║%n", teamName);
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.printf ("║  [1] %-20s %5s %-20s ║%n", home1, score1, away1);
        System.out.printf ("║  [2] %-20s  vs   %-15s(?)║%n", midHome, midAway);
        System.out.printf ("║  [3] %-20s  vs   %-20s ║%n", home2, away2);
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        return new MatchPattern(score1, null, home1, away1, home2, away2, teamName, midHome, midAway);
    }

    public static List<MatchResult> findScorePattern(CloseableHttpClient httpClient, MatchPattern pattern,
                                                     String seasonYear, int teamId) throws IOException {
        List<MatchResult> foundResults = new ArrayList<>();

        String html = fetchHtml(httpClient, String.format(BASE_URL, teamId, seasonYear));
        if (html == null) return foundResults;

        Document doc = Jsoup.parse(html);
        List<Element> leagueMatches = new ArrayList<>();
        boolean isFirstLeague = true, collecting = false;

        for (Element row : doc.select("table tbody tr")) {
            if (row.hasClass("competition")) {
                if (!isFirstLeague) break;
                isFirstLeague = false; collecting = true; continue;
            }
            if (collecting && row.selectFirst("td:nth-child(5) b a") != null)
                leagueMatches.add(row);
        }

        // 3'lu pencere ara
        for (int i = 0; i < leagueMatches.size() - 2; i++) {
            Element row1   = leagueMatches.get(i);
            Element rowMid = leagueMatches.get(i + 1);
            Element row2   = leagueMatches.get(i + 2);

            try {
                String home1 = row1.selectFirst("td:nth-child(3)").text().trim();
                String away1 = row1.selectFirst("td:nth-child(7)").text().trim();
                String home2 = row2.selectFirst("td:nth-child(3)").text().trim();
                String away2 = row2.selectFirst("td:nth-child(7)").text().trim();

                boolean firstOk  = (home1.equals(pattern.homeTeam1) && away1.equals(pattern.awayTeam1)) ||
                        (home1.equals(pattern.awayTeam1) && away1.equals(pattern.homeTeam1));
                boolean secondOk = pattern.homeTeam2 != null &&
                        ((home2.equals(pattern.homeTeam2) && away2.equals(pattern.awayTeam2)) ||
                                (home2.equals(pattern.awayTeam2) && away2.equals(pattern.homeTeam2)));

                if (!firstOk || !secondOk) continue;

                String midScore  = rowMid.selectFirst("td:nth-child(5) b a").text().trim();
                String midResult = extractComebackResult(rowMid);

                if (!COMEBACK_RESULTS.contains(midResult)) continue;

                // ESLESME BULUNDU — sadece bu 3 maci yazdir
                String score1  = row1.selectFirst("td:nth-child(5) b a").text().trim();
                String ht1     = nullIfEmpty(row1.selectFirst("td:nth-child(9)").text().trim());
                String midHome = rowMid.selectFirst("td:nth-child(3)").text().trim();
                String midAway = rowMid.selectFirst("td:nth-child(7)").text().trim();
                String midHT   = nullIfEmpty(rowMid.selectFirst("td:nth-child(9)").text().trim());
                String score2  = row2.selectFirst("td:nth-child(5) b a").text().trim();
                String ht2     = nullIfEmpty(row2.selectFirst("td:nth-child(9)").text().trim());

                System.out.println("  ┌─ ESLESTI! " + pattern.teamName + " — " + seasonYear + " ─────────────────────┐");
                System.out.printf ("  │ [1] %-20s %5s %-20s (HT: %s)│%n", home1, score1, away1, nvl(ht1));
                System.out.printf ("  │ [2] %-20s %5s %-20s (HT: %s) *** %s ***│%n", midHome, midScore, midAway, nvl(midHT), midResult);
                System.out.printf ("  │ [3] %-20s %5s %-20s (HT: %s)│%n", home2, score2, away2, nvl(ht2));
                System.out.println("  └─────────────────────────────────────────────────────────┘");

                MatchResult result = new MatchResult(home1, away1, score1, seasonYear, pattern);
                result.firstMatchHTScore   = ht1;
                result.middleHomeTeam      = midHome;
                result.middleAwayTeam      = midAway;
                result.middleScore         = midScore + " (" + midResult + ")";
                result.middleHTScore       = midHT;
                result.secondMatchHomeTeam = home2;
                result.secondMatchScore    = score2;
                result.secondMatchAwayTeam = away2;
                result.secondMatchHTScore  = ht2;

                if (i > 0) {
                    Element prev = leagueMatches.get(i - 1);
                    String ps = prev.selectFirst("td:nth-child(5) b a").text().trim();
                    String ph = prev.selectFirst("td:nth-child(3)").text().trim();
                    String pa = prev.selectFirst("td:nth-child(7)").text().trim();
                    result.previousMatchScore = ph + " " + ps + " " + pa;
                    result.previousHTScore    = nullIfEmpty(prev.selectFirst("td:nth-child(9)").text().trim());
                } else {
                    result.previousMatchScore = "Bilgi Yok (Ilk Mac)";
                }

                if (i + 3 < leagueMatches.size()) {
                    Element next = leagueMatches.get(i + 3);
                    String ns = next.selectFirst("td:nth-child(5) b a").text().trim();
                    String nh = next.selectFirst("td:nth-child(3)").text().trim();
                    String na = next.selectFirst("td:nth-child(7)").text().trim();
                    result.nextMatchScore = nh + " " + ns + " " + na;
                    result.nextHTScore    = nullIfEmpty(next.selectFirst("td:nth-child(9)").text().trim());
                } else {
                    result.nextMatchScore = "Bilgi Yok (Son Mac)";
                }

                foundResults.add(result);

            } catch (Exception e) {
                log.error("Pencere hatasi idx:{} sezon:{}: {}", i, seasonYear, e.getMessage());
            }
        }

        return foundResults;
    }

    private static String extractComebackResult(Element row) {
        try {
            for (Element td : row.select("td")) {
                String text = td.text().trim();
                if (text.equals("2/1") || text.equals("1/2")) return text;
                Element img = td.selectFirst("img[alt]");
                if (img != null) {
                    String alt = img.attr("alt").trim();
                    if (alt.equals("2/1") || alt.equals("1/2")) return alt;
                }
                Element inner = td.selectFirst("span, div, b");
                if (inner != null) {
                    String t = inner.text().trim();
                    if (t.equals("2/1") || t.equals("1/2")) return t;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String nullIfEmpty(String s) { return (s == null || s.isEmpty()) ? null : s; }
    private static String nvl(String s) { return s != null ? s : "N/A"; }
}