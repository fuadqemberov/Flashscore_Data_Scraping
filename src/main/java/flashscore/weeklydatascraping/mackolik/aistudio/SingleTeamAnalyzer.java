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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SingleTeamAnalyzer {
    private static final String TEAM_ID = "32"; // Analiz edilecek takımın ID'si (Örnek: 171 = Atalanta)
    private static final List<String> SEASONS_TO_ANALYZE = new ArrayList<>();

    static {
        for (int year = 2024; year >= 2015; year--) {
            SEASONS_TO_ANALYZE.add(year + "/" + (year + 1));
        }
    }

    public static void main(String[] args) {
        System.out.println("İşlem Başlatıldı...");
        List<ResultRow> allComebacks = new ArrayList<>();

        for (String season : SEASONS_TO_ANALYZE) {
            try {
                System.out.println("\n===== " + season + " sezonu analiz ediliyor... =====");
                List<ResultRow> seasonResults = findComebacksForSeason(TEAM_ID, season);
                if (!seasonResults.isEmpty()) {
                    allComebacks.addAll(seasonResults);
                    System.out.println(season + " sezonunda " + seasonResults.size() + " geri dönüş bulundu.");
                } else {
                    System.out.println(season + " sezonu için kriterlere uygun geri dönüş maçı bulunamadı.");
                }
            } catch (IOException e) {
                System.err.println("HATA: " + season + " sezonu işlenirken bir sorun oluştu: " + e.getMessage());
            }
        }

        if (!allComebacks.isEmpty()) {
            Collections.sort(allComebacks, (r1, r2) -> r2.comebackMatch.date.compareTo(r1.comebackMatch.date));

            String fileName = "Analiz_Sadece_Lig_Takim_" + TEAM_ID + "_2015-2025.xlsx";
            try {
                exportToExcel(allComebacks, fileName);
                System.out.println("\nİşlem tamamlandı. Toplam " + allComebacks.size() + " adet geri dönüş bulundu.");
                System.out.println("Dosya '" + fileName + "' olarak kaydedildi.");
            } catch (IOException e) {
                System.err.println("Excel dosyası oluşturulurken hata oluştu: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("\nAnaliz edilen tüm sezonlarda belirtilen takım için lig maçlarında geri dönüş maçı bulunamadı.");
        }
    }

    public static List<ResultRow> findComebacksForSeason(String teamId, String season) throws IOException {
        System.out.println("-> Takım ID: " + teamId + " için fikstür analiz ediliyor...");
        String teamUrl = String.format("https://arsiv.mackolik.com/Team/Default.aspx?id=%s&season=%s", teamId, season);

        Document doc = Jsoup.connect(teamUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
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
                if (ftScore.isEmpty() || htScore.isEmpty() || !ftScore.contains("-") || !htScore.contains("-")) {
                    continue;
                }
                MatchData match = new MatchData();
                match.date = row.select("td:nth-child(1)").text();
                match.homeTeam = row.select("td:nth-child(3)").text().trim();
                match.awayTeam = row.select("td:nth-child(7)").text().trim();
                match.ftScore = ftScore;
                match.htScore = htScore;
                leagueMatchesOnly.add(match);
            }
        }
        System.out.println("Sadece ligden toplam " + leagueMatchesOnly.size() + " adet oynanmış maç bulundu.");

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
                System.err.println("Hatalı skor formatı atlanıyor: " + match.ftScore + " / " + match.htScore);
            }
        }
        return comebackResults;
    }

    // ****** BU METOD İSTEĞİNİZ DOĞRULTUSUNDA GÜNCELLENDİ ******
    public static void exportToExcel(List<ResultRow> results, String filePath) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Geri Dönüş Analizi");
        Row headerRow = sheet.createRow(0);
        List<String> headers = new ArrayList<>();

        // Ana Maç Başlıkları
        headers.add("Geri Dönüş Tipi");
        headers.add("Tarih");
        headers.add("Ev Sahibi");
        headers.add("Skor");
        headers.add("Deplasman");
        headers.add("İY Skoru");

        // ***** DEĞİŞİKLİK BURADA BAŞLIYOR: Başlıklar güncellendi *****
        // Önceki Maç Başlıkları
        for (int i = 5; i >= 1; i--) {
            headers.add("Önceki Maç " + i + " - Tarih");
            headers.add("Önceki Maç " + i + " - Ev Sahibi"); // Değiştirildi
            headers.add("Önceki Maç " + i + " - Deplasman"); // Değiştirildi
            headers.add("Önceki Maç " + i + " - Skor");
        }
        // Sonraki Maç Başlıkları
        for (int i = 1; i <= 5; i++) {
            headers.add("Sonraki Maç " + i + " - Tarih");
            headers.add("Sonraki Maç " + i + " - Ev Sahibi"); // Değiştirildi
            headers.add("Sonraki Maç " + i + " - Deplasman"); // Değiştirildi
            headers.add("Sonraki Maç " + i + " - Skor");
        }
        // ***** DEĞİŞİKLİK BURADA BİTİYOR *****

        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
        }

        int rowNum = 1;
        for (ResultRow result : results) {
            Row row = sheet.createRow(rowNum++);
            int cellNum = 0;

            // Ana Maç Verileri
            MatchData cbMatch = result.comebackMatch;
            row.createCell(cellNum++).setCellValue(cbMatch.comebackType);
            row.createCell(cellNum++).setCellValue(cbMatch.date);
            row.createCell(cellNum++).setCellValue(cbMatch.homeTeam);
            row.createCell(cellNum++).setCellValue(cbMatch.ftScore);
            row.createCell(cellNum++).setCellValue(cbMatch.awayTeam);
            row.createCell(cellNum++).setCellValue(cbMatch.htScore);

            // ***** DEĞİŞİKLİK BURADA BAŞLIYOR: Veri yazdırma şekli güncellendi *****
            // Önceki Maç Verileri
            for (int i = 0; i < 5; i++) {
                if (i < result.previousMatches.size()) {
                    MatchData prevMatch = result.previousMatches.get(result.previousMatches.size() - 1 - i);
                    row.createCell(cellNum++).setCellValue(prevMatch.date);
                    row.createCell(cellNum++).setCellValue(prevMatch.homeTeam);   // AYRI SÜTUNA
                    row.createCell(cellNum++).setCellValue(prevMatch.awayTeam);   // AYRI SÜTUNA
                    row.createCell(cellNum++).setCellValue(prevMatch.ftScore);
                } else {
                    row.createCell(cellNum++).setCellValue("-"); // Tarih
                    row.createCell(cellNum++).setCellValue("-"); // Ev Sahibi
                    row.createCell(cellNum++).setCellValue("-"); // Deplasman
                    row.createCell(cellNum++).setCellValue("-"); // Skor
                }
            }
            // Sonraki Maç Verileri
            for (int i = 0; i < 5; i++) {
                if (i < result.nextMatches.size()) {
                    MatchData nextMatch = result.nextMatches.get(i);
                    row.createCell(cellNum++).setCellValue(nextMatch.date);
                    row.createCell(cellNum++).setCellValue(nextMatch.homeTeam);   // AYRI SÜTUNA
                    row.createCell(cellNum++).setCellValue(nextMatch.awayTeam);   // AYRI SÜTUNA
                    row.createCell(cellNum++).setCellValue(nextMatch.ftScore);
                } else {
                    row.createCell(cellNum++).setCellValue("-"); // Tarih
                    row.createCell(cellNum++).setCellValue("-"); // Ev Sahibi
                    row.createCell(cellNum++).setCellValue("-"); // Deplasman
                    row.createCell(cellNum++).setCellValue("-"); // Skor
                }
            }
            // ***** DEĞİŞİKLİK BURADA BİTİYOR *****
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