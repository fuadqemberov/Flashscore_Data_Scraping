package analyzer.bet365;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * GENETİK ALGORİTMA İLE TURNUVA — TÜM 10 MAÇ AYNI ANDA OPTİMİZE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  Filtre havuzundaki maçların en az %80'i hedefle aynı kriteri taşırsa
 *  o kriter başarılı sayılır. En az bir kriter %80 tutarsa filtre o maçı geçer.
 *  Fitness = 10 maçta geçilen maç sayısı.
 */
public class Bet365FilterFinderSQL {

    private static final int POP_SIZE       = 2_000;
    private static final int GENERATIONS    = 100;
    private static final int TOURNAMENT_SIZE = 5;
    private static final int TARGET_COUNT   = 10;
    private static final double THRESHOLD   = 0.80; // %80 aynı olma zorunluluğu

    // ─── kolon tanımları ────────────────────────────────────────────────────
    static class ColumnDef {
        final String sqlColumn, displayName;
        ColumnDef(String s, String d) { sqlColumn = s; displayName = d; }
    }

    private static final List<ColumnDef> ALL_ODDS_COLS = List.of(
            new ColumnDef("ft_1_a","MS 1"), new ColumnDef("ft_x_a","MS X"), new ColumnDef("ft_2_a","MS 2"),
            new ColumnDef("first_1_a","İY 1"), new ColumnDef("first_x_a","İY X"), new ColumnDef("first_2_a","İY 2"),
            new ColumnDef("second_1_a","2Y 1"), new ColumnDef("second_x_a","2Y X"), new ColumnDef("second_2_a","2Y 2"),
            new ColumnDef("bts_ft_yes_a","KG Evet"), new ColumnDef("bts_ft_no_a","KG Hayır"),
            new ColumnDef("bts_first_yes_a","İY KG Evet"), new ColumnDef("bts_first_no_a","İY KG Hayır"),
            new ColumnDef("bts_second_yes_a","2Y KG Evet"), new ColumnDef("bts_second_no_a","2Y KG Hayır"),
            new ColumnDef("dbc_ft_1x_a","ÇŞ 1X"), new ColumnDef("dbc_ft_12_a","ÇŞ 12"), new ColumnDef("dbc_ft_x2_a","ÇŞ X2"),
            new ColumnDef("dbc_first_1x_a","İY ÇŞ 1X"), new ColumnDef("dbc_first_12_a","İY ÇŞ 12"), new ColumnDef("dbc_first_x2_a","İY ÇŞ X2"),
            new ColumnDef("ft_0_5_over_a","A/U 0.5 Üst"), new ColumnDef("ft_0_5_under_a","A/U 0.5 Alt"),
            new ColumnDef("ft_1_5_over_a","A/U 1.5 Üst"), new ColumnDef("ft_1_5_under_a","A/U 1.5 Alt"),
            new ColumnDef("ft_2_5_over_a","A/U 2.5 Üst"), new ColumnDef("ft_2_5_under_a","A/U 2.5 Alt"),
            new ColumnDef("ft_3_5_over_a","A/U 3.5 Üst"), new ColumnDef("ft_3_5_under_a","A/U 3.5 Alt"),
            new ColumnDef("ft_4_5_over_a","A/U 4.5 Üst"), new ColumnDef("ft_4_5_under_a","A/U 4.5 Alt"),
            new ColumnDef("ft_5_5_over_a","A/U 5.5 Üst"), new ColumnDef("ft_5_5_under_a","A/U 5.5 Alt"),
            new ColumnDef("first_0_5_over_a","İY A/U 0.5 Üst"), new ColumnDef("first_0_5_under_a","İY A/U 0.5 Alt"),
            new ColumnDef("first_1_5_over_a","İY A/U 1.5 Üst"), new ColumnDef("first_1_5_under_a","İY A/U 1.5 Alt"),
            new ColumnDef("first_2_5_over_a","İY A/U 2.5 Üst"), new ColumnDef("first_2_5_under_a","İY A/U 2.5 Alt"),
            new ColumnDef("second_0_5_over_a","2Y A/U 0.5 Üst"), new ColumnDef("second_0_5_under_a","2Y A/U 0.5 Alt"),
            new ColumnDef("second_1_5_over_a","2Y A/U 1.5 Üst"), new ColumnDef("second_1_5_under_a","2Y A/U 1.5 Alt"),
            new ColumnDef("second_2_5_over_a","2Y A/U 2.5 Üst"), new ColumnDef("second_2_5_under_a","2Y A/U 2.5 Alt"),
            new ColumnDef("ht_ft_11_a","HT/FT 1/1"), new ColumnDef("ht_ft_1x_a","HT/FT 1/X"), new ColumnDef("ht_ft_12_a","HT/FT 1/2"),
            new ColumnDef("ht_ft_x1_a","HT/FT X/1"), new ColumnDef("ht_ft_xx_a","HT/FT X/X"), new ColumnDef("ht_ft_x2_a","HT/FT X/2"),
            new ColumnDef("ht_ft_21_a","HT/FT 2/1"), new ColumnDef("ht_ft_2x_a","HT/FT 2/X"), new ColumnDef("ht_ft_22_a","HT/FT 2/2"),
            new ColumnDef("first_score_1_0_a","İY Skor 1:0"), new ColumnDef("first_score_2_0_a","İY Skor 2:0"),
            new ColumnDef("first_score_2_1_a","İY Skor 2:1"), new ColumnDef("first_score_3_0_a","İY Skor 3:0"),
            new ColumnDef("first_score_3_1_a","İY Skor 3:1"), new ColumnDef("first_score_3_2_a","İY Skor 3:2"),
            new ColumnDef("first_score_0_0_a","İY Skor 0:0"), new ColumnDef("first_score_1_1_a","İY Skor 1:1"),
            new ColumnDef("first_score_2_2_a","İY Skor 2:2"), new ColumnDef("first_score_0_1_a","İY Skor 0:1"),
            new ColumnDef("first_score_0_2_a","İY Skor 0:2"), new ColumnDef("first_score_1_2_a","İY Skor 1:2"),
            new ColumnDef("first_score_0_3_a","İY Skor 0:3"), new ColumnDef("first_score_1_3_a","İY Skor 1:3"),
            new ColumnDef("first_score_2_3_a","İY Skor 2:3"),
            new ColumnDef("ft_score_1_0_a","MS Skor 1:0"), new ColumnDef("ft_score_2_0_a","MS Skor 2:0"),
            new ColumnDef("ft_score_2_1_a","MS Skor 2:1"), new ColumnDef("ft_score_3_0_a","MS Skor 3:0"),
            new ColumnDef("ft_score_3_1_a","MS Skor 3:1"), new ColumnDef("ft_score_3_2_a","MS Skor 3:2"),
            new ColumnDef("ft_score_4_0_a","MS Skor 4:0"), new ColumnDef("ft_score_4_1_a","MS Skor 4:1"),
            new ColumnDef("ft_score_4_2_a","MS Skor 4:2"), new ColumnDef("ft_score_4_3_a","MS Skor 4:3"),
            new ColumnDef("ft_score_5_0_a","MS Skor 5:0"), new ColumnDef("ft_score_5_1_a","MS Skor 5:1"),
            new ColumnDef("ft_score_5_2_a","MS Skor 5:2"), new ColumnDef("ft_score_0_0_a","MS Skor 0:0"),
            new ColumnDef("ft_score_1_1_a","MS Skor 1:1"), new ColumnDef("ft_score_2_2_a","MS Skor 2:2"),
            new ColumnDef("ft_score_3_3_a","MS Skor 3:3"), new ColumnDef("ft_score_4_4_a","MS Skor 4:4"),
            new ColumnDef("ft_score_0_1_a","MS Skor 0:1"), new ColumnDef("ft_score_0_2_a","MS Skor 0:2"),
            new ColumnDef("ft_score_1_2_a","MS Skor 1:2"), new ColumnDef("ft_score_0_3_a","MS Skor 0:3"),
            new ColumnDef("ft_score_1_3_a","MS Skor 1:3"), new ColumnDef("ft_score_2_3_a","MS Skor 2:3"),
            new ColumnDef("ft_score_0_4_a","MS Skor 0:4"), new ColumnDef("ft_score_1_4_a","MS Skor 1:4"),
            new ColumnDef("ft_score_2_4_a","MS Skor 2:4"), new ColumnDef("ft_score_3_4_a","MS Skor 3:4"),
            new ColumnDef("ft_score_0_5_a","MS Skor 0:5"), new ColumnDef("ft_score_1_5_a","MS Skor 1:5"),
            new ColumnDef("ft_score_2_5_a","MS Skor 2:5")
    );

