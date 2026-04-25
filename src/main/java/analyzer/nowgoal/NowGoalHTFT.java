package analyzer.nowgoal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

public class NowGoalHTFT {

    // ── Parametrlər ──────────────────────────────────────────
    static final int    SEASON_LOOKBACK = 10;
    static final int    THREADS         = 50;
    static final String CURRENT_SEASON  = String.valueOf(java.time.Year.now().getValue());
    static final String BASE            = "https://football.nowgoal26.com";
    static final String LIVE_BASE       = "https://live5.nowgoal26.com";
    // ─────────────────────────────────────────────────────────

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    static final ObjectMapper JSON = new ObjectMapper();

    // ── HTTP GET: gzip + BOM ──────────────────────────────────
    static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer",    BASE + "/")
                .header("Accept",     "application/json,*/*")
                .GET().build();

        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode());

        byte[] b = resp.body();

        // gzip
        if (b.length >= 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) {
            try (var gis = new GZIPInputStream(new ByteArrayInputStream(b));
                 var out = new ByteArrayOutputStream()) {
                b = gis.readAllBytes();
            }
        }
        // BOM
        if (b.length >= 3 && b[0] == (byte)0xEF && b[1] == (byte)0xBB && b[2] == (byte)0xBF)
            b = Arrays.copyOfRange(b, 3, b.length);

        return new String(b, StandardCharsets.UTF_8);
    }

    // ── ANA METOD ─────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().reset();
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        System.setProperty("webdriver.chrome.silentOutput", "true");

        System.out.println(">>> Cari sezon: " + CURRENT_SEASON);
        System.out.println(">>> Liqa ID-ləri gətirilir...");

        List<Integer> leagueIds = fetchLeagueIdsViaBrowser();
        System.out.println(">>> Tapılan liqa sayı: " + leagueIds.size());
        System.out.println(">>> HTTP analiz başladı.\n");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> fs = new ArrayList<>();
        for (int id : leagueIds) {
            final int lid = id;
            fs.add(pool.submit(() -> {
                try { analyzeLeague(lid); }
                catch (Exception e) {
                    System.err.println("[XƏTA] liqa=" + lid + " : " + e.getMessage());
                }
            }));
        }
        for (Future<?> f : fs) { try { f.get(); } catch (Exception ignored) {} }
        pool.shutdown();
        System.out.println("\n>>> Analiz tamamlandı.");
    }

    // ── SELENIUM: Yalnız liqa ID-lərini çək ───────────────────
    static List<Integer> fetchLeagueIdsViaBrowser() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--headless","--disable-gpu","--no-sandbox",
                "--disable-dev-shm-usage","--blink-settings=imagesEnabled=false",
                "--log-level=3","--silent");
        WebDriver driver = new ChromeDriver(opts);
        Set<Integer> ids = new TreeSet<>();
        try {
            driver.get(LIVE_BASE + "/");
            Thread.sleep(5000);
            for (WebElement el : driver.findElements(By.cssSelector("[sclassid]"))) {
                try {
                    String v = el.getAttribute("sclassid");
                    if (v != null && !v.isBlank()) ids.add(Integer.parseInt(v.trim()));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.out.println("Selenium xetasi: " + e.getMessage());
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
        System.out.println("DOM-dan tapilan ID sayi: " + ids.size());
        return new ArrayList<>(ids);
    }

    // ── LİQA ANALİZİ ──────────────────────────────────────────
    static void analyzeLeague(int ligId) throws Exception {
        List<String> allSeasons = loadSeasons(ligId);
        if (allSeasons.isEmpty()) return;

        String curSeason = allSeasons.get(0);
        List<String> pastSeasons = allSeasons.subList(1, Math.min(allSeasons.size(), SEASON_LOOKBACK + 1));

        LeagueData cur = loadLeague(ligId, curSeason);
        if (cur == null) return;

        // ── CARİ AKTİV HƏFTƏNİ DÜZGÜN MÜƏYYƏNLƏŞDİR ────────
        // Aktiv həftə = ən azı 1 oynanmış (Postp. deyil) matç olan ən yüksək həftə + 1
        // (əgər o həftədə hələ oynanmamış real matç varsa, o həftənin özü aktiv həftədir)
        int currentActiveRound = detectCurrentActiveRound(cur);

        if (currentActiveRound < 2) return; // Hələ kifayət qədər həftə keçməyib

        // Yalnız aktiv həftədəki OYNANMAMIŞ (Postp. deyil) matçları analiz et
        List<Match> upcomingMatches = new ArrayList<>();
        List<Match> roundMatches = cur.rounds.get(currentActiveRound);
        if (roundMatches != null) {
            for (Match m : roundMatches) {
                // Oynanmamış VƏ postponed deyil
                if (m.ft == null && !m.postponed) {
                    upcomingMatches.add(m);
                }
            }
        }

        // Bu həftədə oynanacaq real matç yoxdursa çıx
        if (upcomingMatches.isEmpty()) return;

        // Keçən həftənin (currentActiveRound - 1) körpüsünü qur
        // Körpü üçün yalnız OYNANMIŞ matçları nəzərə al (Postp. xaric)
        int bridgeRound = currentActiveRound - 1;
        Map<Integer, Integer> bridge = buildBridge(cur, bridgeRound);

        if (bridge.isEmpty()) return;

        // Oynanmamış hər bir oyunu cütlük olaraq keçmişdə axtarırıq
        for (Match up : upcomingMatches) {
            Integer b1 = bridge.get(up.homeId);
            Integer b2 = bridge.get(up.awayId);

            // Rəqiblərdən biri r-1-də oynamayıbsa, ötürük
            if (b1 == null || b2 == null) continue;

            // Keçmiş sezonlarda axtarışa başla
            for (String season : pastSeasons) {
                try {
                    searchInSeason(ligId, season, currentActiveRound, b1, b2,
                            cur.teamName(up.homeId), cur.teamName(up.awayId),
                            cur.leagueName, cur);
                } catch (Exception ignored) {}
            }
        }
    }

    // ── CARİ AKTİV HƏFTƏNİ TAP ──────────────────────────────
    // Qaydalar:
    //   1. Postponed matçları TAMAMILƏ NƏZƏRƏ ALMA
    //   2. Ən azı 1 oynanmış matç olan ən yüksək həftəni tap → bu "son tamamlanmış həftə"
    //   3. Sonrakı həftədə oynanacaq real matç varsa → o həftə aktivdir
    //   4. Yoxdursa → son tamamlanmış həftənin özünə bax (hələ bitməmişdir)
    static int detectCurrentActiveRound(LeagueData ld) {
        // Hər həftə üçün: oynanmış sayı, real oynanmamış sayı
        TreeMap<Integer, int[]> roundStats = new TreeMap<>(); // [played, realUpcoming]

        for (Map.Entry<Integer, List<Match>> entry : ld.rounds.entrySet()) {
            int r = entry.getKey();
            int played = 0, upcoming = 0;
            for (Match m : entry.getValue()) {
                if (m.postponed) continue; // Postp.-ları say
                if (m.ft != null) played++;
                else upcoming++;
            }
            roundStats.put(r, new int[]{played, upcoming});
        }

        // Oynanmış matçı olan ən yüksək həftəni tap
        int lastPlayedRound = -1;
        for (Map.Entry<Integer, int[]> e : roundStats.entrySet()) {
            if (e.getValue()[0] > 0) lastPlayedRound = e.getKey();
        }

        if (lastPlayedRound == -1) return 1; // Heç oynanmayıb

        // Həmin həftədə hələ oynanmamış real matç varmı?
        int[] lastStats = roundStats.get(lastPlayedRound);
        if (lastStats[1] > 0) {
            // Həftə hələ bitməyib → aktiv həftə = lastPlayedRound
            return lastPlayedRound;
        }

        // Həftə tam bitib → növbəti həftəyə bax
        int nextRound = lastPlayedRound + 1;
        if (roundStats.containsKey(nextRound) && roundStats.get(nextRound)[1] > 0) {
            return nextRound;
        }

        // Növbəti həftə JSON-da yoxdursa və ya orada da oynanmamış yoxdursa
        // son oynanmış həftəni geri qaytar (edge case)
        return lastPlayedRound;
    }

    // ── KEÇMİŞ SEZONDA AXTAR ─────────────────────────────────
    static void searchInSeason(int ligId, String season, int targetRound,
                               int b1, int b2,
                               String curHome, String curAway,
                               String leagueName, LeagueData curData) throws Exception {
        LeagueData past = loadLeague(ligId, season);
        if (past == null) return;

        for (int r : new TreeSet<>(past.rounds.keySet())) {
            if (r <= 1) continue;

            // Həmin sezonda r-1 körpüsünü qur (yalnız oynanmış, Postp. xaric)
            Map<Integer,Integer> pb = buildBridge(past, r - 1);
            Integer oldA = pb.get(b1);
            Integer oldB = pb.get(b2);
            if (oldA == null || oldB == null) continue;

            // r-ci həftədə oldA vs oldB oynamışmı?
            Match found = findMatch(past, r, oldA, oldB);
            if (found == null || found.ft == null || found.ht == null) continue;

            // Comeback yoxla (1/2 və ya 2/1)
            String comeback = comeback(found.ft, found.ht);
            if (comeback.equals("NONE")) continue;

            String msg = (r == targetRound) ? "[Eyni Həftə: " + r + "]"
                    : ">>>> [FƏRQLİ HƏFTƏ] (Həftə: " + r + ") <<<<";

            synchronized (System.out) {
                System.out.println("\n**********************");
                System.out.println("[LİQA]: " + leagueName + " | [GÜNCƏL HƏFTƏ]: " + targetRound);
                System.out.println("[GÜNCƏL OYUN]: " + curHome + " vs " + curAway);
                System.out.println("[KÖPRÜLƏR]: " + curData.teamName(b1) + " & " + curData.teamName(b2));
                System.out.println("[TAPILAN SEZON]: " + season + " | " + msg);
                System.out.println("🔥🔥🔥 [CRAZY COMEBACK HT/FT: " + comeback + "] 🔥🔥🔥");
                System.out.println("[KEÇMİŞ NƏTİCƏ]: " + past.teamName(found.homeId)
                        + " " + found.ft + " " + past.teamName(found.awayId)
                        + " | HT: (" + found.ht + ")");
                System.out.println("**********************");
            }
            break;
        }
    }

    // ── YARDIMCI: bridge xeritesi ─────────────────────────────
    // Yalnız OYNANMIŞ (ft != null) VƏ postponed olmayan matçlar
    static Map<Integer,Integer> buildBridge(LeagueData ld, int round) {
        Map<Integer,Integer> map = new HashMap<>();
        List<Match> ms = ld.rounds.get(round);
        if (ms == null) return map;
        for (Match m : ms) {
            if (m.postponed) continue;      // Postp. körpüyə daxil edilmir
            if (m.ft != null) {             // Yalnız oynanmış matçlar
                map.put(m.homeId, m.awayId);
                map.put(m.awayId, m.homeId);
            }
        }
        return map;
    }

    // ── YARDIMCI: maç axtar ───────────────────────────────────
    static Match findMatch(LeagueData ld, int round, int a, int b) {
        List<Match> ms = ld.rounds.get(round);
        if (ms == null) return null;
        for (Match m : ms)
            if ((m.homeId==a && m.awayId==b)||(m.homeId==b && m.awayId==a))
                return m;
        return null;
    }

    // ── COMEBACK KONTROLU ─────────────────────────────────────
    static String comeback(String ft, String ht) {
        try {
            ft = ft.trim(); ht = ht.trim();
            String[] f = ft.split("-"), h = ht.split("-");
            int fH = Integer.parseInt(f[0]), fA = Integer.parseInt(f[1]);
            int hH = Integer.parseInt(h[0]), hA = Integer.parseInt(h[1]);
            if (hH > hA && fH < fA) return "1/2";
            if (hH < hA && fH > fA) return "2/1";
        } catch (Exception ignored) {}
        return "NONE";
    }

    // ── JSON YÜKLƏ ────────────────────────────────────────────
    static LeagueData loadLeague(int ligId, String season) {
        try {
            String raw = get(BASE + "/jsData/matchResult/json/" + season + "/s" + ligId + "_en.json");
            return new LeagueData(JSON.readTree(raw));
        } catch (Exception e) { return null; }
    }

    static List<String> loadSeasons(int ligId) {
        List<String> list = new ArrayList<>();
        try {
            String url = BASE + "/jsData/leagueSeason/sea" + ligId + ".json";
            String raw = get(url);
            JsonNode node = JSON.readTree(raw).get("SeasonList");
            if (node != null) {
                for (JsonNode s : node) list.add(s.asText());
            }
        } catch (Exception e) {
            String[] candidates = {
                    "2025-2026", "2026", "2025/26",
                    "2024-2025", "2025", "2024/25"
            };
            for (String c : candidates) {
                if (loadLeague(ligId, c) != null) {
                    list.add(c);
                    break;
                }
            }
            if (list.isEmpty()) return list;

            String cur = list.get(0);
            if (cur.contains("-")) {
                try {
                    String[] parts = cur.split("-");
                    int y1 = Integer.parseInt(parts[0]);
                    int y2 = Integer.parseInt(parts[1]);
                    for (int i = 1; i <= SEASON_LOOKBACK; i++)
                        list.add((y1-i) + "-" + (y2-i));
                } catch (Exception ignored) {}
            } else if (cur.contains("/")) {
                try {
                    String[] parts = cur.split("/");
                    int y1 = Integer.parseInt(parts[0]);
                    int y2s = Integer.parseInt(parts[1]);
                    for (int i = 1; i <= SEASON_LOOKBACK; i++)
                        list.add((y1-i) + "/" + String.format("%02d", y2s-i));
                } catch (Exception ignored) {}
            } else {
                try {
                    int y = Integer.parseInt(cur);
                    for (int i = 1; i <= SEASON_LOOKBACK; i++)
                        list.add(String.valueOf(y - i));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    // ── DATA MODEL ────────────────────────────────────────────
    static class Match {
        int homeId, awayId;
        String ft, ht;
        boolean postponed = false; // YENİ: Ertelənmiş matç işarəsi
    }

    static class LeagueData {
        String leagueName = "";
        Map<Integer, String>      teamNames = new HashMap<>();
        Map<Integer, List<Match>> rounds    = new HashMap<>();

        LeagueData(JsonNode root) {
            JsonNode li = root.get("LeagueInfo");
            if (li != null && li.size() > 1) leagueName = li.get(1).asText();

            JsonNode ti = root.get("TeamInfo");
            if (ti != null) for (JsonNode t : ti) {
                try { teamNames.put(t.get(0).asInt(), t.get(1).asText()); }
                catch (Exception ignored) {}
            }

            JsonNode sl = root.get("ScheduleList");
            if (sl == null) return;

            sl.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode val = entry.getValue();

                if (key.startsWith("R_")) {
                    parseRound(key, val);
                } else {
                    val.fields().forEachRemaining(re -> parseRound(re.getKey(), re.getValue()));
                }
            });
        }

        void parseRound(String roundKey, JsonNode roundData) {
            int rNum;
            try { rNum = Integer.parseInt(roundKey.replace("R_", "")); }
            catch (Exception e) { return; }

            if (!roundData.isArray()) return;

            List<Match> list = new ArrayList<>();
            for (JsonNode m : roundData) {
                try {
                    if (m.size() < 6) continue;
                    Match match  = new Match();
                    match.homeId = m.get(4).asInt();
                    match.awayId = m.get(5).asInt();

                    String ft = m.size() > 6 ? m.get(6).asText("") : "";
                    String ht = m.size() > 7 ? m.get(7).asText("") : "";

                    // ── POSTPONED AŞKARLAMA ──────────────────────────────
                    // nowgoal JSON-da Postp. matçlar üçün ft sahəsindəki dəyər
                    // "Postp.", "postponed", "-", "" və ya xüsusi kod ola bilər.
                    // Əlavə olaraq: m.get(2) status kodu (3 = postponed) ola bilər.
                    // Hər iki yolu yoxlayırıq:
                    boolean isPostponed = false;

                    // 1) Status kodu yoxla (index 2 — status field)
                    if (m.size() > 2) {
                        int status = m.get(2).asInt(-1);
                        // nowgoal-da status: 0=upcoming, 1=live, 3=finished, 4=postponed, 5=cancelled
                        if (status == 4 || status == 5) isPostponed = true;
                    }

                    // 2) ft mətnini yoxla (fallback)
                    if (!isPostponed) {
                        String ftLower = ft.toLowerCase().trim();
                        if (ftLower.equals("postp.") || ftLower.equals("postponed")
                                || ftLower.equals("post") || ftLower.equals("canc.")
                                || ftLower.equals("cancelled") || ftLower.equals("aband.")
                                || ftLower.equals("walkover")) {
                            isPostponed = true;
                        }
                    }

                    match.postponed = isPostponed;

                    if (!isPostponed) {
                        match.ft = ft.matches("\\d+-\\d+") ? ft : null;
                        match.ht = ht.matches("\\d+-\\d+") ? ht : null;
                    }
                    // Postponed matçlarda ft=null, ht=null qalır (yuxarıda initialized edilmişdir)

                    list.add(match);
                } catch (Exception ignored) {}
            }
            rounds.computeIfAbsent(rNum, k -> new ArrayList<>()).addAll(list);
        }

        String teamName(int id) {
            return teamNames.getOrDefault(id, "ID:" + id);
        }
    }
}