package flashscore.weeklydatascraping.nowgoal_week_finder;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SingleRowExcelExporter {

    public static void main(String[] args) {
        // --- Ayarlar ---
        String url = "https://global.ds.lsapp.eu/odds/pq_graphql?_hash=oce&eventId=tbWsutEc&projectId=2&geoIpCode=AZ&geoIpSubdivisionCode=AZBA";
        int targetBookmakerId = 977;
        String bookmakerName = "Betandreas.az";
        String eventId = "Khey4Qpf";
        String fileName = eventId + "_" + bookmakerName.replace(".", "_") + "_SingleRow_Odds.xlsx";

        try {
            System.out.println("Veri çekiliyor...");
            JSONObject root = fetchJsonData(url);
            if (root == null) {
                System.out.println("Veri çekilemedi. İşlem sonlandırıldı.");
                return;
            }

            JSONArray allOdds = root.getJSONObject("data")
                    .getJSONObject("findOddsByEventId")
                    .getJSONArray("odds");

            // 1. Sadece gerekli olan (HOME_DRAW_AWAY) verileri ayıkla
            Map<String, JSONObject> requiredData = extractRequiredOdds(allOdds, targetBookmakerId);
            System.out.println("Gerekli olan " + requiredData.size() + " adet pazar için veri bulundu.");

            // 2. Verileri tek satırda Excel'e yaz
            writeSingleRowToExcel(requiredData, fileName, bookmakerName);

            System.out.println("\nBaşarılı! Veriler '" + fileName + "' dosyasına tek bir satırda yazıldı.");

        } catch (Exception e) {
            System.err.println("Ana işlem sırasında bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // URL'den JSON verisini çeken metod
    private static JSONObject fetchJsonData(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? new JSONObject(response.body()) : null;
    }

    // Yalnızca HOME_DRAW_AWAY tipindeki verileri filtreleyip ayıran metod
    private static Map<String, JSONObject> extractRequiredOdds(JSONArray allOdds, int bookmakerId) {
        Map<String, JSONObject> dataMap = new HashMap<>();
        String[] requiredScopes = {"FULL_TIME", "FIRST_HALF", "SECOND_HALF"};

        for (int i = 0; i < allOdds.length(); i++) {
            JSONObject oddsBlock = allOdds.getJSONObject(i);
            if (oddsBlock.getInt("bookmakerId") == bookmakerId &&
                    "HOME_DRAW_AWAY".equals(oddsBlock.getString("bettingType"))) {

                String scope = oddsBlock.getString("bettingScope");
                // Sadece istenen kapsamlardan biriyse haritaya ekle
                for (String reqScope : requiredScopes) {
                    if (reqScope.equals(scope)) {
                        dataMap.put(scope, oddsBlock);
                        break;
                    }
                }
            }
        }
        return dataMap;
    }

    // Verileri tek bir satırda Excel'e yazan ana fonksiyon
    private static void writeSingleRowToExcel(Map<String, JSONObject> data, String fileName, String sheetName) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        // Sabit başlıklarımızı ve sütun indekslerini tanımlayalım
        Map<String, Integer> headers = new LinkedHashMap<>();
        int colIdx = 0;
        String[] scopes = {"FULL_TIME", "FIRST_HALF", "SECOND_HALF"};
        String[] scopePrefixes = {"Maç Sonucu", "İlk Yarı", "İkinci Yarı"};
        String[] outcomes = {"Ev Sahibi (1)", "Deplasman (2)", "Beraberlik (X)"}; // JSON sırasına göre
        String[] oddTypes = {"Mevcut Oran", "Açılış Oranı"};

        for (int i = 0; i < scopes.length; i++) {
            for (String outcome : outcomes) {
                for (String oddType : oddTypes) {
                    headers.put(scopePrefixes[i] + " - " + outcome + " - " + oddType, colIdx++);
                }
            }
        }

        // --- BAŞLIK SATIRINI YAZ ---
        Row headerRow = sheet.createRow(0);
        for (Map.Entry<String, Integer> entry : headers.entrySet()) {
            headerRow.createCell(entry.getValue()).setCellValue(entry.getKey());
        }

        // --- VERİ SATIRINI YAZ ---
        Row dataRow = sheet.createRow(1);

        for (int i = 0; i < scopes.length; i++) {
            String currentScope = scopes[i];
            String currentPrefix = scopePrefixes[i];

            if (data.containsKey(currentScope)) {
                // --- Veri VAR ---
                JSONArray oddsItems = data.get(currentScope).getJSONArray("odds");

                // NOT: JSON'daki sıranın 0:Ev Sahibi, 1:Deplasman, 2:Beraberlik olduğu varsayılmıştır.
                // Bu varsayım, sağlanan JSON verisine dayanmaktadır.
                JSONObject homeOdds = oddsItems.getJSONObject(0);
                JSONObject awayOdds = oddsItems.getJSONObject(1);
                JSONObject drawOdds = oddsItems.getJSONObject(2);

                // Ev Sahibi Oranları
                dataRow.createCell(headers.get(currentPrefix + " - Ev Sahibi (1) - Mevcut Oran"))
                        .setCellValue(homeOdds.optString("value", "-"));
                dataRow.createCell(headers.get(currentPrefix + " - Ev Sahibi (1) - Açılış Oranı"))
                        .setCellValue(homeOdds.optString("opening", "-"));

                // Deplasman Oranları
                dataRow.createCell(headers.get(currentPrefix + " - Deplasman (2) - Mevcut Oran"))
                        .setCellValue(awayOdds.optString("value", "-"));
                dataRow.createCell(headers.get(currentPrefix + " - Deplasman (2) - Açılış Oranı"))
                        .setCellValue(awayOdds.optString("opening", "-"));

                // Beraberlik Oranları
                dataRow.createCell(headers.get(currentPrefix + " - Beraberlik (X) - Mevcut Oran"))
                        .setCellValue(drawOdds.optString("value", "-"));
                dataRow.createCell(headers.get(currentPrefix + " - Beraberlik (X) - Açılış Oranı"))
                        .setCellValue(drawOdds.optString("opening", "-"));

            } else {
                // --- Veri YOK ---
                // Bu kapsama ait tüm 6 sütunu "-" ile doldur
                for (String outcome : outcomes) {
                    for (String oddType : oddTypes) {
                        dataRow.createCell(headers.get(currentPrefix + " - " + outcome + " - " + oddType))
                                .setCellValue("-");
                    }
                }
            }
        }

        // Sütunları otomatik boyutlandır
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        // Dosyayı diske yaz
        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }
}