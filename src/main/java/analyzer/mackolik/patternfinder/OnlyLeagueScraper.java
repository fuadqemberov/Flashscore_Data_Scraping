package analyzer.mackolik.patternfinder;

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
import java.util.HashSet;
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
            if (row.hasClass("competition")) continue;

            Element homeEl = row.selectFirst("td:nth-child(3)");
            Element awayEl = row.selectFirst("td:nth-child(7)");
            if (homeEl == null || awayEl == null) continue;

            // Oynanan maç skoru
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");
            String score = scoreEl != null ? scoreEl.text().trim() : "";

            // Gelecek maç mı? itemprop=sportsevent olan satırlar oynanmamış
            boolean isFuture = row.selectFirst("td[itemprop=sportsevent]") != null;

            homeTeams.add(homeEl.text().trim());
            awayTeams.add(awayEl.text().trim());
            scores.add(score);

            boolean isPlayed = !isFuture
                    && score.contains("-")
                    && !score.trim().equalsIgnoreCase("v")
                    && !score.trim().isEmpty()
                    && !score.trim().equals("-");
            played.add(isPlayed);
        }

        // Son iki oynanan maçı bul (sondan başa)
        int lastIdx = -1;
        int secondLastIdx = -1;
        for (int i = scores.size() - 1; i >= 0; i--) {
            if (played.get(i)) {
                if (lastIdx == -1) {
                    lastIdx = i;
                } else {
                    secondLastIdx = i;
                    break;
                }
            }
        }

        if (lastIdx < 0 || secondLastIdx < 0)
            throw new RuntimeException("2 oynanan mac bulunamadi: " + teamId);

        String score1 = scores.get(secondLastIdx);
        String home1  = homeTeams.get(secondLastIdx);
        String away1  = awayTeams.get(secondLastIdx);

        String score2 = scores.get(lastIdx);
        String home2  = homeTeams.get(lastIdx);
        String away2  = awayTeams.get(lastIdx);

        // lastIdx'ten sonraki ilk oynanmamış maçı bul
        String nextHome = null, nextAway = null;
        for (int i = lastIdx + 1; i < scores.size(); i++) {
            if (!played.get(i)) {
                nextHome = homeTeams.get(i);
                nextAway = awayTeams.get(i);
                break;
            }
        }

        return new MatchPattern(score1, score2, home1, away1, home2, away2, teamName, nextHome, nextAway);
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

        // ✅ TÜM TAKIMLAR — pattern'daki 4 takım ismi
        Set<String> patternTeams = new HashSet<>(Arrays.asList(
                pattern.homeTeam1, pattern.awayTeam1,
                pattern.homeTeam2, pattern.awayTeam2
        ));

        // ✅ 2'Lİ PENCERE ARA
        for (int i = 0; i < leagueMatches.size() - 1; i++) {
            Element row1 = leagueMatches.get(i);
            Element row2 = leagueMatches.get(i + 1);

            try {
                String score1 = row1.selectFirst("td:nth-child(5) b a").text().trim();
                String score2 = row2.selectFirst("td:nth-child(5) b a").text().trim();

                // Skor eşleşmeli
                if (!score1.equals(pattern.score1) || !score2.equals(pattern.score2)) continue;

                // En az 1 takım ismi eşleşmeli
                String h1 = row1.selectFirst("td:nth-child(3)").text().trim();
                String a1 = row1.selectFirst("td:nth-child(7)").text().trim();
                String h2 = row2.selectFirst("td:nth-child(3)").text().trim();
                String a2 = row2.selectFirst("td:nth-child(7)").text().trim();

                boolean teamMatch = patternTeams.contains(h1) || patternTeams.contains(a1)
                        || patternTeams.contains(h2) || patternTeams.contains(a2);

                if (!teamMatch) continue;

                // EŞLEŞME BULUNDU
                String ht1 = nullIfEmpty(row1.selectFirst("td:nth-child(9)").text().trim());
                String ht2 = nullIfEmpty(row2.selectFirst("td:nth-child(9)").text().trim());


                MatchResult result = new MatchResult(h1, a1, score1, seasonYear, pattern);
                result.firstMatchHTScore    = ht1;
                result.secondMatchHomeTeam  = h2;
                result.secondMatchAwayTeam  = a2;
                result.secondMatchScore     = score2;
                result.secondMatchHTScore   = ht2;

                // Önceki maç
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

                // Sonraki maç
                if (i + 2 < leagueMatches.size()) {
                    Element next = leagueMatches.get(i + 2);
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