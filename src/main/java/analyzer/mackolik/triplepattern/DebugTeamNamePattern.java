package analyzer.mackolik.triplepattern;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;

/**
 * Standalone debug runner – tek bir team ID ile her adımı console'a basar.
 * Projenize ekleyin, main()'i çalıştırın.
 */
public class DebugTeamNamePattern {

    private static final String BASE_URL       = "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";
    private static final String CURRENT_SEASON = "2025/2026";

    // ── Buraya test etmek istediğiniz team ID'yi yazın ──────────────────────
    private static final int TEST_TEAM_ID = 368; // örnek: Go Ahead Eagles
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        CloseableHttpClient http = HttpClients.createDefault();

        System.out.println("════════════════════════════════════════════════════");
        System.out.println("  DEBUG – Team Name Pattern Analyzer");
        System.out.println("  Team ID : " + TEST_TEAM_ID);
        System.out.println("  Season  : " + CURRENT_SEASON);
        System.out.println("════════════════════════════════════════════════════\n");

        // ── STEP 1: Fetch current season HTML ───────────────────────────────
        String url  = String.format(BASE_URL, TEST_TEAM_ID, CURRENT_SEASON);
        String html = fetchHtml(http, url);
        if (html == null) {
            System.out.println("[HATA] HTML alınamadı: " + url);
            http.close();
            return;
        }
        System.out.println("[OK] HTML alındı, uzunluk: " + html.length() + " karakter");

        // ── STEP 2: Parse ───────────────────────────────────────────────────
        Document doc = Jsoup.parse(html);

        // Team name
        String teamName = "Unknown";
        try {
            Element title = doc.selectFirst("title");
            if (title != null) teamName = title.text().split("-")[0].trim();
        } catch (Exception ignored) {}
        System.out.println("[OK] Takım adı: " + teamName);

        // ── STEP 3: Find fixture table ───────────────────────────────────────
        Element tableBody = doc.selectFirst("#tblFixture > tbody");
        if (tableBody == null) {
            System.out.println("[HATA] #tblFixture > tbody bulunamadı!");
            System.out.println("       Alternatif arama deneniyor...");
            tableBody = doc.selectFirst("table tbody");
            if (tableBody == null) {
                System.out.println("[HATA] Hiç tbody bulunamadı. HTML başı:");
                System.out.println(html.substring(0, Math.min(2000, html.length())));
                http.close();
                return;
            }
            System.out.println("[OK] Alternatif tbody bulundu.");
        } else {
            System.out.println("[OK] #tblFixture tbody bulundu.");
        }

        // ── STEP 4: Collect rows ─────────────────────────────────────────────
        Elements allRows = tableBody.select("tr");
        System.out.println("[OK] Toplam <tr> sayısı: " + allRows.size());

        // Print first 5 rows raw for inspection
        System.out.println("\n[DEBUG] İlk 10 satır özeti:");
        int rowIdx = 0;
        for (Element row : allRows) {
            if (rowIdx >= 10) break;
            System.out.printf("  Row[%02d] class=%-20s | td count=%-3d | text=%.80s%n",
                    rowIdx,
                    row.className(),
                    row.select("td").size(),
                    row.text().replaceAll("\\s+", " "));
            rowIdx++;
        }

        // ── STEP 5: League row collection ────────────────────────────────────
        List<Element> leagueRows = new ArrayList<>();
        boolean firstCompSeen = false;
        for (Element row : allRows) {
            if (row.hasClass("competition")) {
                if (!firstCompSeen) { firstCompSeen = true; continue; }
                else break;
            }
            if (firstCompSeen) leagueRows.add(row);
        }
        System.out.println("\n[OK] 'competition' class ile toplanan lig satırları: " + leagueRows.size());

        if (leagueRows.isEmpty()) {
            System.out.println("[WARN] competition class ile satır toplanamadı. Fallback deneniyor...");
            for (Element row : allRows) {
                if (!row.hasClass("competition") && row.select("td").size() > 3) {
                    leagueRows.add(row);
                }
            }
            System.out.println("[OK] Fallback sonrası satır sayısı: " + leagueRows.size());
        }

        // ── STEP 6: Find unstarted match ──────────────────────────────────────
        System.out.println("\n[DEBUG] Satır analizi (score detection):");
        int unstartedIdx = -1;
        List<String> playedScores = new ArrayList<>();