    // ─── MatchRecord ──────────────────────────────────────────────────────
    static class MatchRecord {
        String league, date, homeTeam, awayTeam, id;
        int htHome, htAway, ftHome, ftAway;
        double[] odds;
        MatchRecord(int size) { odds = new double[size]; }
        String ftScore()  { return (ftHome >= 0) ? ftHome + "-" + ftAway : "?-?"; }
        String htScore()  { return (htHome >= 0) ? htHome + "-" + htAway : "?-?"; }
        String htFtLabel() {
            if (htHome < 0 || htAway < 0 || ftHome < 0 || ftAway < 0) return "?/?";
            String ht = (htHome > htAway) ? "1" : (htHome < htAway ? "2" : "X");
            String ft = (ftHome > ftAway) ? "1" : (ftHome < ftAway ? "2" : "X");
            return ht + "/" + ft;
        }
        String ftSide() {
            if (ftHome < 0 || ftAway < 0) return "?";
            return (ftHome > ftAway) ? "1" : (ftHome < ftAway ? "2" : "X");
        }
        @Override public String toString() {
            return homeTeam + " vs " + awayTeam + " (" + (date != null ? date : "?") + ")";
        }
        String fullDetail() {
            return toString() + " | İY:" + htScore() + " MS:" + ftScore()
                    + " [HT/FT:" + htFtLabel() + "]" + " [MS:" + ftSide() + "]";
        }
    }

