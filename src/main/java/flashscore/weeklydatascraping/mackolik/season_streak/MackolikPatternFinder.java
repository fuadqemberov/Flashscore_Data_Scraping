package flashscore.weeklydatascraping.mackolik.season_streak;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.time.LocalDate;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class MackolikPatternFinder {

    private static final String BASE_URL =
            "https://arsiv.mackolik.com/Team/Default.aspx?id=%d&season=%s";

    private static final String TEAM_IDS_FILE = "team_ids.txt";

    private static final String[] SEASONS = {
            "2024/2025", "2023/2024", "2022/2023", "2021/2022", "2020/2021"
    };

    private static final String[] MONTH_NUMS = {
            "08","09","10","11","12","01","02","03","04","05"
    };

    private static final Map<String, String> MONTH_NAME = new LinkedHashMap<>();
    static {
        MONTH_NAME.put("01","Yanvar");   MONTH_NAME.put("02","Fevral");
        MONTH_NAME.put("03","Mart");     MONTH_NAME.put("04","Aprel");
        MONTH_NAME.put("05","May");      MONTH_NAME.put("06","Iyun");
        MONTH_NAME.put("07","Iyul");     MONTH_NAME.put("08","Avqust");
        MONTH_NAME.put("09","Sentyabr"); MONTH_NAME.put("10","Oktyabr");
        MONTH_NAME.put("11","Noyabr");   MONTH_NAME.put("12","Dekabr");
    }

    private final Map<String, List<Match>> data = new LinkedHashMap<>();
    private CloseableHttpClient httpClient;

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN — team_ids.txt faylından ID-ləri oxu, hər biri üçün analiz et
    // ══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {
        List<String> teamIds;
        try {
            teamIds = readTeamIdsFromFile(TEAM_IDS_FILE);
            if (teamIds.isEmpty()) {
                System.out.println("[XETA] " + TEAM_IDS_FILE + " faylinda hec bir ID tapilmadi.");
                return;
            }
        } catch (IOException e) {
            System.out.println("[XETA] Fayl oxunmadi: " + TEAM_IDS_FILE);
            System.out.println("       Zehmet olmasa layihe qovluqunda '" + TEAM_IDS_FILE + "' yaradin.");
            System.out.println("       Her setire bir Team ID yazin. Meselen:");
            System.out.println("       110");
            System.out.println("       245");
            System.out.println("       831");
            return;
        }

        System.out.println("[INFO] " + teamIds.size() + " team ID tapildi: " + teamIds);

        for (String idStr : teamIds) {
            try {
                int teamId = Integer.parseInt(idStr.trim());
                new MackolikPatternFinder().run(teamId);
            } catch (NumberFormatException e) {
                System.out.println("[XETA] Gecersiz ID atlandi: '" + idStr + "'");
            }
        }
    }

    private static List<String> readTeamIdsFromFile(String filePath) throws IOException {
        List<String> ids = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                for (String part : line.split(",")) {
                    String id = part.trim();
                    if (!id.isEmpty()) ids.add(id);
                }
            }
        }
        return ids;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RUN — bir komanda üçün bütün mövsümləri scrape et və analiz et
    // ══════════════════════════════════════════════════════════════════════════
    public void run(int teamId) throws Exception {
        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(20_000)
                .setConnectionRequestTimeout(5_000)
                .build();
        httpClient = HttpClients.custom().setDefaultRequestConfig(cfg).build();
        try {
            for (String season : SEASONS) {
                List<Match> matches = scrapeLeague(teamId, season);
                data.put(season, matches);
                Thread.sleep(600);
            }
            printCurrentWindowMatches(teamId);
        } finally {
            httpClient.close();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ANA METOD — 3+ sezonda eyni FT skoru olan pəncərələri çap et
    // ══════════════════════════════════════════════════════════════════════════
    private void printRepeatedScores(int teamId) {

        // ── 1. TEK PƏNCƏRƏ ANALİZİ ───────────────────────────────────────────
        System.out.println("\n========================================");
        System.out.println("  TEAM " + teamId + " | 3+ SEZONDA TEKRARLANAN FT SKORLAR");
        System.out.println("  (Tek pencere: ayin evveli 1-7 / sonu 24-31)");
        System.out.println("========================================\n");

        boolean herhansiBir = false;

        for (String monthNum : MONTH_NUMS) {
            String monthName = MONTH_NAME.getOrDefault(monthNum, monthNum);

            for (String window : new String[]{"1-7", "24-31"}) {

                Map<String, List<Match>> ftToMatches = new LinkedHashMap<>();

                for (String season : SEASONS) {
                    data.getOrDefault(season, List.of()).stream()
                            .filter(m -> String.format("%02d", m.getMonth()).equals(monthNum))
                            .filter(m -> window.equals(m.getWindowLabel()))
                            .forEach(mc -> {
                                String ft = mc.getScore().trim();
                                if (!ft.isEmpty())
                                    ftToMatches.computeIfAbsent(ft, k -> new ArrayList<>()).add(mc);
                            });
                }

                for (Map.Entry<String, List<Match>> e : ftToMatches.entrySet()) {
                    long uniqSeasons = e.getValue().stream()
                            .map(Match::getSeason).distinct().count();
                    if (uniqSeasons >= 3) {
                        System.out.printf("%s [%s]  ->  FT: %s  (%d sezonda)%n",
                                monthName, window, e.getKey(), uniqSeasons);
                        for (Match mc : e.getValue()) {
                            System.out.printf("    %s  |  %s - %s  |  HT: %s  FT: %s%n",
                                    mc.getSeason(),
                                    mc.getHomeTeam(),
                                    mc.getAwayTeam(),
                                    mc.getHalfTimeScore(),
                                    mc.getScore());
                        }
                        System.out.println();
                        herhansiBir = true;
                    }
                }
            }
        }

        if (!herhansiBir)
            System.out.println("Hec bir FT skoru 3+ sezonda tekrarlanmayib.");

        // ── 2. KEÇID DÖVRÜ ANALİZİ ───────────────────────────────────────────
        System.out.println("\n========================================");
        System.out.println("  TEAM " + teamId + " | 3+ SEZONDA TEKRARLANAN FT SKORLAR");
        System.out.println("  (Kecid dovru: ayin sonu + novbeti ayin evveli)");
        System.out.println("========================================\n");

        boolean herhansiBirKecid = false;

        for (int i = 0; i < MONTH_NUMS.length; i++) {
            String monthA = MONTH_NUMS[i];
            String monthB = MONTH_NUMS[(i + 1) % MONTH_NUMS.length];

            String nameA = MONTH_NAME.getOrDefault(monthA, monthA);
            String nameB = MONTH_NAME.getOrDefault(monthB, monthB);
            String label = nameA + " sonu + " + nameB + " evveli";

            Map<String, List<Match>> ftToMatches = new LinkedHashMap<>();

            for (String season : SEASONS) {
                List<Match> allMatches = data.getOrDefault(season, List.of());

                allMatches.stream()
                        .filter(m -> String.format("%02d", m.getMonth()).equals(monthA))
                        .filter(m -> "24-31".equals(m.getWindowLabel()))
                        .forEach(mc -> {
                            String ft = mc.getScore().trim();
                            if (!ft.isEmpty())
                                ftToMatches.computeIfAbsent(ft, k -> new ArrayList<>()).add(mc);
                        });

                allMatches.stream()
                        .filter(m -> String.format("%02d", m.getMonth()).equals(monthB))
                        .filter(m -> "1-7".equals(m.getWindowLabel()))
                        .forEach(mc -> {
                            String ft = mc.getScore().trim();
                            if (!ft.isEmpty())
                                ftToMatches.computeIfAbsent(ft, k -> new ArrayList<>()).add(mc);
                        });
            }

            for (Map.Entry<String, List<Match>> e : ftToMatches.entrySet()) {
                long uniqSeasons = e.getValue().stream()
                        .map(Match::getSeason).distinct().count();
                if (uniqSeasons >= 3) {
                    System.out.printf("[%s]  ->  FT: %s  (%d sezonda)%n",
                            label, e.getKey(), uniqSeasons);
                    for (Match mc : e.getValue()) {
                        System.out.printf("    %s  |  %s - %s  |  HT: %s  FT: %s%n",
                                mc.getSeason(),
                                mc.getHomeTeam(),
                                mc.getAwayTeam(),
                                mc.getHalfTimeScore(),
                                mc.getScore());
                    }
                    System.out.println();
                    herhansiBirKecid = true;
                }
            }
        }

        if (!herhansiBirKecid)
            System.out.println("Hec bir FT skoru kecid dovrunde 3+ sezonda tekrarlanmayib.");

        System.out.println("\n========================================");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SCRAPE + PARSE
    // ══════════════════════════════════════════════════════════════════════════
    private List<Match> scrapeLeague(int teamId, String season) throws IOException {
        String url  = String.format(BASE_URL, teamId, season);
        String html = fetchHtml(url);
        if (html == null) return new ArrayList<>();
        return parseTable(Jsoup.parse(html), season);
    }

    private String fetchHtml(String url) throws IOException {
        HttpGet req = new HttpGet(url);
        req.addHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        req.addHeader("Accept-Language", "tr-TR,tr;q=0.9");
        try (CloseableHttpResponse resp = httpClient.execute(req)) {
            int status = resp.getStatusLine().getStatusCode();
            if (status == 200) return EntityUtils.toString(resp.getEntity(), "UTF-8");
            return null;
        }
    }

    private List<Match> parseTable(Document doc, String season) {
        List<Match> result = new ArrayList<>();
        Element tbody = doc.selectFirst("#tblFixture > tbody");
        if (tbody == null) return result;

        boolean inLeague = false;
        for (Element row : tbody.select("tr")) {
            String cls = row.className();
            if (cls.contains("competition")) {
                inLeague = isMainLeague(row.text().toLowerCase());
                continue;
            }
            if (cls.contains("leg")) { inLeague = false; continue; }
            if (!inLeague) continue;
            if (!cls.contains("alt1") && !cls.contains("alt2")) continue;
            if (row.selectFirst("td[itemprop=sportsevent]") != null) continue;

            Match m = parseRow(row, season);
            if (m != null) result.add(m);
        }
        return result;
    }

    private boolean isMainLeague(String text) {
        return !text.contains("kupa") && !text.contains("cup")
                && !text.contains("hazirlik") && !text.contains("hazırlık")
                && !text.contains("playoff") && !text.contains("play-off")
                && !text.contains("süper kupa");
    }

    private Match parseRow(Element row, String season) {
        try {
            Elements cells = row.select("td");
            if (cells.size() < 13) return null;
            String date = cells.get(0).text().trim();
            if (date.isEmpty()) return null;

            String side = row.attr("side");
            if (side.isEmpty()) side = "unknown";

            String homeTeam = extractTeamName(cells.get(2));
            String awayTeam = extractTeamName(cells.get(6));

            Element scoreEl = cells.get(4).selectFirst("b a");
            if (scoreEl == null) return null;
            String score = scoreEl.text().trim();
            if (score.equals("v") || score.isEmpty()) return null;

            String ht = cells.get(8).text().trim();
            String ou = "";
            Element ouEl = cells.get(10).selectFirst("span");
            if (ouEl != null) ou = ouEl.text().trim();
            String kg = cells.get(11).text().trim();
            String ftResult = "";
            Element resDiv = cells.get(12).selectFirst("div");
            if (resDiv != null) ftResult = resDiv.text().trim();

            return new Match(date, homeTeam, awayTeam, score, ht, ftResult, ou, kg, side, season);
        } catch (Exception e) {
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ANLIK PENCERE — bugunun tarixin gore aktiv penceredeki oyunlari cixar
    // ══════════════════════════════════════════════════════════════════════════
    private void printCurrentWindowMatches(int teamId) {
        LocalDate today = LocalDate.now();
        int day   = today.getDayOfMonth();
        int month = today.getMonthValue();

        boolean isEarly = day >= 1  && day <= 7;
        boolean isLate  = day >= 24;

        if (!isEarly && !isLate) return; // 8-23 arasi — heç nə çap etmə

        String curMonth  = String.format("%02d", month);
        String curWindow = isEarly ? "1-7" : "24-31";
        String curName   = MONTH_NAME.getOrDefault(curMonth, curMonth);
        String winLabel  = isEarly ? "evveli [1-7]" : "sonu [24-31]";

        // FT skoru → uygun maclar
        Map<String, List<Match>> ftToMatches = new LinkedHashMap<>();
        for (String season : SEASONS) {
            data.getOrDefault(season, List.of()).stream()
                    .filter(m -> String.format("%02d", m.getMonth()).equals(curMonth))
                    .filter(m -> curWindow.equals(m.getWindowLabel()))
                    .forEach(mc -> {
                        String ft = mc.getScore().trim();
                        if (!ft.isEmpty())
                            ftToMatches.computeIfAbsent(ft, k -> new ArrayList<>()).add(mc);
                    });
        }

        // Yalniz 3+ farkli sezonda tekrarlanan FT skorlari
        boolean herhansi = false;
        for (Map.Entry<String, List<Match>> e : ftToMatches.entrySet()) {
            long uniq = e.getValue().stream().map(Match::getSeason).distinct().count();
            if (uniq >= 3) {
                if (!herhansi) {
                    System.out.println(" " + "=".repeat(72));
                            System.out.println("  ANLIK PENCERE | " + today + " | " + curName + " " + winLabel);
                    System.out.println("  Team ID: " + teamId);
                    System.out.println("=".repeat(72));
                    herhansi = true;
                }
                System.out.printf("%nFT: %s  (%d sezonda)%n", e.getKey(), uniq);
                for (Match mc : e.getValue()) {
                    System.out.printf("    %s  |  %-20s %s %-20s  |  HT: %-5s  FT: %s%n",
                            mc.getDate(),
                            mc.getHomeTeam(),
                            mc.getScore(),
                            mc.getAwayTeam(),
                            mc.getHalfTimeScore(),
                            mc.getScore());
                }
            }
        }
        if (herhansi) System.out.println(" " + "=".repeat(72));
    }


    private String extractTeamName(Element cell) {
        Element a = cell.selectFirst("a");
        if (a != null && !a.text().trim().isEmpty()) return a.text().trim();
        Element span = cell.selectFirst("span.team");
        if (span != null && !span.text().trim().isEmpty()) return span.text().trim();
        return cell.text().trim();
    }
}