package analyzer.extras;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

public class Bet365LiveFilterUI extends JFrame {

    static class ColumnDef {
        String group, sub, key;
        ColumnDef(String group, String sub, String key) { this.group = group; this.sub = sub; this.key = key; }
    }

    private static final List<ColumnDef> ALL_COLUMNS = List.of(
            new ColumnDef("MAC SONU / FULL TIME", "FT_1_A", "1x2|Full Time|Home"),
            new ColumnDef("MAC SONU / FULL TIME", "FT_X_A", "1x2|Full Time|Draw"),
            new ColumnDef("MAC SONU / FULL TIME", "FT_2_A", "1x2|Full Time|Away"),
            new ColumnDef("ILK YARI / 1ST HALF", "1ST_1_A", "1x2|1st Half|Home"),
            new ColumnDef("ILK YARI / 1ST HALF", "1ST_X_A", "1x2|1st Half|Draw"),
            new ColumnDef("ILK YARI / 1ST HALF", "1ST_2_A", "1x2|1st Half|Away"),
            new ColumnDef("IKINCI YARI / 2ND HALF", "2ND_1_A", "1x2|2nd Half|Home"),
            new ColumnDef("IKINCI YARI / 2ND HALF", "2ND_X_A", "1x2|2nd Half|Draw"),
            new ColumnDef("IKINCI YARI / 2ND HALF", "2ND_2_A", "1x2|2nd Half|Away"),
            new ColumnDef("MAC SONU KG", "BTS_FT_YES_A", "Both teams|Full Time|Yes"),
            new ColumnDef("MAC SONU KG", "BTS_FT_NO_A", "Both teams|Full Time|No"),
            new ColumnDef("ILK YARI KG", "BTS_1ST_YES_A", "Both teams|1st Half|Yes"),
            new ColumnDef("ILK YARI KG", "BTS_1ST_NO_A", "Both teams|1st Half|No"),
            new ColumnDef("IKINCI YARI KG", "BTS_2ND_YES_A", "Both teams|2nd Half|Yes"),
            new ColumnDef("IKINCI YARI KG", "BTS_2ND_NO_A", "Both teams|2nd Half|No"),
            new ColumnDef("ÇİFT ŞANS FT", "DBC_FT_1X_A", "Double chance|Full Time|1X"),
            new ColumnDef("ÇİFT ŞANS FT", "DBC_FT_12_A", "Double chance|Full Time|12"),
            new ColumnDef("ÇİFT ŞANS FT", "DBC_FT_X2_A", "Double chance|Full Time|X2"),
            new ColumnDef("ÇİFT ŞANS IY", "DBC_1ST_1X_A", "Double chance|1st Half|1X"),
            new ColumnDef("ÇİFT ŞANS IY", "DBC_1ST_12_A", "Double chance|1st Half|12"),
            new ColumnDef("ÇİFT ŞANS IY", "DBC_1ST_X2_A", "Double chance|1st Half|X2"),
            new ColumnDef("A/U 0,5", "FT_0,5_OVER_A", "Over/Under|Full Time|O 0.5"),
            new ColumnDef("A/U 0,5", "FT_0,5_UNDER_A", "Over/Under|Full Time|U 0.5"),
            new ColumnDef("A/U 1,5", "FT_1,5_OVER_A", "Over/Under|Full Time|O 1.5"),
            new ColumnDef("A/U 1,5", "FT_1,5_UNDER_A", "Over/Under|Full Time|U 1.5"),
            new ColumnDef("A/U 2,5", "FT_2,5_OVER_A", "Over/Under|Full Time|O 2.5"),
            new ColumnDef("A/U 2,5", "FT_2,5_UNDER_A", "Over/Under|Full Time|U 2.5"),
            new ColumnDef("A/U 3,5", "FT_3,5_OVER_A", "Over/Under|Full Time|O 3.5"),
            new ColumnDef("A/U 3,5", "FT_3,5_UNDER_A", "Over/Under|Full Time|U 3.5"),
            new ColumnDef("A/U 4,5", "FT_4,5_OVER_A", "Over/Under|Full Time|O 4.5"),
            new ColumnDef("A/U 4,5", "FT_4,5_UNDER_A", "Over/Under|Full Time|U 4.5"),
            new ColumnDef("A/U 5,5", "FT_5,5_OVER_A", "Over/Under|Full Time|O 5.5"),
            new ColumnDef("A/U 5,5", "FT_5,5_UNDER_A", "Over/Under|Full Time|U 5.5"),
            new ColumnDef("IY A/U 0,5", "1ST_0,5_OVER_A", "Over/Under|1st Half|O 0.5"),
            new ColumnDef("IY A/U 0,5", "1ST_0,5_UNDER_A", "Over/Under|1st Half|U 0.5"),
            new ColumnDef("IY A/U 1,5", "1ST_1,5_OVER_A", "Over/Under|1st Half|O 1.5"),
            new ColumnDef("IY A/U 1,5", "1ST_1,5_UNDER_A", "Over/Under|1st Half|U 1.5"),
            new ColumnDef("IY A/U 2,5", "1ST_2,5_OVER_A", "Over/Under|1st Half|O 2.5"),
            new ColumnDef("IY A/U 2,5", "1ST_2,5_UNDER_A", "Over/Under|1st Half|U 2.5"),
            new ColumnDef("2Y A/U 0,5", "2ND_0,5_OVER_A", "Over/Under|2nd Half|O 0.5"),
            new ColumnDef("2Y A/U 0,5", "2ND_0,5_UNDER_A", "Over/Under|2nd Half|U 0.5"),
            new ColumnDef("2Y A/U 1,5", "2ND_1,5_OVER_A", "Over/Under|2nd Half|O 1.5"),
            new ColumnDef("2Y A/U 1,5", "2ND_1,5_UNDER_A", "Over/Under|2nd Half|U 1.5"),
            new ColumnDef("2Y A/U 2,5", "2ND_2,5_OVER_A", "Over/Under|2nd Half|O 2.5"),
            new ColumnDef("2Y A/U 2,5", "2ND_2,5_UNDER_A", "Over/Under|2nd Half|U 2.5"),
            new ColumnDef("HT/FT", "1/1_A", "HTFT|1/1"), new ColumnDef("HT/FT", "1/X_A", "HTFT|1/X"),
            new ColumnDef("HT/FT", "1/2_A", "HTFT|1/2"), new ColumnDef("HT/FT", "X/1_A", "HTFT|X/1"),
            new ColumnDef("HT/FT", "X/X_A", "HTFT|X/X"), new ColumnDef("HT/FT", "X/2_A", "HTFT|X/2"),
            new ColumnDef("HT/FT", "2/1_A", "HTFT|2/1"), new ColumnDef("HT/FT", "2/X_A", "HTFT|2/X"),
            new ColumnDef("HT/FT", "2/2_A", "HTFT|2/2"),
            new ColumnDef("1ST SCORE", "1ST_1:0_A","Correct score|1st Half|1:0"),
            new ColumnDef("1ST SCORE", "1ST_2:0_A","Correct score|1st Half|2:0"),
            new ColumnDef("1ST SCORE", "1ST_2:1_A","Correct score|1st Half|2:1"),
            new ColumnDef("1ST SCORE", "1ST_3:0_A","Correct score|1st Half|3:0"),
            new ColumnDef("1ST SCORE", "1ST_3:1_A","Correct score|1st Half|3:1"),
            new ColumnDef("1ST SCORE", "1ST_3:2_A","Correct score|1st Half|3:2"),
            new ColumnDef("1ST SCORE", "1ST_0:0_A","Correct score|1st Half|0:0"),
            new ColumnDef("1ST SCORE", "1ST_1:1_A","Correct score|1st Half|1:1"),
            new ColumnDef("1ST SCORE", "1ST_2:2_A","Correct score|1st Half|2:2"),
            new ColumnDef("1ST SCORE", "1ST_0:1_A","Correct score|1st Half|0:1"),
            new ColumnDef("1ST SCORE", "1ST_0:2_A","Correct score|1st Half|0:2"),
            new ColumnDef("1ST SCORE", "1ST_1:2_A","Correct score|1st Half|1:2"),
            new ColumnDef("1ST SCORE", "1ST_0:3_A","Correct score|1st Half|0:3"),
            new ColumnDef("1ST SCORE", "1ST_1:3_A","Correct score|1st Half|1:3"),
            new ColumnDef("1ST SCORE", "1ST_2:3_A","Correct score|1st Half|2:3"),
            new ColumnDef("FT SCORE", "FT_1:0_A","Correct score|Full Time|1:0"),
            new ColumnDef("FT SCORE", "FT_2:0_A","Correct score|Full Time|2:0"),
            new ColumnDef("FT SCORE", "FT_2:1_A","Correct score|Full Time|2:1"),
            new ColumnDef("FT SCORE", "FT_3:0_A","Correct score|Full Time|3:0"),
            new ColumnDef("FT SCORE", "FT_3:1_A","Correct score|Full Time|3:1"),
            new ColumnDef("FT SCORE", "FT_3:2_A","Correct score|Full Time|3:2"),
            new ColumnDef("FT SCORE", "FT_4:0_A","Correct score|Full Time|4:0"),
            new ColumnDef("FT SCORE", "FT_4:1_A","Correct score|Full Time|4:1"),
            new ColumnDef("FT SCORE", "FT_4:2_A","Correct score|Full Time|4:2"),
            new ColumnDef("FT SCORE", "FT_4:3_A","Correct score|Full Time|4:3"),
            new ColumnDef("FT SCORE", "FT_5:0_A","Correct score|Full Time|5:0"),
            new ColumnDef("FT SCORE", "FT_5:1_A","Correct score|Full Time|5:1"),
            new ColumnDef("FT SCORE", "FT_5:2_A","Correct score|Full Time|5:2"),
            new ColumnDef("FT SCORE", "FT_0:0_A","Correct score|Full Time|0:0"),
            new ColumnDef("FT SCORE", "FT_1:1_A","Correct score|Full Time|1:1"),
            new ColumnDef("FT SCORE", "FT_2:2_A","Correct score|Full Time|2:2"),
            new ColumnDef("FT SCORE", "FT_3:3_A","Correct score|Full Time|3:3"),
            new ColumnDef("FT SCORE", "FT_4:4_A","Correct score|Full Time|4:4"),
            new ColumnDef("FT SCORE", "FT_0:1_A","Correct score|Full Time|0:1"),
            new ColumnDef("FT SCORE", "FT_0:2_A","Correct score|Full Time|0:2"),
            new ColumnDef("FT SCORE", "FT_1:2_A","Correct score|Full Time|1:2"),
            new ColumnDef("FT SCORE", "FT_0:3_A","Correct score|Full Time|0:3"),
            new ColumnDef("FT SCORE", "FT_1:3_A","Correct score|Full Time|1:3"),
            new ColumnDef("FT SCORE", "FT_2:3_A","Correct score|Full Time|2:3"),
            new ColumnDef("FT SCORE", "FT_0:4_A","Correct score|Full Time|0:4"),
            new ColumnDef("FT SCORE", "FT_1:4_A","Correct score|Full Time|1:4"),
            new ColumnDef("FT SCORE", "FT_2:4_A","Correct score|Full Time|2:4"),
            new ColumnDef("FT SCORE", "FT_3:4_A","Correct score|Full Time|3:4"),
            new ColumnDef("FT SCORE", "FT_0:5_A","Correct score|Full Time|0:5"),
            new ColumnDef("FT SCORE", "FT_1:5_A","Correct score|Full Time|1:5"),
            new ColumnDef("FT SCORE", "FT_2:5_A","Correct score|Full Time|2:5")
    );