    // ─── Case enum ────────────────────────────────────────────────────────
    enum Case { HTFT, CORRECT_SCORE, MS_SIDE }

    // ─── Filter ──────────────────────────────────────────────────────────
    static class Filter {
        final int[] cols;
        Filter(int[] cols) { this.cols = cols; }

        // Belirli bir maç için havuz (max 20 maça izin veriyoruz, threshold için)
        List<Integer> getPoolLarge(MatchRecord target, List<MatchRecord> all) {
            List<Integer> pool = new ArrayList<>();
            double[] tOdds = target.odds;
            for (int i = 0; i < all.size(); i++) {
                MatchRecord r = all.get(i);
                if (r == target) continue;
                boolean ok = true;
                for (int col : cols) {
                    if (r.odds[col] != tOdds[col]) { ok = false; break; }
                }
                if (ok) {
                    pool.add(i);
                    if (pool.size() > 20) break; // Büyük havuzda tümünü saymak yavaşlatır, 20 yeter
                }
            }
            return pool;
        }

        // Esnek başarı kontrolü: Her kriter için havuzdaki maçların en az %80'i aynı değeri taşıyorsa o kriter geçerli
        EnumSet<Case> flexibleSuccess(MatchRecord target, List<MatchRecord> all) {
            List<Integer> pool = getPoolLarge(target, all);
            if (pool.isEmpty()) return EnumSet.noneOf(Case.class);

            String tHtFt    = target.htFtLabel();
            String tFtScore = target.ftScore();
            String tMsSide  = target.ftSide();

            int htftMatch = 0, csMatch = 0, msMatch = 0;
            int total = pool.size();
            for (int idx : pool) {
                MatchRecord m = all.get(idx);
                if (m.htFtLabel().equals(tHtFt)) htftMatch++;
                if (m.ftScore().equals(tFtScore)) csMatch++;
                if (m.ftSide().equals(tMsSide))   msMatch++;
            }
            double htftRatio = (double) htftMatch / total;
            double csRatio   = (double) csMatch   / total;
            double msRatio   = (double) msMatch   / total;

            EnumSet<Case> result = EnumSet.noneOf(Case.class);
            if (htftRatio >= THRESHOLD) result.add(Case.HTFT);
            if (csRatio   >= THRESHOLD) result.add(Case.CORRECT_SCORE);
            if (msRatio   >= THRESHOLD) result.add(Case.MS_SIDE);
            return result;
        }

