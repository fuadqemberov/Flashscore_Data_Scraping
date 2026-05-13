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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bet365DailyAutoAnalyzer {

    // ==================== KOLON TANIMLARI (GERÇEK SQL ADLARI) ====================
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

    // Varsayılan filtre kolonları (7 adet, tekrar yok)
    private static final List<ColumnDef> DEFAULT_COLS = List.of(
            new ColumnDef("first_x_a",       "İY X",           "1x2|1st Half|Draw"),
            new ColumnDef("bts_second_no_a", "2Y KG Hayır",    "Both teams|2nd Half|No"),
            new ColumnDef("bts_first_no_a",   "İY KG Hayır",     "Both teams|1st Half|No"),
            new ColumnDef("dbc_ft_12_a",      "ÇŞ 12",          "Double chance|Full Time|12"),
            new ColumnDef("ft_1_5_over_a",    "A/U 1.5 Üst",    "Over/Under|Full Time|O 1.5"),
            new ColumnDef("bts_ft_yes_a",     "KG Evet",        "Both teams|Full Time|Yes"),
            new ColumnDef("first_2_a",        "İY 2",           "1x2|1st Half|Away")
    );

    // Ek daraltma kolonları (sırayla dene, 2-5 aralığına düşene kadar ekle)
    private static final List<ColumnDef> EXTRA_COLS = List.of(
            new ColumnDef("dbc_ft_x2_a",      "ÇŞ X2",         "Double chance|Full Time|X2"),
            new ColumnDef("dbc_ft_1x_a",      "ÇŞ 1X",         "Double chance|Full Time|1X"),
            new ColumnDef("first_1_a",        "İY 1",          "1x2|1st Half|Home"),
            new ColumnDef("first_2_a",        "İY 2",          "1x2|1st Half|Away"),
            new ColumnDef("ft_1_a",           "MS 1",          "1x2|Full Time|Home"),
            new ColumnDef("ft_2_a",           "MS 2",          "1x2|Full Time|Away"),
            new ColumnDef("ft_4_5_under_a",   "A/U 4.5 Alt",   "Over/Under|Full Time|U 4.5")
    );

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    private Connection conn;
    private List<String> sqlColumns = new ArrayList<>();

    // ==================== VERİ MODELLERİ ====================
    static class MatchInfo {
        String id, home, away, date;
        String ftScore = "-";
        Map<String, String> odds = new HashMap<>();
    }

    static class MatchResult {
        Map<String, String> data = new HashMap<>();
    }

    // ==================== YAPILANDIRICI ====================
    public Bet365DailyAutoAnalyzer() {
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
            System.out.println("✅ SQL'de " + getSQLRowCount() + " kayıt var.\n");
        } catch (Exception e) {
            System.err.println("❌ Veritabanı hatası: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private long getSQLRowCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM bet365_matches")) {
            if (rs.next()) return rs.getLong(1);
        }
        return 0;
    }

    // ==================== SCRAPER ====================
    private List<MatchInfo> scrapeTodayMatches() {
        List<MatchInfo> matches = new ArrayList<>();
        System.out.println("🔍 Flashscore'dan bugünkü maçlar çekiliyor...\n");

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true));
             Page page = browser.newPage()) {

            page.navigate("https://www.flashscore.co.uk/football/");
            // Çerez popup'ını kapatmayı dene
            try { page.locator("#onetrust-accept-btn-handler")
                    .click(new Locator.ClickOptions().setTimeout(3000)); } catch (Exception ignored) {}
            page.waitForSelector("div[id^='g_1_'].event__match",
                    new Page.WaitForSelectorOptions().setTimeout(15000));

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
                } catch (Exception ignored) {}
            }

            // Her maç için detayları çek
            for (MatchInfo mi : matches) fetchOddsForMatch(mi);
            System.out.println("✅ " + matches.size() + " maçın oranları çekildi.\n");

        } catch (Exception e) {
            System.err.println("❌ Scraper hatası: " + e.getMessage());
            e.printStackTrace();
        }
        return matches;
    }

    private void fetchOddsForMatch(MatchInfo mi) {
        // Skor çekme
        String scoreUrl = "https://5.flashscore.ninja/5/x/feed/df_sui_1_" + mi.id;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(scoreUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("x-fsign", "SW9D1eZo")
                    .GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) parseScores(mi, resp.body());
        } catch (Exception ignored) {}

        // Oran çekme
        String oddsUrl = String.format(
                "https://global.ds.lsapp.eu/odds/pq_graphql?_hash=oce&eventId=%s&projectId=5&geoIpCode=AZ&geoIpSubdivisionCode=AZBA",
                mi.id);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(oddsUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().startsWith("{"))
                parseOdds(mi, resp.body());
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
            if ("1st Half".equals(half)) {
                htHome = ig;
                htAway = ih;
            } else if ("2nd Half".equals(half)) {
                try {
                    ftHome = String.valueOf(Integer.parseInt(htHome) + Integer.parseInt(ig));
                    ftAway = String.valueOf(Integer.parseInt(htAway) + Integer.parseInt(ih));
                } catch (NumberFormatException e) {
                    ftHome = ig;
                    ftAway = ih;
                }
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

        // Ev / Deplasman katılımcı ID'leri (Home / Away ayrımı için)
        String homePartId = null, awayPartId = null;
        for (int i = 0; i < oddsList.length(); i++) {
            JSONObject entry = oddsList.getJSONObject(i);
            if (entry.getInt("bookmakerId") != 16) continue;
            if ("HOME_DRAW_AWAY".equals(entry.getString("bettingType"))
                    && "FULL_TIME".equals(entry.getString("bettingScope"))) {
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

        // Tüm bahis tiplerini Map'e doldur (sadece bookmakerId=16)
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
                            String pid = item.isNull("eventParticipantId") ? null
                                    : item.getString("eventParticipantId");
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
                            mi.odds.put("Both teams|" + period + "|" + (yes ? "Yes" : "No"),
                                    getOddsValue(item));
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
                                mi.odds.put("Over/Under|" + period + "|"
                                                + ("OVER".equals(sel) ? "O " : "U ") + h,
                                        getOddsValue(item));
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
                            String pid = item.isNull("eventParticipantId") ? null
                                    : item.getString("eventParticipantId");
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
                                mi.odds.put("Correct score|" + period + "|" + score,
                                        getOddsValue(item));
                            }
                        }
                    }
                    break;

                case "HALF_FULL_TIME":
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject item = items.getJSONObject(j);
                        if (!item.isNull("winner")) {
                            mi.odds.put("HTFT|" + item.getString("winner"),
                                    getOddsValue(item));
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

    // ==================== ANALİZ MOTORU (2-5 ARALIĞI) ====================
    private void analyzeMatch(MatchInfo match) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("⚽ " + match.home + " vs " + match.away);
        System.out.println("═══════════════════════════════════════════════════════════════");

        System.out.println("📊 Maç Oranları:");
        for (ColumnDef col : DEFAULT_COLS) {
            String val = match.odds.getOrDefault(col.flashscoreKey, "-");
            System.out.printf("   %-15s = %s%n", col.displayName, val);
        }
        System.out.println();

        List<ColumnDef> activeFilters = new ArrayList<>(DEFAULT_COLS);
        List<MatchResult> results = querySQL(match, activeFilters);

        System.out.println("🔍 AŞAMA 1: Default " + DEFAULT_COLS.size() + " kolon ile filtreleme");
        System.out.println("   Aktif filtreler: " + getFilterNames(activeFilters));
        System.out.println("   Sonuç: " + results.size() + " maç\n");

        int stage = 2;
        for (ColumnDef extraCol : EXTRA_COLS) {
            if (results.size() >= 2 && results.size() <= 5) {
                break; // Hedef aralıkta
            }

            // Zaten eklenmiş mi?
            boolean alreadyActive = activeFilters.stream()
                    .anyMatch(c -> c.sqlColumn.equals(extraCol.sqlColumn));
            if (alreadyActive) continue;

            // Geçerli değer var mı?
            String oddsValue = match.odds.getOrDefault(extraCol.flashscoreKey, "");
            if (oddsValue.isEmpty() || "-".equals(oddsValue)) continue;

            // Geçici olarak ekleyip dene
            activeFilters.add(extraCol);
            List<MatchResult> newResults = querySQL(match, activeFilters);

            if (newResults.size() < 2) {
                // Sonuç çok azaldı, bu filtre uygun değil, geri al
                activeFilters.remove(activeFilters.size() - 1);
                System.out.println("🔍 AŞAMA " + stage + ": +" + extraCol.displayName
                        + " eklenseydi sonuç " + newResults.size() + " olacaktı → ATLANDI");
                stage++;
                continue;
            }

            // Kabul et, sonuçları güncelle
            results = newResults;

            System.out.println("🔍 AŞAMA " + stage + ": +" + extraCol.displayName + " eklendi");
            System.out.println("   Aktif filtreler: " + getFilterNames(activeFilters));
            System.out.println("   Sonuç: " + results.size() + " maç\n");

            stage++;
        }

        printResults(results, activeFilters, match);
        System.out.println();
    }

    private String getFilterNames(List<ColumnDef> filters) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filters.size(); i++) {
            sb.append(filters.get(i).displayName);
            if (i < filters.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private List<MatchResult> querySQL(MatchInfo match, List<ColumnDef> filters) {
        List<MatchResult> results = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (ColumnDef col : filters) {
            String oddsValue = match.odds.getOrDefault(col.flashscoreKey, "");
            if (oddsValue.isEmpty() || "-".equals(oddsValue)) continue;
            if (!sqlColumns.contains(col.sqlColumn)) continue;

            conditions.add(col.sqlColumn + " = ?");
            try {
                params.add(Double.parseDouble(oddsValue.replace(',', '.')));
            } catch (NumberFormatException e) {
                params.add(oddsValue);
            }
        }

        if (conditions.isEmpty()) return results;

        String whereClause = " WHERE " + String.join(" AND ", conditions);
        String sql = "SELECT * FROM bet365_matches " + whereClause
                + " ORDER BY date_time DESC LIMIT 5000";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                MatchResult mr = new MatchResult();
                for (int i = 1; i <= colCount; i++) {
                    mr.data.put(meta.getColumnName(i), rs.getString(i));
                }
                results.add(mr);
            }
        } catch (SQLException ex) {
            System.err.println("SQL Hatası: " + ex.getMessage());
        }

        return results;
    }

    private void printResults(List<MatchResult> results, List<ColumnDef> filters, MatchInfo match) {
        if (results.isEmpty()) {
            System.out.println("⚠️  SONUÇ: Eşleşen maç bulunamadı!");
            return;
        }

        if (results.size() > 5) {
            System.out.println("⚠️  SONUÇ: " + results.size()
                    + " maç bulundu (hala çok fazla, daha fazla filtre gerekli)");
        } else {
            System.out.println("✅ SONUÇ: " + results.size() + " maç bulundu (hedef aralıkta!)");
        }

        System.out.println();
        System.out.println("┌──────┬─────────────────────┬─────────────────────┬──────────┬──────────┬──────────┐");
        System.out.println("│ #    │ Tarih               │ Ev Sahibi           │ Deplasman│ MS       │ İY       │");
        System.out.println("├──────┼─────────────────────┼─────────────────────┼──────────┼──────────┼──────────┤");

        for (int i = 0; i < Math.min(results.size(), 20); i++) {
            MatchResult r = results.get(i);
            String date = truncate(r.data.getOrDefault("date_time", "-"), 19);
            String home = truncate(r.data.getOrDefault("home_team", "-"), 19);
            String away = truncate(r.data.getOrDefault("away_team", "-"), 8);
            String ft = truncate(r.data.getOrDefault("ft_ms", "-"), 8);
            String ht = truncate(r.data.getOrDefault("ht_iy", "-"), 8);
            System.out.printf("│ %-4d │ %-19s │ %-19s │ %-8s │ %-8s │ %-8s │%n",
                    (i + 1), date, home, away, ft, ht);
        }

        if (results.size() > 20) {
            System.out.println("│ ...  │ (" + (results.size() - 20) + " maç daha var)                                      │");
        }
        System.out.println("└──────┴─────────────────────┴─────────────────────┴──────────┴──────────┴──────────┘");

        System.out.println("\n📋 Aktif Filtre Oranları (" + match.home + " vs " + match.away + "):");
        for (ColumnDef f : filters) {
            String val = match.odds.getOrDefault(f.flashscoreKey, "-");
            System.out.println("   • " + f.displayName + " = " + val);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "-";
        if (s.length() > maxLen) return s.substring(0, maxLen - 1) + "…";
        return s;
    }

    // ==================== MAIN ÇALIŞTIRICI ====================
    public void run() {
        List<MatchInfo> todayMatches = scrapeTodayMatches();

        if (todayMatches.isEmpty()) {
            System.out.println("❌ Bugünkü maç bulunamadı!");
            return;
        }

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("🤖 OTOMATİK ANALİZ BAŞLIYOR: " + todayMatches.size() + " maç");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        for (MatchInfo match : todayMatches) {
            analyzeMatch(match);
        }

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("✅ ANALİZ TAMAMLANDI");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    static void main(String[] args) {
        Bet365DailyAutoAnalyzer analyzer = new Bet365DailyAutoAnalyzer();
        analyzer.run();
    }
}