package analyzer.bet365;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class Bet365FilterFinderSQL {

    // ==================== KOLON TANIMLARI ====================
    static class ColumnDef {
        String sqlColumn;
        String displayName;
        ColumnDef(String sqlColumn, String displayName) {
            this.sqlColumn = sqlColumn;
            this.displayName = displayName;
        }
    }

    private static final List<ColumnDef> ALL_ODDS_COLS = List.of(
            new ColumnDef("ft_1_a", "MS 1"),
            new ColumnDef("ft_x_a", "MS X"),
            new ColumnDef("ft_2_a", "MS 2"),
            new ColumnDef("first_1_a", "İY 1"),
            new ColumnDef("first_x_a", "İY X"),
            new ColumnDef("first_2_a", "İY 2"),
            new ColumnDef("second_1_a", "2Y 1"),
            new ColumnDef("second_x_a", "2Y X"),
            new ColumnDef("second_2_a", "2Y 2"),
            new ColumnDef("bts_ft_yes_a", "KG Evet"),
            new ColumnDef("bts_ft_no_a", "KG Hayır"),
            new ColumnDef("bts_first_yes_a", "İY KG Evet"),
            new ColumnDef("bts_first_no_a", "İY KG Hayır"),
            new ColumnDef("bts_second_yes_a", "2Y KG Evet"),
            new ColumnDef("bts_second_no_a", "2Y KG Hayır"),
            new ColumnDef("dbc_ft_1x_a", "ÇŞ 1X"),
            new ColumnDef("dbc_ft_12_a", "ÇŞ 12"),
            new ColumnDef("dbc_ft_x2_a", "ÇŞ X2"),
            new ColumnDef("dbc_first_1x_a", "İY ÇŞ 1X"),
            new ColumnDef("dbc_first_12_a", "İY ÇŞ 12"),
            new ColumnDef("dbc_first_x2_a", "İY ÇŞ X2"),
            new ColumnDef("ft_0_5_over_a", "A/U 0.5 Üst"),
            new ColumnDef("ft_0_5_under_a", "A/U 0.5 Alt"),
            new ColumnDef("ft_1_5_over_a", "A/U 1.5 Üst"),
            new ColumnDef("ft_1_5_under_a", "A/U 1.5 Alt"),
            new ColumnDef("ft_2_5_over_a", "A/U 2.5 Üst"),
            new ColumnDef("ft_2_5_under_a", "A/U 2.5 Alt"),
            new ColumnDef("ft_3_5_over_a", "A/U 3.5 Üst"),
            new ColumnDef("ft_3_5_under_a", "A/U 3.5 Alt"),
            new ColumnDef("ft_4_5_over_a", "A/U 4.5 Üst"),
            new ColumnDef("ft_4_5_under_a", "A/U 4.5 Alt"),
            new ColumnDef("ft_5_5_over_a", "A/U 5.5 Üst"),
            new ColumnDef("ft_5_5_under_a", "A/U 5.5 Alt"),
            new ColumnDef("first_0_5_over_a", "İY A/U 0.5 Üst"),
            new ColumnDef("first_0_5_under_a", "İY A/U 0.5 Alt"),
            new ColumnDef("first_1_5_over_a", "İY A/U 1.5 Üst"),
            new ColumnDef("first_1_5_under_a", "İY A/U 1.5 Alt"),
            new ColumnDef("first_2_5_over_a", "İY A/U 2.5 Üst"),
            new ColumnDef("first_2_5_under_a", "İY A/U 2.5 Alt"),
            new ColumnDef("second_0_5_over_a", "2Y A/U 0.5 Üst"),
            new ColumnDef("second_0_5_under_a", "2Y A/U 0.5 Alt"),
            new ColumnDef("second_1_5_over_a", "2Y A/U 1.5 Üst"),
            new ColumnDef("second_1_5_under_a", "2Y A/U 1.5 Alt"),
            new ColumnDef("second_2_5_over_a", "2Y A/U 2.5 Üst"),
            new ColumnDef("second_2_5_under_a", "2Y A/U 2.5 Alt"),
            new ColumnDef("ht_ft_11_a", "HT/FT 1/1"),
            new ColumnDef("ht_ft_1x_a", "HT/FT 1/X"),
            new ColumnDef("ht_ft_12_a", "HT/FT 1/2"),
            new ColumnDef("ht_ft_x1_a", "HT/FT X/1"),
            new ColumnDef("ht_ft_xx_a", "HT/FT X/X"),
            new ColumnDef("ht_ft_x2_a", "HT/FT X/2"),
            new ColumnDef("ht_ft_21_a", "HT/FT 2/1"),
            new ColumnDef("ht_ft_2x_a", "HT/FT 2/X"),
            new ColumnDef("ht_ft_22_a", "HT/FT 2/2"),
            new ColumnDef("first_score_1_0_a", "İY Skor 1:0"),
            new ColumnDef("first_score_2_0_a", "İY Skor 2:0"),
            new ColumnDef("first_score_2_1_a", "İY Skor 2:1"),
            new ColumnDef("first_score_3_0_a", "İY Skor 3:0"),
            new ColumnDef("first_score_3_1_a", "İY Skor 3:1"),
            new ColumnDef("first_score_3_2_a", "İY Skor 3:2"),
            new ColumnDef("first_score_0_0_a", "İY Skor 0:0"),
            new ColumnDef("first_score_1_1_a", "İY Skor 1:1"),
            new ColumnDef("first_score_2_2_a", "İY Skor 2:2"),
            new ColumnDef("first_score_0_1_a", "İY Skor 0:1"),
            new ColumnDef("first_score_0_2_a", "İY Skor 0:2"),
            new ColumnDef("first_score_1_2_a", "İY Skor 1:2"),
            new ColumnDef("first_score_0_3_a", "İY Skor 0:3"),
            new ColumnDef("first_score_1_3_a", "İY Skor 1:3"),
            new ColumnDef("first_score_2_3_a", "İY Skor 2:3"),
            new ColumnDef("ft_score_1_0_a", "MS Skor 1:0"),
            new ColumnDef("ft_score_2_0_a", "MS Skor 2:0"),
            new ColumnDef("ft_score_2_1_a", "MS Skor 2:1"),
            new ColumnDef("ft_score_3_0_a", "MS Skor 3:0"),
            new ColumnDef("ft_score_3_1_a", "MS Skor 3:1"),
            new ColumnDef("ft_score_3_2_a", "MS Skor 3:2"),
            new ColumnDef("ft_score_4_0_a", "MS Skor 4:0"),
            new ColumnDef("ft_score_4_1_a", "MS Skor 4:1"),
            new ColumnDef("ft_score_4_2_a", "MS Skor 4:2"),
            new ColumnDef("ft_score_4_3_a", "MS Skor 4:3"),
            new ColumnDef("ft_score_5_0_a", "MS Skor 5:0"),
            new ColumnDef("ft_score_5_1_a", "MS Skor 5:1"),
            new ColumnDef("ft_score_5_2_a", "MS Skor 5:2"),
            new ColumnDef("ft_score_0_0_a", "MS Skor 0:0"),
            new ColumnDef("ft_score_1_1_a", "MS Skor 1:1"),
            new ColumnDef("ft_score_2_2_a", "MS Skor 2:2"),
            new ColumnDef("ft_score_3_3_a", "MS Skor 3:3"),
            new ColumnDef("ft_score_4_4_a", "MS Skor 4:4"),
            new ColumnDef("ft_score_0_1_a", "MS Skor 0:1"),
            new ColumnDef("ft_score_0_2_a", "MS Skor 0:2"),
            new ColumnDef("ft_score_1_2_a", "MS Skor 1:2"),
            new ColumnDef("ft_score_0_3_a", "MS Skor 0:3"),
            new ColumnDef("ft_score_1_3_a", "MS Skor 1:3"),
            new ColumnDef("ft_score_2_3_a", "MS Skor 2:3"),
            new ColumnDef("ft_score_0_4_a", "MS Skor 0:4"),
            new ColumnDef("ft_score_1_4_a", "MS Skor 1:4"),
            new ColumnDef("ft_score_2_4_a", "MS Skor 2:4"),
            new ColumnDef("ft_score_3_4_a", "MS Skor 3:4"),
            new ColumnDef("ft_score_0_5_a", "MS Skor 0:5"),
            new ColumnDef("ft_score_1_5_a", "MS Skor 1:5"),
            new ColumnDef("ft_score_2_5_a", "MS Skor 2:5")
    );

    // ==================== HAFIZA DOSTU VERİ YAPISI ====================
    static class MatchRecord {
        String league, date, homeTeam, awayTeam;
        int htHome, htAway, ftHome, ftAway;   // -1 bilinmiyor
        double[] odds;                        // ALL_ODDS_COLS boyutunda

        MatchRecord(int oddsSize) {
            this.odds = new double[oddsSize];
        }

        String ftScore() {
            return (ftHome >= 0) ? ftHome + "-" + ftAway : "?-?";
        }
        String htScore() {
            return (htHome >= 0) ? htHome + "-" + htAway : "?-?";
        }
    }

    // ==================== RUN SONUCU (Tüm modlar için) ====================
    static class RunResult {
        String modeName;
        int runNumber;              // mod içindeki run numarası (greedy için 1,2..)
        List<Integer> matchedIndices;
        List<Integer> usedCols;    // null olabilir (k-NN gibi)
        String predictedScore;
        double confidence;
        int correctIY, correctMS, correctHTFT;
        int totalMatches;

        RunResult(String modeName, List<Integer> matchedIndices) {
            this.modeName = modeName;
            this.matchedIndices = matchedIndices;
            this.totalMatches = matchedIndices.size();
        }
    }

    // ==================== VERİTABANI VE VERİ YÜKLEME ====================
    private Connection conn;
    private List<MatchRecord> allRecords = new ArrayList<>();
    private double[] columnStdDev;

    public Bet365FilterFinderSQL() {
        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/postgres",
                    "postgres", "fuad123"
            );
            System.out.println("✅ Veritabanına bağlanıldı.");
            loadAllRecords();
            computeColumnStatistics();
        } catch (Exception e) {
            System.err.println("❌ Veritabanı hatası: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void loadAllRecords() throws SQLException {
        System.out.println("📊 Veriler yükleniyor (sadece gerekli sütunlar)...");
        List<String> columnNames = new ArrayList<>();
        columnNames.add("country_league");
        columnNames.add("date_time");
        columnNames.add("home_team");
        columnNames.add("away_team");
        columnNames.add("ht_iy");
        columnNames.add("ft_ms");
        for (ColumnDef cd : ALL_ODDS_COLS) {
            columnNames.add(cd.sqlColumn);
        }

        String sql = "SELECT " + String.join(", ", columnNames) + " FROM bet365_matches ORDER BY date_time DESC";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                MatchRecord rec = new MatchRecord(ALL_ODDS_COLS.size());
                rec.league = rs.getString("country_league");
                rec.date = rs.getString("date_time");
                rec.homeTeam = rs.getString("home_team");
                rec.awayTeam = rs.getString("away_team");

                rec.htHome = rec.htAway = rec.ftHome = rec.ftAway = -1;
                String htStr = rs.getString("ht_iy");
                if (htStr != null && htStr.contains("-")) {
                    String[] parts = htStr.split("-");
                    if (parts.length == 2) {
                        try {
                            rec.htHome = Integer.parseInt(parts[0].trim());
                            rec.htAway = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                String ftStr = rs.getString("ft_ms");
                if (ftStr != null && ftStr.contains("-")) {
                    String[] parts = ftStr.split("-");
                    if (parts.length == 2) {
                        try {
                            rec.ftHome = Integer.parseInt(parts[0].trim());
                            rec.ftAway = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }

                for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
                    String val = rs.getString(ALL_ODDS_COLS.get(i).sqlColumn);
                    rec.odds[i] = parseOdds(val);
                }
                allRecords.add(rec);
            }
        }
        System.out.println("✅ " + allRecords.size() + " kayıt yüklendi.\n");
    }

    private void computeColumnStatistics() {
        int n = allRecords.size();
        columnStdDev = new double[ALL_ODDS_COLS.size()];
        double[] sum = new double[ALL_ODDS_COLS.size()];
        double[] sumSq = new double[ALL_ODDS_COLS.size()];

        for (MatchRecord rec : allRecords) {
            for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
                double v = rec.odds[i];
                if (v > 0) {
                    sum[i] += v;
                    sumSq[i] += v * v;
                }
            }
        }
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
            double mean = sum[i] / n;
            columnStdDev[i] = Math.sqrt(Math.max(0, sumSq[i] / n - mean * mean));
        }
    }

    // ==================== ORTAK YARDIMCILAR ====================
    private double parseOdds(String val) {
        if (val == null || val.isEmpty() || "-".equals(val)) return 0.0;
        try {
            return Double.parseDouble(val.replace(',', '.'));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String trunc(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "-";
        if (s.length() > maxLen) return s.substring(0, maxLen - 1) + "…";
        return s;
    }

    private void printMatchTable(List<Integer> indices) {
        System.out.println("┌──────┬─────────────────────┬─────────────────────┬─────────────────────┬──────────┬──────────┬──────────┐");
        System.out.println("│  #   │ Lig                 │ Tarih               │ Ev Sahibi           │ Deplasman│ İY       │ MS       │");
        System.out.println("├──────┼─────────────────────┼─────────────────────┼─────────────────────┼──────────┼──────────┼──────────┤");
        for (int i = 0; i < indices.size(); i++) {
            MatchRecord m = allRecords.get(indices.get(i));
            String iy = m.htScore();
            String ms = m.ftScore();
            System.out.printf("│ %-4d │ %-19s │ %-19s │ %-19s │ %-8s │ %-8s │ %-8s │\n",
                    i + 1,
                    trunc(m.league, 19),
                    trunc(m.date, 19),
                    trunc(m.homeTeam, 19),
                    trunc(m.awayTeam, 8),
                    trunc(iy, 8),
                    trunc(ms, 8));
        }
        System.out.println("└──────┴─────────────────────┴─────────────────────┴─────────────────────┴──────────┴──────────┴──────────┘");
    }

    // Hedef maça göre bir RunResult'ın başarı metriklerini doldur
    private void fillAccuracy(RunResult rr, MatchRecord target) {
        if (target.ftHome < 0 || target.htHome < 0) return; // gerçek skor yoksa doldurma
        int iyMatch = 0, msMatch = 0, htftMatch = 0;
        for (int idx : rr.matchedIndices) {
            MatchRecord m = allRecords.get(idx);
            if (m.htHome == target.htHome && m.htAway == target.htAway) iyMatch++;
            if (m.ftHome == target.ftHome && m.ftAway == target.ftAway) msMatch++;
            if (m.htHome == target.htHome && m.htAway == target.htAway &&
                m.ftHome == target.ftHome && m.ftAway == target.ftAway) htftMatch++;
        }
        rr.correctIY = iyMatch;
        rr.correctMS = msMatch;
        rr.correctHTFT = htftMatch;
    }

    // Skor tahmini oluştur (çoğunluk)
    private void computePrediction(RunResult rr) {
        Map<String, Integer> scoreCount = new LinkedHashMap<>();
        for (int idx : rr.matchedIndices) {
            MatchRecord m = allRecords.get(idx);
            String sc = m.ftScore();
            scoreCount.put(sc, scoreCount.getOrDefault(sc, 0) + 1);
        }
        String best = scoreCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("?-?");
        int bestCnt = scoreCount.get(best);
        rr.predictedScore = best;
        rr.confidence = Math.round(bestCnt * 1000.0 / rr.totalMatches) / 10.0;
    }

    private double getEpsilon(int colIdx, boolean dynamic) {
        if (!dynamic || columnStdDev == null || columnStdDev[colIdx] == 0) {
            return 0.02;
        }
        double dynEps = columnStdDev[colIdx] * 0.10;
        return Math.max(0.01, Math.min(0.15, dynEps));
    }

    private int countMatches(double targetVal, int colIdx, int targetRow, double epsilon) {
        int cnt = 0;
        for (int i = 0; i < allRecords.size(); i++) {
            if (i == targetRow) continue;
            double v = allRecords.get(i).odds[colIdx];
            if (v > 0 && Math.abs(v - targetVal) <= epsilon) cnt++;
        }
        return cnt;
    }

    private List<Integer> applyFilter(List<Integer> pool, int colIdx, double targetVal, double eps) {
        List<Integer> newPool = new ArrayList<>();
        for (int idx : pool) {
            double v = allRecords.get(idx).odds[colIdx];
            if (v > 0.0 && Math.abs(v - targetVal) <= eps) newPool.add(idx);
        }
        return newPool;
    }

    // Ağırlıklı rastgele sıralama
    private List<Integer> weightedShuffle(List<Integer> cols, Map<Integer, Integer> matchCount, Random rng) {
        List<Integer> shuffled = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>(cols);
        while (!remaining.isEmpty()) {
            double totalWeight = 0;
            double[] weights = new double[remaining.size()];
            for (int i = 0; i < remaining.size(); i++) {
                int cnt = matchCount.get(remaining.get(i));
                weights[i] = 1.0 / (cnt + 1);
                totalWeight += weights[i];
            }
            double r = rng.nextDouble() * totalWeight;
            double cum = 0;
            for (int i = 0; i < remaining.size(); i++) {
                cum += weights[i];
                if (r <= cum) {
                    shuffled.add(remaining.remove(i));
                    break;
                }
            }
        }
        return shuffled;
    }

    // ==================== ANA ÇALIŞTIRICI (TÜM MODLAR) ====================
    public void runAllMethods(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= allRecords.size()) {
            System.out.println("❌ Geçersiz hedef indeks.");
            return;
        }
        MatchRecord target = allRecords.get(targetIndex);
        printTargetInfo(target);

        List<RunResult> allModeResults = new ArrayList<>();

        // MOD 1
        List<RunResult> mod1 = runRandomizedFiltering(targetIndex, 100, false, false, "Mod1: Rastgele 100 Run");
        allModeResults.addAll(mod1);

        // MOD 2
        List<RunResult> mod2 = runRandomizedFiltering(targetIndex, 100, true, true, "Mod2: Akıllı Karma");
        allModeResults.addAll(mod2);

        // MOD 3
        List<RunResult> mod3 = runGreedyBestFirst(targetIndex);
        allModeResults.addAll(mod3);

        // MOD 4
        RunResult mod4 = runKNN(targetIndex, 5);
        if (mod4 != null) allModeResults.add(mod4);

        // MOD 5
        RunResult mod5 = runCombinatorial(targetIndex);
        if (mod5 != null) allModeResults.add(mod5);

        // Tüm sonuçların doğruluk metriklerini doldur
        for (RunResult rr : allModeResults) {
            fillAccuracy(rr, target);
        }

        // ÖZET TABLO
        printFinalSummary(allModeResults, target);
    }

    // ==================== MOD 1 & 2: RASTGELE FİLTRELEME ====================
    private List<RunResult> runRandomizedFiltering(int targetIndex, int totalRuns,
                                                   boolean useSmartShuffle, boolean useDynamicEpsilon,
                                                   String modeName) {
        MatchRecord target = allRecords.get(targetIndex);
        List<Integer> availableCols = new ArrayList<>();
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
            if (target.odds[i] > 0.0) availableCols.add(i);
        }
        if (availableCols.isEmpty()) return Collections.emptyList();

        Map<Integer, Integer> colMatchCount = new HashMap<>();
        if (useSmartShuffle) {
            for (int ci : availableCols) {
                colMatchCount.put(ci, countMatches(target.odds[ci], ci, targetIndex, getEpsilon(ci, true)));
            }
        }

        List<RunResult> successfulRuns = new ArrayList<>();
        Random rng = new Random();

        for (int run = 1; run <= totalRuns; run++) {
            List<Integer> shuffledCols;
            if (useSmartShuffle) {
                shuffledCols = weightedShuffle(availableCols, colMatchCount, rng);
            } else {
                shuffledCols = new ArrayList<>(availableCols);
                Collections.shuffle(shuffledCols, rng);
            }

            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < allRecords.size(); i++) {
                if (i != targetIndex) pool.add(i);
            }
            List<Integer> usedCols = new ArrayList<>();
            boolean success = false;

            for (int colIdx : shuffledCols) {
                double eps = getEpsilon(colIdx, useDynamicEpsilon);
                List<Integer> newPool = applyFilter(pool, colIdx, target.odds[colIdx], eps);

                if (newPool.size() < 2) {
                    // Kademeli epsilon genişletme
                    for (double factor : new double[]{1.5, 2.0, 2.5}) {
                        newPool = applyFilter(pool, colIdx, target.odds[colIdx], eps * factor);
                        if (newPool.size() >= 2) break;
                    }
                    if (newPool.size() < 2) continue;
                }

                usedCols.add(colIdx);
                pool = newPool;
                if (pool.size() >= 2 && pool.size() <= 5) {
                    success = true;
                    break;
                }
            }

            if (success) {
                RunResult rr = new RunResult(modeName, new ArrayList<>(pool));
                rr.runNumber = run;
                rr.usedCols = new ArrayList<>(usedCols);
                computePrediction(rr);
                successfulRuns.add(rr);
            }
        }

        // Mod başlığı
        System.out.println("\n══════════════════════════════════════════════════════");
        System.out.println(" " + modeName + " - Başarılı Runlar: " + successfulRuns.size() + "/" + totalRuns);
        System.out.println("══════════════════════════════════════════════════════");
        for (RunResult rr : successfulRuns) {
            System.out.println("──────────────────────────────────────────────────────");
            System.out.printf("🔁 Run %d: %d maç (%d filtre)\n", rr.runNumber, rr.totalMatches, rr.usedCols.size());
            System.out.print("   Filtreler: ");
            for (int i = 0; i < rr.usedCols.size(); i++) {
                if (i > 0) System.out.print(" → ");
                System.out.print(ALL_ODDS_COLS.get(rr.usedCols.get(i)).displayName);
            }
            System.out.println();
            printMatchTable(rr.matchedIndices);
            System.out.printf("   ➡️ Tahmin: %s (Güven: %%%s)\n\n", rr.predictedScore, rr.confidence);
        }
        return successfulRuns;
    }

    // ==================== MOD 3: AÇGÖZLÜ ZİNCİR ====================
    private List<RunResult> runGreedyBestFirst(int targetIndex) {
        MatchRecord target = allRecords.get(targetIndex);
        List<Integer> availableCols = new ArrayList<>();
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
            if (target.odds[i] > 0.0) availableCols.add(i);
        }
        if (availableCols.isEmpty()) return Collections.emptyList();

        Map<Integer, Integer> matchCount = new HashMap<>();
        for (int ci : availableCols) {
            matchCount.put(ci, countMatches(target.odds[ci], ci, targetIndex, getEpsilon(ci, true)));
        }
        List<Integer> starters = matchCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<RunResult> results = new ArrayList<>();
        int chainNo = 1;
        for (int startCol : starters) {
            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < allRecords.size(); i++) if (i != targetIndex) pool.add(i);
            List<Integer> usedCols = new ArrayList<>();

            pool = applyFilter(pool, startCol, target.odds[startCol], getEpsilon(startCol, true));
            if (pool.size() < 2) continue;
            usedCols.add(startCol);

            List<Integer> remainingCols = new ArrayList<>(availableCols);
            remainingCols.remove(Integer.valueOf(startCol));
            boolean success = false;
            while (!remainingCols.isEmpty() && pool.size() > 5) {
                int bestCol = -1;
                int bestSize = Integer.MAX_VALUE;
                for (int ci : remainingCols) {
                    List<Integer> temp = applyFilter(pool, ci, target.odds[ci], getEpsilon(ci, true));
                    if (temp.size() >= 2 && temp.size() < bestSize) {
                        bestSize = temp.size();
                        bestCol = ci;
                    }
                }
                if (bestCol == -1) break;
                pool = applyFilter(pool, bestCol, target.odds[bestCol], getEpsilon(bestCol, true));
                usedCols.add(bestCol);
                remainingCols.remove(Integer.valueOf(bestCol));
                if (pool.size() >= 2 && pool.size() <= 5) {
                    success = true;
                    break;
                }
            }
            if (success || (pool.size() >= 2 && pool.size() <= 5)) {
                RunResult rr = new RunResult("Mod3: Açgözlü Zincir", new ArrayList<>(pool));
                rr.runNumber = chainNo++;
                rr.usedCols = usedCols;
                computePrediction(rr);
                results.add(rr);
            }
        }

        System.out.println("\n══════════════════════════════════════════════════════");
        System.out.println(" Mod3: Açgözlü En İyi Zincir - Zincirler: " + results.size());
        System.out.println("══════════════════════════════════════════════════════");
        for (RunResult rr : results) {
            System.out.println("──────────────────────────────────────────────────────");
            System.out.printf("🔗 Zincir %d: %d maç (%d filtre)\n", rr.runNumber, rr.totalMatches, rr.usedCols.size());
            System.out.print("   Filtreler: ");
            for (int i = 0; i < rr.usedCols.size(); i++) {
                if (i > 0) System.out.print(" → ");
                System.out.print(ALL_ODDS_COLS.get(rr.usedCols.get(i)).displayName);
            }
            System.out.println();
            printMatchTable(rr.matchedIndices);
            System.out.printf("   ➡️ Tahmin: %s (Güven: %%%s)\n\n", rr.predictedScore, rr.confidence);
        }
        return results;
    }

    // ==================== MOD 4: k-NN ====================
    private RunResult runKNN(int targetIndex, int k) {
        MatchRecord target = allRecords.get(targetIndex);
        List<Integer> activeCols = new ArrayList<>();
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
            if (target.odds[i] > 0) activeCols.add(i);
        }
        if (activeCols.isEmpty()) return null;

        double[] targetVec = new double[activeCols.size()];
        for (int i = 0; i < activeCols.size(); i++) targetVec[i] = target.odds[activeCols.get(i)];

        class IndexDist implements Comparable<IndexDist> {
            int index; double dist;
            IndexDist(int i, double d) { index = i; dist = d; }
            public int compareTo(IndexDist o) { return Double.compare(this.dist, o.dist); }
        }
        List<IndexDist> distances = new ArrayList<>();
        for (int i = 0; i < allRecords.size(); i++) {
            if (i == targetIndex) continue;
            double sumSq = 0; int valid = 0;
            for (int j = 0; j < activeCols.size(); j++) {
                double val = allRecords.get(i).odds[activeCols.get(j)];
                if (val > 0) { sumSq += Math.pow(val - targetVec[j], 2); valid++; }
            }
            if (valid > 0) distances.add(new IndexDist(i, Math.sqrt(sumSq / valid)));
        }
        Collections.sort(distances);
        List<Integer> topK = distances.stream().limit(k).map(d -> d.index).collect(Collectors.toList());

        RunResult rr = new RunResult("Mod4: k-NN (k=" + k + ")", topK);
        computePrediction(rr);

        System.out.println("\n══════════════════════════════════════════════════════");
        System.out.println(" Mod4: k-En Yakın Komşu (k=" + k + ")");
        System.out.println("══════════════════════════════════════════════════════");
        printMatchTable(topK);
        System.out.printf("   ➡️ Tahmin: %s (Güven: %%%s)\n\n", rr.predictedScore, rr.confidence);
        return rr;
    }

    // ==================== MOD 5: KOMBİNATORYAL ====================
    private RunResult runCombinatorial(int targetIndex) {
        MatchRecord target = allRecords.get(targetIndex);
        List<Integer> availableCols = new ArrayList<>();
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
            if (target.odds[i] > 0.0) availableCols.add(i);
        }
        if (availableCols.size() < 2) return null;

        Random rng = new Random();
        for (int attempt = 0; attempt < 200; attempt++) {
            Collections.shuffle(availableCols, rng);
            int col1 = availableCols.get(0);
            int col2 = availableCols.get(1);
            double eps1 = getEpsilon(col1, true);
            double eps2 = getEpsilon(col2, true);

            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < allRecords.size(); i++) {
                if (i == targetIndex) continue;
                double v1 = allRecords.get(i).odds[col1];
                double v2 = allRecords.get(i).odds[col2];
                if (v1 > 0 && v2 > 0 &&
                    Math.abs(v1 - target.odds[col1]) <= eps1 &&
                    Math.abs(v2 - target.odds[col2]) <= eps2) {
                    pool.add(i);
                }
            }
            if (pool.size() >= 2 && pool.size() <= 5) {
                RunResult rr = new RunResult("Mod5: Kombinatoryal 2'li", pool);
                rr.usedCols = List.of(col1, col2);
                computePrediction(rr);

                System.out.println("\n══════════════════════════════════════════════════════");
                System.out.println(" Mod5: Kombinatoryal Filtre (2'li grup)");
                System.out.println("══════════════════════════════════════════════════════");
                System.out.println("   Seçilen kolonlar: " + ALL_ODDS_COLS.get(col1).displayName + " + " + ALL_ODDS_COLS.get(col2).displayName);
                printMatchTable(pool);
                System.out.printf("   ➡️ Tahmin: %s (Güven: %%%s)\n\n", rr.predictedScore, rr.confidence);
                return rr;
            }
        }
        System.out.println("\nMod5: Kombinatoryal filtre başarılı olamadı.");
        return null;
    }

    // ==================== FİNAL ÖZET ====================
    private void printFinalSummary(List<RunResult> allResults, MatchRecord target) {
        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        🏁 TÜM MODLAR KARŞILAŞTIRMA ÖZETİ                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.printf("Hedef: %s vs %s | Tarih: %s\n", target.homeTeam, target.awayTeam, target.date);
        System.out.printf("Gerçek skor: İY %s | MS %s\n", target.htScore(), target.ftScore());
        System.out.println();

        // Mod bazında grupla
        Map<String, List<RunResult>> grouped = allResults.stream()
                .collect(Collectors.groupingBy(r -> r.modeName, LinkedHashMap::new, Collectors.toList()));

        System.out.println("┌──────────────────────────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐");
        System.out.println("│ Mod Adı                      │ Run/Zncr │  Maç Say │ Tahmin   │ Güven    │ İY %     │ MS %     │");
        System.out.println("├──────────────────────────────┼──────────┼──────────┼──────────┼──────────┼──────────┼──────────┤");

        for (Map.Entry<String, List<RunResult>> entry : grouped.entrySet()) {
            String modName = entry.getKey();
            List<RunResult> runs = entry.getValue();
            for (RunResult rr : runs) {
                String runLabel = (rr.runNumber > 0) ? String.valueOf(rr.runNumber) : "-";
                String iyPct = (target.htHome >= 0) ? (rr.correctIY * 100 / rr.totalMatches) + "%" : "N/A";
                String msPct = (target.ftHome >= 0) ? (rr.correctMS * 100 / rr.totalMatches) + "%" : "N/A";
                System.out.printf("│ %-28s │ %-8s │ %-8d │ %-8s │ %-8.1f │ %-8s │ %-8s │\n",
                        trunc(modName, 28), runLabel, rr.totalMatches,
                        rr.predictedScore, rr.confidence, iyPct, msPct);
            }
        }
        System.out.println("└──────────────────────────────┴──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘");

        // Genel en iyi tahmin
        Map<String, Integer> globalScoreCount = new LinkedHashMap<>();
        for (RunResult rr : allResults) {
            for (int idx : rr.matchedIndices) {
                MatchRecord m = allRecords.get(idx);
                String sc = m.ftScore();
                globalScoreCount.put(sc, globalScoreCount.getOrDefault(sc, 0) + 1);
            }
        }
        String globalBest = globalScoreCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("?-?");
        int globalBestCnt = globalScoreCount.get(globalBest);
        double globalConf = Math.round(globalBestCnt * 1000.0 / allResults.stream().mapToInt(r -> r.totalMatches).sum()) / 10.0;

        System.out.println("\n🔮 TÜM MODLARIN BİRLEŞİK SKOR DAĞILIMI:");
        globalScoreCount.entrySet().stream()
                .sorted((a,b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> System.out.printf("   %-10s : %d kez\n", e.getKey(), e.getValue()));
        System.out.printf("\n🏆 GENEL TAHMİN (En sık çıkan MS): %s (Ağırlıklı Güven: %%%s)\n", globalBest, globalConf);
    }

    private void printTargetInfo(MatchRecord target) {
        System.out.println("\n════════════════════════════════════════════════════════════════");
        System.out.println("🎯 Hedef Maç: " + target.homeTeam + " vs " + target.awayTeam);
        System.out.println("   Tarih: " + target.date + " | Lig: " + target.league);
        System.out.println("   MS: " + target.ftScore() + " | İY: " + target.htScore());
        System.out.println("════════════════════════════════════════════════════════════════\n");
    }

    // ==================== MAIN ====================
    public static void main(String[] args) {
        Bet365FilterFinderSQL analyzer = new Bet365FilterFinderSQL();

        System.out.println("════════════════════════════════════════════════════");
        System.out.println(" TÜM MODLAR OTOMATİK ÇALIŞTIRILIYOR");
        System.out.println("════════════════════════════════════════════════════");
        System.out.println(" Toplam kayıt: " + analyzer.allRecords.size());
        System.out.print(" Hedef satır indeksini girin (0-" + (analyzer.allRecords.size() - 1) + "): ");

        Scanner scanner = new Scanner(System.in);
        int target;
        try {
            target = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            target = 0;
        }

        analyzer.runAllMethods(target);
    }
}