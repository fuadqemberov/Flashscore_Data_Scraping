package analyzer.mackolik.before_after_pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class APIPatternFinder {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final List<String> matchedPatterns = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger completedCount = new AtomicInteger(0);

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        System.out.println("🚀 API Modu Başlatıldı. Maçlar toplanıyor...");

        // 1. JSON Endpoint'inden maç ID'lerini çek
        Set<String> matchIds = fetchMatchIdsFromAPI();

        if (matchIds.isEmpty()) {
            System.out.println("⚠️ JSON'dan maç ID'si alınamadı.");
            return;
        }

        System.out.println("📊 " + matchIds.size() + " maç bulundu. Analiz başlıyor...");

        // 2. Virtual Threads ile Head2Head sayfalarını tara
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String id : matchIds) {
                executor.submit(() -> {
                    analyzeMatch(id);
                    int current = completedCount.incrementAndGet();
                    if (current % 20 == 0) System.out.print("\r⏳ Analiz: %" + (current * 100 / matchIds.size()));
                });
            }
        }

        // 3. Sonuçları dök
        printResults(startTime);
    }

    private static Set<String> fetchMatchIdsFromAPI() {
        Set<String> ids = new HashSet<>();
        try {
            // Verdiğin livedata endpoint'i
            String apiUrl = "https://vd.mackolik.com/livedata?group=0";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // JSON içindeki "m" (matches) dizisini Regex ile ayıkla (Kütüphane bağımlılığını azaltmak için)
            // Format: [4464355,195,"PSG",...
            Pattern p = Pattern.compile("\\[(\\d{7}),");
            Matcher m = p.matcher(response.body());
            while (m.find()) {
                ids.add(m.group(1));
            }
        } catch (Exception e) {
            System.err.println("API Hatası: " + e.getMessage());
        }
        return ids;
    }

    private static void analyzeMatch(String matchId) {
        // Doğrudan Head2Head istatistik sayfası (Form durumunun olduğu yer)
        String url = "https://arsiv.mackolik.com/Match/Head2Head.aspx?id=" + matchId + "&s=1";
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Document doc = Jsoup.parse(response.body());

            // Form Durumu kutuları
            Elements forms = doc.select("div.md:has(div.detail-title:contains(Form Durumu))");
            if (forms.size() < 2) return;

            MatchData home = parseForm(forms.get(0), matchId);
            MatchData away = parseForm(forms.get(1), matchId);

            checkLogic(home, away, "https://arsiv.mackolik.com/Mac/" + matchId);

        } catch (Exception ignored) {}
    }

    private static MatchData parseForm(Element container, String targetId) {
        String team = container.select(".detail-title").text().split("-")[0].replace("Form Durumu", "").trim();
        Elements rows = container.select("tr.row, tr.row-2");

        int idx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).select("a[href*=" + targetId + "]").size() > 0) {
                idx = i;
                break;
            }
        }

        MatchData data = new MatchData(team);
        if (idx != -1) {
            data.p2 = getOpp(rows, idx - 2);
            data.p1 = getOpp(rows, idx - 1);
            data.n1 = getOpp(rows, idx + 1);
            data.n2 = getOpp(rows, idx + 2);
        }
        return data;
    }

    private static String getOpp(Elements rows, int i) {
        if (i < 0 || i >= rows.size()) return null;
        Elements links = rows.get(i).select("td:nth-child(3) a, td:nth-child(5) a");
        return links.isEmpty() ? null : links.first().text().trim();
    }

    private static void checkLogic(MatchData h, MatchData a, String url) {
        // PATTERN 1: ÇAPRAZ EŞLEŞME (M1)
        if (h.p1 != null && h.p1.equals(a.n1)) record("🔵 ÇAPRAZ M1 (Ev -1 = Dep +1)", h, a, h.p1, url);
        if (h.n1 != null && h.n1.equals(a.p1)) record("🔵 ÇAPRAZ M1 (Ev +1 = Dep -1)", h, a, h.n1, url);

        // PATTERN 2: ÇAPRAZ EŞLEŞME (M2)
        if (h.p2 != null && h.p2.equals(a.n2)) record("🟢 ÇAPRAZ M2 (Ev -2 = Dep +2)", h, a, h.p2, url);
        if (h.n2 != null && h.n2.equals(a.p2)) record("🟢 ÇAPRAZ M2 (Ev +2 = Dep -2)", h, a, h.n2, url);

        // PATTERN 3: HAVUZ (BİLGİ-3)
        Set<String> hPool = new HashSet<>(Arrays.asList(h.p1, h.p2));
        Set<String> aPool = new HashSet<>(Arrays.asList(a.n1, a.n2));
        hPool.retainAll(aPool);
        hPool.remove(null);
        if (!hPool.isEmpty()) record("🔥 HAVUZ EŞLEŞMESİ (BİLGİ-3)", h, a, hPool.toString(), url);
    }

    private static void record(String type, MatchData h, MatchData a, String common, String url) {
        matchedPatterns.add(String.format("%s\n   Maç: %s vs %s\n   Ortak Takım: %s\n   Link: %s", type, h.name, a.name, common, url));
    }

    private static void printResults(long startTime) {
        System.out.println("\n\n=======================================================");
        System.out.println("🎯 ANALİZ SONUÇLARI");
        System.out.println("=======================================================");
        if (matchedPatterns.isEmpty()) {
            System.out.println("❌ Kriterlere uyan maç bulunamadı.");
        } else {
            matchedPatterns.forEach(s -> System.out.println(s + "\n-------------------------------------------------------"));
        }
        System.out.println("⏱️ Bitiş Süresi: " + (System.currentTimeMillis() - startTime) / 1000 + " saniye.");
    }

    static class MatchData {
        String name, p1, p2, n1, n2;
        MatchData(String n) { this.name = n; }
    }
}