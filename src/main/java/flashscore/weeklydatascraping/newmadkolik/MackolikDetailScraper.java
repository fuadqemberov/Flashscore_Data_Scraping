package flashscore.weeklydatascraping.newmadkolik;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.*;
import java.util.concurrent.*;

public class MackolikDetailScraper {

    private static final String BASE_URL = "https://www.mackolik.com";
    private static final String URL_FILE = "tum_mac_urlleri.txt";
    private static final String EXCEL_FILE = "mackolik_datas.xlsx";
    private static final int THREAD_COUNT = 10;

    public static void main(String[] args) throws InterruptedException {
        List<String> urlsFromFile = readUrlsFromFile(URL_FILE);
        if (urlsFromFile.isEmpty()) {
            System.out.println(URL_FILE + " dosyası boş veya bulunamadı. İşlem durduruluyor.");
            return;
        }
        System.out.println(urlsFromFile.size() + " adet URL dosyadan okundu.");

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<MatchData2>> futures = new ArrayList<>();
        int counter = 0;
        for (String urlFromFile : urlsFromFile) {
            final int current = ++counter;
            futures.add(executor.submit(() -> {
                System.out.println("\n----------------------------------------------------");
                System.out.printf("İşleniyor: [%d/%d] - Kaynak: %s%n", current, urlsFromFile.size(), urlFromFile);
                try {
                    return scrapeMatchDetails(urlFromFile);
                } catch (Exception e) {
                    System.err.println("URL işlenirken bir hata oluştu ve atlandı: " + urlFromFile);
                    System.err.println("Hata: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    return null;
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.MINUTES);

        List<MatchData2> allMatchesData = new ArrayList<>();
        for (Future<MatchData2> f : futures) {
            try {
                MatchData2 data = f.get();
                if (data != null) allMatchesData.add(data);
            } catch (Exception ignored) {}
        }

        writeToExcel(allMatchesData, EXCEL_FILE);
    }

    private static MatchData2 scrapeMatchDetails(String urlFromFile) {
        String fullUrl;
        String urlPath;

        if (urlFromFile.trim().startsWith("http")) {
            fullUrl = urlFromFile.trim();
            urlPath = fullUrl.replace(BASE_URL, "");
        } else {
            fullUrl = BASE_URL + urlFromFile.trim();
            urlPath = urlFromFile.trim();
        }

        MatchData2 data = new MatchData2(fullUrl);

        int lastSlashIndex = urlPath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            System.err.println("  -> Geçersiz URL formatı: " + urlPath);
            return null;
        }
        String urlBase = urlPath.substring(0, lastSlashIndex);
        String matchId = urlPath.substring(lastSlashIndex);
        String h2hUrl = BASE_URL + urlBase + "/karsilastirma" + matchId;
        String oddsUrl = BASE_URL + urlBase + "/iddaa" + matchId;

        parseMainPage(data, fullUrl);
        parseH2HPage(data, h2hUrl);
        parseOddsPage(data, oddsUrl);

        data.print();
        return data;
    }

    private static void parseMainPage(MatchData2 data, String url) {
        try {
            Document doc = fetchDocument(url);
            Element homeImg = doc.selectFirst("a.p0c-soccer-match-details-header__team-crest--home img");
            Element awayImg = doc.selectFirst("a.p0c-soccer-match-details-header__team-crest--away img");
            Element homeScore = doc.selectFirst("span.p0c-soccer-match-details-header__score-home");
            Element awayScore = doc.selectFirst("span.p0c-soccer-match-details-header__score-away");
            Element halfTime = doc.selectFirst(".p0c-soccer-match-details-header__detailed-score");

            if (homeImg != null) data.setHomeTeam(homeImg.attr("alt"));
            if (awayImg != null) data.setAwayTeam(awayImg.attr("alt"));
            if (homeScore != null && awayScore != null)
                data.setFinalScore(homeScore.text() + " - " + awayScore.text());
            if (halfTime != null)
                data.setHalfTimeScore(halfTime.text().replace("(", "").replace(")", "").trim());
        } catch (Exception e) {
            System.err.println("  -> Ana sayfa verileri çekilirken hata: " + e.getMessage());
        }
    }

    private static void parseH2HPage(MatchData2 data, String url) {
        try {
            Document doc = fetchDocument(url);
            Element homeBlock = doc.selectFirst("div.p0c-match-head-to-head__played-matches--total");
            Element awayBlock = doc.selectFirst("div.p0c-match-head-to-head__played-matches--at-home");

            if (homeBlock != null)
                data.setHomeTeamLast5Scores(parseLastMatchScores(homeBlock));
            if (awayBlock != null)
                data.setAwayTeamLast5Scores(parseLastMatchScores(awayBlock));
        } catch (Exception e) {
            System.err.println("  -> H2H verileri çekilirken hata: " + e.getMessage());
        }
    }

    private static List<String> parseLastMatchScores(Element block) {
        List<String> lastScores = new ArrayList<>();
        Elements matchRows = block.select(".p0c-match-head-to-head__last-games--row");
        for (Element row : matchRows) {
            Element score = row.selectFirst(".p0c-match-head-to-head__last-games--score");
            if (score != null) lastScores.add(score.text().trim());
        }
        return lastScores;
    }

    private static void parseOddsPage(MatchData2 data, String url) {
        try {
            Document doc = fetchDocument(url);
            // Maç Sonucu
            Element msMarket = doc.selectFirst("li[data-market='1']");
            if (msMarket != null) {
                Elements msOdds = msMarket.select("ul:not(.hidden) .widget-iddaa-markets__option .widget-iddaa-markets__value");
                if (msOdds.size() >= 3) {
                    data.setMs1(msOdds.get(0).text());
                    data.setMsX(msOdds.get(1).text());
                    data.setMs2(msOdds.get(2).text());
                }
            }
            // Çifte Şans
            Element dcMarket = doc.selectFirst("li[data-market='3']");
            if (dcMarket != null) {
                Elements dcOdds = dcMarket.select("ul:not(.hidden) .widget-iddaa-markets__option .widget-iddaa-markets__value");
                if (dcOdds.size() >= 3) {
                    data.setDc1X(dcOdds.get(0).text());
                    data.setDc12(dcOdds.get(1).text());
                    data.setDcX2(dcOdds.get(2).text());
                }
            }
        } catch (Exception e) {
            System.err.println("  -> İddaa verileri çekilirken hata: " + e.getMessage());
        }
    }

    private static Document fetchDocument(String urlString) throws IOException {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

                int status = conn.getResponseCode();
                if (status != 200) throw new IOException("HTTP hata kodu: " + status);

                try (InputStream is = conn.getInputStream()) {
                    return Jsoup.parse(is, "UTF-8", urlString);
                }
            } catch (Exception e) {
                if (i == maxRetries - 1)
                    throw new IOException(e);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw new IOException("Bağlantı başarısız: " + urlString);
    }

    private static List<String> readUrlsFromFile(String fileName) {
        List<String> urls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) urls.add(line.trim());
            }
        } catch (IOException e) {
            System.err.println("URL dosyası okunurken hata: " + e.getMessage());
        }
        return urls;
    }

    private static void writeToExcel(List<MatchData2> allData, String fileName) {
        if (allData.isEmpty()) {
            System.out.println("Excel'e yazılacak veri bulunamadı.");
            return;
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Maç Verileri");

            String[] headers = {
                    "URL", "Ev Sahibi", "Deplasman", "MS Skoru", "İY Skoru",
                    "MS 1", "MS X", "MS 2", "ÇŞ 1-X", "ÇŞ 1-2", "ÇŞ X-2",
                    "Ev S. Son Maç 1 (En Son)", "Ev S. Son Maç 2", "Ev S. Son Maç 3", "Ev S. Son Maç 4", "Ev S. Son Maç 5",
                    "Dep. Son Maç 1 (En Son)", "Dep. Son Maç 2", "Dep. Son Maç 3", "Dep. Son Maç 4", "Dep. Son Maç 5"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (MatchData2 data : allData) {
                Row row = sheet.createRow(rowNum++);
                int cellNum = 0;
                row.createCell(cellNum++).setCellValue(data.getOriginalUrl());
                row.createCell(cellNum++).setCellValue(data.getHomeTeam());
                row.createCell(cellNum++).setCellValue(data.getAwayTeam());
                row.createCell(cellNum++).setCellValue(data.getFinalScore());
                row.createCell(cellNum++).setCellValue(data.getHalfTimeScore());
                row.createCell(cellNum++).setCellValue(data.getMs1());
                row.createCell(cellNum++).setCellValue(data.getMsX());
                row.createCell(cellNum++).setCellValue(data.getMs2());
                row.createCell(cellNum++).setCellValue(data.getDc1X());
                row.createCell(cellNum++).setCellValue(data.getDc12());
                row.createCell(cellNum++).setCellValue(data.getDcX2());
                row.createCell(cellNum++).setCellValue(data.getHomeLast1());
                row.createCell(cellNum++).setCellValue(data.getHomeLast2());
                row.createCell(cellNum++).setCellValue(data.getHomeLast3());
                row.createCell(cellNum++).setCellValue(data.getHomeLast4());
                row.createCell(cellNum++).setCellValue(data.getHomeLast5());
                row.createCell(cellNum++).setCellValue(data.getAwayLast1());
                row.createCell(cellNum++).setCellValue(data.getAwayLast2());
                row.createCell(cellNum++).setCellValue(data.getAwayLast3());
                row.createCell(cellNum++).setCellValue(data.getAwayLast4());
                row.createCell(cellNum++).setCellValue(data.getAwayLast5());
            }
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
            }
            System.out.println("\nVeriler başarıyla '" + fileName + "' dosyasına yazıldı.");
        } catch (IOException e) {
            System.err.println("Excel dosyası oluşturulurken hata: " + e.getMessage());
        }
    }


    /**
     * Tek bir maçın tüm verilerini tutmak için kullanılan yardımcı sınıf (POJO).
     */
    public static class MatchData2 {
        private String originalUrl;
        private String homeTeam = "N/A", awayTeam = "N/A", finalScore = "N/A", halfTimeScore = "N/A";
        private String ms1 = "N/A", msX = "N/A", ms2 = "N/A";
        private String dc1X = "N/A", dc12 = "N/A", dcX2 = "N/A";
        private String homeLast1 = "-", homeLast2 = "-", homeLast3 = "-", homeLast4 = "-", homeLast5 = "-";
        private String awayLast1 = "-", awayLast2 = "-", awayLast3 = "-", awayLast4 = "-", awayLast5 = "-";

        public MatchData2(String originalUrl) { this.originalUrl = originalUrl; }

        public void print() {
            System.out.printf("  > %s vs %s | Skor: %s | Ev S. Son 5: %s, %s, %s, %s, %s%n",
                    homeTeam, awayTeam, finalScore, homeLast1, homeLast2, homeLast3, homeLast4, homeLast5);
        }

        public void setHomeTeamLast5Scores(List<String> scores) {
            if (scores.isEmpty()) return;
            if (scores.size() > 0) this.homeLast1 = scores.get(0);
            if (scores.size() > 1) this.homeLast2 = scores.get(1);
            if (scores.size() > 2) this.homeLast3 = scores.get(2);
            if (scores.size() > 3) this.homeLast4 = scores.get(3);
            if (scores.size() > 4) this.homeLast5 = scores.get(4);
        }

        public void setAwayTeamLast5Scores(List<String> scores) {
            if (scores.isEmpty()) return;
            if (scores.size() > 0) this.awayLast1 = scores.get(0);
            if (scores.size() > 1) this.awayLast2 = scores.get(1);
            if (scores.size() > 2) this.awayLast3 = scores.get(2);
            if (scores.size() > 3) this.awayLast4 = scores.get(3);
            if (scores.size() > 4) this.awayLast5 = scores.get(4);
        }

        // --- Getter/Setter Metotları ---
        public String getOriginalUrl() { return originalUrl; }
        public String getHomeTeam() { return homeTeam; }
        public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
        public String getAwayTeam() { return awayTeam; }
        public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
        public String getFinalScore() { return finalScore; }
        public void setFinalScore(String finalScore) { this.finalScore = finalScore; }
        public String getHalfTimeScore() { return halfTimeScore; }
        public void setHalfTimeScore(String halfTimeScore) { this.halfTimeScore = halfTimeScore; }
        public String getMs1() { return ms1; }
        public void setMs1(String ms1) { this.ms1 = ms1; }
        public String getMsX() { return msX; }
        public void setMsX(String msX) { this.msX = msX; }
        public String getMs2() { return ms2; }
        public void setMs2(String ms2) { this.ms2 = ms2; }
        public String getDc1X() { return dc1X; }
        public void setDc1X(String dc1x) { this.dc1X = dc1x; }
        public String getDc12() { return dc12; }
        public void setDc12(String dc12) { this.dc12 = dc12; }
        public String getDcX2() { return dcX2; }
        public void setDcX2(String dcX2) { this.dcX2 = dcX2; }
        public String getHomeLast1() { return homeLast1; }
        public String getHomeLast2() { return homeLast2; }
        public String getHomeLast3() { return homeLast3; }
        public String getHomeLast4() { return homeLast4; }
        public String getHomeLast5() { return homeLast5; }
        public String getAwayLast1() { return awayLast1; }
        public String getAwayLast2() { return awayLast2; }
        public String getAwayLast3() { return awayLast3; }
        public String getAwayLast4() { return awayLast4; }
        public String getAwayLast5() { return awayLast5; }
    }
}