        String getColumnNames() {
            StringJoiner sj = new StringJoiner(", ");
            for (int c : cols) sj.add("\"" + ALL_ODDS_COLS.get(c).displayName + "\"");
            return sj.toString();
        }
    }

    // ─── Akıllı filtre üretici (frekans + altın kolon) ──────────────────
    private Map<Integer, Map<Double, Integer>> colValueFreq;
    private static final Map<String, List<Integer>> GOLDEN_BY_SIDE = new HashMap<>();
    private static final Map<String, List<Integer>> GOLDEN_BY_HTFT = new HashMap<>();
    static {
        GOLDEN_BY_SIDE.put("1", List.of(
                indexOf("ft_1_a"), indexOf("first_1_a"), indexOf("second_1_a"),
                indexOf("ht_ft_11_a"), indexOf("ht_ft_x1_a"), indexOf("ht_ft_21_a"),
                indexOf("ft_score_1_0_a"), indexOf("ft_score_2_0_a"), indexOf("ft_score_2_1_a"),
                indexOf("ft_score_3_0_a"), indexOf("ft_score_3_1_a"), indexOf("ft_score_3_2_a"),
                indexOf("ft_score_4_0_a"), indexOf("ft_score_4_1_a"), indexOf("ft_score_4_2_a"),
                indexOf("ft_score_5_0_a"), indexOf("ft_score_5_1_a"), indexOf("ft_score_5_2_a")));
        GOLDEN_BY_SIDE.put("X", List.of(
                indexOf("ft_x_a"), indexOf("first_x_a"), indexOf("second_x_a"),
                indexOf("ht_ft_xx_a"), indexOf("ht_ft_1x_a"), indexOf("ht_ft_2x_a"),
                indexOf("ft_score_0_0_a"), indexOf("ft_score_1_1_a"), indexOf("ft_score_2_2_a"),
                indexOf("ft_score_3_3_a"), indexOf("ft_score_4_4_a")));
        GOLDEN_BY_SIDE.put("2", List.of(
                indexOf("ft_2_a"), indexOf("first_2_a"), indexOf("second_2_a"),
                indexOf("ht_ft_22_a"), indexOf("ht_ft_12_a"), indexOf("ht_ft_x2_a"),
                indexOf("ft_score_0_1_a"), indexOf("ft_score_0_2_a"), indexOf("ft_score_1_2_a"),
                indexOf("ft_score_0_3_a"), indexOf("ft_score_1_3_a"), indexOf("ft_score_2_3_a"),
                indexOf("ft_score_0_4_a"), indexOf("ft_score_1_4_a"), indexOf("ft_score_2_4_a"),
                indexOf("ft_score_0_5_a"), indexOf("ft_score_1_5_a"), indexOf("ft_score_2_5_a")));
        for (String htft : List.of("1/1","1/X","1/2","X/1","X/X","X/2","2/1","2/X","2/2")) {
            String colName = "ht_ft_" + htft.replace("/", "").toLowerCase() + "_a";
            int idx = indexOf(colName);
            if (idx >= 0) GOLDEN_BY_HTFT.put(htft, List.of(idx));
        }
    }

