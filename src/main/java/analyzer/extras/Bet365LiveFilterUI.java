package analyzer.extras;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
import java.util.Vector;

public class Bet365LiveFilterUI extends JFrame {

    // ==================== KOLON TANIMLARI ====================
    static class ColumnDef {
        String sqlColumn;      // SQL'deki kolon adı: ft_1_a
        String displayName;    // Görünen ad: "MS 1"
        String group;          // Grup: "MS", "İY", "KG"
        Color groupColor;
        String flashscoreKey;  // Flashscore API'den gelen key: "1x2|Full Time|Home"

        ColumnDef(String sqlColumn, String displayName, String group, Color groupColor, String flashscoreKey) {
            this.sqlColumn = sqlColumn;
            this.displayName = displayName;
            this.group = group;
            this.groupColor = groupColor;
            this.flashscoreKey = flashscoreKey;
        }
    }

    // Renkler
    private static final Color C_MS    = new Color(219, 234, 254); // mavi
    private static final Color C_IY    = new Color(220, 252, 231); // yeşil
    private static final Color C_2Y    = new Color(254, 249, 195); // sarı
    private static final Color C_KG    = new Color(255, 228, 230); // pembe
    private static final Color C_CS    = new Color(243, 232, 255); // mor
    private static final Color C_AU    = new Color(255, 237, 213); // turuncu
    private static final Color C_AU1   = new Color(207, 250, 254); // camgöbeği
    private static final Color C_AU2   = new Color(236, 252, 203); // lime
    private static final Color C_HTFT  = new Color(254, 215, 170); // koyu turuncu
    private static final Color C_SC1   = new Color(245, 243, 255); // lavanta
    private static final Color C_SCFT  = new Color(224, 242, 254); // gök mavisi

