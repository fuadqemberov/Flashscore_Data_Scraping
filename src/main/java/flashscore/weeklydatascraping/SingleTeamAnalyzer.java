package flashscore.weeklydatascraping;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SingleTeamAnalyzer {

    private static final String TEAM_ID = "171"; // Analiz edilecek takımın ID'si (Örnek: 171 = Atalanta)
    private static final String SEASON = "2024/2025";
    private static final String TEAM_URL_TEMPLATE = "https://arsiv.mackolik.com/Team/Default.aspx?id=%s&season=" + SEASON;

    public static void main(String[] args) {
        try {
            System.out.println("İşlem Başlatıldı...");
            List<ResultRow> results = findComebacksForTeam(TEAM_ID);

            if (!results.isEmpty()) {
                String fileName = "Analiz_Sadece_Lig_Takim_" + TEAM_ID + ".xlsx";
                exportToExcel(results, fileName);
                System.out.println("\nİşlem tamamlandı. " + results.size() + " adet geri dönüş bulundu.");
                System.out.println("Dosya '" + fileName + "' olarak kaydedildi.");
            } else {
                System.out.println("\nBelirtilen takım için lig maçlarında kriterlere uygun geri dönüş maçı bulunamadı.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ****** BU METOD GÜNCELLENDİ ******
    public static List<ResultRow> findComebacksForTeam(String teamId) throws IOException {
        System.out.println("-> Takım ID: " + teamId + " için fikstür analiz ediliyor...");
        String teamUrl = String.format(TEAM_URL_TEMPLATE, teamId);
        Document doc = Jsoup.connect(teamUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .get();

        List<MatchData> leagueMatchesOnly = new ArrayList<>();
        // Sayfadaki tüm satırları sırasıyla alıyoruz (hem turnuva başlıkları hem maçlar)
        Elements allRows = doc.select("table#tblFixture > tbody > tr");

        boolean isScrapingLeague = false; // Lig maçlarını toplamaya başladık mı? Bayrağımız.

        for (Element row : allRows) {
            // Eğer satır bir turnuva başlığı ise...
            if (row.hasClass("competition")) {
                if (isScrapingLeague) {
                    // Zaten lig maçlarını topluyorduk ve yeni bir turnuva başlığına denk geldik.
                    // Demek ki lig bitti. Döngüyü burada sonlandır.
                    break;
                } else {
                    // Bu ilk turnuva başlığı. Artık maçları toplamaya başlayabiliriz.
                    isScrapingLeague = true;
                    // Bu satırı atlayıp bir sonraki satıra geç.
                    continue;
                }
            }

            // Eğer lig maçlarını toplama modundaysak VE bu satır bir maç satırı ise...
            if (isScrapingLeague && row.hasClass("row")) {
                String ftScore = row.select("td:nth-child(5) a").text().trim();
                String htScore = row.select("td:nth-child(9)").text().trim();

                if (ftScore.isEmpty() || htScore.isEmpty() || !ftScore.contains("-") || !htScore.contains("-")) {
                    continue;
                }

                MatchData match = new MatchData();
                match.date = row.select("td:nth-child(1)").text();
                match.homeTeam = row.select("td:nth-child(3)").text().trim();
                match.awayTeam = row.select("td:nth-child(7)").text().trim();
                match.ftScore = ftScore;
                match.htScore = htScore;
                leagueMatchesOnly.add(match); // Sadece lig maçlarını listeye ekle
            }
        }

        System.out.println("Sadece ligden toplam " + leagueMatchesOnly.size() + " adet oynanmış maç bulundu.");

        // ------ Analiz Kısmı Değişmedi, artık sadece lig maçları üzerinde çalışacak ------

        List<ResultRow> comebackResults = new ArrayList<>();
        // Artık sadece lig maçlarını içeren 'leagueMatchesOnly' listesini analiz ediyoruz.
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
                System.err.println("Hatalı skor formatı atlanıyor: " + match.ftScore + " / " + match.htScore);
                continue;
            }
        }
        return comebackResults;
    }

    // exportToExcel metodu aynı kalabilir.
    public static void exportToExcel(List<ResultRow> results, String filePath) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Geri Dönüş Analizi");

        Row headerRow = sheet.createRow(0);
        List<String> headers = new ArrayList<>();
        headers.add("Geri Dönüş Tipi");
        headers.add("Tarih");
        headers.add("Ev Sahibi");
        headers.add("Skor");
        headers.add("Deplasman");
        headers.add("İY Skoru");

        for (int i = 5; i >= 1; i--) {
            headers.add("Önceki Maç " + i + " - Tarih");
            headers.add("Önceki Maç " + i + " - Maç");
            headers.add("Önceki Maç " + i + " - Skor");
        }
        for (int i = 1; i <= 5; i++) {
            headers.add("Sonraki Maç " + i + " - Tarih");
            headers.add("Sonraki Maç " + i + " - Maç");
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
                    row.createCell(cellNum++).setCellValue(prevMatch.homeTeam + " vs " + prevMatch.awayTeam);
                    row.createCell(cellNum++).setCellValue(prevMatch.ftScore);
                } else {
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                }
            }

            for (int i = 0; i < 5; i++) {
                if (i < result.nextMatches.size()) {
                    MatchData nextMatch = result.nextMatches.get(i);
                    row.createCell(cellNum++).setCellValue(nextMatch.date);
                    row.createCell(cellNum++).setCellValue(nextMatch.homeTeam + " vs " + nextMatch.awayTeam);
                    row.createCell(cellNum++).setCellValue(nextMatch.ftScore);
                } else {
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                    row.createCell(cellNum++).setCellValue("-");
                }
            }
        }

        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            workbook.write(outputStream);
        }
        workbook.close();
    }
}