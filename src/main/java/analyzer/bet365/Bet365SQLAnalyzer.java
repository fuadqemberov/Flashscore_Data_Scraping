package analyzer.bet365;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class Bet365SQLAnalyzer {

    // ==================== KOLON TANIMLARI ====================
    static class ColumnDef {
        String sqlColumn;
        String displayName;
        String flashscoreKey;
        ColumnDef(String sqlColumn, String displayName, String flashscoreKey) {
            this.sqlColumn = sqlColumn;
            this.displayName = displayName;
            this.flashscoreKey = flashscoreKey;
        }
    }

    private static final List<ColumnDef> ALL_ODDS_COLS = List.of(
            new ColumnDef("ft_1_a", "MS 1", "1x2|Full Time|Home"),
            new ColumnDef("ft_x_a", "MS X", "1x2|Full Time|Draw"),
            new ColumnDef("ft_2_a", "MS 2", "1x2|Full Time|Away"),
            new ColumnDef("first_1_a", "İY 1", "1x2|1st Half|Home"),
            new ColumnDef("first_x_a", "İY X", "1x2|1st Half|Draw"),
            new ColumnDef("first_2_a", "İY 2", "1x2|1st Half|Away"),
            new ColumnDef("second_1_a", "2Y 1", "1x2|2nd Half|Home"),
            new ColumnDef("second_x_a", "2Y X", "1x2|2nd Half|Draw"),
            new ColumnDef("second_2_a", "2Y 2", "1x2|2nd Half|Away"),
            new ColumnDef("bts_ft_yes_a", "KG Evet", "Both teams|Full Time|Yes"),
            new ColumnDef("bts_ft_no_a", "KG Hayır", "Both teams|Full Time|No"),
            new ColumnDef("bts_first_yes_a", "İY KG Evet", "Both teams|1st Half|Yes"),
            new ColumnDef("bts_first_no_a", "İY KG Hayır", "Both teams|1st Half|No"),
            new ColumnDef("bts_second_yes_a", "2Y KG Evet", "Both teams|2nd Half|Yes"),
            new ColumnDef("bts_second_no_a", "2Y KG Hayır", "Both teams|2nd Half|No"),
            new ColumnDef("dbc_ft_1x_a", "ÇŞ 1X", "Double chance|Full Time|1X"),
            new ColumnDef("dbc_ft_12_a", "ÇŞ 12", "Double chance|Full Time|12"),
            new ColumnDef("dbc_ft_x2_a", "ÇŞ X2", "Double chance|Full Time|X2"),
            new ColumnDef("dbc_first_1x_a", "İY ÇŞ 1X", "Double chance|1st Half|1X"),
            new ColumnDef("dbc_first_12_a", "İY ÇŞ 12", "Double chance|1st Half|12"),
            new ColumnDef("dbc_first_x2_a", "İY ÇŞ X2", "Double chance|1st Half|X2"),
            new ColumnDef("ft_0_5_over_a", "A/U 0.5 Üst", "Over/Under|Full Time|O 0.5"),
            new ColumnDef("ft_0_5_under_a", "A/U 0.5 Alt", "Over/Under|Full Time|U 0.5"),
            new ColumnDef("ft_1_5_over_a", "A/U 1.5 Üst", "Over/Under|Full Time|O 1.5"),
            new ColumnDef("ft_1_5_under_a", "A/U 1.5 Alt", "Over/Under|Full Time|U 1.5"),
            new ColumnDef("ft_2_5_over_a", "A/U 2.5 Üst", "Over/Under|Full Time|O 2.5"),
            new ColumnDef("ft_2_5_under_a", "A/U 2.5 Alt", "Over/Under|Full Time|U 2.5"),
            new ColumnDef("ft_3_5_over_a", "A/U 3.5 Üst", "Over/Under|Full Time|O 3.5"),
            new ColumnDef("ft_3_5_under_a", "A/U 3.5 Alt", "Over/Under|Full Time|U 3.5"),
            new ColumnDef("ft_4_5_over_a", "A/U 4.5 Üst", "Over/Under|Full Time|O 4.5"),
            new ColumnDef("ft_4_5_under_a", "A/U 4.5 Alt", "Over/Under|Full Time|U 4.5"),
            new ColumnDef("ft_5_5_over_a", "A/U 5.5 Üst", "Over/Under|Full Time|O 5.5"),
            new ColumnDef("ft_5_5_under_a", "A/U 5.5 Alt", "Over/Under|Full Time|U 5.5"),
            new ColumnDef("first_0_5_over_a", "İY A/U 0.5 Üst", "Over/Under|1st Half|O 0.5"),
            new ColumnDef("first_0_5_under_a", "İY A/U 0.5 Alt", "Over/Under|1st Half|U 0.5"),
            new ColumnDef("first_1_5_over_a", "İY A/U 1.5 Üst", "Over/Under|1st Half|O 1.5"),
            new ColumnDef("first_1_5_under_a", "İY A/U 1.5 Alt", "Over/Under|1st Half|U 1.5"),
            new ColumnDef("first_2_5_over_a", "İY A/U 2.5 Üst", "Over/Under|1st Half|O 2.5"),
            new ColumnDef("first_2_5_under_a", "İY A/U 2.5 Alt", "Over/Under|1st Half|U 2.5"),
            new ColumnDef("second_0_5_over_a", "2Y A/U 0.5 Üst", "Over/Under|2nd Half|O 0.5"),
            new ColumnDef("second_0_5_under_a", "2Y A/U 0.5 Alt", "Over/Under|2nd Half|U 0.5"),
            new ColumnDef("second_1_5_over_a", "2Y A/U 1.5 Üst", "Over/Under|2nd Half|O 1.5"),
            new ColumnDef("second_1_5_under_a", "2Y A/U 1.5 Alt", "Over/Under|2nd Half|U 1.5"),
            new ColumnDef("second_2_5_over_a", "2Y A/U 2.5 Üst", "Over/Under|2nd Half|O 2.5"),
            new ColumnDef("second_2_5_under_a", "2Y A/U 2.5 Alt", "Over/Under|2nd Half|U 2.5"),
            new ColumnDef("ht_ft_11_a", "HT/FT 1/1", "HTFT|1/1"),
            new ColumnDef("ht_ft_1x_a", "HT/FT 1/X", "HTFT|1/X"),
            new ColumnDef("ht_ft_12_a", "HT/FT 1/2", "HTFT|1/2"),
            new ColumnDef("ht_ft_x1_a", "HT/FT X/1", "HTFT|X/1"),
            new ColumnDef("ht_ft_xx_a", "HT/FT X/X", "HTFT|X/X"),
            new ColumnDef("ht_ft_x2_a", "HT/FT X/2", "HTFT|X/2"),
            new ColumnDef("ht_ft_21_a", "HT/FT 2/1", "HTFT|2/1"),
            new ColumnDef("ht_ft_2x_a", "HT/FT 2/X", "HTFT|2/X"),
            new ColumnDef("ht_ft_22_a", "HT/FT 2/2", "HTFT|2/2"),
            new ColumnDef("first_score_1_0_a", "İY Skor 1:0", "Correct score|1st Half|1:0"),
            new ColumnDef("first_score_2_0_a", "İY Skor 2:0", "Correct score|1st Half|2:0"),
            new ColumnDef("first_score_2_1_a", "İY Skor 2:1", "Correct score|1st Half|2:1"),
            new ColumnDef("first_score_3_0_a", "İY Skor 3:0", "Correct score|1st Half|3:0"),
            new ColumnDef("first_score_3_1_a", "İY Skor 3:1", "Correct score|1st Half|3:1"),
            new ColumnDef("first_score_3_2_a", "İY Skor 3:2", "Correct score|1st Half|3:2"),
            new ColumnDef("first_score_0_0_a", "İY Skor 0:0", "Correct score|1st Half|0:0"),
            new ColumnDef("first_score_1_1_a", "İY Skor 1:1", "Correct score|1st Half|1:1"),
            new ColumnDef("first_score_2_2_a", "İY Skor 2:2", "Correct score|1st Half|2:2"),
            new ColumnDef("first_score_0_1_a", "İY Skor 0:1", "Correct score|1st Half|0:1"),
            new ColumnDef("first_score_0_2_a", "İY Skor 0:2", "Correct score|1st Half|0:2"),
            new ColumnDef("first_score_1_2_a", "İY Skor 1:2", "Correct score|1st Half|1:2"),
            new ColumnDef("first_score_0_3_a", "İY Skor 0:3", "Correct score|1st Half|0:3"),
            new ColumnDef("first_score_1_3_a", "İY Skor 1:3", "Correct score|1st Half|1:3"),
            new ColumnDef("first_score_2_3_a", "İY Skor 2:3", "Correct score|1st Half|2:3"),
            new ColumnDef("ft_score_1_0_a", "MS Skor 1:0", "Correct score|Full Time|1:0"),
            new ColumnDef("ft_score_2_0_a", "MS Skor 2:0", "Correct score|Full Time|2:0"),
            new ColumnDef("ft_score_2_1_a", "MS Skor 2:1", "Correct score|Full Time|2:1"),
            new ColumnDef("ft_score_3_0_a", "MS Skor 3:0", "Correct score|Full Time|3:0"),
            new ColumnDef("ft_score_3_1_a", "MS Skor 3:1", "Correct score|Full Time|3:1"),
            new ColumnDef("ft_score_3_2_a", "MS Skor 3:2", "Correct score|Full Time|3:2"),
            new ColumnDef("ft_score_4_0_a", "MS Skor 4:0", "Correct score|Full Time|4:0"),
            new ColumnDef("ft_score_4_1_a", "MS Skor 4:1", "Correct score|Full Time|4:1"),
            new ColumnDef("ft_score_4_2_a", "MS Skor 4:2", "Correct score|Full Time|4:2"),
            new ColumnDef("ft_score_4_3_a", "MS Skor 4:3", "Correct score|Full Time|4:3"),
            new ColumnDef("ft_score_5_0_a", "MS Skor 5:0", "Correct score|Full Time|5:0"),
            new ColumnDef("ft_score_5_1_a", "MS Skor 5:1", "Correct score|Full Time|5:1"),
            new ColumnDef("ft_score_5_2_a", "MS Skor 5:2", "Correct score|Full Time|5:2"),
            new ColumnDef("ft_score_0_0_a", "MS Skor 0:0", "Correct score|Full Time|0:0"),
            new ColumnDef("ft_score_1_1_a", "MS Skor 1:1", "Correct score|Full Time|1:1"),
            new ColumnDef("ft_score_2_2_a", "MS Skor 2:2", "Correct score|Full Time|2:2"),
            new ColumnDef("ft_score_3_3_a", "MS Skor 3:3", "Correct score|Full Time|3:3"),
            new ColumnDef("ft_score_4_4_a", "MS Skor 4:4", "Correct score|Full Time|4:4"),
            new ColumnDef("ft_score_0_1_a", "MS Skor 0:1", "Correct score|Full Time|0:1"),
            new ColumnDef("ft_score_0_2_a", "MS Skor 0:2", "Correct score|Full Time|0:2"),
            new ColumnDef("ft_score_1_2_a", "MS Skor 1:2", "Correct score|Full Time|1:2"),
            new ColumnDef("ft_score_0_3_a", "MS Skor 0:3", "Correct score|Full Time|0:3"),
            new ColumnDef("ft_score_1_3_a", "MS Skor 1:3", "Correct score|Full Time|1:3"),
            new ColumnDef("ft_score_2_3_a", "MS Skor 2:3", "Correct score|Full Time|2:3"),
            new ColumnDef("ft_score_0_4_a", "MS Skor 0:4", "Correct score|Full Time|0:4"),
            new ColumnDef("ft_score_1_4_a", "MS Skor 1:4", "Correct score|Full Time|1:4"),
            new ColumnDef("ft_score_2_4_a", "MS Skor 2:4", "Correct score|Full Time|2:4"),
            new ColumnDef("ft_score_3_4_a", "MS Skor 3:4", "Correct score|Full Time|3:4"),
            new ColumnDef("ft_score_0_5_a", "MS Skor 0:5", "Correct score|Full Time|0:5"),
            new ColumnDef("ft_score_1_5_a", "MS Skor 1:5", "Correct score|Full Time|1:5"),
            new ColumnDef("ft_score_2_5_a", "MS Skor 2:5", "Correct score|Full Time|2:5")
    );

    // ==================== VERİ YAPILARI ====================
    static class MatchInfo {
        String id, home, away, date;
        String ftScore = "-";
        Map<String, String> odds = new HashMap<>();
    }

    static class SQLRecord {
        Map<String, Object> data = new HashMap<>();
        double getOdds(String col) {
            Object v = data.get(col);
            if (v == null) return 0.0;
            if (v instanceof Number) return ((Number) v).doubleValue();
            try { return Double.parseDouble(v.toString().replace(',', '.')); } catch (Exception e) { return 0.0; }
        }
        String getStr(String col) {
            Object v = data.get(col);
            return v == null ? "-" : v.toString();
        }
    }

    static class TwinResult {
        String lig, date, home, away, htScore, ftScore;
        double similarity;
    }

    static class RunResult {
        int runNumber;
        List<SQLRecord> matches;
        List<String> activeCols;
        boolean success;
    }

    // ==================== BAĞLANTI & HTTP ====================
    private Connection conn;
    private List<String> sqlColumns = new ArrayList<>();
    private List<SQLRecord> allSQLRecords = new ArrayList<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public Bet365SQLAnalyzer() {
        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/postgres",
                    "postgres", "fuad123"
            );
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "bet365_matches", null)) {
                while (rs.next()) sqlColumns.add(rs.getString("COLUMN_NAME"));
            }
            System.out.println("✅ Veritabanına bağlandı. " + sqlColumns.size() + " kolon bulundu.");
            loadAllSQLRecords();
        } catch (Exception e) {
            System.err.println("❌ Veritabanı hatası: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void loadAllSQLRecords() throws SQLException {
        System.out.println("📊 SQL kayıtları yükleniyor...");
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < sqlColumns.size(); i++) {
            sql.append(sqlColumns.get(i));
            if (i < sqlColumns.size() - 1) sql.append(", ");
        }
        sql.append(" FROM bet365_matches ORDER BY date_time DESC");

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            while (rs.next()) {
                SQLRecord rec = new SQLRecord();
                for (String col : sqlColumns) {
                    rec.data.put(col, rs.getObject(col));
                }
                allSQLRecords.add(rec);
            }
        }
        System.out.println("✅ " + allSQLRecords.size() + " SQL kaydı yüklendi.\n");
    }

    // ==================== FLASHSCORE SCRAPER ====================
    private List<MatchInfo> scrapeTodayMatches() {
        List<MatchInfo> matches = new ArrayList<>();
        System.out.println("🔍 Flashscore'dan bugünkü maçlar çekiliyor...\n");

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             Page page = browser.newPage()) {

            page.navigate("https://www.flashscore.co.uk/football/");
            try { page.locator("#onetrust-accept-btn-handler").click(new Locator.ClickOptions().setTimeout(3000)); } catch (Exception ignored) {}
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
                    mi.id = matchId; mi.home = home; mi.away = away; mi.date = LocalDate.now().toString();
                    matches.add(mi);
                } catch (Exception ignored) {}
            }

            for (MatchInfo mi : matches) fetchOddsForMatch(mi);
            System.out.println("✅ " + matches.size() + " maçın oranları çekildi.\n");

        } catch (Exception e) {
            System.err.println("❌ Scraper hatası: " + e.getMessage());
            e.printStackTrace();
        }
        return matches;
    }

    private void fetchOddsForMatch(MatchInfo mi) {
        String scoreUrl = "https://5.flashscore.ninja/5/x/feed/df_sui_1_" + mi.id;
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(scoreUrl))
                    .header("User-Agent", "Mozilla/5.0").header("x-fsign", "SW9D1eZo").GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) parseScores(mi, resp.body());
        } catch (Exception ignored) {}

        String oddsUrl = String.format("https://global.ds.lsapp.eu/odds/pq_graphql?_hash=oce&eventId=%s&projectId=5&geoIpCode=AZ&geoIpSubdivisionCode=AZBA", mi.id);
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oddsUrl))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().startsWith("{")) parseOdds(mi, resp.body());
        } catch (Exception ignored) {}
    }

    private void parseScores(MatchInfo mi, String body) {
        String htHome = "-", htAway = "-", ftHome = "-", ftAway = "-";
        for (String sec : body.split("~")) {
            String half = null, ig = null, ih = null;
            for (String part : sec.split("¬")) {
                if (part.startsWith("AC÷")) half = part.substring(3);
                else if (part.startsWith("IG÷")) ig = part.substring(3);
                else if (part.startsWith("IH÷")) ih = part.substring(3);
            }
            if (half == null || ig == null || ih == null) continue;
            if ("1st Half".equals(half)) { htHome = ig; htAway = ih; }
            else if ("2nd Half".equals(half)) {
                try {
                    ftHome = String.valueOf(Integer.parseInt(htHome) + Integer.parseInt(ig));
                    ftAway = String.valueOf(Integer.parseInt(htAway) + Integer.parseInt(ih));
                } catch (NumberFormatException e) { ftHome = ig; ftAway = ih; }
            }
        }
        mi.ftScore = ftHome + "-" + ftAway;
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
        } catch (Exception ignored) {}
        return "-";
    }

    // ==================== MOD 1: TOLERANCE + SIMILARITY (Flashscore → SQL) ====================
    public void runToleranceSimilarity() {
        List<MatchInfo> todayMatches = scrapeTodayMatches();
        if (todayMatches.isEmpty()) {
            System.out.println("❌ Bugünkü maç bulunamadı!");
            return;
        }

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("🔬 MOD 1: TOLERANCE + SIMILARITY ANALİZİ");
        System.out.println("   Flashscore'dan çekilen maçlar → SQL'deki geçmiş maçlarla karşılaştırılıyor");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        double[] tolerances = {0.01};
        double[] similarities = {0.99, 0.98, 0.97,0.96,0.95};

        for (MatchInfo match : todayMatches) {
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("⚽ " + match.home + " vs " + match.away);
            System.out.println("═══════════════════════════════════════════════════════════════");

            System.out.println();

            // Tüm kombinasyonları test et
            List<Map<String, Object>> allResults = new ArrayList<>();

            for (double tol : tolerances) {
                for (double sim : similarities) {
                    List<TwinResult> twins = findTwinsTolerance(match, tol, sim);
                    System.out.printf("→ Tolerance: %.1f%% | Similarity: %.1f%% → %d maç%n",
                            tol * 100, sim * 100, twins.size());

                    if (!twins.isEmpty()) {
                        Map<String, Object> res = new HashMap<>();
                        res.put("tolerance", tol);
                        res.put("similarity", sim);
                        res.put("count", twins.size());
                        res.put("twins", twins);
                        allResults.add(res);
                    }
                }
            }

            // Sonuçları göster
            printToleranceResults(allResults, match);
            System.out.println();
        }
    }

    private List<TwinResult> findTwinsTolerance(MatchInfo match, double tol, double sim) {
        List<TwinResult> twins = new ArrayList<>();

        // Hedef maçın oranlarını al
        double[] targetOdds = new double[ALL_ODDS_COLS.size()];
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
            String val = match.odds.getOrDefault(ALL_ODDS_COLS.get(i).flashscoreKey, "");
            targetOdds[i] = parseOdds(val);
        }

        // SQL kayıtları ile karşılaştır
        for (SQLRecord rec : allSQLRecords) {
            int matchCount = 0;
            int validCount = 0;

            for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
                double tVal = targetOdds[i];
                double rVal = rec.getOdds(ALL_ODDS_COLS.get(i).sqlColumn);

                if (tVal == 0 || rVal == 0) continue;
                validCount++;

                double denom = tVal != 0 ? tVal : 1.0;
                double relDiff = Math.abs(rVal - tVal) / denom;
                if (relDiff <= tol) matchCount++;
            }

            if (validCount == 0) continue;
            double matchRatio = (double) matchCount / validCount;

            if (matchRatio >= sim) {
                TwinResult tr = new TwinResult();
                tr.lig = rec.getStr("country_league");
                tr.date = rec.getStr("date_time");
                tr.home = rec.getStr("home_team");
                tr.away = rec.getStr("away_team");
                tr.htScore = rec.getStr("ht_iy");
                tr.ftScore = rec.getStr("ft_ms");
                tr.similarity = Math.round(matchRatio * 1000.0) / 10.0;
                twins.add(tr);
            }
        }

        twins.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        return twins;
    }

    private void printToleranceResults(List<Map<String, Object>> allResults, MatchInfo match) {
        if (allResults.isEmpty()) {
            System.out.println("\n⚠️  SONUÇ: Hiçbir kombinasyonda eşleşen maç bulunamadı!");
            return;
        }

        // En yüksek benzerliğe göre sırala
        allResults.sort((a, b) -> {
            int cmp = Double.compare((Double) b.get("similarity"), (Double) a.get("similarity"));
            if (cmp != 0) return cmp;
            return Double.compare((Double) a.get("tolerance"), (Double) b.get("tolerance"));
        });

        System.out.println("\n📊 TÜM KOMBİNASYON SONUÇLARI:");
        for (Map<String, Object> res : allResults) {
            double tol = (Double) res.get("tolerance");
            double sim = (Double) res.get("similarity");
            int count = (Integer) res.get("count");
            @SuppressWarnings("unchecked")
            List<TwinResult> twins = (List<TwinResult>) res.get("twins");

            System.out.printf("\n✅ Tolerance: %.1f%% | Similarity: %.1f%% → %d maç%n", tol * 100, sim * 100, count);
            printTwinTable(twins, 10);

            // Skor tahmini
            Map<String, Integer> scoreCounts = new HashMap<>();
            for (TwinResult t : twins) scoreCounts.merge(t.ftScore, 1, Integer::sum);
            String mostCommon = scoreCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
            int cnt = scoreCounts.getOrDefault(mostCommon, 0);
            double conf = Math.round(cnt * 100.0 / count * 10.0) / 10.0;
            System.out.printf("   🔮 Proqnoz (FT Score): %s | Etibarlılıq: %%%s (%d/%d)%n", mostCommon, conf, cnt, count);
        }

        // En iyi sonuç
        System.out.println("\n" + "═".repeat(90));
        System.out.println("🏆 EN İYİ SONUÇ");
        System.out.println("═".repeat(90));

        Map<String, Object> best = allResults.get(0);
        double bestTol = (Double) best.get("tolerance");
        double bestSim = (Double) best.get("similarity");
        int bestCount = (Integer) best.get("count");
        @SuppressWarnings("unchecked")
        List<TwinResult> bestTwins = (List<TwinResult>) best.get("twins");

        System.out.printf("Tolerance: %.1f%% | Similarity: %.1f%% | Maç: %d%n", bestTol * 100, bestSim * 100, bestCount);
        printTwinTable(bestTwins, 12);

        Map<String, Integer> bestScoreCounts = new HashMap<>();
        for (TwinResult t : bestTwins) bestScoreCounts.merge(t.ftScore, 1, Integer::sum);
        String bestMostCommon = bestScoreCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
        int bestCnt = bestScoreCounts.getOrDefault(bestMostCommon, 0);
        double bestConf = Math.round(bestCnt * 100.0 / bestCount * 10.0) / 10.0;

        System.out.printf("\n🔮 FİNAL PROQNOZ (%s vs %s): %s%n", match.home, match.away, bestMostCommon);
        System.out.printf("📈 Etibarlılıq: %%%s%n", bestConf);
    }

    // ==================== MOD 2: RANDOM SHUFFLE (Sadece SQL) ====================
    public void runRandomShuffle(int targetRowIndex) {
        if (targetRowIndex < 0 || targetRowIndex >= allSQLRecords.size()) {
            System.out.println("❌ Hedef satır dışında! Toplam: " + allSQLRecords.size());
            return;
        }

        SQLRecord target = allSQLRecords.get(targetRowIndex);
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("🎲 MOD 2: RANDOM SHUFFLE (100 RUN)");
        System.out.println("   Sadece SQL'deki datalar ile analiz");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("🆕 Hedef maç: " + target.getStr("home_team") + " vs " + target.getStr("away_team"));
        System.out.println("   FT: " + target.getStr("ft_ms") + " | İY: " + target.getStr("ht_iy") + " | Tarih: " + target.getStr("date_time"));
        System.out.println("📊 Toplam SQL maç sayısı: " + (allSQLRecords.size() - 1) + "\n");

        int RUNS = 100;
        List<String> allResults = new ArrayList<>();
        List<RunResult> successfulRuns = new ArrayList<>();
        List<String> oddsCols = ALL_ODDS_COLS.stream().map(c -> c.sqlColumn).collect(Collectors.toList());

        for (int run = 1; run <= RUNS; run++) {
            List<String> shuffled = new ArrayList<>(oddsCols);
            Collections.shuffle(shuffled);

            List<String> activeCols = new ArrayList<>();
            List<SQLRecord> pool = new ArrayList<>(allSQLRecords);
            pool.remove(targetRowIndex);
            boolean success = false;

            for (String col : shuffled) {
                activeCols.add(col);
                double targetVal = target.getOdds(col);
                if (targetVal == 0) continue;

                final double tv = targetVal;
                pool = pool.stream().filter(m -> m.getOdds(col) == tv).collect(Collectors.toList());

                if (pool.isEmpty()) {
                    activeCols.remove(activeCols.size() - 1);
                    continue;
                }
                if (pool.size() >= 2 && pool.size() <= 3) {
                    success = true;
                    break;
                }
            }

            System.out.println("═".repeat(80));
            System.out.printf("🔁 RUN %02d%n", run);

            if (success && !pool.isEmpty()) {
                System.out.printf("✅ %d maç bulundu%n", pool.size());
                System.out.printf(" 🔍 Kullanılan filtreler (%d adet):%n", activeCols.size());
                for (String c : activeCols) {
                    String display = ALL_ODDS_COLS.stream()
                            .filter(cd -> cd.sqlColumn.equals(c))
                            .map(cd -> cd.displayName)
                            .findFirst().orElse(c);
                    System.out.printf("   • %-25s = %.2f%n", display, target.getOdds(c));
                }

                System.out.println("\n  Lig                  Tarih         Ev                    Dep                   İY       MS");
                System.out.println("  " + "-".repeat(80));
                for (SQLRecord r : pool) {
                    System.out.printf("  %-20s %-12s %-20s %-20s %-8s %-8s%n",
                            truncate(r.getStr("country_league"), 20),
                            truncate(r.getStr("date_time"), 12),
                            truncate(r.getStr("home_team"), 20),
                            truncate(r.getStr("away_team"), 20),
                            truncate(r.getStr("ht_iy"), 8),
                            truncate(r.getStr("ft_ms"), 8));
                }

                for (SQLRecord r : pool) {
                    allResults.add(r.getStr("ft_ms"));
                }

                Map<String, Integer> scoreCounts = new HashMap<>();
                for (SQLRecord r : pool) scoreCounts.merge(r.getStr("ft_ms"), 1, Integer::sum);
                String mostCommon = scoreCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
                int cnt = scoreCounts.getOrDefault(mostCommon, 0);
                double conf = Math.round(cnt * 100.0 / pool.size() * 10.0) / 10.0;
                System.out.printf("\n 🔮 Bu run tahmini: %s | Güven: %%%s%n", mostCommon, conf);

                RunResult rr = new RunResult();
                rr.runNumber = run;
                rr.matches = new ArrayList<>(pool);
                rr.activeCols = new ArrayList<>(activeCols);
                rr.success = true;
                successfulRuns.add(rr);
            } else {
                System.out.printf("⚠️  Uygun aralıkta maç bulunamadı (kalan: %d)%n", pool.size());
            }
        }

        // Yekun netice
        System.out.println("\n" + "═".repeat(80));
        System.out.printf("📊 YEKUN NƏTİCƏ — %d RUN\n", RUNS);
        System.out.println("═".repeat(80));

        if (allResults.isEmpty()) {
            System.out.println("❌ Hiçbir run'da sonuç bulunamadı.");
            return;
        }

        Map<String, Integer> scoreCounts = new HashMap<>();
        for (String s : allResults) scoreCounts.merge(s, 1, Integer::sum);
        int total = allResults.size();

        System.out.println("\n🏆 SKOR DAĞILIMI:");
        List<Map.Entry<String, Integer>> sorted = scoreCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());

        for (Map.Entry<String, Integer> entry : sorted) {
            String skor = entry.getKey();
            int cnt = entry.getValue();
            double pct = Math.round(cnt * 100.0 / total * 10.0) / 10.0;
            String bar = "█".repeat(Math.min(cnt, 40));
            System.out.printf("  %-12s %3dx   %5.1f%%   %s%n", skor, cnt, pct, bar);
        }

        String champion = sorted.get(0).getKey();
        int finalCnt = sorted.get(0).getValue();
        double finalConf = Math.round(finalCnt * 100.0 / total * 10.0) / 10.0;

        System.out.printf("\n🔮 FİNAL PROQNOZ: %s | Etibarlılıq: %%%s%n", champion, finalConf);
        System.out.printf("📊 Başarılı run: %d / %d%n", successfulRuns.size(), RUNS);
    }

    // ==================== YARDIMCI METODLAR ====================
    private void printTwinTable(List<TwinResult> twins, int limit) {
        System.out.println("┌──────┬─────────────────────┬─────────────────────┬─────────────────────┬──────────┬──────────┬──────────┐");
        System.out.println("│ #    │ Lig                 │ Tarih               │ Ev Sahibi           │ Deplasman│ İY       │ MS       │");
        System.out.println("├──────┼─────────────────────┼─────────────────────┼─────────────────────┼──────────┼──────────┼──────────┤");

        for (int i = 0; i < Math.min(twins.size(), limit); i++) {
            TwinResult t = twins.get(i);
            System.out.printf("│ %-4d │ %-19s │ %-19s │ %-19s │ %-8s │ %-8s │ %-8s │%n",
                    (i + 1), truncate(t.lig, 19), truncate(t.date, 19),
                    truncate(t.home, 19), truncate(t.away, 8),
                    truncate(t.htScore, 8), truncate(t.ftScore, 8));
        }

        if (twins.size() > limit) {
            System.out.println("│ ...  │ (" + (twins.size() - limit) + " maç daha var)                                      │");
        }
        System.out.println("└──────┴─────────────────────┴─────────────────────┴─────────────────────┴──────────┴──────────┴──────────┘");
    }

    private double parseOdds(String val) {
        if (val == null || val.isEmpty() || "-".equals(val)) return 0.0;
        try {
            return Double.parseDouble(val.replace(',', '.'));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "-";
        if (s.length() > maxLen) return s.substring(0, maxLen - 1) + "…";
        return s;
    }

    // ==================== MAIN ====================
    public static void main(String[] args) {
        Bet365SQLAnalyzer analyzer = new Bet365SQLAnalyzer();

        Scanner scanner = new Scanner(System.in);
        System.out.println("\n[1] Tolerance + Similarity (Flashscore → SQL)");
        System.out.println("[2] Random Shuffle (100 Run) - Sadece SQL");
        System.out.println("[3] Her iki mod");
        System.out.print("\nSeçiminiz: ");
        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                analyzer.runToleranceSimilarity();
                break;
            case "2":
                System.out.print("\n🎯 Hedef SQL satır indeksini girin (0-" + (analyzer.allSQLRecords.size() - 1) + "): ");
                int targetRow;
                try { targetRow = Integer.parseInt(scanner.nextLine().trim()); }
                catch (Exception e) { targetRow = 0; }
                analyzer.runRandomShuffle(targetRow);
                break;
            case "3":
                analyzer.runToleranceSimilarity();
                System.out.println("\n");
                System.out.print("🎯 Hedef SQL satır indeksini girin (0-" + (analyzer.allSQLRecords.size() - 1) + "): ");
                try { targetRow = Integer.parseInt(scanner.nextLine().trim()); }
                catch (Exception e) { targetRow = 0; }
                analyzer.runRandomShuffle(targetRow);
                break;
            default:
                System.out.println("Geçersiz seçim. Mod 1 çalıştırılıyor...");
                analyzer.runToleranceSimilarity();
        }
    }
}