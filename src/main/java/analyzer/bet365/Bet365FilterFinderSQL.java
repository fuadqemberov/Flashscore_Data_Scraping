package analyzer.bet365;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

public class Bet365FilterFinderSQL {

    static class ColumnDef {
        String sqlColumn, displayName;
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
            new ColumnDef("first_score_1_0_a","İY Skor 1:0"), new ColumnDef("first_score_2_0_a","İY Skor 2:0"), new ColumnDef("first_score_2_1_a","İY Skor 2:1"),
            new ColumnDef("first_score_3_0_a","İY Skor 3:0"), new ColumnDef("first_score_3_1_a","İY Skor 3:1"), new ColumnDef("first_score_3_2_a","İY Skor 3:2"),
            new ColumnDef("first_score_0_0_a","İY Skor 0:0"), new ColumnDef("first_score_1_1_a","İY Skor 1:1"), new ColumnDef("first_score_2_2_a","İY Skor 2:2"),
            new ColumnDef("first_score_0_1_a","İY Skor 0:1"), new ColumnDef("first_score_0_2_a","İY Skor 0:2"), new ColumnDef("first_score_1_2_a","İY Skor 1:2"),
            new ColumnDef("first_score_0_3_a","İY Skor 0:3"), new ColumnDef("first_score_1_3_a","İY Skor 1:3"), new ColumnDef("first_score_2_3_a","İY Skor 2:3"),
            new ColumnDef("ft_score_1_0_a","MS Skor 1:0"), new ColumnDef("ft_score_2_0_a","MS Skor 2:0"), new ColumnDef("ft_score_2_1_a","MS Skor 2:1"),
            new ColumnDef("ft_score_3_0_a","MS Skor 3:0"), new ColumnDef("ft_score_3_1_a","MS Skor 3:1"), new ColumnDef("ft_score_3_2_a","MS Skor 3:2"),
            new ColumnDef("ft_score_4_0_a","MS Skor 4:0"), new ColumnDef("ft_score_4_1_a","MS Skor 4:1"), new ColumnDef("ft_score_4_2_a","MS Skor 4:2"),
            new ColumnDef("ft_score_4_3_a","MS Skor 4:3"), new ColumnDef("ft_score_5_0_a","MS Skor 5:0"), new ColumnDef("ft_score_5_1_a","MS Skor 5:1"),
            new ColumnDef("ft_score_5_2_a","MS Skor 5:2"), new ColumnDef("ft_score_0_0_a","MS Skor 0:0"), new ColumnDef("ft_score_1_1_a","MS Skor 1:1"),
            new ColumnDef("ft_score_2_2_a","MS Skor 2:2"), new ColumnDef("ft_score_3_3_a","MS Skor 3:3"), new ColumnDef("ft_score_4_4_a","MS Skor 4:4"),
            new ColumnDef("ft_score_0_1_a","MS Skor 0:1"), new ColumnDef("ft_score_0_2_a","MS Skor 0:2"), new ColumnDef("ft_score_1_2_a","MS Skor 1:2"),
            new ColumnDef("ft_score_0_3_a","MS Skor 0:3"), new ColumnDef("ft_score_1_3_a","MS Skor 1:3"), new ColumnDef("ft_score_2_3_a","MS Skor 2:3"),
            new ColumnDef("ft_score_0_4_a","MS Skor 0:4"), new ColumnDef("ft_score_1_4_a","MS Skor 1:4"), new ColumnDef("ft_score_2_4_a","MS Skor 2:4"),
            new ColumnDef("ft_score_3_4_a","MS Skor 3:4"), new ColumnDef("ft_score_0_5_a","MS Skor 0:5"), new ColumnDef("ft_score_1_5_a","MS Skor 1:5"),
            new ColumnDef("ft_score_2_5_a","MS Skor 2:5")
    );

