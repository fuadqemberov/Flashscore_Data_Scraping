package flashscore.weeklydatascraping.aiscore.v2;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class MatchAnalyzerHttpGet {

    private static final String URL_SOURCE_FILE = "urls_to_analyze.txt";
    private static final String OUTPUT_FILE = "found_urls.txt";
    private static final int MAX_THREADS = 16;
    private static final int KONTROL_ARALIGI_SANIYE = 60;
    private static final Set<String> ISLENEN_URLLER = ConcurrentHashMap.newKeySet();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        System.out.println("--- MAÇ ANALİZ GÖZETMENİ BAŞLATILDI (HttpGet Metodu) ---");
        System.out.printf("Script, %s dosyasını izliyor.%n", URL_SOURCE_FILE);
        System.out.println("Durdurmak için programı sonlandırın (Ctrl+C).");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(MatchAnalyzerHttpGet::runAnalysisCycle, 0, KONTROL_ARALIGI_SANIYE, TimeUnit.SECONDS);
    }

    private static void runAnalysisCycle() {
        ZonedDateTime nowBaku = ZonedDateTime.now(java.time.ZoneId.of("Asia/Baku"));
        System.out.printf("%n[%s] Yeni kontrol döngüsü...%n", nowBaku.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(URL_SOURCE_FILE));
        } catch (IOException e) {
            System.out.printf("'%s' bulunamadı. Dosyanın oluşturulması bekleniyor...%n", URL_SOURCE_FILE);
            return;
        }

        List<String> anlikAnalizEdilecekler = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            try {
                String[] parts = line.split(",", 2);
                if (parts.length < 2) continue;
                String url = parts[0];
                String timeStr = parts[1];

                if (ISLENEN_URLLER.contains(url)) continue;

                ZonedDateTime matchTime = ZonedDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ"));
                if (matchTime.isBefore(nowBaku)) {
                    ISLENEN_URLLER.add(url);
                    continue;
                }
                if (ChronoUnit.MINUTES.between(nowBaku, matchTime) <= 300) {
                    System.out.printf("  -> Yaklaşan maç yakalandı: %s%n", url);
                    anlikAnalizEdilecekler.add(url);
                    ISLENEN_URLLER.add(url);
                }
            } catch (Exception e) {
                System.err.printf("Hatalı formatlı satır atlanıyor: %s%n", line);
            }
        }

        if (!anlikAnalizEdilecekler.isEmpty()) {
            System.out.printf("--> %d adet maç %d thread ile analiz ediliyor...%n", anlikAnalizEdilecekler.size(), MAX_THREADS);
            ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
            List<Future<String>> futures = new ArrayList<>();

            for (String url : anlikAnalizEdilecekler) {
                futures.add(executor.submit(() -> analyzeSingleMatchWithHttp(url)));
            }

            Set<String> allFoundUrls = new HashSet<>();
            for (Future<String> future : futures) {
                try {
                    String result = future.get();
                    if (result != null) {
                        allFoundUrls.add(result);
                    }
                } catch (Exception e) {
                }
            }
            executor.shutdown();

            if (!allFoundUrls.isEmpty()) {
                System.out.printf("+++ Bulunan %d yeni paternli maç '%s' dosyasına ekleniyor...%n", allFoundUrls.size(), OUTPUT_FILE);
                try (FileWriter writer = new FileWriter(OUTPUT_FILE, true)) {
                    for (String u : allFoundUrls) {
                        writer.write(u + "\n");
                    }
                } catch (IOException e) {
                    System.err.println("Dosyaya yazma hatası: " + e.getMessage());
                }
            } else {
                System.out.println("--> Analiz edilen maçlarda eşleşen patern bulunamadı.");
            }
        } else {
            System.out.println("Analiz kriterlerine uyan yeni maç yok.");
        }
    }

    private static String analyzeSingleMatchWithHttp(String matchUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(matchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String htmlBody = response.body();

            Document doc = Jsoup.parse(htmlBody);

            Element titleElement = doc.selectFirst("h1.text");
            String matchTitle = (titleElement != null) ? titleElement.text().split(" betting odds")[0].trim() : "Bilinmiyor";

            Element logo = doc.selectFirst("img.logo[src*='fe8aec51afeb2de633c9']");
            if (logo == null) {
                return null;
            }

            Element bet365Row = logo.parents().select(".borderBottom").first();
            if (bet365Row == null) {
                return null;
            }

            Elements openingOddsDivs = bet365Row.select("div.openingBg1 .oddsItemBox > div.oddItems");
            Elements preMatchOddsDivs = bet365Row.select("div.preMatchBg1 .oddsItemBox > div.oddItems");

            if (openingOddsDivs.size() < 3 || preMatchOddsDivs.size() < 3) {
                return null;
            }

            double open1 = parseDouble(openingOddsDivs.get(0).text());
            double openX = parseDouble(openingOddsDivs.get(1).text());
            double open2 = parseDouble(openingOddsDivs.get(2).text());

            double pre1 = parseDouble(preMatchOddsDivs.get(0).text());
            double preX = parseDouble(preMatchOddsDivs.get(1).text());
            double pre2 = parseDouble(preMatchOddsDivs.get(2).text());

            if (open1 == 0.0 || pre1 == 0.0) {
                return null;
            }

            return checkPatterns(matchTitle, matchUrl, open1, openX, open2, pre1, preX, pre2);

        } catch (Exception e) {
            return null;
        }
    }

    private static double parseDouble(String text) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                return Double.parseDouble(text.trim().replace(',', '.'));
            }
        } catch (NumberFormatException e) {
        }
        return 0.0;
    }

    private static String checkPatterns(String matchTitle, String url, double o1, double oX, double o2, double p1, double pX, double p2) {
        StringBuilder report = new StringBuilder();
        if (p1 == 2.62 || p2 == 2.62) report.append("    -> PATTERN 1 - SİHİRLİ 2.62\n");
        if (pX == p2 || p1 == pX) report.append("    -> PATTERN 2 - ORAN TEKRARI\n");
        if ((o1 < o2 && p1 > p2) || (o2 < o1 && p2 > p1)) report.append("    -> PATTERN 3 - FAVORİ DEĞİŞİMİ\n");
        if ((p1 >= 1.60 && p1 <= 1.69 && pX >= 4.00 && p2 >= 4.70 && p2 <= 4.79) ||
                (p2 >= 1.60 && p2 <= 1.69 && pX >= 4.00 && p1 >= 4.70 && p1 <= 4.79)) {
            report.append("    -> PATTERN 5 - 1.6x / 4.xx / 4.7x ORANI\n");
        }
        if ((p1 == 1.55 && pX == 4.33 && p2 == 5.25) || (p2 == 1.55 && pX == 4.33 && p1 == 5.25)) {
            report.append("    -> YENİ SKOR PATERNİ (1.55 - 4.33 - 5.25)\n");
        }
        if ((p1 == 1.60 && pX == 4.10 && p2 == 4.52) || (p2 == 1.60 && pX == 4.10 && p1 == 4.52)) {
            report.append("    -> YENİ SKOR PATERNİ (1.60 - 4.10 - 4.52)\n");
        }

        if (report.length() > 0) {
            String fullReport = String.format(
                    "\n------------------ [!] PATERN BULUNDU: %s ------------------\n" +
                            "%s" +
                            "    URL: %s\n" +
                            "    Açılış Oranları: %.2f | %.2f | %.2f\n" +
                            "    Kapanış Oranları: %.2f | %.2f | %.2f\n" +
                            "----------------------------------------------------------------------------------\n",
                    matchTitle, report.toString(), url, o1, oX, o2, p1, pX, p2
            );
            System.out.print(fullReport);
            return url;
        }
        return null;
    }
}