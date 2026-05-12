package analyzer.extras;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.List;
import java.util.*;

public class Bet365FullColumnFilter extends JFrame {
    private Connection conn;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JPanel filterPanel;
    private List<String> allColumns = new ArrayList<>();
    private Map<String, JCheckBox> checkBoxMap = new HashMap<>();
    private Map<String, JComboBox<String>> operatorMap = new HashMap<>();
    private Map<String, JTextField> valueMap = new HashMap<>();

    public Bet365FullColumnFilter() {
        setTitle("Bet365 - Tüm Kolonlarda Filtreleme (Checkbox + Değer)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(null);

        // PostgreSQL bağlantısı
        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/postgres",
                    "postgres",
                    "fuad123"  // Şifrenizi güncelleyin
            );
            // Tüm kolonları al
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "bet365_matches", null)) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    allColumns.add(colName);
                }
            }
            // Tabloda veri var mı kontrol et
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM bet365_matches")) {
                if (rs.next()) {
                    long count = rs.getLong(1);
                    System.out.println("Tabloda " + count + " satır var.");
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Veritabanı hatası: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        setLayout(new BorderLayout());

        // Filtre paneli (scrollable)
        filterPanel = new JPanel();
        filterPanel.setLayout(new GridLayout(0, 4, 5, 5));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filtreler (Checkbox = aktif) - Tüm Kolonlar"));

        for (String col : allColumns) {
            JCheckBox chk = new JCheckBox();
            checkBoxMap.put(col, chk);
            JLabel colLabel = new JLabel(col);
            colLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
            JComboBox<String> opCombo = new JComboBox<>(new String[]{"=", ">", "<", ">=", "<=", "LIKE", "IS NULL", "IS NOT NULL"});
            opCombo.setPreferredSize(new Dimension(70, 25));
            operatorMap.put(col, opCombo);
            JTextField valueField = new JTextField(10);
            valueMap.put(col, valueField);

            filterPanel.add(chk);
            filterPanel.add(colLabel);
            filterPanel.add(opCombo);
            filterPanel.add(valueField);
        }

        JScrollPane filterScroll = new JScrollPane(filterPanel);
        filterScroll.setPreferredSize(new Dimension(1300, 300));
        add(filterScroll, BorderLayout.NORTH);

        // Buton paneli
        JPanel buttonPanel = new JPanel();
        JButton searchBtn = new JButton("🔍 FİLTRELE VE GETİR");
        searchBtn.setFont(new Font("Arial", Font.BOLD, 14));
        searchBtn.setBackground(new Color(50, 150, 50));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.addActionListener(e -> executeQuery());
        JButton clearBtn = new JButton("🗑 TÜM FİLTRELERİ TEMİZLE");
        clearBtn.addActionListener(e -> clearFilters());
        buttonPanel.add(searchBtn);
        buttonPanel.add(clearBtn);
        add(buttonPanel, BorderLayout.CENTER);

        // Sonuç tablosu
        tableModel = new DefaultTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Maç Sonuçları (Tüm Kolonlar)"));
        add(tableScroll, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void executeQuery() {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (String col : allColumns) {
            JCheckBox chk = checkBoxMap.get(col);
            if (chk == null || !chk.isSelected()) continue;

            String operator = operatorMap.get(col).getSelectedItem().toString();
            String value = valueMap.get(col).getText().trim();

            if (operator.equals("IS NULL") || operator.equals("IS NOT NULL")) {
                conditions.add(col + " " + operator);
            } else if (!value.isEmpty()) {
                conditions.add(col + " " + operator + " ?");
                if (isNumericColumn(col)) {
                    try {
                        params.add(Double.parseDouble(value.replace(',', '.')));
                    } catch (NumberFormatException e) {
                        params.add(null);
                    }
                } else {
                    if (operator.equals("LIKE")) params.add("%" + value + "%");
                    else params.add(value);
                }
            }
        }

        String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
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
                columnNames.add(meta.getColumnName(i));
            }
            tableModel.setColumnIdentifiers(columnNames);

            // Verileri doldur
            int rowCount = 0;
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= colCount; i++) {
                    row.add(rs.getString(i));
                }
                tableModel.addRow(row);
                rowCount++;
            }
            setTitle("Bet365 - " + rowCount + " maç listeleniyor");
            if (rowCount == 0) {
                JOptionPane.showMessageDialog(this, "⚠️ Sonuç bulunamadı. Filtreleri kontrol edin veya veritabanında veri olduğundan emin olun.");
            } else {
                // Tablo sütun genişliklerini otomatik ayarla (basit)
                for (int i = 0; i < resultTable.getColumnCount(); i++) {
                    resultTable.getColumnModel().getColumn(i).setPreferredWidth(100);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Sorgu hatası: " + ex.getMessage());
        }
    }

    private void clearFilters() {
        for (JCheckBox chk : checkBoxMap.values()) chk.setSelected(false);
        for (JTextField tf : valueMap.values()) tf.setText("");
    }

    private boolean isNumericColumn(String colName) {
        // Sayısal kolonları otomatik algıla: oran kolonları (_a ile biter) ve ID gibi sayısal olanlar
        // Ayrıca integer olabilecek kolonlar (code_kod gibi) da sayısal
        if (colName.equals("code_kod")) return true;
        return colName.endsWith("_a") && !colName.equals("home_team") && !colName.equals("away_team")
                && !colName.equals("ft_ms") && !colName.equals("ht_iy")
                && !colName.equals("country_league") && !colName.equals("date_time");
    }

    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {}
            new Bet365FullColumnFilter();
        });
    }
}