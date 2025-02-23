package flashscore.weeklydatascraping.datepickermackolik;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatePickerAutomation {

    private static List<String> links = Collections.synchronizedList(new ArrayList<>());
    private static CopyOnWriteArrayList<List<String>> allData = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws InterruptedException, IOException {
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);

        long startTime = System.currentTimeMillis();

        CloseableHttpClient httpClient = HttpClients.createDefault();

        List<String> links =  readFile();
        if (!links.isEmpty()) {
            for (String link : links) { // Tüm linkleri işle
                List<String> matchData = getData(link, httpClient);
                if (matchData != null) {
                    allData.add(matchData); // Tüm maç verilerini allData'ya ekle
                    System.out.println("Veri başarıyla alındı ve allData'ya eklendi.");
                } else {
                    System.out.println("getData metodu null döndürdü.");
                }
            }
        } else {
            System.out.println("Okunacak link bulunamadı.");
        }

        writeMatchesToExcel();

        long endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) / 1000 + " seconds");
    }

    public static String basitUrlDonustur(String url) {
        // URL'nin sonundaki maç ID'sini al
        int sonSlashIndex = url.lastIndexOf("/");
        if (sonSlashIndex == -1) {
            return null; // Veya bir istisna fırlatılabilir
        }
        String macId = url.substring(sonSlashIndex + 1);

        // Yeni URL'yi oluştur
        String yeniUrl = url.substring(0, sonSlashIndex) + "/karsilastirma/" + macId;

        return yeniUrl;
    }

    private static List<String> getData(String link, CloseableHttpClient httpClient) {
        System.out.println("getData metodu çalıştı, link: " + link);
        String html = fetchContent(link, httpClient);
        if (html == null) {
            System.err.println("Failed to fetch content for link: " + link);
            return null;
        }

        Document doc = Jsoup.parse(html);
        String htFt = extractFormattedScore(doc);
        List<String> teamList = new ArrayList<>();

        // Aberdeen Maçları
        Elements aberdeenMatches = doc.select(".p0c-match-head-to-head__played-matches--total .p0c-match-head-to-head__last-games--row");
        List<List<String>> aberdeenMatchesData = getMatchData(aberdeenMatches);
        teamList.addAll(prepareMatchData(aberdeenMatchesData));

        // Ross County Maçları
        Elements rossCountyMatches = doc.select(".p0c-match-head-to-head__played-matches--at-home .p0c-match-head-to-head__last-games--row");
        List<List<String>> rossCountyMatchesData = getMatchData(rossCountyMatches);
        teamList.addAll(prepareMatchData(rossCountyMatchesData));

        // Eksik veri varsa N/A ile doldur
        while (teamList.size() < 15) {
            teamList.add("N/A");
        }

        teamList.add(htFt);  // HT/FT skorunu ekle

        return teamList;
    }

    private static List<List<String>> getMatchData(Elements matchRows) {
        List<List<String>> matchesData = new ArrayList<>();
        for (int i = 0; i < matchRows.size(); i++) {
            List<String> matchData = new ArrayList<>();
            Element row = matchRows.get(i); // Elements yerine Element kullan

            // Takım isimlerini ve skoru al
            Element homeTeamElement = row.selectFirst(".p0c-match-head-to-head__last-games--home-team-name");
            Element scoreElement = row.selectFirst(".p0c-match-head-to-head__last-games--score");
            Element awayTeamElement = row.selectFirst(".p0c-match-head-to-head__last-games--away-team-name");

            String homeTeam = (homeTeamElement != null) ? homeTeamElement.text().trim() : "N/A";
            String score = (scoreElement != null) ? scoreElement.text().trim() : "N/A";
            String awayTeam = (awayTeamElement != null) ? awayTeamElement.text().trim() : "N/A";

            matchData.add(homeTeam);
            matchData.add(score);
            matchData.add(awayTeam);

            matchesData.add(matchData);
        }
        return matchesData;
    }

    // Maç verilerini istenen formata göre düzenle
    private static List<String> prepareMatchData(List<List<String>> matchesData) {
        List<String> formattedData = new ArrayList<>();
        for (List<String> match : matchesData) {
            formattedData.addAll(match);
        }
        return formattedData;
    }


    public static String fetchContent(String url, CloseableHttpClient httpClient) {
        System.out.println("fetchContent metodu çalıştı, URL: " + url);
        HttpGet httpGet = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                String content = EntityUtils.toString(entity, "UTF-8");
                System.out.println("HTML içeriği başarıyla alındı.");
                return content;
            } else {
                System.err.println("HTTP request failed with status code: " + statusCode + " for URL: " + url);
                return null;
            }
        } catch (IOException e) {
            System.err.println("Error fetching content from URL: " + url + " - " + e.getMessage());
            return null;
        }
    }

    public static String extractFormattedScore(Document doc) {
        System.out.println("extractFormattedScore metodu çalıştı.");
        String formattedScore = "N/A";

        try {
            Elements homeScoreElements = doc.select(".p0c-soccer-match-details-header__score-home");
            Elements awayScoreElements = doc.select(".p0c-soccer-match-details-header__score-away");

            String homeScore = homeScoreElements.first().text().trim();
            String awayScore = awayScoreElements.first().text().trim();
            Elements detailedScoreElements = doc.select(".p0c-soccer-match-details-header__detailed-score");
            String detailedScore = detailedScoreElements.text().trim();

            String halfTimeScore = "0-0"; // Default if parsing fails
            if (detailedScore.contains("İY")) {
                String htScoreText = detailedScore.replace("(İY", "").replace(")", "").trim();
                if (!htScoreText.isEmpty()) {
                    halfTimeScore = htScoreText;
                }
            }

            // Format as "HalfTimeScore/FullTimeScore"
            formattedScore = halfTimeScore + "/" + homeScore + "-" + awayScore;

        } catch (Exception e) {
            System.err.println("Error extracting score data: " + e.getMessage());
        }

        return formattedScore;
    }


    public static List<String> readFile() throws IOException {
        System.out.println("readFile metodu çalıştı.");
        List<String> lines = new ArrayList<>();
        File file = new File("mackolik.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim(); // Satırın başındaki ve sonundaki boşlukları temizle
                if (!line.isEmpty()) { // Boş satırları filtrele
                    lines.add(basitUrlDonustur(line));
                }
            }
        }
        return lines;
    }

    public static void writeMatchesToExcel() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Home-5");
        headerRow.createCell(1).setCellValue("Score-5");
        headerRow.createCell(2).setCellValue("Away-5");

        headerRow.createCell(3).setCellValue("Home-4");
        headerRow.createCell(4).setCellValue("Score-4");
        headerRow.createCell(5).setCellValue("Away-4");

        headerRow.createCell(6).setCellValue("Home-3");
        headerRow.createCell(7).setCellValue("Score-3");
        headerRow.createCell(8).setCellValue("Away-3");

        headerRow.createCell(9).setCellValue("Home-2");
        headerRow.createCell(10).setCellValue("Score-2");
        headerRow.createCell(11).setCellValue("Away-2");

        headerRow.createCell(12).setCellValue("Home-1");
        headerRow.createCell(13).setCellValue("Score-1");
        headerRow.createCell(14).setCellValue("Away-1");


        headerRow.createCell(15).setCellValue("Home-5");
        headerRow.createCell(16).setCellValue("Score-5");
        headerRow.createCell(17).setCellValue("Away-5");

        headerRow.createCell(18).setCellValue("Home-4");
        headerRow.createCell(19).setCellValue("Score-4");
        headerRow.createCell(20).setCellValue("Away-4");

        headerRow.createCell(21).setCellValue("Home-3");
        headerRow.createCell(22).setCellValue("Score-3");
        headerRow.createCell(23).setCellValue("Away-3");

        headerRow.createCell(24).setCellValue("Home-2");
        headerRow.createCell(25).setCellValue("Score-2");
        headerRow.createCell(26).setCellValue("Away-2");

        headerRow.createCell(27).setCellValue("Home-1");
        headerRow.createCell(28).setCellValue("Score-1");
        headerRow.createCell(29).setCellValue("Away-1");


        headerRow.createCell(30).setCellValue("HT / FT");

        int rowNum = 1;
        for (List<String> teamMatches : allData) {
            Row row = sheet.createRow(rowNum++);

            if (teamMatches.size() >= 31) {
                for (int i = 0; i < 31; i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
            } else {
                System.out.println("Warning: Not enough match data for row " + rowNum);
                // Fill available data
                for (int i = 0; i < teamMatches.size(); i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
                // Fill remaining cells with N/A
                for (int i = teamMatches.size(); i < 31; i++) {
                    row.createCell(i).setCellValue("N/A");
                }
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream("mackolik_results.xlsx")) {
            workbook.write(fileOut);
            System.out.println("Excel file created successfully with " + allData.size() + " rows!");
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}