    private JComboBox<String> matchDropdown;
    private JPanel filterPanel;
    private final Map<String, JCheckBox> checkBoxMap = new HashMap<>();
    private final Map<String, JTextField> valueFieldMap = new HashMap<>();
    private final List<MatchInfo> allMatches = new ArrayList<>();
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    static class MatchInfo {
        String id, home, away, date;
        String ftScore = "-";
        Map<String, String> odds = new HashMap<>();
    }

    public Bet365LiveFilterUI() {
        setTitle("Bet365 Canlı Scraper - Maç Seç, Oranları Getir, Checkbox ile Filtrele");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Üst panel: Dropdown + buton
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel("Bugünkü Maçlar:"));
        matchDropdown = new JComboBox<>();
        matchDropdown.setPreferredSize(new Dimension(400, 30));
        topPanel.add(matchDropdown);
        JButton scrapeBtn = new JButton("🔄 Flashscore'dan Bugünü Getir");
        scrapeBtn.addActionListener(e -> scrapeTodayMatches());
        topPanel.add(scrapeBtn);
        add(topPanel, BorderLayout.NORTH);

        // Orta panel: Tüm kolonlar için checkbox + değer
        filterPanel = new JPanel(new GridLayout(0, 4, 5, 5));
        JScrollPane filterScroll = new JScrollPane(filterPanel);
        filterScroll.setBorder(BorderFactory.createTitledBorder("Tüm Oran Kolonları (Checkbox işaretle, değer otomatik gelir)"));
        filterScroll.setPreferredSize(new Dimension(1200, 350));
        add(filterScroll, BorderLayout.CENTER);

