package flashscore.weeklydatascraping;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class TurboAzScraper {

    // ═══════════════════════════════════════════════════════
    //  KONFİQURASİYA
    // ═══════════════════════════════════════════════════════
    private static final String BASE_URL   = "https://turbo.az";
    private static final String LIST_URL   = BASE_URL + "/autos?page=";
    private static final int    MAX_PAGES  = 417;   // bütün sayt

    // List səhifələri arası gözləmə (ms) — random seçilir
    private static final int LIST_DELAY_MIN = 1500;
    private static final int LIST_DELAY_MAX = 3000;

    // Detail səhifələri arası gözləmə (ms) — random seçilir
    private static final int DETAIL_DELAY_MIN = 800;
    private static final int DETAIL_DELAY_MAX = 2000;

    // Eyni anda neçə detail yüklənsin (az saxla — blok olmamaq üçün)
    private static final int DETAIL_THREADS = 3;

    // Xəta olduqda neçə dəfə yenidən cəhd et
    private static final int MAX_RETRY = 4;

    // 429/503 aldıqda neçə ms gözlə
    private static final int BLOCKED_PAUSE_MS = 30_000;

    // ═══════════════════════════════════════════════════════
    //  ROTATING USER-AGENT-LƏR
    // ═══════════════════════════════════════════════════════
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) " +
            "Gecko/20100101 Firefox/125.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4_1) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.4.1 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 Edg/123.0.0.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    };

    private static final Random RND = new Random();

    // ═══════════════════════════════════════════════════════
    //  EXCEL SÜTUNLARI
    // ═══════════════════════════════════════════════════════
    private static final String[] COLUMNS = {
            "ilan_url", "başlıq", "qiymət_azn", "qiymət_usd",
            "şəhər", "marka", "model", "buraxılış_ili",
            "ban_növü", "rəng", "mühərrik", "yürüş",
            "sürətlər_qutusu", "ötürücü", "yeni", "yerlərin_sayı"
    };

    private static final Set<String> SKIP_FIELDS = new HashSet<>(Arrays.asList(
            "vəziyyəti", "əlavə_seçimlər", "hansı_bazar_üçün_yığılıb", "sahiblər"
    ));

    // ═══════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {

        List<Map<String, String>> allCars =
                Collections.synchronizedList(new ArrayList<>());

        RequestConfig reqConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(20))
                .setResponseTimeout(Timeout.ofSeconds(45))
                .build();

        try (CloseableHttpClient http = HttpClients.custom()
                .setDefaultRequestConfig(reqConfig)
                .build()) {

            // ── 1. LIST SƏHİFƏLƏRİ ─────────────────────────────
            for (int page = 1; page <= MAX_PAGES; page++) {
                String url = LIST_URL + page;
                System.out.printf("📄 Səhifə %d: %s%n", page, url);

                String html = fetchWithRetry(http, url);
                if (html == null) {
                    System.out.println("   ⛔ Səhifə alınmadı, dayandırıldı.");
                    break;
                }

                List<Map<String, String>> batch = parseList(html);
                if (batch.isEmpty()) {
                    System.out.println("   📭 Son səhifəyə çatıldı.");
                    break;
                }

                allCars.addAll(batch);
                System.out.printf("   ✅ %d elan (cəmi: %d)%n",
                        batch.size(), allCars.size());

                // Random delay — bot kimi görünməmək üçün
                randomSleep(LIST_DELAY_MIN, LIST_DELAY_MAX);
            }

            if (allCars.isEmpty()) {
                System.out.println("❌ Heç bir elan tapılmadı!");
                return;
            }

            // ── 2. DETAIL SƏHİFƏLƏR ─────────────────────────────
            System.out.printf("%n🔄 %d elan üçün detail yüklənir " +
                              "(%d paralel thread)...%n", allCars.size(), DETAIL_THREADS);

            AtomicInteger done = new AtomicInteger(0);
            int total = allCars.size();

            // Məhdud sayda thread — saytı boğmamaq üçün
            ExecutorService exec = Executors.newFixedThreadPool(DETAIL_THREADS,
                    r -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    });

            // Semaphore ilə eyni anda DETAIL_THREADS-dan çox sorğu getməsin
            Semaphore sem = new Semaphore(DETAIL_THREADS);

            List<Future<?>> futures = new ArrayList<>();
            for (Map<String, String> car : allCars) {
                sem.acquire();
                futures.add(exec.submit(() -> {
                    try {
                        parseDetail(http, car);
                        int n = done.incrementAndGet();
                        if (n % 50 == 0 || n == total)
                            System.out.printf("   📊 %d/%d tamamlandı%n",
                                    n, total);
                    } catch (Exception e) {
                        System.err.printf("⚠️  %s → %s%n",
                                car.get("ilan_url"), e.getMessage());
                    } finally {
                        sem.release();
                    }
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
            exec.shutdown();

            // ── 3. EXCEL ─────────────────────────────────────────
            writeExcel(allCars);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FETCH WITH RETRY + ANTI-BLOCK
    //
    //  Strategiyalar:
    //  1. Hər sorğuda fərqli User-Agent
    //  2. Tam browser header-ları (Referer, Accept-Encoding vs.)
    //  3. HTTP status yoxla:
    //     - 200 → OK
    //     - 429 / 503 → bloklandın, 30 san gözlə, yenidən cəhd et
    //     - 404 → elan silinib, null qaytar
    //  4. IOException olsa 3 san gözlə, yenidən cəhd et
    // ═══════════════════════════════════════════════════════════
    private static String fetchWithRetry(CloseableHttpClient http, String url) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                HttpGet req = new HttpGet(url);

                // Rotating User-Agent
                req.setHeader("User-Agent",
                        USER_AGENTS[RND.nextInt(USER_AGENTS.length)]);

                // Real browser-a bənzər header-lar
                req.setHeader("Accept",
                        "text/html,application/xhtml+xml," +
                        "application/xml;q=0.9,image/avif," +
                        "image/webp,*/*;q=0.8");
                req.setHeader("Accept-Language",
                        "az-AZ,az;q=0.9,en-US;q=0.8,en;q=0.7");
                req.setHeader("Accept-Encoding", "gzip, deflate, br");
                req.setHeader("Connection", "keep-alive");
                req.setHeader("Upgrade-Insecure-Requests", "1");
                req.setHeader("Sec-Fetch-Dest", "document");
                req.setHeader("Sec-Fetch-Mode", "navigate");
                req.setHeader("Sec-Fetch-Site", "none");
                req.setHeader("Cache-Control", "max-age=0");
                // Referer əlavə et — sanki əvvəlki səhifədən gəlmisən
                req.setHeader("Referer", BASE_URL + "/autos");

                // HTTP cavabını statusla birlikdə al
                String[] result = {null};
                int[] statusCode = {0};

                http.execute(req, (ClassicHttpResponse response) -> {
                    statusCode[0] = response.getCode();
                    if (statusCode[0] == 200) {
                        result[0] = EntityUtils.toString(
                                response.getEntity(), "UTF-8");
                    } else {
                        EntityUtils.consume(response.getEntity());
                    }
                    return null;
                });

                if (statusCode[0] == 200 && result[0] != null) {
                    return result[0];
                }

                // 429 = Too Many Requests, 503 = Service Unavailable
                if (statusCode[0] == 429 || statusCode[0] == 503) {
                    System.out.printf("   🚫 HTTP %d — %d san gözlənilir " +
                                      "(cəhd %d/%d)%n",
                            statusCode[0], BLOCKED_PAUSE_MS / 1000,
                            attempt, MAX_RETRY);
                    Thread.sleep(BLOCKED_PAUSE_MS);
                    continue;
                }

                // 404 — elan yoxdur
                if (statusCode[0] == 404) {
                    return null;
                }

                System.out.printf("   ⚠️  HTTP %d — %s (cəhd %d/%d)%n",
                        statusCode[0], url, attempt, MAX_RETRY);

            } catch (Exception e) {
                System.out.printf("   ⚠️  Bağlantı xətası (cəhd %d/%d): %s%n",
                        attempt, MAX_RETRY, e.getMessage());
                if (attempt < MAX_RETRY) {
                    try { Thread.sleep(3000L * attempt); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        System.err.println("   ⛔ " + MAX_RETRY + " cəhddən sonra alınmadı: " + url);
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  RANDOM SLEEP
    // ═══════════════════════════════════════════════════════════
    private static void randomSleep(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + RND.nextInt(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LIST SƏHİFƏSİ PARSE
    // ═══════════════════════════════════════════════════════════
    private static List<Map<String, String>> parseList(String html) {
        List<Map<String, String>> result = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select("div.products-i");

        for (Element card : cards) {
            Map<String, String> car = new LinkedHashMap<>();

            String href = card.select("a.products-i__link").attr("href");
            if (href.isBlank())
                href = card.select("a[href*='/autos/']").attr("href");
            if (href.isBlank()) continue;

            car.put("ilan_url", BASE_URL + href);
            car.put("başlıq",
                    card.select("div.products-i__name").text().trim());
            car.put("qiymət_azn",
                    card.select("div.products-i__price")
                            .text().replace("\u00a0", " ").trim());

            String dt = card.select("div.products-i__datetime").text().trim();
            if (dt.contains(","))
                car.put("şəhər", dt.split(",")[0].trim());

            result.add(car);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  DETAIL SƏHİFƏSİ PARSE
    // ═══════════════════════════════════════════════════════════
    private static void parseDetail(CloseableHttpClient http,
                                    Map<String, String> car) throws Exception {
        String url = car.get("ilan_url");
        if (url == null) return;

        String html = fetchWithRetry(http, url);
        if (html == null) return;

        Document doc = Jsoup.parse(html);

        // Başlıq
        String h1 = doc.select("h1.product-title").text().trim();
        if (!h1.isBlank()) car.put("başlıq", h1);

        // AZN qiymət
        String azn = doc.select(
                "div.product-price__i.product-price__i--bold").text().trim();
        if (!azn.isBlank())
            car.put("qiymət_azn",
                    azn.replace("≈", "").replace("\u00a0", " ").trim());

        // USD qiymət
        for (Element pi : doc.select("div.product-price__i")) {
            String t = pi.text().trim();
            if (t.contains("USD")) {
                car.put("qiymət_usd", t.replace("\u00a0", " ").trim());
                break;
            }
        }

        // Xüsusiyyətlər
        for (Element prop : doc.select("div.product-properties__i")) {
            String label = prop
                    .select("label.product-properties__i-name").text().trim();
            String value = prop
                    .select("span.product-properties__i-value").text().trim();
            if (label.isBlank() || value.isBlank()) continue;
            String key = labelToKey(label);
            if (!SKIP_FIELDS.contains(key)) car.put(key, value);
        }

        // Detail-lər arası random gözləmə — blok olmamaq üçün
        randomSleep(DETAIL_DELAY_MIN, DETAIL_DELAY_MAX);
    }

    // ═══════════════════════════════════════════════════════════
    //  LABEL → AÇAR
    // ═══════════════════════════════════════════════════════════
    private static String labelToKey(String label) {
        switch (label) {
            case "Şəhər":           return "şəhər";
            case "Marka":           return "marka";
            case "Model":           return "model";
            case "Buraxılış ili":   return "buraxılış_ili";
            case "Ban növü":        return "ban_növü";
            case "Rəng":            return "rəng";
            case "Mühərrik":        return "mühərrik";
            case "Yürüş":           return "yürüş";
            case "Sürətlər qutusu": return "sürətlər_qutusu";
            case "Ötürücü":         return "ötürücü";
            case "Yeni":            return "yeni";
            case "Yerlərin sayı":   return "yerlərin_sayı";
            case "Vəziyyəti":       return "vəziyyəti";
            default:
                return label.toLowerCase()
                        .replace(" ", "_").replace("/", "_");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EXCEL YAZMA
    // ═══════════════════════════════════════════════════════════
    private static void writeExcel(List<Map<String, String>> cars)
            throws IOException {

        Set<String> allKeys = new LinkedHashSet<>(Arrays.asList(COLUMNS));
        for (Map<String, String> car : cars)
            for (String k : car.keySet())
                if (!SKIP_FIELDS.contains(k)) allKeys.add(k);
        String[] cols = allKeys.toArray(new String[0]);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Turbo.az");

            CellStyle hs = wb.createCellStyle();
            hs.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hs.setBorderBottom(BorderStyle.MEDIUM);
            Font hf = wb.createFont();
            hf.setColor(IndexedColors.WHITE.getIndex());
            hf.setBold(true);
            hs.setFont(hf);

            CellStyle zs = wb.createCellStyle();
            zs.setFillForegroundColor(
                    IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            zs.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row hRow = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hs);
            }

            int rn = 1;
            for (Map<String, String> car : cars) {
                Row row = sheet.createRow(rn++);
                for (int i = 0; i < cols.length; i++) {
                    Cell c = row.createCell(i);
                    if (rn % 2 == 0) c.setCellStyle(zs);
                    c.setCellValue(car.getOrDefault(cols[i], ""));
                }
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) > 14000)
                    sheet.setColumnWidth(i, 14000);
            }
            sheet.createFreezePane(0, 1);

            String out = "turbo_az_cars.xlsx";
            try (FileOutputStream fos = new FileOutputStream(out)) {
                wb.write(fos);
            }
            System.out.printf("%n🎉 Bitdi! %d avtomobil → %s%n",
                    cars.size(), out);
        }
    }
}