        for (int i = 0; i < leagueRows.size(); i++) {
            Element row    = leagueRows.get(i);
            Element scoreEl = row.selectFirst("td:nth-child(5) b a");
            String  home   = cellText(row, "td:nth-child(3)");
            String  away   = cellText(row, "td:nth-child(7)");
            String  rawScore = scoreEl != null ? scoreEl.text().trim() : "[yok]";
            String  cell5  = cellText(row, "td:nth-child(5)");
            String  htCell = cellText(row, "td:nth-child(9)");

            System.out.printf("  [%02d] home=%-20s away=%-20s score=%-10s ht=%-8s%n",
                    i, home, away, rawScore, htCell != null ? htCell : "");

            boolean isPlayed = false;
            if (scoreEl != null && !rawScore.isEmpty() && !rawScore.equalsIgnoreCase("v")) {
                String norm = rawScore.replaceAll("\\s*-\\s*", "-");
                String[] p  = norm.split("-");
                try {
                    if (p.length == 2) {
                        Integer.parseInt(p[0].trim());
                        Integer.parseInt(p[1].trim());
                        playedScores.add(norm);
                        isPlayed = true;
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (!isPlayed && unstartedIdx < 0) {
                if ((home != null && !home.isEmpty()) && (away != null && !away.isEmpty())) {
                    unstartedIdx = i;
                    System.out.println("  ^^^ OYNANMAMIŞ MAÇ ADAYI ^^^");
                }
            }
        }

        System.out.println("\n[OK] Oynanan maç skoru sayısı   : " + playedScores.size());
        System.out.println("[OK] Oynanan skorlar             : " + playedScores);
        System.out.println("[OK] Oynanmamış maç indeksi      : " + unstartedIdx);

        if (unstartedIdx < 0) {
            System.out.println("[HATA] Oynanmamış maç bulunamadı!");
            http.close();
            return;
        }

        // ── STEP 7: Extract prev/next opponents ───────────────────────────────
        List<String> prevOpponents = new ArrayList<>();
        for (int i = Math.max(0, unstartedIdx - 3); i < unstartedIdx; i++) {
            Element row  = leagueRows.get(i);
            String  home = cellText(row, "td:nth-child(3)");
            String  away = cellText(row, "td:nth-child(7)");
            String  opp  = extractOpponent(home, away, teamName);
            prevOpponents.add(opp);
        }

        List<String> nextOpponents = new ArrayList<>();
        for (int i = unstartedIdx + 1; i < Math.min(leagueRows.size(), unstartedIdx + 4); i++) {
            Element row  = leagueRows.get(i);
            String  home = cellText(row, "td:nth-child(3)");
            String  away = cellText(row, "td:nth-child(7)");
            // For upcoming we return the non-team side
            String  opp  = extractOpponent(home, away, teamName);
            if (opp == null) opp = (away != null ? away : home);
            nextOpponents.add(opp);
        }

        String targetHome = cellText(leagueRows.get(unstartedIdx), "td:nth-child(3)");
        String targetAway = cellText(leagueRows.get(unstartedIdx), "td:nth-child(7)");

        System.out.println("\n════════════════════════════════════════════════════");
        System.out.println("  MEVCUT SEZON PATERNİ:");
        System.out.println("  Takım          : " + teamName + " (" + TEST_TEAM_ID + ")");
        System.out.println("  Hedef Maç      : " + targetHome + " vs " + targetAway);
        System.out.println("  Önceki Rakipler: " + prevOpponents);
        System.out.println("  Sonraki Rakipler: " + nextOpponents);
        System.out.println("════════════════════════════════════════════════════");

        // ── STEP 8: Quick historical scan for ONE season ──────────────────────
        String testSeason = "2021/2022";
        System.out.println("\n[TEST] Geçmiş sezon taraması: " + testSeason);

        TeamNamePattern pattern = new TeamNamePattern(
                TEST_TEAM_ID, teamName, prevOpponents, nextOpponents, targetHome, targetAway);

        List<TeamNameMatchResult> hits =
                HttpTeamNamePatternFetcher.searchHistoricalSeason(http, pattern, testSeason, TEST_TEAM_ID);

        if (hits.isEmpty()) {
            System.out.println("[SONUÇ] " + testSeason + " sezonunda HT/FT 1/2 veya 2/1 pattern eşleşmesi bulunamadı.");
        } else {
            System.out.println("[SONUÇ] " + hits.size() + " eşleşme bulundu:\n");
            for (TeamNameMatchResult hit : hits) {
                System.out.println(hit);
            }
        }

        // ── STEP 9: HT/FT sanity check ────────────────────────────────────────
        System.out.println("\n[HT/FT TEST] Birkaç örnek:");
        String[][] testCases = {
                {"2-1", "1-0"}, // home leads HT, home wins FT → NOT 1/2 or 2/1
                {"1-2", "1-0"}, // home leads HT, away wins FT → 1/2 ✓
                {"2-1", "0-1"}, // away leads HT, home wins FT → 2/1 ✓
                {"1-1", "0-0"}, // draw both → null
        };
        for (String[] tc : testCases) {
            String res = HttpTeamNamePatternFetcher.computeHtFt(tc[0], tc[1]);
            System.out.printf("  FT=%s HT=%s → %s%n", tc[0], tc[1], res != null ? res : "null (uygun değil)");
        }

        http.close();
        System.out.println("\n[DONE] Debug tamamlandı.");
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private static String cellText(Element row, String css) {
        Element el = row.selectFirst(css);
        return el != null ? el.text().trim() : null;
    }

    private static String extractOpponent(String home, String away, String teamName) {
        if (home == null || away == null) return null;
        String norm = normalize(teamName);
        if (normalize(home).contains(norm) || norm.contains(normalize(home))) return away;
        if (normalize(away).contains(norm) || norm.contains(normalize(away))) return home;
        return away;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]", "");
    }

    private static String fetchHtml(CloseableHttpClient http, String url) throws IOException {
        HttpGet req = new HttpGet(url);
        req.addHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/91 Safari/537.36");
        try (CloseableHttpResponse resp = http.execute(req)) {
            int code = resp.getStatusLine().getStatusCode();
            System.out.println("[HTTP] " + code + " → " + url);
            if (code == 200) return EntityUtils.toString(resp.getEntity());
            return null;
        }
    }
}