    private static int indexOf(String sqlColumn) {
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++)
            if (ALL_ODDS_COLS.get(i).sqlColumn.equals(sqlColumn)) return i;
        return -1;
    }

    private void buildFrequencyMap() {
        colValueFreq = new HashMap<>();
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++) colValueFreq.put(i, new HashMap<>());
        for (MatchRecord m : allRecords) {
            for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
                double val = m.odds[i];
                if (val > 0.0) colValueFreq.get(i).merge(val, 1, Integer::sum);
            }
        }
    }

    private List<Integer> weightedRandomSelect(List<Integer> available, double[] targetOdds, Random rng, int count) {
        List<Integer> result = new ArrayList<>();
        List<Integer> pool = new ArrayList<>(available);
        for (int k = 0; k < count && !pool.isEmpty(); k++) {
            double totalWeight = 0.0;
            double[] weights = new double[pool.size()];
            for (int j = 0; j < pool.size(); j++) {
                int col = pool.get(j);
                double val = targetOdds[col];
                int freq = colValueFreq.get(col).getOrDefault(val, 1);
                weights[j] = 1.0 / (freq + 1);
                totalWeight += weights[j];
            }
            if (totalWeight == 0) break;
            double r = rng.nextDouble() * totalWeight;
            double cum = 0.0;
            int chosenIdx = 0;
            for (int j = 0; j < pool.size(); j++) {
                cum += weights[j];
                if (r <= cum) { chosenIdx = j; break; }
            }
            result.add(pool.remove(chosenIdx));
        }
        return result;
    }

    private List<Integer> collectGoldenColumns(MatchRecord target) {
        Set<Integer> golden = new LinkedHashSet<>();
        String side = target.ftSide();
        if (GOLDEN_BY_SIDE.containsKey(side)) golden.addAll(GOLDEN_BY_SIDE.get(side));
        String htft = target.htFtLabel();
        if (GOLDEN_BY_HTFT.containsKey(htft)) golden.addAll(GOLDEN_BY_HTFT.get(htft));
        // CS altın kolonu da ekleyelim (ör: "2-1")
        String colName = "ft_score_" + target.ftScore().replace("-", "_") + "_a";
        int csIdx = indexOf(colName);
        if (csIdx >= 0) golden.add(csIdx);
        golden.removeIf(col -> target.odds[col] <= 0.0);
        return new ArrayList<>(golden);
    }

    private Filter generateRandomFilter(MatchRecord target, Random rng) {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < ALL_ODDS_COLS.size(); i++)
            if (target.odds[i] > 0.0) available.add(i);
        if (available.isEmpty()) return new Filter(new int[0]);

        List<Integer> golden = collectGoldenColumns(target);
        int goldenCount = Math.min(golden.size(), 2);
        if (goldenCount > 0) goldenCount = 1 + rng.nextInt(goldenCount);
        int totalSize = 1 + rng.nextInt(5);
        if (goldenCount > totalSize) goldenCount = totalSize;

        Set<Integer> chosenSet = new LinkedHashSet<>();
        if (goldenCount > 0) {
            List<Integer> goldenCopy = new ArrayList<>(golden);
            Collections.shuffle(goldenCopy, rng);
            for (int i = 0; i < goldenCount; i++) chosenSet.add(goldenCopy.get(i));
        }
        int remaining = totalSize - chosenSet.size();
        if (remaining > 0) {
            List<Integer> remainingAvailable = new ArrayList<>(available);
            remainingAvailable.removeAll(chosenSet);
            if (!remainingAvailable.isEmpty()) {
                chosenSet.addAll(weightedRandomSelect(remainingAvailable, target.odds, rng, remaining));
            }
        }
        return new Filter(chosenSet.stream().mapToInt(Integer::intValue).toArray());
    }

    // ─── GA bileşenleri ──────────────────────────────────────────────────
    private List<MatchRecord> targets;

    // Fitness: 10 maçta geçilen maç sayısı (esnek kriter ile)
    private int fitness(Filter f) {
        int score = 0;
        for (MatchRecord m : targets) {
            if (!f.flexibleSuccess(m, allRecords).isEmpty()) score++;
        }
        return score;
    }

    // Turnuva seçilimi
    private Filter tournamentSelect(List<Filter> pop, int[] fitnesses, Random rng) {
        Filter best = null;
        int bestFit = -1;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            int idx = rng.nextInt(pop.size());
            if (fitnesses[idx] > bestFit) {
                bestFit = fitnesses[idx];
                best = pop.get(idx);
            }
        }
        return best;
    }

    // Çaprazlama (tek nokta)
    private Filter crossover(Filter p1, Filter p2, Random rng) {
        Set<Integer> set = new LinkedHashSet<>();
        for (int col : p1.cols) set.add(col);
        for (int col : p2.cols) set.add(col);
        // En az 1, en çok 5 kolon seç
        List<Integer> list = new ArrayList<>(set);
        int size = Math.min(5, Math.max(1, rng.nextInt(set.size()) + 1));
        Collections.shuffle(list, rng);
        return new Filter(list.subList(0, size).stream().mapToInt(Integer::intValue).toArray());
    }

    // Mutasyon (bir kolonu frekans ağırlıklı değiştir)
    private Filter mutate(Filter f, MatchRecord refTarget, Random rng) {
        List<Integer> current = new ArrayList<>();
        for (int c : f.cols) current.add(c);
        if (current.isEmpty() || rng.nextDouble() < 0.2) {
            // Yeni bir kolon ekle
            List<Integer> available = new ArrayList<>();
            for (int i = 0; i < ALL_ODDS_COLS.size(); i++)
                if (refTarget.odds[i] > 0.0) available.add(i);
            available.removeAll(current);
            if (!available.isEmpty()) {
                int newCol = weightedRandomSelect(available, refTarget.odds, rng, 1).get(0);
                current.add(newCol);
            }
        } else {
            // Bir kolonu değiştir
            int idx = rng.nextInt(current.size());
            List<Integer> available = new ArrayList<>();
            for (int i = 0; i < ALL_ODDS_COLS.size(); i++)
                if (refTarget.odds[i] > 0.0) available.add(i);
            available.removeAll(current);
            if (!available.isEmpty()) {
                int newCol = weightedRandomSelect(available, refTarget.odds, rng, 1).get(0);
                current.set(idx, newCol);
            }
        }
        if (current.size() > 5) current = current.subList(0, 5);
        return new Filter(current.stream().mapToInt(Integer::intValue).toArray());
    }

    // ─── Veritabanı işlemleri ────────────────────────────────────────────
    private Connection        conn;
    private List<MatchRecord> allRecords = new ArrayList<>();

    public Bet365FilterFinderSQL() {
        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/postgres", "postgres", "fuad123");
            System.out.println("✅ Veritabanına bağlanıldı.");
            loadAllRecords();
            buildFrequencyMap();
            System.out.println("✅ Frekans haritası oluşturuldu.");
        } catch (Exception e) {
            System.err.println("❌ " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void loadAllRecords() throws SQLException {
        System.out.println("📊 Veriler yükleniyor...");
        StringBuilder sb = new StringBuilder(
                "SELECT country_league,date_time,home_team,away_team,ht_iy,ft_ms,id");
        for (ColumnDef cd : ALL_ODDS_COLS) sb.append(",").append(cd.sqlColumn);
        sb.append(" FROM bet365_matches ORDER BY date_time DESC");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sb.toString())) {
            while (rs.next()) {
                MatchRecord rec = new MatchRecord(ALL_ODDS_COLS.size());
                rec.league   = rs.getString("country_league");
                rec.date     = rs.getString("date_time");
                rec.homeTeam = rs.getString("home_team");
                rec.awayTeam = rs.getString("away_team");
                rec.id       = rs.getString("id");
                rec.htHome = rec.htAway = rec.ftHome = rec.ftAway = -1;
                String ht = rs.getString("ht_iy");
                if (ht != null && ht.contains("-")) {
                    String[] p = ht.split("-", 2);
                    try { rec.htHome = Integer.parseInt(p[0].trim()); rec.htAway = Integer.parseInt(p[1].trim()); }
                    catch (Exception ignored) {}
                }
                String ft = rs.getString("ft_ms");
                if (ft != null && ft.contains("-")) {
                    String[] p = ft.split("-", 2);
                    try { rec.ftHome = Integer.parseInt(p[0].trim()); rec.ftAway = Integer.parseInt(p[1].trim()); }
                    catch (Exception ignored) {}
                }
                for (int i = 0; i < ALL_ODDS_COLS.size(); i++) {
                    rec.odds[i] = parseOdds(rs.getString(ALL_ODDS_COLS.get(i).sqlColumn));
                }
                allRecords.add(rec);
            }
        }
        System.out.println("✅ " + allRecords.size() + " kayıt yüklendi.\n");
    }

    private double parseOdds(String s) {
        if (s == null || s.isEmpty() || s.equals("-")) return 0.0;
        try { return Double.parseDouble(s.replace(',', '.')); }
        catch (Exception ignored) { return 0.0; }
    }

    // ─── ANA GENETİK ALGORİTMA ───────────────────────────────────────────
    public void startFilterHunt() {
        List<MatchRecord> valid = allRecords.stream()
                .filter(m -> m.ftHome >= 0 && m.ftAway >= 0 && m.htHome >= 0 && m.htAway >= 0)
                .collect(Collectors.toList());
        if (valid.size() < TARGET_COUNT) { System.out.println("❌ Yeterli maç yok."); return; }
        Collections.shuffle(valid, new Random(42));
        targets = valid.subList(0, TARGET_COUNT);

        System.out.println("🏆 GENETİK ALGORİTMA TURNUVASI — " + TARGET_COUNT + " MAÇ");
        System.out.println("=".repeat(70));
        for (int i = 0; i < TARGET_COUNT; i++)
            System.out.printf(" MAÇ %2d: %s%n", i + 1, targets.get(i).fullDetail());
        System.out.println("=".repeat(70));

        // Başlangıç popülasyonu (ilk maça göre üretilmiş)
        List<Filter> population = new ArrayList<>();
        MatchRecord first = targets.get(0);
        Random rng = new Random(123);
        while (population.size() < POP_SIZE) {
            Filter f = generateRandomFilter(first, rng);
            if (f.cols.length > 0) population.add(f);
        }

        int[] fitnesses = new int[POP_SIZE];
        int bestEver = 0;
        Filter bestFilterEver = null;

        long t0 = System.currentTimeMillis();
        for (int gen = 0; gen < GENERATIONS; gen++) {
            // Fitness hesapla (paralel)
            List<Filter> finalPopulation = population;
            IntStream.range(0, population.size()).parallel().forEach(i -> {
                fitnesses[i] = fitness(finalPopulation.get(i));
            });

            int maxFit = Arrays.stream(fitnesses).max().orElse(0);
            if (maxFit > bestEver) {
                bestEver = maxFit;
                int idx = IntStream.range(0, fitnesses.length).filter(i -> fitnesses[i] == maxFit).findFirst().orElse(0);
                bestFilterEver = population.get(idx);
            }

            if (gen % 10 == 0) {
                System.out.printf("Nesil %3d: En iyi = %d/%d, Ortalama = %.1f%n",
                        gen, maxFit, TARGET_COUNT, Arrays.stream(fitnesses).average().orElse(0));
            }

            // Yeni nesil
            List<Filter> newPop = new ArrayList<>();
            while (newPop.size() < POP_SIZE) {
                Filter p1 = tournamentSelect(population, fitnesses, rng);
                Filter p2 = tournamentSelect(population, fitnesses, rng);
                Filter child = crossover(p1, p2, rng);
                if (rng.nextDouble() < 0.3) child = mutate(child, first, rng);
                newPop.add(child);
            }
            population = newPop;
        }

        System.out.printf("⏱️ Süre: %d ms%n", System.currentTimeMillis() - t0);
        System.out.println("En iyi filtre skoru: " + bestEver + "/" + TARGET_COUNT);
        if (bestFilterEver != null) {
            System.out.println("Kolonlar: " + bestFilterEver.getColumnNames());
            // Detaylı maç raporu
            for (int i = 0; i < TARGET_COUNT; i++) {
                MatchRecord m = targets.get(i);
                EnumSet<Case> cs = bestFilterEver.flexibleSuccess(m, allRecords);
                System.out.printf("MAÇ %d: %s → %s%n", i+1, m.fullDetail(), cs.isEmpty() ? "❌" : "✅ " + cs);
            }
        }
    }

    public static void main(String[] args) {
        new Bet365FilterFinderSQL().startFilterHunt();
    }
}