    private static final List<ColumnDef> ALL_COLUMNS = List.of(
            // MAÇ SONU
            new ColumnDef("ft_1_a", "MS 1", "MAÇ SONU", C_MS, "1x2|Full Time|Home"),
            new ColumnDef("ft_x_a", "MS X", "MAÇ SONU", C_MS, "1x2|Full Time|Draw"),
            new ColumnDef("ft_2_a", "MS 2", "MAÇ SONU", C_MS, "1x2|Full Time|Away"),
            // İLK YARI
            new ColumnDef("iy_1_a", "İY 1", "İLK YARI", C_IY, "1x2|1st Half|Home"),
            new ColumnDef("iy_x_a", "İY X", "İLK YARI", C_IY, "1x2|1st Half|Draw"),
            new ColumnDef("iy_2_a", "İY 2", "İLK YARI", C_IY, "1x2|1st Half|Away"),
            // İKİNCİ YARI
            new ColumnDef("iiy_1_a", "2Y 1", "İKİNCİ YARI", C_2Y, "1x2|2nd Half|Home"),
            new ColumnDef("iiy_x_a", "2Y X", "İKİNCİ YARI", C_2Y, "1x2|2nd Half|Draw"),
            new ColumnDef("iiy_2_a", "2Y 2", "İKİNCİ YARI", C_2Y, "1x2|2nd Half|Away"),
            // KG
            new ColumnDef("bts_ft_yes_a", "KG Evet", "KG", C_KG, "Both teams|Full Time|Yes"),
            new ColumnDef("bts_ft_no_a", "KG Hayır", "KG", C_KG, "Both teams|Full Time|No"),
            new ColumnDef("bts_iy_yes_a", "İY KG Evet", "İY KG", C_KG, "Both teams|1st Half|Yes"),
            new ColumnDef("bts_iy_no_a", "İY KG Hayır", "İY KG", C_KG, "Both teams|1st Half|No"),
            new ColumnDef("bts_iiy_yes_a", "2Y KG Evet", "2Y KG", C_KG, "Both teams|2nd Half|Yes"),
            new ColumnDef("bts_iiy_no_a", "2Y KG Hayır", "2Y KG", C_KG, "Both teams|2nd Half|No"),
            // ÇİFT ŞANS
            new ColumnDef("dc_ft_1x_a", "ÇŞ 1X", "ÇİFT ŞANS", C_CS, "Double chance|Full Time|1X"),
            new ColumnDef("dc_ft_12_a", "ÇŞ 12", "ÇİFT ŞANS", C_CS, "Double chance|Full Time|12"),
            new ColumnDef("dc_ft_x2_a", "ÇŞ X2", "ÇİFT ŞANS", C_CS, "Double chance|Full Time|X2"),
            new ColumnDef("dc_iy_1x_a", "İY ÇŞ 1X", "İY ÇŞ", C_CS, "Double chance|1st Half|1X"),
            new ColumnDef("dc_iy_12_a", "İY ÇŞ 12", "İY ÇŞ", C_CS, "Double chance|1st Half|12"),
            new ColumnDef("dc_iy_x2_a", "İY ÇŞ X2", "İY ÇŞ", C_CS, "Double chance|1st Half|X2"),
            // A/U 0.5
            new ColumnDef("au_0_5_over_a", "A/U 0.5 Üst", "A/U 0.5", C_AU, "Over/Under|Full Time|O 0.5"),
            new ColumnDef("au_0_5_under_a", "A/U 0.5 Alt", "A/U 0.5", C_AU, "Over/Under|Full Time|U 0.5"),
            // A/U 1.5
            new ColumnDef("au_1_5_over_a", "A/U 1.5 Üst", "A/U 1.5", C_AU, "Over/Under|Full Time|O 1.5"),
            new ColumnDef("au_1_5_under_a", "A/U 1.5 Alt", "A/U 1.5", C_AU, "Over/Under|Full Time|U 1.5"),
            // A/U 2.5
            new ColumnDef("au_2_5_over_a", "A/U 2.5 Üst", "A/U 2.5", C_AU, "Over/Under|Full Time|O 2.5"),
            new ColumnDef("au_2_5_under_a", "A/U 2.5 Alt", "A/U 2.5", C_AU, "Over/Under|Full Time|U 2.5"),
            // A/U 3.5
            new ColumnDef("au_3_5_over_a", "A/U 3.5 Üst", "A/U 3.5", C_AU, "Over/Under|Full Time|O 3.5"),
            new ColumnDef("au_3_5_under_a", "A/U 3.5 Alt", "A/U 3.5", C_AU, "Over/Under|Full Time|U 3.5"),
            // A/U 4.5
            new ColumnDef("au_4_5_over_a", "A/U 4.5 Üst", "A/U 4.5", C_AU, "Over/Under|Full Time|O 4.5"),
            new ColumnDef("au_4_5_under_a", "A/U 4.5 Alt", "A/U 4.5", C_AU, "Over/Under|Full Time|U 4.5"),
            // A/U 5.5
            new ColumnDef("au_5_5_over_a", "A/U 5.5 Üst", "A/U 5.5", C_AU, "Over/Under|Full Time|O 5.5"),
            new ColumnDef("au_5_5_under_a", "A/U 5.5 Alt", "A/U 5.5", C_AU, "Over/Under|Full Time|U 5.5"),
            // İY A/U
            new ColumnDef("iy_au_0_5_over_a", "İY A/U 0.5 Üst", "İY A/U 0.5", C_AU1, "Over/Under|1st Half|O 0.5"),
            new ColumnDef("iy_au_0_5_under_a", "İY A/U 0.5 Alt", "İY A/U 0.5", C_AU1, "Over/Under|1st Half|U 0.5"),
            new ColumnDef("iy_au_1_5_over_a", "İY A/U 1.5 Üst", "İY A/U 1.5", C_AU1, "Over/Under|1st Half|O 1.5"),
            new ColumnDef("iy_au_1_5_under_a", "İY A/U 1.5 Alt", "İY A/U 1.5", C_AU1, "Over/Under|1st Half|U 1.5"),
            new ColumnDef("iy_au_2_5_over_a", "İY A/U 2.5 Üst", "İY A/U 2.5", C_AU1, "Over/Under|1st Half|O 2.5"),
            new ColumnDef("iy_au_2_5_under_a", "İY A/U 2.5 Alt", "İY A/U 2.5", C_AU1, "Over/Under|1st Half|U 2.5"),
            // 2Y A/U
            new ColumnDef("iiy_au_0_5_over_a", "2Y A/U 0.5 Üst", "2Y A/U 0.5", C_AU2, "Over/Under|2nd Half|O 0.5"),
            new ColumnDef("iiy_au_0_5_under_a", "2Y A/U 0.5 Alt", "2Y A/U 0.5", C_AU2, "Over/Under|2nd Half|U 0.5"),
            new ColumnDef("iiy_au_1_5_over_a", "2Y A/U 1.5 Üst", "2Y A/U 1.5", C_AU2, "Over/Under|2nd Half|O 1.5"),
            new ColumnDef("iiy_au_1_5_under_a", "2Y A/U 1.5 Alt", "2Y A/U 1.5", C_AU2, "Over/Under|2nd Half|U 1.5"),
            new ColumnDef("iiy_au_2_5_over_a", "2Y A/U 2.5 Üst", "2Y A/U 2.5", C_AU2, "Over/Under|2nd Half|O 2.5"),
            new ColumnDef("iiy_au_2_5_under_a", "2Y A/U 2.5 Alt", "2Y A/U 2.5", C_AU2, "Over/Under|2nd Half|U 2.5"),
            // HT/FT
            new ColumnDef("htft_1_1_a", "HT/FT 1/1", "HT/FT", C_HTFT, "HTFT|1/1"),
            new ColumnDef("htft_1_x_a", "HT/FT 1/X", "HT/FT", C_HTFT, "HTFT|1/X"),
            new ColumnDef("htft_1_2_a", "HT/FT 1/2", "HT/FT", C_HTFT, "HTFT|1/2"),
            new ColumnDef("htft_x_1_a", "HT/FT X/1", "HT/FT", C_HTFT, "HTFT|X/1"),
            new ColumnDef("htft_x_x_a", "HT/FT X/X", "HT/FT", C_HTFT, "HTFT|X/X"),
            new ColumnDef("htft_x_2_a", "HT/FT X/2", "HT/FT", C_HTFT, "HTFT|X/2"),
            new ColumnDef("htft_2_1_a", "HT/FT 2/1", "HT/FT", C_HTFT, "HTFT|2/1"),
            new ColumnDef("htft_2_x_a", "HT/FT 2/X", "HT/FT", C_HTFT, "HTFT|2/X"),
            new ColumnDef("htft_2_2_a", "HT/FT 2/2", "HT/FT", C_HTFT, "HTFT|2/2"),
            // İY SKOR
            new ColumnDef("iy_skor_1_0_a", "İY Skor 1:0", "İY SKOR", C_SC1, "Correct score|1st Half|1:0"),
            new ColumnDef("iy_skor_2_0_a", "İY Skor 2:0", "İY SKOR", C_SC1, "Correct score|1st Half|2:0"),
            new ColumnDef("iy_skor_2_1_a", "İY Skor 2:1", "İY SKOR", C_SC1, "Correct score|1st Half|2:1"),
            new ColumnDef("iy_skor_3_0_a", "İY Skor 3:0", "İY SKOR", C_SC1, "Correct score|1st Half|3:0"),
            new ColumnDef("iy_skor_3_1_a", "İY Skor 3:1", "İY SKOR", C_SC1, "Correct score|1st Half|3:1"),
            new ColumnDef("iy_skor_3_2_a", "İY Skor 3:2", "İY SKOR", C_SC1, "Correct score|1st Half|3:2"),
            new ColumnDef("iy_skor_0_0_a", "İY Skor 0:0", "İY SKOR", C_SC1, "Correct score|1st Half|0:0"),
            new ColumnDef("iy_skor_1_1_a", "İY Skor 1:1", "İY SKOR", C_SC1, "Correct score|1st Half|1:1"),
            new ColumnDef("iy_skor_2_2_a", "İY Skor 2:2", "İY SKOR", C_SC1, "Correct score|1st Half|2:2"),
            new ColumnDef("iy_skor_0_1_a", "İY Skor 0:1", "İY SKOR", C_SC1, "Correct score|1st Half|0:1"),
            new ColumnDef("iy_skor_0_2_a", "İY Skor 0:2", "İY SKOR", C_SC1, "Correct score|1st Half|0:2"),
            new ColumnDef("iy_skor_1_2_a", "İY Skor 1:2", "İY SKOR", C_SC1, "Correct score|1st Half|1:2"),
            new ColumnDef("iy_skor_0_3_a", "İY Skor 0:3", "İY SKOR", C_SC1, "Correct score|1st Half|0:3"),
            new ColumnDef("iy_skor_1_3_a", "İY Skor 1:3", "İY SKOR", C_SC1, "Correct score|1st Half|1:3"),
            new ColumnDef("iy_skor_2_3_a", "İY Skor 2:3", "İY SKOR", C_SC1, "Correct score|1st Half|2:3"),
            // MS SKOR
            new ColumnDef("ft_skor_1_0_a", "MS Skor 1:0", "MS SKOR", C_SCFT, "Correct score|Full Time|1:0"),
            new ColumnDef("ft_skor_2_0_a", "MS Skor 2:0", "MS SKOR", C_SCFT, "Correct score|Full Time|2:0"),
            new ColumnDef("ft_skor_2_1_a", "MS Skor 2:1", "MS SKOR", C_SCFT, "Correct score|Full Time|2:1"),
            new ColumnDef("ft_skor_3_0_a", "MS Skor 3:0", "MS SKOR", C_SCFT, "Correct score|Full Time|3:0"),
            new ColumnDef("ft_skor_3_1_a", "MS Skor 3:1", "MS SKOR", C_SCFT, "Correct score|Full Time|3:1"),
            new ColumnDef("ft_skor_3_2_a", "MS Skor 3:2", "MS SKOR", C_SCFT, "Correct score|Full Time|3:2"),
            new ColumnDef("ft_skor_4_0_a", "MS Skor 4:0", "MS SKOR", C_SCFT, "Correct score|Full Time|4:0"),
            new ColumnDef("ft_skor_4_1_a", "MS Skor 4:1", "MS SKOR", C_SCFT, "Correct score|Full Time|4:1"),
            new ColumnDef("ft_skor_4_2_a", "MS Skor 4:2", "MS SKOR", C_SCFT, "Correct score|Full Time|4:2"),
            new ColumnDef("ft_skor_4_3_a", "MS Skor 4:3", "MS SKOR", C_SCFT, "Correct score|Full Time|4:3"),
            new ColumnDef("ft_skor_5_0_a", "MS Skor 5:0", "MS SKOR", C_SCFT, "Correct score|Full Time|5:0"),
            new ColumnDef("ft_skor_5_1_a", "MS Skor 5:1", "MS SKOR", C_SCFT, "Correct score|Full Time|5:1"),
            new ColumnDef("ft_skor_5_2_a", "MS Skor 5:2", "MS SKOR", C_SCFT, "Correct score|Full Time|5:2"),
            new ColumnDef("ft_skor_0_0_a", "MS Skor 0:0", "MS SKOR", C_SCFT, "Correct score|Full Time|0:0"),
            new ColumnDef("ft_skor_1_1_a", "MS Skor 1:1", "MS SKOR", C_SCFT, "Correct score|Full Time|1:1"),
            new ColumnDef("ft_skor_2_2_a", "MS Skor 2:2", "MS SKOR", C_SCFT, "Correct score|Full Time|2:2"),
            new ColumnDef("ft_skor_3_3_a", "MS Skor 3:3", "MS SKOR", C_SCFT, "Correct score|Full Time|3:3"),
            new ColumnDef("ft_skor_4_4_a", "MS Skor 4:4", "MS SKOR", C_SCFT, "Correct score|Full Time|4:4"),
            new ColumnDef("ft_skor_0_1_a", "MS Skor 0:1", "MS SKOR", C_SCFT, "Correct score|Full Time|0:1"),
            new ColumnDef("ft_skor_0_2_a", "MS Skor 0:2", "MS SKOR", C_SCFT, "Correct score|Full Time|0:2"),
            new ColumnDef("ft_skor_1_2_a", "MS Skor 1:2", "MS SKOR", C_SCFT, "Correct score|Full Time|1:2"),
            new ColumnDef("ft_skor_0_3_a", "MS Skor 0:3", "MS SKOR", C_SCFT, "Correct score|Full Time|0:3"),
            new ColumnDef("ft_skor_1_3_a", "MS Skor 1:3", "MS SKOR", C_SCFT, "Correct score|Full Time|1:3"),
            new ColumnDef("ft_skor_2_3_a", "MS Skor 2:3", "MS SKOR", C_SCFT, "Correct score|Full Time|2:3"),
            new ColumnDef("ft_skor_0_4_a", "MS Skor 0:4", "MS SKOR", C_SCFT, "Correct score|Full Time|0:4"),
            new ColumnDef("ft_skor_1_4_a", "MS Skor 1:4", "MS SKOR", C_SCFT, "Correct score|Full Time|1:4"),
            new ColumnDef("ft_skor_2_4_a", "MS Skor 2:4", "MS SKOR", C_SCFT, "Correct score|Full Time|2:4"),
            new ColumnDef("ft_skor_3_4_a", "MS Skor 3:4", "MS SKOR", C_SCFT, "Correct score|Full Time|3:4"),
            new ColumnDef("ft_skor_0_5_a", "MS Skor 0:5", "MS SKOR", C_SCFT, "Correct score|Full Time|0:5"),
            new ColumnDef("ft_skor_1_5_a", "MS Skor 1:5", "MS SKOR", C_SCFT, "Correct score|Full Time|1:5"),
            new ColumnDef("ft_skor_2_5_a", "MS Skor 2:5", "MS SKOR", C_SCFT, "Correct score|Full Time|2:5")
    );

