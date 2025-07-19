package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class TestSingleMatch {

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        String matchId = "phWo4X3K";
        String homeTeam = "Palmeiras";
        String awayTeam = "null";
        String matchDate = "";

        try {
            // ADIM 1: SAYFAYI AÇ VE İLK MAÇI BUL
            System.out.println("1. Flashscore sitesine gidiliyor...");
            driver.get("https://www.flashscore.com/");

            try {
                WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
                acceptCookiesButton.click();
                System.out.println("2. Çerezler kabul edildi.");
            } catch (Exception e) {
                System.out.println("2. Çerez bildirimi bulunamadı.");
            }

            // Sayfanın yüklenmesini bekle
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".sportName.soccer")));
            System.out.println("3. Sayfa yüklendi. İlk maç bilgileri aranıyor...");

            // Gelecekte oynanacak ilk maçı bul
            WebElement firstMatch = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.event__match[id^='g_1_']")));

            matchId = firstMatch.getAttribute("id").substring(4); // "g_1_" ön ekini kaldır
            homeTeam = firstMatch.findElement(By.cssSelector(".event__homeParticipant")).getText();
            awayTeam = firstMatch.findElement(By.cssSelector(".event__awayParticipant")).getText();
            matchDate = driver.findElement(By.cssSelector("[data-testid='wcl-dayPickerButton']")).getText();

            System.out.println("4. Maç bulundu:");
            System.out.println("   -> ID: " + matchId);
            System.out.println("   -> Takımlar: " + homeTeam + " vs " + awayTeam);
            System.out.println("   -> Tarih: " + matchDate);

        } catch (Exception e) {
            System.err.println("Hata! Maç bilgileri çekilemedi: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit(); // Tarayıcıyı kapat
        }

        // Eğer maç bilgisi başarıyla alındıysa, oranları çek ve Excel'e yaz
        if (matchId != null) {
            System.out.println("\n5. Oran verileri çekiliyor...");
            Map<String, String> odds = fetchAndParseOdds(matchId);

            System.out.println("6. Excel dosyası oluşturuluyor...");
            createSingleRowExcel(matchDate, matchId, homeTeam, awayTeam, odds);
        } else {
            System.out.println("Maç ID'si alınamadığı için işlem durduruldu.");
        }
    }

    private static Map<String, String> fetchAndParseOdds(String matchId) {
        Map<String, String> resultMap = new HashMap<>();
        String url = "https://global.ds.lsapp.eu/odds/pq_graphql?_hash=pobtm&eventId=" + matchId + "&projectId=2&geoIpCode=AZ&geoIpSubdivisionCode=AZ-BA";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0").build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("   -> API Yanıt Kodu: " + response.statusCode());
            System.out.println("   -> Gelen Ham JSON: \n" + response.body()); // Gelen veriyi görmek için

            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                if (!jsonResponse.has("data") || jsonResponse.getJSONObject("data").isNull("findOddsByEventId") || !jsonResponse.getJSONObject("data").getJSONObject("findOddsByEventId").has("odds")) {
                    System.out.println("   -> JSON yanıtında 'findOddsByEventId' veya 'odds' alanı bulunamadı.");
                    return resultMap;
                }

                JSONArray allOdds = jsonResponse.getJSONObject("data").getJSONObject("findOddsByEventId").getJSONArray("odds");
                System.out.println("   -> " + allOdds.length() + " adet oran seti bulundu.");

                for (int i = 0; i < allOdds.length(); i++) {
                    JSONObject oddData = allOdds.getJSONObject(i);
                    if (oddData.optInt("bookmakerId") != 977) continue;

                    String bettingType = oddData.getString("bettingType");
                    String bettingScope = oddData.getString("bettingScope");
                    JSONArray oddsValues = oddData.getJSONArray("odds");

                    if (!"FULL_TIME".equals(bettingScope)) continue;

                    switch (bettingType) {
                        case "HOME_DRAW_AWAY":
                            resultMap.put("1X2_1", oddsValues.getJSONObject(0).optString("value", "-"));
                            resultMap.put("1X2_X", oddsValues.getJSONObject(1).optString("value", "-"));
                            resultMap.put("1X2_2", oddsValues.getJSONObject(2).optString("value", "-"));
                            break;
                        case "OVER_UNDER":
                            for (int j = 0; j < oddsValues.length(); j++) {
                                JSONObject overUnderOdd = oddsValues.getJSONObject(j);
                                if ("2.5".equals(overUnderOdd.getJSONObject("handicap").getString("value"))) {
                                    if ("OVER".equals(overUnderOdd.getString("selection"))) resultMap.put("OU_Over_2.5", overUnderOdd.optString("value", "-"));
                                    else if ("UNDER".equals(overUnderOdd.getString("selection"))) resultMap.put("OU_Under_2.5", overUnderOdd.optString("value", "-"));
                                }
                            }
                            break;
                        case "BOTH_TEAMS_TO_SCORE":
                            resultMap.put("BTTS_Yes", oddsValues.getJSONObject(0).optString("value", "-"));
                            resultMap.put("BTTS_No", oddsValues.getJSONObject(1).optString("value", "-"));
                            break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("   -> Oran verisi işlenirken HATA: " + e.getMessage());
            e.printStackTrace();
        }
        return resultMap;
    }

    private static void createSingleRowExcel(String date, String id, String home, String away, Map<String, String> oddsMap) {
        String[] columns = {"Tarih", "Maç ID", "Ev Sahibi", "Deplasman", "1X2 - Ev Sahibi (1)", "1X2 - Beraberlik (X)", "1X2 - Deplasman (2)", "Alt/Üst 2.5 - Üst", "Alt/Üst 2.5 - Alt", "KG Var - Evet", "KG Var - Hayır"};

        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fileOut = new FileOutputStream("tek_mac_test.xlsx")) {
            Sheet sheet = workbook.createSheet("Oranlar");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) headerRow.createCell(i).setCellValue(columns[i]);

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(date);
            row.createCell(1).setCellValue(id);
            row.createCell(2).setCellValue(home);
            row.createCell(3).setCellValue(away);
            row.createCell(4).setCellValue(oddsMap.getOrDefault("1X2_1", "-"));
            row.createCell(5).setCellValue(oddsMap.getOrDefault("1X2_X", "-"));
            row.createCell(6).setCellValue(oddsMap.getOrDefault("1X2_2", "-"));
            row.createCell(7).setCellValue(oddsMap.getOrDefault("OU_Over_2.5", "-"));
            row.createCell(8).setCellValue(oddsMap.getOrDefault("OU_Under_2.5", "-"));
            row.createCell(9).setCellValue(oddsMap.getOrDefault("BTTS_Yes", "-"));
            row.createCell(10).setCellValue(oddsMap.getOrDefault("BTTS_No", "-"));

            for(int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            workbook.write(fileOut);
            System.out.println("7. Excel dosyası 'tek_mac_test.xlsx' başarıyla oluşturuldu.");
        } catch (IOException e) {
            System.err.println("Excel dosyası oluşturulurken hata: " + e.getMessage());
        }
    }
}