    static class MatchRecord {
        String league, date, homeTeam, awayTeam, id;
        int htHome, htAway, ftHome, ftAway;
        double[] odds;
        MatchRecord(int size) { odds = new double[size]; }
        String ftScore() { return (ftHome>=0) ? ftHome+"-"+ftAway : "?-?"; }
        String htScore() { return (htHome>=0) ? htHome+"-"+htAway : "?-?"; }
        @Override
        public String toString() {
            return homeTeam+" vs "+awayTeam+" ("+(date!=null?date:"tarih yok")+")";
        }
        String fullDetail() {
            return toString() + " | İY:"+htScore()+" MS:"+ftScore();
        }
    }

    static class Filter {
        int[] cols;
        Filter(List<Integer> c) { cols = c.stream().mapToInt(i->i).toArray(); }

        List<Integer> getPool(MatchRecord target, List<MatchRecord> all) {
            List<Integer> pool = new ArrayList<>();
            double[] tOdds = target.odds;
            for (int i=0; i<all.size(); i++) {
                MatchRecord r = all.get(i);
                if (r == target) continue;
                boolean ok = true;
                for (int col : cols) {
                    if (r.odds[col] != tOdds[col]) { ok = false; break; }
                }
                if (ok) pool.add(i);
            }
            return pool;
        }

        boolean isSuccessfulFor(MatchRecord target, List<MatchRecord> all) {
            List<Integer> pool = getPool(target, all);
            if (pool.size() < 1 || pool.size() > 3) return false;
            for (int idx : pool) {
                if (matchesAnyOutcome(target, all.get(idx))) return true;
            }
            return false;
        }

        private boolean matchesAnyOutcome(MatchRecord a, MatchRecord b) {
            if (a.htHome>=0 && a.htAway>=0 && a.ftHome>=0 && a.ftAway>=0 &&
                b.htHome>=0 && b.htAway>=0 && b.ftHome>=0 && b.ftAway>=0) {
                if (htFt(a).equals(htFt(b))) return true;
            }
            if (a.ftHome>=0 && a.ftAway>=0 && b.ftHome>=0 && b.ftAway>=0) {
                if (a.ftScore().equals(b.ftScore())) return true;
                if (result(a).equals(result(b))) return true;
            }
            return false;
        }

        private static String htFt(MatchRecord m) {
            String ht = (m.htHome>m.htAway)?"1":(m.htHome==m.htAway?"X":"2");
            String ft = (m.ftHome>m.ftAway)?"1":(m.ftHome==m.ftAway?"X":"2");
            return ht+"/"+ft;
        }
        private static String result(MatchRecord m) {
            return (m.ftHome>m.ftAway)?"1":(m.ftHome==m.ftAway?"X":"2");
        }

        public String getColumnNames() {
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<cols.length; i++) {
                if (i>0) sb.append(", ");
                sb.append("\"").append(ALL_ODDS_COLS.get(cols[i]).displayName).append("\"");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return "Filtre [ " + getColumnNames() + " ]";
        }
    }

    private Connection conn;
    private List<MatchRecord> allRecords = new ArrayList<>();

