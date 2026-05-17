package analyzer.bet365;

import com.microsoft.playwright.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Bet365DailySimilarityAnalyzer {

    // ==================== KOLON TANIMLARI (Flashscore ile SQL eşlemesi) ====================
    static class ColumnDef {
        String sqlColumn, displayName, flashscoreKey;
        ColumnDef(String s, String d, String f) { sqlColumn = s; displayName = d; flashscoreKey = f; }
    }

    // Tüm oran kolonları (Similarity için gereken tüm kolonlar)
    private static final List<ColumnDef> ALL_COLS = new ArrayList<>();
    static {
        // 1x2
        ALL_COLS.add(new ColumnDef("ft_1_a", "MS 1", "1x2|Full Time|Home"));
        ALL_COLS.add(new ColumnDef("ft_x_a", "MS X", "1x2|Full Time|Draw"));
        ALL_COLS.add(new ColumnDef("ft_2_a", "MS 2", "1x2|Full Time|Away"));
        ALL_COLS.add(new ColumnDef("first_1_a", "İY 1", "1x2|1st Half|Home"));
        ALL_COLS.add(new ColumnDef("first_x_a", "İY X", "1x2|1st Half|Draw"));
        ALL_COLS.add(new ColumnDef("first_2_a", "İY 2", "1x2|1st Half|Away"));
        ALL_COLS.add(new ColumnDef("second_1_a", "2Y 1", "1x2|2nd Half|Home"));
        ALL_COLS.add(new ColumnDef("second_x_a", "2Y X", "1x2|2nd Half|Draw"));
        ALL_COLS.add(new ColumnDef("second_2_a", "2Y 2", "1x2|2nd Half|Away"));

        // Both teams to score
        ALL_COLS.add(new ColumnDef("bts_ft_yes_a", "KG Evet", "Both teams|Full Time|Yes"));
        ALL_COLS.add(new ColumnDef("bts_ft_no_a", "KG Hayır", "Both teams|Full Time|No"));
        ALL_COLS.add(new ColumnDef("bts_first_yes_a", "İY KG Evet", "Both teams|1st Half|Yes"));
        ALL_COLS.add(new ColumnDef("bts_first_no_a", "İY KG Hayır", "Both teams|1st Half|No"));
        ALL_COLS.add(new ColumnDef("bts_second_yes_a", "2Y KG Evet", "Both teams|2nd Half|Yes"));
        ALL_COLS.add(new ColumnDef("bts_second_no_a", "2Y KG Hayır", "Both teams|2nd Half|No"));

        // Double chance
        ALL_COLS.add(new ColumnDef("dbc_ft_1x_a", "ÇŞ 1X", "Double chance|Full Time|1X"));
        ALL_COLS.add(new ColumnDef("dbc_ft_12_a", "ÇŞ 12", "Double chance|Full Time|12"));
        ALL_COLS.add(new ColumnDef("dbc_ft_x2_a", "ÇŞ X2", "Double chance|Full Time|X2"));
        ALL_COLS.add(new ColumnDef("dbc_first_1x_a", "İY ÇŞ 1X", "Double chance|1st Half|1X"));
        ALL_COLS.add(new ColumnDef("dbc_first_12_a", "İY ÇŞ 12", "Double chance|1st Half|12"));
        ALL_COLS.add(new ColumnDef("dbc_first_x2_a", "İY ÇŞ X2", "Double chance|1st Half|X2"));

        // Over/Under Full Time
        ALL_COLS.add(new ColumnDef("ft_0_5_over_a", "A/U 0.5 Üst", "Over/Under|Full Time|O 0.5"));
        ALL_COLS.add(new ColumnDef("ft_0_5_under_a", "A/U 0.5 Alt", "Over/Under|Full Time|U 0.5"));
        ALL_COLS.add(new ColumnDef("ft_1_5_over_a", "A/U 1.5 Üst", "Over/Under|Full Time|O 1.5"));
        ALL_COLS.add(new ColumnDef("ft_1_5_under_a", "A/U 1.5 Alt", "Over/Under|Full Time|U 1.5"));
        ALL_COLS.add(new ColumnDef("ft_2_5_over_a", "A/U 2.5 Üst", "Over/Under|Full Time|O 2.5"));
        ALL_COLS.add(new ColumnDef("ft_2_5_under_a", "A/U 2.5 Alt", "Over/Under|Full Time|U 2.5"));
        ALL_COLS.add(new ColumnDef("ft_3_5_over_a", "A/U 3.5 Üst", "Over/Under|Full Time|O 3.5"));
        ALL_COLS.add(new ColumnDef("ft_3_5_under_a", "A/U 3.5 Alt", "Over/Under|Full Time|U 3.5"));
        ALL_COLS.add(new ColumnDef("ft_4_5_over_a", "A/U 4.5 Üst", "Over/Under|Full Time|O 4.5"));
        ALL_COLS.add(new ColumnDef("ft_4_5_under_a", "A/U 4.5 Alt", "Over/Under|Full Time|U 4.5"));
        ALL_COLS.add(new ColumnDef("ft_5_5_over_a", "A/U 5.5 Üst", "Over/Under|Full Time|O 5.5"));
        ALL_COLS.add(new ColumnDef("ft_5_5_under_a", "A/U 5.5 Alt", "Over/Under|Full Time|U 5.5"));

        // Over/Under 1st Half
        ALL_COLS.add(new ColumnDef("first_0_5_over_a", "İY A/U 0.5 Üst", "Over/Under|1st Half|O 0.5"));
        ALL_COLS.add(new ColumnDef("first_0_5_under_a", "İY A/U 0.5 Alt", "Over/Under|1st Half|U 0.5"));
        ALL_COLS.add(new ColumnDef("first_1_5_over_a", "İY A/U 1.5 Üst", "Over/Under|1st Half|O 1.5"));
        ALL_COLS.add(new ColumnDef("first_1_5_under_a", "İY A/U 1.5 Alt", "Over/Under|1st Half|U 1.5"));
        ALL_COLS.add(new ColumnDef("first_2_5_over_a", "İY A/U 2.5 Üst", "Over/Under|1st Half|O 2.5"));
        ALL_COLS.add(new ColumnDef("first_2_5_under_a", "İY A/U 2.5 Alt", "Over/Under|1st Half|U 2.5"));

        // Over/Under 2nd Half
        ALL_COLS.add(new ColumnDef("second_0_5_over_a", "2Y A/U 0.5 Üst", "Over/Under|2nd Half|O 0.5"));
        ALL_COLS.add(new ColumnDef("second_0_5_under_a", "2Y A/U 0.5 Alt", "Over/Under|2nd Half|U 0.5"));
        ALL_COLS.add(new ColumnDef("second_1_5_over_a", "2Y A/U 1.5 Üst", "Over/Under|2nd Half|O 1.5"));
        ALL_COLS.add(new ColumnDef("second_1_5_under_a", "2Y A/U 1.5 Alt", "Over/Under|2nd Half|U 1.5"));
        ALL_COLS.add(new ColumnDef("second_2_5_over_a", "2Y A/U 2.5 Üst", "Over/Under|2nd Half|O 2.5"));
        ALL_COLS.add(new ColumnDef("second_2_5_under_a", "2Y A/U 2.5 Alt", "Over/Under|2nd Half|U 2.5"));

        // HT/FT
        ALL_COLS.add(new ColumnDef("ht_ft_11_a", "HT/FT 1/1", "HTFT|1/1"));
        ALL_COLS.add(new ColumnDef("ht_ft_1x_a", "HT/FT 1/X", "HTFT|1/X"));
        ALL_COLS.add(new ColumnDef("ht_ft_12_a", "HT/FT 1/2", "HTFT|1/2"));
        ALL_COLS.add(new ColumnDef("ht_ft_x1_a", "HT/FT X/1", "HTFT|X/1"));
        ALL_COLS.add(new ColumnDef("ht_ft_xx_a", "HT/FT X/X", "HTFT|X/X"));
        ALL_COLS.add(new ColumnDef("ht_ft_x2_a", "HT/FT X/2", "HTFT|X/2"));
        ALL_COLS.add(new ColumnDef("ht_ft_21_a", "HT/FT 2/1", "HTFT|2/1"));
        ALL_COLS.add(new ColumnDef("ht_ft_2x_a", "HT/FT 2/X", "HTFT|2/X"));
        ALL_COLS.add(new ColumnDef("ht_ft_22_a", "HT/FT 2/2", "HTFT|2/2"));

        // Correct Score Full Time
        String[] ftScores = {"1:0","2:0","2:1","3:0","3:1","3:2","4:0","4:1","4:2","4:3","5:0","5:1","5:2",
                "0:0","1:1","2:2","3:3","4:4","0:1","0:2","1:2","0:3","1:3","2:3","0:4","1:4","2:4","3:4","0:5","1:5","2:5"};
        for (String sc : ftScores) {
            ALL_COLS.add(new ColumnDef("ft_score_" + sc.replace(":", "_") + "_a", "MS Skor " + sc, "Correct score|Full Time|" + sc));
        }

        // Correct Score 1st Half
        String[] htScores = {"1:0","2:0","2:1","3:0","3:1","3:2","0:0","1:1","2:2","0:1","0:2","1:2","0:3","1:3","2:3"};
        for (String sc : htScores) {
            ALL_COLS.add(new ColumnDef("first_score_" + sc.replace(":", "_") + "_a", "İY Skor " + sc, "Correct score|1st Half|" + sc));
        }
    }

    // ==================== HTTP / Playwright yardımcıları ====================
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    // ==================== Veri modelleri ====================
    static class MatchInfo {
        String id, home, away, date;
        Map<String, String> odds = new HashMap<>();
    }

    // Geçmiş maç kaydı (veritabanından gelen)
    static class HistRecord {
        String league, date, homeTeam, awayTeam, id;
        int htHome, htAway, ftHome, ftAway;
        double[] odds;

        HistRecord(int size) { odds = new double[size]; }
        String ftScore() { return (ftHome >= 0) ? ftHome + "-" + ftAway : "?-?"; }
        String htScore() { return (htHome >= 0) ? htHome + "-" + htAway : "?-?"; }
    }

    // ==================== Veritabanı bağlantısı ====================
    private Connection conn;
    private List<HistRecord> allRecords = new ArrayList<>();

    public Bet365DailySimilarityAnalyzer() {
        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "fuad123");
            System.out.println("✅ Veritabanına bağlanıldı.");
            loadAllRecords();
        } catch (Exception e) {
            System.err.println("❌ " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void loadAllRecords() throws SQLException {
        System.out.println("📊 Geçmiş veriler yükleniyor...");
        StringBuilder sb = new StringBuilder("SELECT country_league,date_time,home_team,away_team,ht_iy,ft_ms,id");
        for (ColumnDef cd : ALL_COLS) sb.append(",").append(cd.sqlColumn);
        sb.append(" FROM bet365_matches ORDER BY date_time DESC");

        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sb.toString())) {
            while (rs.next()) {
                HistRecord rec = new HistRecord(ALL_COLS.size());
                rec.league = rs.getString("country_league");
                rec.date = rs.getString("date_time");
                rec.homeTeam = rs.getString("home_team");
                rec.awayTeam = rs.getString("away_team");
                rec.id = rs.getString("id");
                rec.htHome = rec.htAway = rec.ftHome = rec.ftAway = -1;

                String ht = rs.getString("ht_iy");
                if (ht != null && ht.contains("-")) {
                    String[] p = ht.split("-");
                    if (p.length == 2) {
                        try { rec.htHome = Integer.parseInt(p[0].trim()); } catch (Exception _) {}
                        try { rec.htAway = Integer.parseInt(p[1].trim()); } catch (Exception _) {}
                    }
                }
                String ft = rs.getString("ft_ms");
                if (ft != null && ft.contains("-")) {
                    String[] p = ft.split("-");
                    if (p.length == 2) {
                        try { rec.ftHome = Integer.parseInt(p[0].trim()); } catch (Exception _) {}
                        try { rec.ftAway = Integer.parseInt(p[1].trim()); } catch (Exception _) {}
                    }
                }

                for (int i = 0; i < ALL_COLS.size(); i++) {
                    String v = rs.getString(ALL_COLS.get(i).sqlColumn);
                    rec.odds[i] = parseOdds(v);
                }
                allRecords.add(rec);
            }
        }
        System.out.println("✅ " + allRecords.size() + " geçmiş kayıt yüklendi.\n");
    }

    private double parseOdds(String s) {
        if (s == null || s.isEmpty() || s.equals("-")) return 0.0;
        try { return Double.parseDouble(s.replace(',', '.')); } catch (Exception _) { return 0.0; }
    }

    // ==================== FLASHSCORE KAZIMA ====================
    private List<MatchInfo> scrapeTodayMatches() {
        List<MatchInfo> matches = new ArrayList<>();
        System.out.println("🔍 Flashscore'dan bugünkü maçlar çekiliyor...\n");

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             Page page = browser.newPage()) {

            page.navigate("https://www.flashscore.co.uk/football/");
            try { page.locator("#onetrust-accept-btn-handler").click(new Locator.ClickOptions().setTimeout(3000)); } catch (Exception _) {}
            page.waitForSelector("div[id^='g_1_'].event__match", new Page.WaitForSelectorOptions().setTimeout(15000));

            Locator rows = page.locator("div[id^='g_1_'].event__match");
            int count = rows.count();
            System.out.println("📊 Toplam " + count + " maç bulundu.\n");

            for (int i = 0; i < count; i++) {
                try {
                    Locator row = rows.nth(i);
                    String matchId = row.getAttribute("id").replace("g_1_", "");
                    String home = row.locator(".event__homeParticipant").innerText().trim();
                    String away = row.locator(".event__awayParticipant").innerText().trim();
                    MatchInfo mi = new MatchInfo();
                    mi.id = matchId;
                    mi.home = home;
                    mi.away = away;
                    mi.date = LocalDate.now().toString();
                    matches.add(mi);
                } catch (Exception _) {}
            }

            for (MatchInfo mi : matches) fetchOddsForMatch(mi);
            System.out.println("✅ " + matches.size() + " maçın oranları çekildi.\n");

        } catch (Exception e) {
            System.err.println("❌ Scraper hatası: " + e.getMessage());
        }
        return matches;
    }

    private void fetchOddsForMatch(MatchInfo mi) {
        // Skor çekme (gerekirse, ama similarity için skor gerekmez, sadece oranlar lazım)
        String scoreUrl = "https://5.flashscore.ninja/5/x/feed/df_sui_1_" + mi.id;
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(scoreUrl))
                    .header("User-Agent", "Mozilla/5.0").header("x-fsign", "SW9D1eZo").GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            // skor ayrıştırma atlanabilir
        } catch (Exception _) {}

        String oddsUrl = String.format(
                "https://global.ds.lsapp.eu/odds/pq_graphql?_hash=oce&eventId=%s&projectId=5&geoIpCode=AZ&geoIpSubdivisionCode=AZBA",
                mi.id);
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oddsUrl))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().startsWith("{")) parseOdds(mi, resp.body());
        } catch (Exception _) {}
    }

    private void parseOdds(MatchInfo mi, String jsonBody) {
        JSONObject root = new JSONObject(jsonBody);
        JSONObject data = root.optJSONObject("data");
        if (data == null) return;
        JSONObject oddsData = data.optJSONObject("findOddsByEventId");
        if (oddsData == null) return;
        JSONArray oddsList = oddsData.optJSONArray("odds");
        if (oddsList == null) return;

        String homePartId = null, awayPartId = null;
        for (int i = 0; i < oddsList.length(); i++) {
            JSONObject entry = oddsList.getJSONObject(i);
            if (entry.getInt("bookmakerId") != 16) continue;
            if ("HOME_DRAW_AWAY".equals(entry.getString("bettingType")) && "FULL_TIME".equals(entry.getString("bettingScope"))) {
                JSONArray items = entry.getJSONArray("odds");
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.getJSONObject(j);
                    if (!item.isNull("eventParticipantId")) {
                        String pid = item.getString("eventParticipantId");
                        if (homePartId == null) homePartId = pid;
                        else if (!pid.equals(homePartId)) awayPartId = pid;
                    }
                }
                break;
            }
        }

        for (int i = 0; i < oddsList.length(); i++) {
            JSONObject entry = oddsList.getJSONObject(i);
            if (entry.getInt("bookmakerId") != 16) continue;
            String bettingType = entry.getString("bettingType");
            String scope = entry.getString("bettingScope");
            JSONArray items = entry.getJSONArray("odds");

            switch (bettingType) {
                case "HOME_DRAW_AWAY":
                    String period = mapScope(scope);
                    if (period != null) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.getJSONObject(j);
                            String val = getOddsValue(item);
                            String pid = item.isNull("eventParticipantId") ? null : item.getString("eventParticipantId");
                            String key;
                            if (pid == null) key = "1x2|" + period + "|Draw";
                            else if (pid.equals(homePartId)) key = "1x2|" + period + "|Home";
                            else key = "1x2|" + period + "|Away";
                            mi.odds.put(key, val);
                        }
                    }
                    break;
                case "BOTH_TEAMS_TO_SCORE":
                    period = mapScope(scope);
                    if (period != null) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.getJSONObject(j);
                            boolean yes = item.getBoolean("bothTeamsToScore");
                            mi.odds.put("Both teams|" + period + "|" + (yes ? "Yes" : "No"), getOddsValue(item));
                        }
                    }
                    break;
                case "OVER_UNDER":
                    period = mapScope(scope);
                    if (period != null) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.getJSONObject(j);
                            if (!item.isNull("handicap")) {
                                double h = item.getJSONObject("handicap").getDouble("value");
                                String sel = item.getString("selection");
                                mi.odds.put("Over/Under|" + period + "|" + ("OVER".equals(sel) ? "O " : "U ") + h, getOddsValue(item));
                            }
                        }
                    }
                    break;
                case "DOUBLE_CHANCE":
                    period = mapScope(scope);
                    if (period != null) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.getJSONObject(j);
                            String val = getOddsValue(item);
                            String pid = item.isNull("eventParticipantId") ? null : item.getString("eventParticipantId");
                            String key;
                            if (pid == null) key = "Double chance|" + period + "|12";
                            else if (pid.equals(homePartId)) key = "Double chance|" + period + "|1X";
                            else key = "Double chance|" + period + "|X2";
                            mi.odds.put(key, val);
                        }
                    }
                    break;
                case "CORRECT_SCORE":
                    period = mapScope(scope);
                    if (period != null && !"2nd Half".equals(period)) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.getJSONObject(j);
                            if (!item.isNull("score")) {
                                String score = item.getString("score").replace(" ", "");
                                mi.odds.put("Correct score|" + period + "|" + score, getOddsValue(item));
                            }
                        }
                    }
                    break;
                case "HALF_FULL_TIME":
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject item = items.getJSONObject(j);
                        if (!item.isNull("winner")) {
                            mi.odds.put("HTFT|" + item.getString("winner"), getOddsValue(item));
                        }
                    }
                    break;
            }
        }
    }

    private String mapScope(String scope) {
        switch (scope) {
            case "FULL_TIME": return "Full Time";
            case "FIRST_HALF": return "1st Half";
            case "SECOND_HALF": return "2nd Half";
            default: return null;
        }
    }

    private String getOddsValue(JSONObject item) {
        try {
            if (!item.isNull("opening")) return item.getString("opening");
            if (!item.isNull("value")) return item.getString("value");
        } catch (Exception _) {}
        return "-";
    }

    // ==================== BENZERLİK ANALİZİ ====================
    private static final double[] TOLERANCES = {0.00, 0.01};
    private static final double[] SIMILARITIES = {0.99, 0.97, 0.95};

    private void analyzeMatchSimilarity(MatchInfo match) {
        System.out.println("\n⚽ " + match.home + " vs " + match.away + " (" + match.date + ")");
        System.out.println("═══════════════════════════════════════════════════════════════");

        // Günlük maçı HistRecord'a dönüştür (odds dizisi)
        HistRecord target = new HistRecord(ALL_COLS.size());
        target.homeTeam = match.home;
        target.awayTeam = match.away;
        target.id = "today_" + match.id; // tarihî kayıtlarla çakışmasın diye
        for (int i = 0; i < ALL_COLS.size(); i++) {
            String key = ALL_COLS.get(i).flashscoreKey;
            String val = match.odds.getOrDefault(key, "-");
            target.odds[i] = parseOdds(val);
        }

        List<CombinationResult> allCombinations = new ArrayList<>();
        for (double tol : TOLERANCES) {
            for (double sim : SIMILARITIES) {
                System.out.printf("→ Tolerance: %.1f%% | Similarity: %.1f%%", tol * 100, sim * 100);
                List<SimilarMatch> matches = findSimilar(target, tol, sim);
                System.out.printf(" → %d maç bulundu%n", matches.size());
                if (!matches.isEmpty()) {
                    allCombinations.add(new CombinationResult(tol, sim, matches));
                }
            }
        }

        printSimilarityResults(allCombinations);
    }

    private List<SimilarMatch> findSimilar(HistRecord target, double tol, double simThreshold) {
        List<SimilarMatch> result = new ArrayList<>();
        double[] tOdds = target.odds;
        int oddsLen = tOdds.length;

        for (HistRecord row : allRecords) {
            if (row.id.equals(target.id)) continue;
            int valid = 0, match = 0;
            for (int i = 0; i < oddsLen; i++) {
                double tv = tOdds[i], rv = row.odds[i];
                if (tv == 0.0 || Double.isNaN(tv) || Double.isNaN(rv)) continue;
                double denom = (tv != 0.0) ? tv : 1.0;
                double relDiff = Math.abs(rv - tv) / denom;
                valid++;
                if (!Double.isNaN(relDiff) && relDiff <= tol) match++;
            }
            if (valid > 0) {
                double ratio = (double) match / valid;
                if (ratio >= simThreshold) {
                    SimilarMatch sm = new SimilarMatch();
                    sm.league = row.league;
                    sm.date = row.date;
                    sm.homeTeam = row.homeTeam;
                    sm.awayTeam = row.awayTeam;
                    sm.htScore = row.htScore();
                    sm.ftScore = row.ftScore();
                    sm.similarityPercent = ratio * 100.0;
                    result.add(sm);
                }
            }
        }
        return result;
    }

    private void printSimilarityResults(List<CombinationResult> results) {
        if (results.isEmpty()) {
            System.out.println("❌ Hiçbir kombinasyonda yeterli benzerlik bulunamadı.\n");
            return;
        }

        results.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        for (CombinationResult res : results) {
            System.out.printf("%n✅ Tolerance: %.1f%% | Similarity: %.1f%% → %d maç%n",
                    res.tolerance * 100, res.similarity * 100, res.matches.size());
            res.matches.stream()
                    .sorted((m1, m2) -> Double.compare(m2.similarityPercent, m1.similarityPercent))
                    .limit(10)
                    .forEach(m -> System.out.printf("   %-25s %-20s %-20s %-5s %-5s  %%%.1f%n",
                            m.league, m.date, m.homeTeam + " vs " + m.awayTeam,
                            m.htScore, m.ftScore, m.similarityPercent));

            Map<String, Long> ftCount = res.matches.stream()
                    .collect(Collectors.groupingBy(m -> m.ftScore, Collectors.counting()));
            if (!ftCount.isEmpty()) {
                Map.Entry<String, Long> mostCommon = ftCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue()).orElse(null);
                if (mostCommon != null) {
                    double conf = (double) mostCommon.getValue() / res.matches.size() * 100;
                    System.out.printf("   🔮 Tahmin (FT Skor): %s | Güven: %%%.1f (%d/%d)%n",
                            mostCommon.getKey(), conf, mostCommon.getValue(), res.matches.size());
                }
            }
        }

        // En iyi sonuç
        CombinationResult best = results.get(0);
        System.out.println("\n🏆 EN İYİ BENZERLİK SONUCU:");
        System.out.printf("   Tolerance: %.1f%%, Similarity: %.1f%% → %d maç%n",
                best.tolerance * 100, best.similarity * 100, best.matches.size());
        best.matches.stream()
                .sorted((m1, m2) -> Double.compare(m2.similarityPercent, m1.similarityPercent))
                .limit(12)
                .forEach(m -> System.out.printf("   %-25s %-20s %-20s %-5s %-5s  %%%.1f%n",
                        m.league, m.date, m.homeTeam + " vs " + m.awayTeam,
                        m.htScore, m.ftScore, m.similarityPercent));
        Map<String, Long> bestFtCount = best.matches.stream()
                .collect(Collectors.groupingBy(m -> m.ftScore, Collectors.counting()));
        if (!bestFtCount.isEmpty()) {
            Map.Entry<String, Long> mostCommon = bestFtCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(null);
            if (mostCommon != null) {
                double conf = (double) mostCommon.getValue() / best.matches.size() * 100;
                System.out.printf("%n🔮 FİNAL TAHMİN: %s (Güven: %%%.1f)%n", mostCommon.getKey(), conf);
            }
        }
        System.out.println();
    }

    // ==================== YARDIMCI SINIFLAR ====================
    static class SimilarMatch {
        String league, date, homeTeam, awayTeam;
        String htScore, ftScore;
        double similarityPercent;
    }

    static class CombinationResult {
        double tolerance, similarity;
        List<SimilarMatch> matches;
        CombinationResult(double t, double s, List<SimilarMatch> m) {
            tolerance = t; similarity = s; matches = m;
        }
    }

    // ==================== ANA ÇALIŞTIRMA ====================
    public void run() {
        List<MatchInfo> todayMatches = scrapeTodayMatches();
        if (todayMatches.isEmpty()) {
            System.out.println("❌ Bugünkü maç bulunamadı!");
            return;
        }
        System.out.println("\n🤖 BENZERLİK TABANLI OTOMATİK ANALİZ BAŞLIYOR (" + todayMatches.size() + " maç)\n");
        for (MatchInfo match : todayMatches) {
            analyzeMatchSimilarity(match);
        }
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("✅ TÜM ANALİZLER TAMAMLANDI");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    public static void main(String[] args) {
        new Bet365DailySimilarityAnalyzer().run();
    }
}