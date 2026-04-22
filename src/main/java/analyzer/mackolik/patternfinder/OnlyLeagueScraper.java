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
import java.util.*;

public class OnlyLeagueScraper {

    private static final Logger log = LoggerFactory.getLogger(OnlyLeagueScraper.class);
    private static final String BASE_URL = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";

    // İşlem yaptığın sezon hangisiyse burada ayarlı kalsın. (Örn: 2024/2025 veya 2025/2026)
    private static final String CURRENT_SEASON = "2025/2026";

    private static String fetchHtml(CloseableHttpClient httpClient, String url) throws IOException {
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) return EntityUtils.toString(response.getEntity());
            return null;
        }
    }

    /**
     * Başlığın esas lig mi yoksa bir kupa/hazırlık maçı mı olduğunu kontrol eder.
     * Bu sayede Güney Amerika'daki Apertura/Clausura (Aşama 1, Aşama 2) ligleri tek lig gibi birleşir.
     */
    private static boolean isLeagueCompetition(String compName) {
        if (compName == null) return false;
        String lower = compName.toLowerCase(new Locale("tr", "TR"));

        // Kupa, hazırlık ve kıtasal turnuvaları engelleme listesi
        String[] excluded = {
                "kupa", "cup", "copa", "coppa", "pokal", "taça", "taca",
                "hazırlık", "hazirlik", "friendlies", "avrupa", "europe",
                "şampiyonlar ligi", "sampiyonlar ligi", "champions league", "uefa",
                "konferans", "conference", "trophy", "shield",
                "dünya", "dunya", "world", "turnuva", "tournament",
                "recopa", "sudamericana", "libertadores"
        };

        for (String ex : excluded) {
            if (lower.contains(ex)) return false;
        }
        return true; // Eğer yasaklı kelime yoksa bu bir LİG'dir.
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

        boolean collecting = false;

        for (Element row : tableBody.select("tr")) {
            // Sadece Esas Ligleri Al, Kupaları Atla
            if (row.hasClass("competition")) {
                Element a = row.selectFirst("a");
                String compName = a != null ? a.text() : row.text();
                collecting = isLeagueCompetition(compName);
                continue;
            }

            if (!collecting) continue;
            if (row.hasClass("leg")) continue;

            Element homeEl = row.selectFirst("td:nth-child(3)");
            Element awayEl = row.selectFirst("td:nth-child(7)");
            if (homeEl == null || awayEl == null) continue;

            Element scoreEl = row.selectFirst("td:nth-child(5) b a");
            String score = "";
            boolean isPlayed = false;

            boolean isFuture = row.hasAttr("itemprop") && row.attr("itemprop").equals("sportsevent");

            if (scoreEl != null) {
                score = scoreEl.text().trim();
                // Eğer skor "v" veya Ertelenmiş (ERT) değilse ve maç gelecek maçı değilse OYNANMIŞTIR
                if (!score.equalsIgnoreCase("v") && !score.toLowerCase().contains("ert") && !isFuture) {
                    isPlayed = true;
                }
            } else {
                Element scoreTd = row.selectFirst("td:nth-child(5)");
                if (scoreTd != null) {
                    score = scoreTd.text().trim();
                    if (score.contains("-") && !score.equals("-") && !score.equalsIgnoreCase("v")
                        && !score.toLowerCase().contains("ert") && !isFuture) {
                        isPlayed = true;
                    }
                }
            }

            homeTeams.add(homeEl.text().trim().replaceAll("&nbsp;", ""));
            awayTeams.add(awayEl.text().trim().replaceAll("&nbsp;", ""));
            scores.add(score);
            played.add(isPlayed);
        }

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

        // Sıradaki maçı bul
        String nextHome = "Belirsiz", nextAway = "Belirsiz";
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
        boolean collecting = false;

        for (Element row : doc.select("table tbody tr")) {
            if (row.hasClass("competition")) {
                Element a = row.selectFirst("a");
                String compName = a != null ? a.text() : row.text();
                collecting = isLeagueCompetition(compName);
                continue;
            }
            if (collecting && row.selectFirst("td:nth-child(5) b a") != null)
                leagueMatches.add(row);
        }

        String mainTeam = pattern.teamName;
        if (pattern.homeTeam1.equals(pattern.homeTeam2) || pattern.homeTeam1.equals(pattern.awayTeam2)) {
            mainTeam = pattern.homeTeam1;
        } else if (pattern.awayTeam1.equals(pattern.homeTeam2) || pattern.awayTeam1.equals(pattern.awayTeam2)) {
            mainTeam = pattern.awayTeam1;
        }

        Set<String> patternOpponents = new HashSet<>(Arrays.asList(
                pattern.homeTeam1, pattern.awayTeam1,
                pattern.homeTeam2, pattern.awayTeam2
        ));
        patternOpponents.remove(mainTeam);

        for (int i = 0; i < leagueMatches.size() - 1; i++) {
            Element row1 = leagueMatches.get(i);
            Element row2 = leagueMatches.get(i + 1);

            try {
                String score1 = row1.selectFirst("td:nth-child(5) b a").text().trim();
                String score2 = row2.selectFirst("td:nth-child(5) b a").text().trim();

                boolean directOrder = isScoreEquivalent(score1, pattern.score1) &&
                                      isScoreEquivalent(score2, pattern.score2);
                boolean reverseOrder = isScoreEquivalent(score1, pattern.score2) &&
                                       isScoreEquivalent(score2, pattern.score1);

                if (!directOrder && !reverseOrder) continue;

                String h1 = row1.selectFirst("td:nth-child(3)").text().trim();
                String a1 = row1.selectFirst("td:nth-child(7)").text().trim();
                String h2 = row2.selectFirst("td:nth-child(3)").text().trim();
                String a2 = row2.selectFirst("td:nth-child(7)").text().trim();

                Set<String> pastOpponents = new HashSet<>(Arrays.asList(h1, a1, h2, a2));
                pastOpponents.remove(mainTeam);

                boolean opponentMatch = false;
                for (String pastOpp : pastOpponents) {
                    if (patternOpponents.contains(pastOpp)) {
                        opponentMatch = true;
                        break;
                    }
                }

                if (!opponentMatch) continue;

                String ht1 = nullIfEmpty(row1.selectFirst("td:nth-child(9)").text().trim());
                String ht2 = nullIfEmpty(row2.selectFirst("td:nth-child(9)").text().trim());

                Element prev = (i > 0) ? leagueMatches.get(i - 1) : null;
                Element next = (i + 2 < leagueMatches.size()) ? leagueMatches.get(i + 2) : null;

                String prevScore = null, prevHt = null;
                if (prev != null) {
                    prevScore = prev.selectFirst("td:nth-child(5) b a").text().trim();
                    prevHt = nullIfEmpty(prev.selectFirst("td:nth-child(9)").text().trim());
                }

                String nextScore = null, nextHt = null;
                if (next != null) {
                    nextScore = next.selectFirst("td:nth-child(5) b a").text().trim();
                    nextHt = nullIfEmpty(next.selectFirst("td:nth-child(9)").text().trim());
                }

                String cb1 = calculateComeback(score1, ht1);
                String cb2 = calculateComeback(score2, ht2);
                String cbPrev = calculateComeback(prevScore, prevHt);
                String cbNext = calculateComeback(nextScore, nextHt);

                if (cb1 == null && cb2 == null && cbPrev == null && cbNext == null) {
                    continue;
                }

                MatchResult result = new MatchResult(h1, a1, score1, seasonYear, pattern);

                result.firstMatchHTScore = ht1 + (cb1 != null ? " 🔥" + cb1 : "");
                result.secondMatchHTScore = ht2 + (cb2 != null ? " 🔥" + cb2 : "");
                result.secondMatchHomeTeam  = h2;
                result.secondMatchAwayTeam  = a2;
                result.secondMatchScore     = score2;

                if (prev != null) {
                    String ph = prev.selectFirst("td:nth-child(3)").text().trim();
                    String pa = prev.selectFirst("td:nth-child(7)").text().trim();
                    result.previousMatchScore = ph + " " + prevScore + " " + pa;
                    result.previousHTScore = prevHt + (cbPrev != null ? " 🔥" + cbPrev : "");
                } else {
                    result.previousMatchScore = "Bilgi Yok (Ilk Mac)";
                }

                if (next != null) {
                    String nh = next.selectFirst("td:nth-child(3)").text().trim();
                    String na = next.selectFirst("td:nth-child(7)").text().trim();
                    result.nextMatchScore = nh + " " + nextScore + " " + na;
                    result.nextHTScore = nextHt + (cbNext != null ? " 🔥" + cbNext : "");
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

    private static boolean isScoreEquivalent(String s1, String s2) {
        if (s1 == null || s2 == null) return false;

        String clean1 = s1.replaceAll("\\s+", "");
        String clean2 = s2.replaceAll("\\s+", "");

        if (clean1.equals(clean2)) return true;

        String[] parts1 = clean1.split("-");
        String[] parts2 = clean2.split("-");

        if (parts1.length == 2 && parts2.length == 2) {
            return parts1[0].equals(parts2[1]) && parts1[1].equals(parts2[0]);
        }
        return false;
    }

    private static String calculateComeback(String ftScore, String htScore) {
        if (ftScore == null || htScore == null) return null;
        try {
            String cleanFt = ftScore.replaceAll("[^0-9-]", "").trim();
            String cleanHt = htScore.replaceAll("[^0-9-]", "").trim();

            if (cleanFt.isEmpty() || cleanHt.isEmpty()) return null;

            String[] ft = cleanFt.split("-");
            String[] ht = cleanHt.split("-");

            if (ft.length == 2 && ht.length == 2) {
                int ftH = Integer.parseInt(ft[0]);
                int ftA = Integer.parseInt(ft[1]);
                int htH = Integer.parseInt(ht[0]);
                int htA = Integer.parseInt(ht[1]);

                int htResult = Integer.compare(htH, htA);
                int ftResult = Integer.compare(ftH, ftA);

                if (htResult == -1 && ftResult == 1) return "2/1";
                if (htResult == 1 && ftResult == -1) return "1/2";
            }
        } catch (Exception ignored) { }

        return null;
    }

    private static String nullIfEmpty(String s) { return (s == null || s.isEmpty()) ? null : s; }
}