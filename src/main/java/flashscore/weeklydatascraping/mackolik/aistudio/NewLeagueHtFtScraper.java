package flashscore.weeklydatascraping.mackolik.aistudio;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NewLeagueHtFtScraper {

    private static final String INPUT_FILE_NAME = "takim_idleri.txt";
    private static final String OUTPUT_DIRECTORY_NAME = "excel";
    private static final String OUTPUT_FILE_NAME = "Tum_Takimlar_Geri_Donus_Analizi.xlsx";
    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2025;
    private static final int THREAD_COUNT = 16;

    // Tüm sonuçları toplamak için synchronized list
    private static final List<ResultRow> ALL_RESULTS = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        createOutputDirectory();
        List<String> teamIds = readTeamIdsFromFile();
        if (teamIds.isEmpty()) {
            System.out.println(INPUT_FILE_NAME + " dosyası bulunamadı veya içi boş. İşlem durduruldu.");
            return;
        }

        System.out.println("Toplam " + teamIds.size() + " takım için analiz başlatılıyor...");
        System.out.println("Thread sayısı: " + THREAD_COUNT);

        // ExecutorService ile thread pool oluştur
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Her takım için bir task oluştur
        List<Future<Void>> futures = new ArrayList<>();

        for (String teamId : teamIds) {
            Future<Void> future = executor.submit(() -> {
                try {
                    processTeam(teamId, processedCount, successCount, teamIds.size());
                    return null;
                } catch (Exception e) {
                    System.err.println("HATA: " + teamId + " işlenirken hata oluştu: " + e.getMessage());
                    return null;
                }
            });
            futures.add(future);
        }

        // Tüm taskların tamamlanmasını bekle
        try {
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            System.err.println("Thread execution hatası: " + e.getMessage());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Tüm takımlar işlendikten sonra tek Excel dosyasına yaz
        if (!ALL_RESULTS.isEmpty()) {
            try {
                exportAllToExcel(ALL_RESULTS, OUTPUT_FILE_NAME);
                System.out.println("\n*** TÜM VERİLER BAŞARIYLA KAYDEDİLDİ ***");
                System.out.println("Toplam " + ALL_RESULTS.size() + " geri dönüş bulundu.");
                System.out.println("Dosya: " + Paths.get(OUTPUT_DIRECTORY_NAME, OUTPUT_FILE_NAME).toAbsolutePath());
            } catch (IOException e) {
                System.err.println("HATA: Excel dosyası oluşturulurken hata: " + e.getMessage());
            }
        } else {
            System.out.println("\n*** BİLGİ: Hiç geri dönüş bulunamadı. ***");
        }

        System.out.println("\n*** TÜM İŞLEMLER TAMAMLANDI ***");
        System.out.println("Toplam işlenen: " + processedCount.get() + "/" + teamIds.size());
        System.out.println("Başarılı: " + successCount.get());
    }

    private static void processTeam(String teamId, AtomicInteger processedCount,
                                    AtomicInteger successCount, int totalTeams) {
        System.out.println("\n=======================================================");
        System.out.println("İŞLENEN TAKIM ID: " + teamId + " [Thread: " + Thread.currentThread().getName() + "]");
        System.out.println("=======================================================");

        List<ResultRow> allSeasonsResults = Collections.synchronizedList(new ArrayList<>());

        // Sezonları da paralel işle
        ExecutorService seasonExecutor = Executors.newFixedThreadPool(4); // Her takım için 4 thread
        List<Future<List<ResultRow>>> seasonFutures = new ArrayList<>();

        for (int year = END_YEAR; year >= START_YEAR; year--) {
            final int currentYear = year;
            Future<List<ResultRow>> seasonFuture = seasonExecutor.submit(() -> {
                String season = currentYear + "/" + (currentYear + 1);
                try {
                    List<ResultRow> seasonResults = findComebacksForSeason(teamId, season);
                    // Sunucuyu yormamak için rastgele bekleme (300-700ms arası)
                    Thread.sleep(300 + (int)(Math.random() * 400));
                    return seasonResults;
                } catch (IOException e) {
                    System.err.println("HATA: " + teamId + " ID'li takımın " + season +
                            " sezonu analiz edilirken ağ hatası: " + e.getMessage());
                    return new ArrayList<>();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ArrayList<>();
                }
            });
            seasonFutures.add(seasonFuture);
        }

        // Sezon sonuçlarını topla
        try {
            for (Future<List<ResultRow>> future : seasonFutures) {
                List<ResultRow> seasonResults = future.get();
                allSeasonsResults.addAll(seasonResults);
            }
        } catch (Exception e) {
            System.err.println("Sezon işleme hatası: " + e.getMessage());
        } finally {
            seasonExecutor.shutdown();
        }

        if (!allSeasonsResults.isEmpty()) {
            // Sonuçları global listeye ekle
            ALL_RESULTS.addAll(allSeasonsResults);
            successCount.incrementAndGet();
            System.out.println("\n-> BAŞARILI: " + teamId + " ID'li takımın analizi tamamlandı. " +
                    "Toplam " + allSeasonsResults.size() + " geri dönüş bulundu.");
        } else {
            System.out.println("\n-> BİLGİ: " + teamId + " ID'li takım için kriterlere uygun maç bulunamadı.");
        }

        int completed = processedCount.incrementAndGet();
        System.out.println("İlerleme: " + completed + "/" + totalTeams +
                " (" + String.format("%.1f", (completed * 100.0 / totalTeams)) + "%)");
    }

    private static void createOutputDirectory() {
        Path path = Paths.get(OUTPUT_DIRECTORY_NAME);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                System.out.println("'" + OUTPUT_DIRECTORY_NAME + "' klasörü oluşturuldu.");
            } catch (IOException e) {
                System.err.println("HATA: Çıktı klasörü oluşturulamadı: " + e.getMessage());
            }
        }
    }

    private static List<String> readTeamIdsFromFile() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(INPUT_FILE_NAME)));
            return Arrays.asList(content.split("\\s*,\\s*"));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static List<ResultRow> findComebacksForSeason(String teamId, String season) throws IOException {
        System.out.println("-> Sezon: " + season + " | Takım ID: " + teamId +
                " | Thread: " + Thread.currentThread().getName());

        String teamUrl = "https://arsiv.mackolik.com/Team/Default.aspx?id=" + teamId + "&season=" + season;
        Document doc = Jsoup.connect(teamUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(15000)
                .get();

        List<MatchData> leagueMatchesOnly = new ArrayList<>();
        Elements allRows = doc.select("table#tblFixture > tbody > tr");

        boolean isScrapingLeague = false;

        for (Element row : allRows) {
            if (row.hasClass("competition")) {
                if (isScrapingLeague) {
                    break;
                } else {
                    isScrapingLeague = true;
                    continue;
                }
            }

            if (isScrapingLeague && row.hasClass("row")) {
                String ftScore = row.select("td:nth-child(5) a").text().trim();
                String htScore = row.select("td:nth-child(9)").text().trim();
                if (ftScore.isEmpty() || htScore.isEmpty() || !ftScore.contains("-") || !htScore.contains("-"))
                    continue;

                MatchData match = new MatchData();
                match.season = season;
                match.date = row.select("td:nth-child(1)").text();
                match.homeTeam = row.select("td:nth-child(3)").text().trim();
                match.awayTeam = row.select("td:nth-child(7)").text().trim();
                match.ftScore = ftScore;
                match.htScore = htScore;
                match.teamId = teamId; // Takım ID'sini de kaydet
                leagueMatchesOnly.add(match);
            }
        }

        List<ResultRow> comebackResults = new ArrayList<>();
        for (int i = 0; i < leagueMatchesOnly.size(); i++) {
            MatchData match = leagueMatchesOnly.get(i);
            try {
                String[] ftScores = match.ftScore.split("\\s*-\\s*");
                String[] htScores = match.htScore.split("\\s*-\\s*");
                int homeFT = Integer.parseInt(ftScores[0].trim());
                int awayFT = Integer.parseInt(ftScores[1].trim());
                int homeHT = Integer.parseInt(htScores[0].trim());
                int awayHT = Integer.parseInt(htScores[1].trim());

                boolean isComeback1_2 = (homeHT > awayHT) && (homeFT < awayFT);
                boolean isComeback2_1 = (homeHT < awayHT) && (homeFT > awayFT);

                if (isComeback1_2 || isComeback2_1) {
                    match.comebackType = isComeback1_2 ? "1/2" : "2/1";
                    System.out.println("   * Geri dönüş bulundu! " + match +
                            " [Thread: " + Thread.currentThread().getName() + "]");
                    int start = Math.max(0, i - 5);
                    int end = Math.min(leagueMatchesOnly.size(), i + 6);
                    List<MatchData> prevMatches = new ArrayList<>(leagueMatchesOnly.subList(start, i));
                    List<MatchData> nextMatches = new ArrayList<>(leagueMatchesOnly.subList(i + 1, end));
                    comebackResults.add(new ResultRow(match, prevMatches, nextMatches));
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Hatalı skorları sessizce atla
            }
        }
        return comebackResults;
    }

    // Tüm verileri tek Excel dosyasına yaz
    public static void exportAllToExcel(List<ResultRow> results, String fileName) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Tüm Takımlar Geri Dönüş Analizi");

        Row headerRow = sheet.createRow(0);
        List<String> headers = new ArrayList<>();
        headers.add("Takım ID");
        headers.add("Sezon");
        headers.add("Geri Dönüş Tipi");
        headers.add("Tarih");
        headers.add("Ev Sahibi");
        headers.add("Skor");
        headers.add("Deplasman");
        headers.add("İY Skoru");

        for (int i = 1; i <= 5; i++) {
            headers.add("Önceki Maç " + i + " - Tarih");
            headers.add("Önceki Maç " + i + " - Ev Sahibi");
            headers.add("Önceki Maç " + i + " - Deplasman");
            headers.add("Önceki Maç " + i + " - Skor");

        }
        for (int i = 1; i <= 5; i++) {
            headers.add("Sonraki Maç " + i + " - Tarih");
            headers.add("Sonraki Maç " + i + " - Ev Sahibi");
            headers.add("Sonraki Maç " + i + " - Deplasman");
            headers.add("Sonraki Maç " + i + " - Skor");
        }
        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
        }

        int rowNum = 1;
        for (ResultRow result : results) {
            Row row = sheet.createRow(rowNum++);
            int cellNum = 0;
            MatchData cbMatch = result.comebackMatch;

            // Takım ID'sini ekle
            row.createCell(cellNum++).setCellValue(cbMatch.teamId);
            row.createCell(cellNum++).setCellValue(cbMatch.season);
            row.createCell(cellNum++).setCellValue(cbMatch.comebackType);
            row.createCell(cellNum++).setCellValue(cbMatch.date);
            row.createCell(cellNum++).setCellValue(cbMatch.homeTeam);
            row.createCell(cellNum++).setCellValue(cbMatch.ftScore);
            row.createCell(cellNum++).setCellValue(cbMatch.awayTeam);
            row.createCell(cellNum++).setCellValue(cbMatch.htScore);

            for (int i = 0; i < 5; i++) {
                if (i < result.previousMatches.size()) {
                    MatchData prevMatch = result.previousMatches.get(result.previousMatches.size() - 1 - i);
                    row.createCell(cellNum++).setCellValue(prevMatch.date);
                    row.createCell(cellNum++).setCellValue(prevMatch.homeTeam);
                    row.createCell(cellNum++).setCellValue(prevMatch.awayTeam);
                    row.createCell(cellNum++).setCellValue(prevMatch.ftScore);
                } else {
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                }
            }

            for (int i = 0; i < 5; i++) {
                if (i < result.nextMatches.size()) {
                    MatchData nextMatch = result.nextMatches.get(i);
                    row.createCell(cellNum++).setCellValue(nextMatch.date);
                    row.createCell(cellNum++).setCellValue(nextMatch.homeTeam);
                    row.createCell(cellNum++).setCellValue(nextMatch.awayTeam);
                    row.createCell(cellNum++).setCellValue(nextMatch.ftScore);
                } else {
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                }
            }
        }

        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        Path filePath = Paths.get(OUTPUT_DIRECTORY_NAME, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
            workbook.write(outputStream);
        }
        workbook.close();
    }

    static class MatchData {
        String teamId; // Yeni alan: Takım ID'si
        String season;
        String date;
        String homeTeam;
        String awayTeam;
        String ftScore;
        String htScore;
        String comebackType;

        @Override
        public String toString() {
            return String.format("%s: %s vs %s (%s) - %s",
                    date, homeTeam, awayTeam, ftScore, comebackType);
        }
    }

    static class ResultRow {
        MatchData comebackMatch;
        List<MatchData> previousMatches;
        List<MatchData> nextMatches;

        public ResultRow(MatchData comebackMatch, List<MatchData> previousMatches, List<MatchData> nextMatches) {
            this.comebackMatch = comebackMatch;
            this.previousMatches = previousMatches;
            this.nextMatches = nextMatches;
        }
    }
}