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
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatePickerAutomationV2 {
    private static final int THREAD_POOL_SIZE = 10;
    private static final CopyOnWriteArrayList<List<String>> allData = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws InterruptedException, IOException {
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);

        long startTime = System.currentTimeMillis();

        // Create thread pool
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<List<String>>> futures = new ArrayList<>();

        // Read links
        List<String> links = readFile();
        if (!links.isEmpty()) {
            // Create a shared HttpClient
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // Submit tasks to thread pool
                for (String link : links) {
                    futures.add(executorService.submit(() -> getData(link, httpClient)));
                }

                // Collect results
                for (Future<List<String>> future : futures) {
                    try {
                        List<String> matchData = future.get(30, TimeUnit.SECONDS);
                        if (matchData != null) {
                            allData.add(matchData);
                            System.out.println("Data successfully added to allData.");
                        }
                    } catch (ExecutionException | TimeoutException e) {
                        System.err.println("Error processing link: " + e.getMessage());
                    }
                }
            }
        } else {
            System.out.println("No links found to process.");
        }

        // Shutdown thread pool
        executorService.shutdown();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        writeMatchesToExcel();

        long endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) / 1000 + " seconds");
    }

    public static String basitUrlDonustur(String url) {
        int sonSlashIndex = url.lastIndexOf("/");
        if (sonSlashIndex == -1) {
            return null;
        }
        String macId = url.substring(sonSlashIndex + 1);
        return url.substring(0, sonSlashIndex) + "/karsilastirma/" + macId;
    }

    private static List<String> getData(String link, CloseableHttpClient httpClient) {
        System.out.println("Processing link: " + link + " on thread: " + Thread.currentThread().getName());
        String html = fetchContent(link, httpClient);
        if (html == null) {
            System.err.println("Failed to fetch content for link: " + link);
            return null;
        }

        Document doc = Jsoup.parse(html);
        String htFt = extractFormattedScore(doc);
        List<String> teamList = Collections.synchronizedList(new ArrayList<>());

        // Aberdeen Matches
        Elements aberdeenMatches = doc.select(".p0c-match-head-to-head__played-matches--total .p0c-match-head-to-head__last-games--row");
        List<List<String>> aberdeenMatchesData = getMatchData(aberdeenMatches);
        teamList.addAll(prepareMatchData(aberdeenMatchesData));

        // Ross County Matches
        Elements rossCountyMatches = doc.select(".p0c-match-head-to-head__played-matches--at-home .p0c-match-head-to-head__last-games--row");
        List<List<String>> rossCountyMatchesData = getMatchData(rossCountyMatches);
        teamList.addAll(prepareMatchData(rossCountyMatchesData));

        // Fill missing data with N/A
        while (teamList.size() < 15) {
            teamList.add("N/A");
        }

        teamList.add(htFt);

        return teamList;
    }

    private static synchronized List<List<String>> getMatchData(Elements matchRows) {
        List<List<String>> matchesData = new ArrayList<>();
        for (Element row : matchRows) {
            List<String> matchData = new ArrayList<>();

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

    private static List<String> prepareMatchData(List<List<String>> matchesData) {
        List<String> formattedData = new ArrayList<>();
        for (List<String> match : matchesData) {
            formattedData.addAll(match);
        }
        return formattedData;
    }

    public static String fetchContent(String url, CloseableHttpClient httpClient) {
        HttpGet httpGet = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                return EntityUtils.toString(entity, "UTF-8");
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
        String formattedScore = "N/A";

        try {
            Elements homeScoreElements = doc.select(".p0c-soccer-match-details-header__score-home");
            Elements awayScoreElements = doc.select(".p0c-soccer-match-details-header__score-away");

            String homeScore = homeScoreElements.first().text().trim();
            String awayScore = awayScoreElements.first().text().trim();
            Elements detailedScoreElements = doc.select(".p0c-soccer-match-details-header__detailed-score");
            String detailedScore = detailedScoreElements.text().trim();

            String halfTimeScore = "0-0";
            if (detailedScore.contains("İY")) {
                String htScoreText = detailedScore.replace("(İY", "").replace(")", "").trim();
                if (!htScoreText.isEmpty()) {
                    halfTimeScore = htScoreText;
                }
            }

            formattedScore = halfTimeScore + "/" + homeScore + "-" + awayScore;
        } catch (Exception e) {
            System.err.println("Error extracting score data: " + e.getMessage());
        }

        return formattedScore;
    }

    public static List<String> readFile() throws IOException {
        List<String> lines = new ArrayList<>();
        File file = new File("mackolik.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(basitUrlDonustur(line));
                }
            }
        }
        return lines;
    }

    public static void writeMatchesToExcel() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Match Results");

            // Create header row
            createHeaderRow(sheet);

            // Write data rows
            int rowNum = 1;
            for (List<String> teamMatches : allData) {
                Row row = sheet.createRow(rowNum++);
                writeDataRow(row, teamMatches);
            }

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream("mackolik_results.xlsx")) {
                workbook.write(fileOut);
                System.out.println("Excel file created successfully with " + allData.size() + " rows!");
            }
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Home-5", "Score-5", "Away-5",
                "Home-4", "Score-4", "Away-4",
                "Home-3", "Score-3", "Away-3",
                "Home-2", "Score-2", "Away-2",
                "Home-1", "Score-1", "Away-1",
                "Home-5", "Score-5", "Away-5",
                "Home-4", "Score-4", "Away-4",
                "Home-3", "Score-3", "Away-3",
                "Home-2", "Score-2", "Away-2",
                "Home-1", "Score-1", "Away-1",
                "HT / FT"
        };

        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private static void writeDataRow(Row row, List<String> teamMatches) {
        int maxColumns = 31;
        int dataSize = teamMatches.size();

        // Write available data
        for (int i = 0; i < Math.min(dataSize, maxColumns); i++) {
            row.createCell(i).setCellValue(teamMatches.get(i));
        }

        // Fill remaining cells with N/A
        for (int i = dataSize; i < maxColumns; i++) {
            row.createCell(i).setCellValue("N/A");
        }
    }
}