        // Kolonları UI'a ekle
        for (ColumnDef col : ALL_COLUMNS) {
            JCheckBox chk = new JCheckBox();
            checkBoxMap.put(col.sub, chk);
            JLabel label = new JLabel(col.sub);
            label.setToolTipText(col.key);
            JTextField valField = new JTextField(10);
            valueFieldMap.put(col.sub, valField);
            filterPanel.add(chk);
            filterPanel.add(label);
            filterPanel.add(new JLabel("→"));
            filterPanel.add(valField);
        }

        // Alt panel: Filtrele butonu + sonuç tablosu
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JButton filterBtn = new JButton("🔍 Filtrele (Sadece işaretli kolonları dikkate al)");
        filterBtn.addActionListener(e -> applyFilters());
        bottomPanel.add(filterBtn, BorderLayout.NORTH);

        Vector<String> colNames = new Vector<>();
        colNames.add("Tarih"); colNames.add("Ev Sahibi"); colNames.add("Deplasman"); colNames.add("Skor");
        for (ColumnDef col : ALL_COLUMNS) colNames.add(col.sub);
        tableModel = new DefaultTableModel(colNames, 0);
        resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setPreferredSize(new Dimension(1200, 400));
        bottomPanel.add(tableScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Dropdown seçiminde oranları doldur (checkbox işaretleme)
        matchDropdown.addActionListener(e -> {
            int idx = matchDropdown.getSelectedIndex();
            if (idx > 0 && idx - 1 < allMatches.size()) {
                fillOddsToInputs(allMatches.get(idx - 1));
            }
        });

        setVisible(true);
    }

