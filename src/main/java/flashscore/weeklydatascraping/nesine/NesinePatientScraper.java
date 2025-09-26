package flashscore.weeklydatascraping.nesine;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NesinePatientScraper {

    private static final String API_URL = "https://st.nesine.com/v1/Result";
    private static final Random random = new Random();

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        List<String[]> allMatches = new ArrayList<>();

        // Multi-thread yok, her şey sırayla ve yavaşça yapılacak.
        for (int pNo = 253; pNo <= 302; pNo++) {
            System.out.println("İşleniyor -> Hafta No: " + pNo);

            List<String[]> pageMatches = scrapeApi(pNo);

            if (pageMatches != null) {
                allMatches.addAll(pageMatches);
            }

            // EN KRİTİK KISIM: AŞIRI UZUN VE RASTGELE BEKLEME
            try {
                // 7 ila 15 saniye arasında tamamen rastgele bir süre bekle.
                int sleepTime = 7000 + random.nextInt(8000);
                System.out.println("Blok yememek için " + (double)sleepTime / 1000 + " saniye bekleniyor...");
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // Bekleme kesilirse döngüden çık
            }
        }

        writeToExcel(allMatches, "Nesine_Sonuclari_Patient.xlsx");

        long endTime = System.currentTimeMillis();
        System.out.println("Toplam işlem süresi: " + (endTime - startTime) + " ms");
    }

    private static List<String[]> scrapeApi(int pNo) {
        List<String[]> pageMatches = new ArrayList<>();
        try {
            String jsonBody = Jsoup.connect(API_URL)
                    .header("Connection", "close") // Her ihtimale karşı tutuyoruz
                    .data("pno", String.valueOf(pNo))
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.nesine.com/")
                    .timeout(30000)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            JSONObject mainObject = new JSONObject(jsonBody);
            JSONArray resultsArray = mainObject.getJSONObject("d").getJSONArray("programResultList");

            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject matchObject = resultsArray.getJSONObject(i);
                pageMatches.add(new String[]{
                        String.valueOf(pNo), String.valueOf(matchObject.getInt("number")),
                        matchObject.getString("date") + " " + matchObject.getString("time"),
                        matchObject.getString("name"), matchObject.optString("score", "-"),
                        matchObject.getString("result")
                });
            }
        } catch (Exception e) {
            System.err.println("HATA: Hafta " + pNo + " işlenirken sorun oluştu: " + e.getMessage());
            return null;
        }
        return pageMatches;
    }

    private static void writeToExcel(List<String[]> allMatches, String fileName) {
        if (allMatches.isEmpty()) { System.out.println("Excel'e yazılacak veri bulunamadı."); return; }
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fileOut = new FileOutputStream(fileName)) {
            Sheet sheet = workbook.createSheet("Maç Sonuçları");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Hafta No", "Maç No", "Tarih", "Karşılaşma", "Skor", "Sonuç"};
            for (int i = 0; i < headers.length; i++) { headerRow.createCell(i).setCellValue(headers[i]); }
            int rowNum = 1;
            for (String[] matchData : allMatches) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < matchData.length; i++) { row.createCell(i).setCellValue(matchData[i]); }
            }
            workbook.write(fileOut);
            System.out.println("\nExcel dosyası başarıyla oluşturuldu: " + fileName);
        } catch (IOException e) { System.err.println("Excel dosyası yazılırken bir hata oluştu: " + e.getMessage()); }
    }
}