    // ==================== SCRAPER ALANLARI ====================
    private JComboBox<String> matchDropdown;
    private final Map<String, JCheckBox> checkBoxMap = new HashMap<>();
    private final Map<String, JTextField> valueFieldMap = new HashMap<>();
    private final List<MatchInfo> allMatches = new ArrayList<>();
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    // ==================== SQL ALANLARI ====================
    private Connection conn;
    private List<String> sqlColumns = new ArrayList<>();

    static class MatchInfo {
        String id, home, away, date;
        String ftScore = "-";
        Map<String, String> odds = new HashMap<>();
    }

    public Bet365LiveFilterUI() {
        setTitle("⚽ Bet365 Canlı Scraper → SQL Filtreleme");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 1050);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // PostgreSQL bağlantısı
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
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM bet365_matches")) {
                if (rs.next()) System.out.println("SQL'de " + rs.getLong(1) + " kayıt var.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Veritabanı hatası: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Üst kontrol bar
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        topBar.setBackground(new Color(15, 23, 42));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JLabel lblMatch = new JLabel("🎯 Bugünkü Maçlar:");
        lblMatch.setForeground(Color.WHITE);
        lblMatch.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topBar.add(lblMatch);

        matchDropdown = new JComboBox<>();
        matchDropdown.setPreferredSize(new Dimension(400, 32));
        matchDropdown.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        topBar.add(matchDropdown);

        JButton scrapeBtn = createStyledButton("🔄 Flashscore'dan Getir", new Color(59, 130, 246));
        scrapeBtn.addActionListener(e -> scrapeTodayMatches());
        topBar.add(scrapeBtn);

        JButton filterBtn = createStyledButton("🔍 SQL'de Filtrele", new Color(16, 185, 129));
        filterBtn.addActionListener(e -> applySQLFilters());
        topBar.add(filterBtn);

        JButton clearBtn = createStyledButton("🗑 Temizle", new Color(239, 68, 68));
        clearBtn.addActionListener(e -> clearFilters());
        topBar.add(clearBtn);

        add(topBar, BorderLayout.NORTH);

        // Filtre paneli - GridLayout(0, 5) ile her satırda 5 kart
        JPanel filterPanel = new JPanel(new GridLayout(0, 5, 6, 6));
        filterPanel.setBackground(new Color(248, 250, 252));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (ColumnDef col : ALL_COLUMNS) {
            JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
            card.setBackground(col.groupColor);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0, 0, 0, 40), 1),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));

            JCheckBox chk = new JCheckBox();
            chk.setBackground(col.groupColor);
            chk.setToolTipText(col.group + " | " + col.sqlColumn);
            checkBoxMap.put(col.sqlColumn, chk);

            JLabel lbl = new JLabel(col.displayName);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lbl.setToolTipText(col.sqlColumn);

            JTextField txt = new JTextField(4);
            txt.setFont(new Font("Consolas", Font.PLAIN, 12));
            txt.setPreferredSize(new Dimension(55, 24));
            txt.setHorizontalAlignment(JTextField.CENTER);
            txt.setToolTipText(col.sqlColumn + " = " + col.flashscoreKey);
            valueFieldMap.put(col.sqlColumn, txt);

            card.add(chk);
            card.add(lbl);
            card.add(txt);
            filterPanel.add(card);
        }

        JScrollPane filterScroll = new JScrollPane(filterPanel);
        filterScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(148, 163, 184)),
                " Oran Filtreleri (Checkbox işaretle → SQL'de bu orana sahip maçları bul) ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));
        filterScroll.setPreferredSize(new Dimension(1500, 500));
        add(filterScroll, BorderLayout.CENTER);

        // Tablo
        Vector<String> colNames = new Vector<>();
        colNames.add("Tarih"); colNames.add("Lig"); colNames.add("Ev Sahibi"); colNames.add("Deplasman");
        colNames.add("MS"); colNames.add("İY");
        for (ColumnDef col : ALL_COLUMNS) colNames.add(col.displayName);
        tableModel = new DefaultTableModel(colNames, 0);
        resultTable = new JTable(tableModel);
        resultTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultTable.setRowHeight(24);
        resultTable.setGridColor(new Color(226, 232, 240));
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < resultTable.getColumnCount(); i++) {
            resultTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(148, 163, 184)),
                " SQL Filtreleme Sonuçları (bet365_matches tablosu) ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(30, 41, 59)
        ));
        tableScroll.setPreferredSize(new Dimension(1500, 380));
        add(tableScroll, BorderLayout.SOUTH);

        // Dropdown seçiminde oranları doldur
        matchDropdown.addActionListener(e -> {
            int idx = matchDropdown.getSelectedIndex();
            if (idx > 0 && idx - 1 < allMatches.size()) {
                fillOddsToInputs(allMatches.get(idx - 1));
            }
        });

        setVisible(true);
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        return btn;
    }

    // ==================== SCRAPER METODLARI ====================
    private void scrapeTodayMatches() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        allMatches.clear();
        matchDropdown.removeAllItems();
        matchDropdown.addItem("-- Maç Seçin --");

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
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
                    mi.id = matchId; mi.home = home; mi.away = away; mi.date = LocalDate.now().toString();
                    allMatches.add(mi);
                    matchDropdown.addItem(home + " - " + away);
                } catch (Exception ignored) {}
            }

            for (MatchInfo mi : allMatches) fetchOddsForMatch(mi);
            JOptionPane.showMessageDialog(this, allMatches.size() + " maç çekildi. Dropdown'dan seçip oranları doldurabilirsiniz.");
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

    // Default olarak işaretli olacak kolonlar
    private static final List<String> DEFAULT_CHECKED_COLS = List.of(
            "iy_x_a",       // İY X
            "bts_iiy_no_a", // 2Y KG Hayır
            "bts_iy_no_a",  // İY KG Hayır
            "dc_ft_12_a",   // ÇŞ 12
            "au_1_5_over_a", // A/U 1.5 Üst
            "bts_ft_yes_a", // KG Evet
            "iy_2_a",       // İY 2
            "dc_ft_1x_a"    // ÇŞ 1X
    );

    private void fillOddsToInputs(MatchInfo selected) {
        // 1. Önce TÜM checkbox'ları false yap
        for (JCheckBox chk : checkBoxMap.values()) {
            chk.setSelected(false);
        }
        // 2. Tüm inputları temizle ve beyaz yap
        for (JTextField tf : valueFieldMap.values()) {
            tf.setText("");
            tf.setBackground(Color.WHITE);
        }

        // 3. Default kolonları işaretle (değer olmasa bile)
        for (String defaultCol : DEFAULT_CHECKED_COLS) {
            JCheckBox chk = checkBoxMap.get(defaultCol);
            if (chk != null) chk.setSelected(true);
        }

        // 4. Flashscore'dan gelen oranları inputlara doldur (ama checkbox'ları değiştirme!)
        for (ColumnDef col : ALL_COLUMNS) {
            String oddsValue = selected.odds.getOrDefault(col.flashscoreKey, "");
            if (!oddsValue.isEmpty() && !"-".equals(oddsValue)) {
                JTextField tf = valueFieldMap.get(col.sqlColumn);
                if (tf != null) {
                    tf.setText(oddsValue);
                    tf.setBackground(new Color(220, 252, 231)); // yeşil vurgu
                }
            }
        }
    }

    // ==================== SQL FİLTRELEME ====================
    private void applySQLFilters() {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<String> selectedDisplayCols = new ArrayList<>();

        for (ColumnDef col : ALL_COLUMNS) {
            JCheckBox chk = checkBoxMap.get(col.sqlColumn);
            if (chk == null || !chk.isSelected()) continue;

            String value = valueFieldMap.get(col.sqlColumn).getText().trim();
            if (value.isEmpty() || "-".equals(value)) continue;

            // SQL'de bu kolon var mı kontrol et
            if (!sqlColumns.contains(col.sqlColumn)) continue;

            conditions.add(col.sqlColumn + " = ?");
            try {
                params.add(Double.parseDouble(value.replace(',', '.')));
            } catch (NumberFormatException e) {
                params.add(value);
            }
            selectedDisplayCols.add(col.displayName);
        }

        if (conditions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "⚠️ En az bir oran seçmelisiniz!");
            return;
        }

        String whereClause = " WHERE " + String.join(" AND ", conditions);
        String sql = "SELECT * FROM bet365_matches " + whereClause + " ORDER BY date_time DESC LIMIT 5000";

        System.out.println("SQL: " + sql);
        System.out.println("Params: " + params);

        tableModel.setRowCount(0);
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            // Kolon başlıklarını ayarla
            Vector<String> columnNames = new Vector<>();
            for (int i = 1; i <= colCount; i++) {
                String colName = meta.getColumnName(i);
                // SQL kolon adını display name'e çevir
                String display = colName;
                for (ColumnDef cd : ALL_COLUMNS) {
                    if (cd.sqlColumn.equalsIgnoreCase(colName)) {
                        display = cd.displayName;
                        break;
                    }
                }
                columnNames.add(display);
            }
            tableModel.setColumnIdentifiers(columnNames);

            int rowCount = 0;
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= colCount; i++) {
                    row.add(rs.getString(i));
                }
                tableModel.addRow(row);
                rowCount++;
            }

            setTitle("Bet365 - " + rowCount + " maç bulundu (" + selectedDisplayCols.size() + " oran filtresi)");
            JOptionPane.showMessageDialog(this, rowCount + " maç bulundu!\nFiltreler: " + String.join(", ", selectedDisplayCols));

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "SQL Hatası: " + ex.getMessage());
        }
    }

    private void clearFilters() {
        for (JCheckBox chk : checkBoxMap.values()) {
            chk.setSelected(false);
        }
        for (JTextField tf : valueFieldMap.values()) {
            tf.setText("");
            tf.setBackground(Color.WHITE);
        }
        tableModel.setRowCount(0);
        setTitle("⚽ Bet365 Canlı Scraper → SQL Filtreleme");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(Bet365LiveFilterUI::new);
    }
}