    public Bet365FilterFinderSQL() {
        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres","postgres","fuad123");
            System.out.println("✅ Veritabanına bağlanıldı.");
            loadAllRecords();
        } catch (Exception e) {
            System.err.println("❌ "+e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }

    private void loadAllRecords() throws SQLException {
        System.out.println("📊 Veriler yükleniyor...");
        StringBuilder sb = new StringBuilder("SELECT country_league,date_time,home_team,away_team,ht_iy,ft_ms,id");
        for (ColumnDef cd : ALL_ODDS_COLS) sb.append(",").append(cd.sqlColumn);
        sb.append(" FROM bet365_matches ORDER BY date_time DESC");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sb.toString())) {
            while (rs.next()) {
                MatchRecord rec = new MatchRecord(ALL_ODDS_COLS.size());
                rec.league = rs.getString("country_league");
                rec.date = rs.getString("date_time");
                rec.homeTeam = rs.getString("home_team");
                rec.awayTeam = rs.getString("away_team");
                rec.id = rs.getString("id");
                rec.htHome=rec.htAway=rec.ftHome=rec.ftAway=-1;
                String ht = rs.getString("ht_iy");
                if (ht!=null && ht.contains("-")) {
                    String[] p = ht.split("-");
                    if (p.length==2) try { rec.htHome=Integer.parseInt(p[0].trim()); rec.htAway=Integer.parseInt(p[1].trim()); } catch(Exception _) {}
                }
                String ft = rs.getString("ft_ms");
                if (ft!=null && ft.contains("-")) {
                    String[] p = ft.split("-");
                    if (p.length==2) try { rec.ftHome=Integer.parseInt(p[0].trim()); rec.ftAway=Integer.parseInt(p[1].trim()); } catch(Exception _) {}
                }
                for (int i=0; i<ALL_ODDS_COLS.size(); i++) {
                    String v = rs.getString(ALL_ODDS_COLS.get(i).sqlColumn);
                    rec.odds[i] = parseOdds(v);
                }
                allRecords.add(rec);
            }
        }
        System.out.println("✅ "+allRecords.size()+" kayıt yüklendi.\n");
    }

    private double parseOdds(String s) {
        if (s==null || s.isEmpty() || s.equals("-")) return 0.0;
        try { return Double.parseDouble(s.replace(',','.')); } catch (Exception _) { return 0.0; }
    }

    // ==================== ANA AV ====================
    // ==================== ANA AV ====================
    public void startFilterHunt() {
        List<MatchRecord> valid = allRecords.stream().filter(m->m.ftHome>=0&&m.ftAway>=0).collect(Collectors.toList());
        if (valid.size()<10) { System.out.println("❌ Yetersiz maç."); return; }
        Collections.shuffle(valid, new Random(42));
        List<MatchRecord> targets = valid.subList(0,10);

        System.out.println("🔍 EVRENSEL ALTIN FİLTRE AVI (Birebir eşleşme, Virtual Threads)");
        for (int i=0; i<targets.size(); i++) {
            System.out.printf(" %d. %s%n", i+1, targets.get(i).fullDetail());
        }

        MatchRecord first = targets.get(0);
        System.out.println("\n🎯 1. hedef için 100.000 filtre üretiliyor...");
        ConcurrentLinkedQueue<Filter> good = new ConcurrentLinkedQueue<>();
        AtomicInteger success = new AtomicInteger(), done = new AtomicInteger();
        int total = 100_000;

        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i=0; i<total; i++) {
                ex.submit(() -> {
                    Filter f = generateRandomFilter(first, ThreadLocalRandom.current());
                    if (f.isSuccessfulFor(first, allRecords)) {
                        good.add(f); success.incrementAndGet();
                    }
                    int d = done.incrementAndGet();
                    if (d%5000==0) System.out.printf("   ... %d/%d (başarılı: %d)%n", d, total, success.get());
                });
            }
        }

        System.out.println("👉 İlk tur başarılı: "+good.size());
        if (good.isEmpty()) { System.out.println("❌ İlk turda başarısız."); return; }

        // İlk hedefte çok sayıda varsa detay yazdırma
        if (good.size() <= 20) {
            System.out.println("   Detaylar:");
            printFiltersWithPool(good.stream().collect(Collectors.toList()), first);
        }

        List<Filter> survivors = new ArrayList<>(good);
        for (int t=1; t<targets.size(); t++) {
            MatchRecord target = targets.get(t);
            System.out.printf("%n🎯 %d. hedef: %s - %d filtre test ediliyor...%n", t+1, target.fullDetail(), survivors.size());
            List<Filter> next = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger tested = new AtomicInteger();
            int tot = survivors.size();
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Filter f : survivors) {
                    ex.submit(() -> {
                        if (f.isSuccessfulFor(target, allRecords)) next.add(f);
                        int tst = tested.incrementAndGet();
                        if (tst%100==0) System.out.printf("   ... %d/%d (geçen: %d)%n", tst, tot, next.size());
                    });
                }
            }
            System.out.printf("👉 Kalan filtre: %d%n", next.size());
            if (!next.isEmpty() && next.size() <= 20) {
                System.out.println("   Kalan filtreler ve bu hedefteki eşleşmeleri:");
                printFiltersWithPool(new ArrayList<>(next), target);
            }
            survivors = new ArrayList<>(next);
            if (survivors.isEmpty()) {
                System.out.println("❌ Hiçbiri geçemedi. Av sonlandı.");
                break;
            }
            if (survivors.size() <= 2) {
                System.out.println("✨ Az sayıda filtre kaldı, av sonlandırılıyor.");
                break;
            }
        }

        // Final özet
        System.out.println("\n🏁 AV TAMAMLANDI. Kalan filtre sayısı: " + survivors.size());
        if (survivors.size() == 1 || survivors.size() == 2) {
            // 🟢 BAŞARILI: 1 veya 2 filtre kaldı
            System.out.println("✨ ALTIN FİLTRELER ve tüm hedeflerdeki eşleşmeleri:");
            for (int i = 0; i < survivors.size(); i++) {
                Filter f = survivors.get(i);
                System.out.println("\n🔸 Altın Filtre #" + (i + 1) + ": " + f.getColumnNames());
                for (MatchRecord tgt : targets) {
                    System.out.println("   Hedef: " + tgt.fullDetail());
                    printPoolForFilter(f, tgt);
                }
            }
        } else {
            // 🔴 BAŞARISIZ: 0 veya 2'den fazla filtre kaldı
            System.out.println("❌ Altın filtre bulunamadı (başarısız).");
        }
    }

    private void printFiltersWithPool(List<Filter> filters, MatchRecord target) {
        for (int i=0; i<filters.size(); i++) {
            Filter f = filters.get(i);
            System.out.println("\nfiltre " + (i+1) + " -> buldugu filtreler : " + f.getColumnNames());
            printPoolForFilter(f, target);
        }
    }

    private void printPoolForFilter(Filter f, MatchRecord target) {
        List<Integer> pool = f.getPool(target, allRecords);
        if (pool.isEmpty()) {
            System.out.println("      (Havuz boş)");
            return;
        }
        System.out.println("┌──────┬─────────────────────┬─────────────────────┬──────────┬──────────┬──────────┐");
        System.out.println("│  #   │ Tarih               │ Ev Sahibi           │ Deplasman│ MS       │ İY       │");
        System.out.println("├──────┼─────────────────────┼─────────────────────┼──────────┼──────────┼──────────┤");
        int index = 1;
        for (int idx : pool) {
            MatchRecord m = allRecords.get(idx);
            String date = m.date != null ? m.date : "tarih yok";
            String home = truncate(m.homeTeam, 19);
            String away = truncate(m.awayTeam, 8);
            String ms = m.ftScore();
            String iy = m.htScore();
            System.out.printf("│ %-4d │ %-19s │ %-19s │ %-8s │ %-8s │ %-8s │%n", index++, date, home, away, ms, iy);
        }
        System.out.println("└──────┴─────────────────────┴─────────────────────┴──────────┴──────────┴──────────┘");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() > maxLen) return s.substring(0, maxLen-1) + "…";
        return s;
    }

    private Filter generateRandomFilter(MatchRecord target, Random rng) {
        List<Integer> avail = new ArrayList<>();
        for (int i=0; i<ALL_ODDS_COLS.size(); i++) if (target.odds[i]>0.0) avail.add(i);
        if (avail.isEmpty()) return new Filter(List.of());
        int cnt = 1 + rng.nextInt(Math.min(5, avail.size()));
        Collections.shuffle(avail, rng);
        return new Filter(avail.subList(0, cnt));
    }

    public static void main(String[] args) {
        new Bet365FilterFinderSQL().startFilterHunt();
    }
}