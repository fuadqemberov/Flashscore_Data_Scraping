package analyzer.bet365;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.math.BigDecimal;

public class ImportCsvToPostgres {
    static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/postgres";
        String user = "postgres";
        String password = "fuad123";
        String csvFile = "bet365.csv";

        // 92 kolon adı (sizin önceki listeniz)
        String[] columns = {
                "code_kod", "ht_iy", "ft_ms", "home_team", "away_team",
                "ft_1_a", "ft_x_a", "ft_2_a", "first_1_a", "first_x_a", "first_2_a",
                "second_1_a", "second_x_a", "second_2_a", "bts_ft_yes_a", "bts_ft_no_a",
                "bts_first_yes_a", "bts_first_no_a", "bts_second_yes_a", "bts_second_no_a",
                "dbc_ft_1x_a", "dbc_ft_12_a", "dbc_ft_x2_a", "dbc_first_1x_a", "dbc_first_12_a", "dbc_first_x2_a",
                "ft_0_5_over_a", "ft_0_5_under_a", "ft_1_5_over_a", "ft_1_5_under_a", "ft_2_5_over_a", "ft_2_5_under_a",
                "ft_3_5_over_a", "ft_3_5_under_a", "ft_4_5_over_a", "ft_4_5_under_a", "ft_5_5_over_a", "ft_5_5_under_a",
                "first_0_5_over_a", "first_0_5_under_a", "first_1_5_over_a", "first_1_5_under_a", "first_2_5_over_a", "first_2_5_under_a",
                "second_0_5_over_a", "second_0_5_under_a", "second_1_5_over_a", "second_1_5_under_a", "second_2_5_over_a", "second_2_5_under_a",
                "ht_ft_11_a", "ht_ft_1x_a", "ht_ft_12_a", "ht_ft_x1_a", "ht_ft_xx_a", "ht_ft_x2_a", "ht_ft_21_a", "ht_ft_2x_a", "ht_ft_22_a",
                "first_score_1_0_a", "first_score_2_0_a", "first_score_2_1_a", "first_score_3_0_a", "first_score_3_1_a", "first_score_3_2_a",
                "first_score_0_0_a", "first_score_1_1_a", "first_score_2_2_a", "first_score_0_1_a", "first_score_0_2_a", "first_score_1_2_a",
                "first_score_0_3_a", "first_score_1_3_a", "first_score_2_3_a",
                "ft_score_1_0_a", "ft_score_2_0_a", "ft_score_2_1_a", "ft_score_3_0_a", "ft_score_3_1_a", "ft_score_3_2_a",
                "ft_score_4_0_a", "ft_score_4_1_a", "ft_score_4_2_a", "ft_score_4_3_a", "ft_score_5_0_a", "ft_score_5_1_a", "ft_score_5_2_a",
                "ft_score_0_0_a", "ft_score_1_1_a", "ft_score_2_2_a", "ft_score_3_3_a", "ft_score_4_4_a",
                "ft_score_0_1_a", "ft_score_0_2_a", "ft_score_1_2_a", "ft_score_0_3_a", "ft_score_1_3_a", "ft_score_2_3_a",
                "ft_score_0_4_a", "ft_score_1_4_a", "ft_score_2_4_a", "ft_score_3_4_a",
                "ft_score_0_5_a", "ft_score_1_5_a", "ft_score_2_5_a",
                "country_league", "date_time"
        };
        int totalCols = columns.length; // 92

        StringBuilder insertSql = new StringBuilder("INSERT INTO bet365_matches (");
        for (int i = 0; i < totalCols; i++) {
            if (i > 0) insertSql.append(", ");
            insertSql.append(columns[i]);
        }
        insertSql.append(") VALUES (");
        for (int i = 0; i < totalCols; i++) {
            if (i > 0) insertSql.append(", ");
            insertSql.append("?");
        }
        insertSql.append(")");

        try (Connection conn = DriverManager.getConnection(url, user, password);
             CSVReader reader = new CSVReaderBuilder(Files.newBufferedReader(Paths.get(csvFile)))
                     .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                     .build()) {

            conn.setAutoCommit(false);
            PreparedStatement pstmt = conn.prepareStatement(insertSql.toString());
            int batchSize = 2000;
            int count = 0;
            int lineNum = 0;
            String[] row;

            while ((row = reader.readNext()) != null) {
                lineNum++;
                if (lineNum <= 2) continue;

                int rowCols = row.length;
                // Sütunları ata, eksik olanlara null ver
                for (int i = 0; i < totalCols; i++) {
                    if (i < rowCols) {
                        String val = row[i].trim();
                        if (val.isEmpty()) {
                            pstmt.setNull(i + 1, Types.NULL);
                        } else {
                            // İlk 5 kolon metin, sonrası sayısal
                            if (i < 5) {
                                pstmt.setString(i + 1, val);
                            } else {
                                try {
                                    BigDecimal bd = new BigDecimal(val.replace(',', '.'));
                                    pstmt.setBigDecimal(i + 1, bd);
                                } catch (NumberFormatException e) {
                                    pstmt.setNull(i + 1, Types.NUMERIC);
                                }
                            }
                        }
                    } else {
                        pstmt.setNull(i + 1, Types.NULL);
                    }
                }
                pstmt.addBatch();
                count++;

                if (count % batchSize == 0) {
                    pstmt.executeBatch();
                    conn.commit();
                    System.out.println("✅ " + count + " satır eklendi...");
                }
            }
            pstmt.executeBatch();
            conn.commit();
            System.out.println("🎉 TAMAMLANDI! Toplam " + count + " satır eklendi.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}