    private void scrapeTodayMatches() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        allMatches.clear();
        matchDropdown.removeAllItems();
        matchDropdown.addItem("-- Maç Seçin --");

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
             Page page = browser.newPage()) {

            page.navigate("https://www.flashscore.co.uk/football/");
            try { page.locator("#onetrust-accept-btn-handler").click(new Locator.ClickOptions().setTimeout(3000)); } catch (Exception ignored) {}
            page.waitForSelector("div[id^='g_1_'].event__match", new Page.WaitForSelectorOptions().setTimeout(10000));

            Locator rows = page.locator("div[id^='g_1_'].event__match");
            int count = rows.count();

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
                    allMatches.add(mi);
                    matchDropdown.addItem(home + " - " + away);
                } catch (Exception ignored) {}
            }

            for (MatchInfo mi : allMatches) {
                fetchOddsForMatch(mi);
            }
            JOptionPane.showMessageDialog(this, allMatches.size() + " maç çekildi.");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Hata: " + e.getMessage());
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
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
        String htHome = "-", htAway = "-";
        String ftHome = "-", ftAway = "-";
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

    private void fillOddsToInputs(MatchInfo selected) {
        for (JTextField tf : valueFieldMap.values()) tf.setText("");
        for (Map.Entry<String, JTextField> entry : valueFieldMap.entrySet()) {
            String colSub = entry.getKey();
            String dataKey = null;
            for (ColumnDef col : ALL_COLUMNS) {
                if (col.sub.equals(colSub)) { dataKey = col.key; break; }
            }
            if (dataKey != null && selected.odds.containsKey(dataKey)) {
                entry.getValue().setText(selected.odds.get(dataKey));
            }
        }
        for (JCheckBox chk : checkBoxMap.values()) chk.setSelected(false);
    }

    private void applyFilters() {
        List<MatchInfo> filtered = new ArrayList<>(allMatches);
        for (Map.Entry<String, JCheckBox> entry : checkBoxMap.entrySet()) {
            if (entry.getValue().isSelected()) {
                String colSub = entry.getKey();
                String filterValue = valueFieldMap.get(colSub).getText().trim();
                if (filterValue.isEmpty()) continue;
                String dataKey = null;
                for (ColumnDef col : ALL_COLUMNS) {
                    if (col.sub.equals(colSub)) { dataKey = col.key; break; }
                }
                if (dataKey == null) continue;
                final String fKey = dataKey;
                final String fVal = filterValue;
                filtered = filtered.stream().filter(m -> {
                    String oddsVal = m.odds.getOrDefault(fKey, "-");
                    if ("-".equals(oddsVal)) return false;
                    try {
                        double d1 = Double.parseDouble(oddsVal.replace(',', '.'));
                        double d2 = Double.parseDouble(fVal.replace(',', '.'));
                        return Math.abs(d1 - d2) < 0.0001;
                    } catch (NumberFormatException e) {
                        return oddsVal.equalsIgnoreCase(fVal);
                    }
                }).collect(Collectors.toList());
            }
        }
        tableModel.setRowCount(0);
        for (MatchInfo m : filtered) {
            Vector<Object> row = new Vector<>();
            row.add(m.date); row.add(m.home); row.add(m.away); row.add(m.ftScore);
            for (ColumnDef col : ALL_COLUMNS) {
                row.add(m.odds.getOrDefault(col.key, "-"));
            }
            tableModel.addRow(row);
        }
        JOptionPane.showMessageDialog(this, filtered.size() + " maç filtrelendi.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Bet365LiveFilterUI::new);
    }
}