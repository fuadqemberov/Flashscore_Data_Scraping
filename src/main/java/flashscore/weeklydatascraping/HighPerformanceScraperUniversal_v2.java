package flashscore.weeklydatascraping;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration; // Timeout için gerekli
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger; // Thread-safe sayaç için gerekli
import java.util.stream.Collectors;

public class HighPerformanceScraperUniversal_v2 {

    private static final String INPUT_FILE_NAME = "tum_mac_linkleri.txt";
    private static final String OUTPUT_EXCEL_FILE = "mac_oranlari_multithread.xlsx";

    public static void main(String[] args) {
        // 1. URL'leri dosyadan oku
        List<String> matchUrls = readUrlsFromFile(INPUT_FILE_NAME);
        if (matchUrls.isEmpty()) {
            System.out.println("Okunacak URL bulunamadı. Program sonlandırılıyor.");
            return;
        }

        // 2. Thread havuzu ve HTTP istemcisini oluştur
        int coreCount = Runtime.getRuntime().availableProcessors();
        System.out.println("Kullanılabilir işlemci çekirdeği: " + coreCount + ". Bu sayıda thread havuzu oluşturuluyor.");
        ExecutorService executor = Executors.newFixedThreadPool(coreCount);

        HttpClient client = HttpClient.newBuilder()
                .executor(executor)
                .build();

        System.out.println(matchUrls.size() + " URL için asenkron tarama başlıyor...");
        long startTime = System.currentTimeMillis();

        // İlerleme takibi için thread-safe sayaç
        AtomicInteger counter = new AtomicInteger(0);
        int totalUrls = matchUrls.size();

        // 3. Her URL için asenkron bir görev (CompletableFuture) oluştur
        List<CompletableFuture<Map<String, String>>> futures = matchUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                .timeout(Duration.ofSeconds(30)) // 30 saniye sonra zaman aşımı
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        // İlerleme durumunu her zaman yazdır
                        int currentCount = counter.incrementAndGet();
                        System.out.println("İşlendi: (" + currentCount + "/" + totalUrls + ") -> " + url);

                        if (response.statusCode() == 200) {
                            Document doc = Jsoup.parse(response.body());
                            return parseMatchDetails(doc); // Başarılıysa veriyi döndür
                        } else {
                            System.err.println("Hata! Yanıt kodu: " + response.statusCode() + " -> " + url);
                            return null; // Başarısızsa null döndür
                        }
                    } catch (java.net.http.HttpTimeoutException e) {
                        System.err.println("Zaman Aşımı! URL işlenemedi: " + url);
                        return null;
                    } catch (IOException | InterruptedException e) {
                        System.err.println("URL işlenirken hata oluştu: " + url + " - " + e.getMessage());
                        return null;
                    }
                }, executor))
                .collect(Collectors.toList());

        // 4. Tüm asenkron görevlerin tamamlanmasını bekle
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        // 5. Tamamlanan görevlerden sonuçları topla
        CompletableFuture<List<Map<String, String>>> allFuturesResult = allOf.thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .filter(result -> result != null && !result.isEmpty()) // Hatalı veya boş sonuçları atla
                        .collect(Collectors.toList())
        );

        List<Map<String, String>> allMatchesData = allFuturesResult.join(); // Nihai sonuç listesini al

        // 6. Thread havuzunu düzgünce kapat
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        System.out.println("\nTarama tamamlandı. Toplam " + allMatchesData.size() + " maç verisi başarıyla çekildi.");
        System.out.println("Toplam süre: " + (endTime - startTime) / 1000.0 + " saniye");

        // 7. Sonuçları Excel'e yaz
        if (!allMatchesData.isEmpty()) {
            writeToExcel(allMatchesData);
        } else {
            System.out.println("Excel'e yazılacak veri bulunamadı.");
        }
    }

    private static List<String> readUrlsFromFile(String fileName) {
        List<String> urls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    urls.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.err.println("URL dosyası okunurken hata: " + e.getMessage());
        }
        return urls;
    }

    private static Map<String, String> parseMatchDetails(Document doc) {
        Map<String, String> data = new LinkedHashMap<>();
        try {
            Element h1 = doc.select("h1").first();
            if (h1 != null) {
                String matchTitle = h1.ownText().trim();
                String matchDate = h1.select("small.text-muted").text().replace("(", "").replace(")", "").trim();
                data.put("Tarih", matchDate);
                String[] teams = matchTitle.split("\\s+vs\\s+");
                data.put("Ev Sahibi", teams.length > 0 ? teams[0].trim() : matchTitle);
                data.put("Deplasman", teams.length > 1 ? teams[1].trim() : "N/A");
            }

            String htScore = doc.select("span.badge:contains(HT:)").text().replace("HT: ", "").trim();
            String ftScore = doc.select("span.badge:contains(FT:)").text().replace("FT: ", "").trim();
            data.put("HT Skor", htScore.isEmpty() ? "N/A" : htScore);
            data.put("FT Skor", ftScore.isEmpty() ? "N/A" : ftScore);

            Elements betTypeContainers = doc.select("div.container.mt-3");
            for (Element container : betTypeContainers) {
                String betTypeTitle = container.select("div.table-dark > div.col").text().trim();
                if (betTypeTitle.isEmpty()) continue;
                Elements rows = container.select("div.row > div[class*='col-sm-']");
                for (Element row : rows) {
                    Elements cols = row.select("div.row > div.col-4, div.row > div.col-2");
                    if (cols.size() >= 2) {
                        String optionName = cols.get(0).text().trim();
                        String openingOdd = cols.get(1).text().trim();
                        String header = betTypeTitle + " - " + optionName;
                        data.put(header, openingOdd);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Sayfa parse edilirken bir hata oluştu: " + e.getMessage());
        }
        return data;
    }

    private static void writeToExcel(List<Map<String, String>> allMatchesData) {
        Map<String, Void> headers = new LinkedHashMap<>();
        headers.put("Tarih", null);
        headers.put("Ev Sahibi", null);
        headers.put("Deplasman", null);
        headers.put("HT Skor", null);
        headers.put("FT Skor", null);

        for (Map<String, String> matchData : allMatchesData) {
            matchData.keySet().forEach(key -> headers.put(key, null));
        }

        List<String> headerList = new ArrayList<>(headers.keySet());

        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(OUTPUT_EXCEL_FILE)) {
            Sheet sheet = workbook.createSheet("Maç Oranları");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headerList.size(); i++) {
                headerRow.createCell(i).setCellValue(headerList.get(i));
            }

            int rowNum = 1;
            for (Map<String, String> matchData : allMatchesData) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < headerList.size(); i++) {
                    String header = headerList.get(i);
                    String value = matchData.getOrDefault(header, "");
                    row.createCell(i).setCellValue(value);
                }
            }

            for (int i = 0; i < headerList.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(fos);
            System.out.println("\nVeriler başarıyla '" + OUTPUT_EXCEL_FILE + "' dosyasına yazıldı.");
        } catch (IOException e) {
            System.err.println("Excel dosyası oluşturulurken hata: " + e.getMessage());
        }
    }
}