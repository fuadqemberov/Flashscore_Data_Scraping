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

public class MultiTeamSeasonAnalyzerFinal {

    private static final String INPUT_FILE_NAME = "takim_idleri.txt";
    private static final String OUTPUT_DIRECTORY_NAME = "excel";
    private static final int START_YEAR = 2015;
    private static final int END_YEAR = 2024;

    public static void main(String[] args) {
        createOutputDirectory();
        List<String> teamIds = readTeamIdsFromFile();
        if (teamIds.isEmpty()) {
            System.out.println(INPUT_FILE_NAME + " dosyası bulunamadı veya içi boş. İşlem durduruldu.");
            return;
        }

        System.out.println("Toplam " + teamIds.size() + " takım için analiz başlatılıyor...");

        for (String teamId : teamIds) {
            System.out.println("\n=======================================================");
            System.out.println("İŞLENEN TAKIM ID: " + teamId);
            System.out.println("=======================================================");

            List<ResultRow> allSeasonsResults = new ArrayList<>();
            for (int year = END_YEAR; year >= START_YEAR; year--) {
                String season = year + "/" + (year + 1);
                try {
                    List<ResultRow> seasonResults = findComebacksForSeason(teamId, season);
                    allSeasonsResults.addAll(seasonResults);
                    Thread.sleep(500); // Sunucuyu yormamak için bekleme
                } catch (IOException e) {
                    System.err.println("HATA: " + teamId + " ID'li takımın " + season + " sezonu analiz edilirken bir ağ hatası oluştu: " + e.getMessage());
                } catch (InterruptedException e) {
                    System.err.println("Bekleme sırasında hata oluştu.");
                    Thread.currentThread().interrupt();
                }
            }

            if (!allSeasonsResults.isEmpty()) {
                String fileName = String.format("Analiz_Sadece_Lig_Takim_%s_%d-%d.xlsx",
                        teamId, START_YEAR, END_YEAR + 1);
                try {
                    exportToExcel(allSeasonsResults, fileName);
                    System.out.println("\n-> BAŞARILI: " + teamId + " ID'li takımın analizi tamamlandı. Toplam " + allSeasonsResults.size() + " geri dönüş bulundu.");
                    System.out.println("   Dosya '" + Paths.get(OUTPUT_DIRECTORY_NAME, fileName).toAbsolutePath() + "' olarak kaydedildi.");
                } catch (IOException e) {
                    System.err.println("HATA: " + fileName + " dosyası oluşturulurken bir hata meydana geldi: " + e.getMessage());
                }
            } else {
                System.out.println("\n-> BİLGİ: " + teamId + " ID'li takım için belirtilen sezonlarda kriterlere uygun maç bulunamadı.");
            }
        }
        System.out.println("\n*** TÜM İŞLEMLER TAMAMLANDI ***");
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

    // ****** GÜNCELLENDİ ******
    // Bu metot artık sadece lig maçlarını alacak şekilde daha akıllı çalışıyor.
    public static List<ResultRow> findComebacksForSeason(String teamId, String season) throws IOException {
        System.out.println("-> Sezon: " + season + " | Takım ID: " + teamId + " analiz ediliyor...");
        String teamUrl = "https://arsiv.mackolik.com/Team/Default.aspx?id=" + teamId + "&season=" + season;
        Document doc = Jsoup.connect(teamUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(15000)
                .get();

        List<MatchData> leagueMatchesOnly = new ArrayList<>();
        Elements allRows = doc.select("table#tblFixture > tbody > tr");

        boolean isScrapingLeague = false; // Lig maçlarını toplamaya başladık mı? Bu bayrak kontrol edecek.

        for (Element row : allRows) {
            // Eğer satır bir turnuva başlığı ise...
            if (row.hasClass("competition")) {
                if (isScrapingLeague) {
                    // Zaten lig maçlarını topluyorduk ve YENİ BİR turnuva başlığına denk geldik.
                    // Demek ki lig bitti. Döngüyü burada sonlandır.
                    break;
                } else {
                    // Bu İLK turnuva başlığı. Artık maçları toplamaya başlayabiliriz.
                    isScrapingLeague = true;
                    // Bu başlık satırını atlayıp bir sonraki satıra geç.
                    continue;
                }
            }

            // Eğer lig maçlarını toplama modundaysak VE bu satır bir maç satırı ise...
            if (isScrapingLeague && row.hasClass("row")) {
                String ftScore = row.select("td:nth-child(5) a").text().trim();
                String htScore = row.select("td:nth-child(9)").text().trim();
                if (ftScore.isEmpty() || htScore.isEmpty() || !ftScore.contains("-") || !htScore.contains("-")) continue;

                MatchData match = new MatchData();
                match.season = season;
                match.date = row.select("td:nth-child(1)").text();
                match.homeTeam = row.select("td:nth-child(3)").text().trim();
                match.awayTeam = row.select("td:nth-child(7)").text().trim();
                match.ftScore = ftScore;
                match.htScore = htScore;
                leagueMatchesOnly.add(match); // Sadece lig maçlarını listeye ekle
            }
        }

        // --- Analiz Kısmı Değişmedi, artık sadece lig maçları üzerinde çalışacak ---
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
                    System.out.println("   * Ligde geri dönüş bulundu! " + match);
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

    // ****** GÜNCELLENDİ ******
    // Bu metot artık takımları ayrı sütunlara yazıyor ve eksik maçlar için "-" koyuyor.
    public static void exportToExcel(List<ResultRow> results, String fileName) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Geri Dönüş Analizi");

        // --- Yeni başlıklar ---
        Row headerRow = sheet.createRow(0);
        List<String> headers = new ArrayList<>();
        headers.add("Sezon");
        headers.add("Geri Dönüş Tipi");
        headers.add("Tarih");
        headers.add("Ev Sahibi");
        headers.add("Skor");
        headers.add("Deplasman");
        headers.add("İY Skoru");

        for (int i = 5; i >= 1; i--) {
            headers.add("Önceki Maç " + i + " - Tarih");
            headers.add("Önceki Maç " + i + " - Ev Sahibi"); // AYRI SÜTUN
            headers.add("Önceki Maç " + i + " - Deplasman"); // AYRI SÜTUN
            headers.add("Önceki Maç " + i + " - Skor");
        }
        for (int i = 1; i <= 5; i++) {
            headers.add("Sonraki Maç " + i + " - Tarih");
            headers.add("Sonraki Maç " + i + " - Ev Sahibi"); // AYRI SÜTUN
            headers.add("Sonraki Maç " + i + " - Deplasman"); // AYRI SÜTUN
            headers.add("Sonraki Maç " + i + " - Skor");
        }
        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
        }

        // --- Veri yazma mantığı ---
        int rowNum = 1;
        for (ResultRow result : results) {
            Row row = sheet.createRow(rowNum++);
            int cellNum = 0;
            MatchData cbMatch = result.comebackMatch;
            row.createCell(cellNum++).setCellValue(cbMatch.season);
            row.createCell(cellNum++).setCellValue(cbMatch.comebackType);
            row.createCell(cellNum++).setCellValue(cbMatch.date);
            row.createCell(cellNum++).setCellValue(cbMatch.homeTeam);
            row.createCell(cellNum++).setCellValue(cbMatch.ftScore);
            row.createCell(cellNum++).setCellValue(cbMatch.awayTeam);
            row.createCell(cellNum++).setCellValue(cbMatch.htScore);

            // Önceki 5 maçı yaz
            for (int i = 0; i < 5; i++) {
                // Listenin sonundan başlayarak maçları alıyoruz.
                if (i < result.previousMatches.size()) {
                    MatchData prevMatch = result.previousMatches.get(result.previousMatches.size() - 1 - i);
                    row.createCell(cellNum++).setCellValue(prevMatch.date);
                    row.createCell(cellNum++).setCellValue(prevMatch.homeTeam); // AYRI SÜTUN
                    row.createCell(cellNum++).setCellValue(prevMatch.awayTeam); // AYRI SÜTUN
                    row.createCell(cellNum++).setCellValue(prevMatch.ftScore);
                } else {
                    // Eğer maç yoksa, 4 hücreye de "-" yaz
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                }
            }

            // Sonraki 5 maçı yaz
            for (int i = 0; i < 5; i++) {
                if (i < result.nextMatches.size()) {
                    MatchData nextMatch = result.nextMatches.get(i);
                    row.createCell(cellNum++).setCellValue(nextMatch.date);
                    row.createCell(cellNum++).setCellValue(nextMatch.homeTeam); // AYRI SÜTUN
                    row.createCell(cellNum++).setCellValue(nextMatch.awayTeam); // AYRI SÜTUN
                    row.createCell(cellNum++).setCellValue(nextMatch.ftScore);
                } else {
                    // Eğer maç yoksa, 4 hücreye de "-" yaz
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
}