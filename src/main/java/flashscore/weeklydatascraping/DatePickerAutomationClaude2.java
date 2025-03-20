package flashscore.weeklydatascraping;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatePickerAutomationClaude2 {

    private static List<String> links = Collections.synchronizedList(new ArrayList<>());
    private static CopyOnWriteArrayList<List<String>> allData = new CopyOnWriteArrayList<>();
    private static final int MAX_THREADS = 3; // Leave one core free
    private static final Object fileLock = new Object();
    private static final int MAX_RETRIES = 0;

    public static void main(String[] args) throws InterruptedException, IOException {
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);
        long startTime = System.currentTimeMillis();

        // Step 1: Get all match links
        List<String> linksToProcess = readFile();
        processLinksInParallel(linksToProcess);

        // Step 3: Write results to Excel
        writeMatchesToExcel();

        long endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) / 1000 + " seconds");
    }

    private static void processLinksInParallel(List<String> linksToProcess) throws InterruptedException {
        System.out.println("Processing " + linksToProcess.size() + " links using " + MAX_THREADS + " threads...");

        ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
        CountDownLatch latch = new CountDownLatch(linksToProcess.size());
        AtomicInteger processedCount = new AtomicInteger(0);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) { // HttpClient'ı burada oluştur
            for (String link : linksToProcess) {
                executorService.submit(() -> {
                    try {
                        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                            try {
                                getData(link, httpClient); // HttpClient'ı fonksiyona geçir
                                break; // Success, exit retry loop
                            } catch (Exception e) {
                                if (attempt == MAX_RETRIES) {
                                    System.err.println("Final attempt failed for link " + link + ": " + e.getMessage());
                                } else {
                                    System.out.println("Attempt " + attempt + " failed for link " + link + ". Retrying...");
                                    Thread.sleep(1000 * attempt); // Exponential backoff
                                }
                            }
                        }

                        int count = processedCount.incrementAndGet();
                        if (count % 5 == 0 || count == linksToProcess.size()) {
                            System.out.println("Processed " + count + " out of " + linksToProcess.size() + " links");
                        }
                    } catch (Exception e) {
                        System.err.println("Error in thread processing link " + link + ": " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(); // Wait for all threads to complete
        } catch (IOException e) {
            System.err.println("Failed to create HttpClient: " + e.getMessage());
        }

        executorService.shutdown();
        System.out.println("All links processed successfully!");
    }

    private static void getData(String link, CloseableHttpClient httpClient) {  // HttpClient'ı parametre olarak al
        String html = fetchContent(link + "/karsilastirma", httpClient); // linke "/karsilastirma" ekle
        if (html == null) {
            System.err.println("Failed to fetch content for link: " + link);
            return;
        }

        Document doc = Jsoup.parse(html);
        String htFt = extractFormattedScore(doc);

        for (int j = 1; j < 3; j++) {
            List<String> teamList = new ArrayList<>();
            boolean hasData = false;

            for (int i = 2; i < 7; i++) {
                try {
                    String xpathHome = ".p0c-match-head-to-head__played-matches--total > div:nth-child(" + j + ") > div:nth-child(" + i + ") a:nth-child(1) div span";
                    String xpathScore = ".p0c-match-head-to-head__played-matches--total > div:nth-child(" + j + ") > div:nth-child(" + i + ") a:nth-child(2) div";
                    String xpathAway = ".p0c-match-head-to-head__played-matches--total > div:nth-child(" + j + ") > div:nth-child(" + i + ") a:nth-child(3) div span:nth-child(2)";


                    Element homeElement = doc.selectFirst(xpathHome);
                    Element scoreElement = doc.selectFirst(xpathScore);
                    Element awayElement = doc.selectFirst(xpathAway);


                    String home = (homeElement != null) ? homeElement.text() : "";
                    String score = (scoreElement != null) ? scoreElement.text() : "";
                    String away = (awayElement != null) ? awayElement.text() : "";

                    if (!home.isEmpty() && !score.isEmpty() && !away.isEmpty()) {
                        teamList.add(home);
                        teamList.add(score);
                        teamList.add(away);
                        hasData = true;
                    } else {
                        teamList.add("N/A");
                        teamList.add("N/A");
                        teamList.add("N/A");
                    }

                    if (i == 6) {
                        teamList.add(htFt);
                    }
                } catch (Exception ex) {
                    System.out.println("Veri alınamadı (j=" + j + ", i=" + i + "): " + ex.getMessage());
                    // Add placeholders to maintain data structure
                    teamList.add("N/A");
                    teamList.add("N/A");
                    teamList.add("N/A");
                    if (i == 6) {
                        teamList.add("N/A");
                    }
                }
            }

            if (hasData) {
                allData.add(new ArrayList<>(teamList));
            }
        }
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
            Element homeScoreElement = doc.selectFirst(".p0c-soccer-match-details-header__score-home");
            Element awayScoreElement = doc.selectFirst(".p0c-soccer-match-details-header__score-away");

            String homeScore = (homeScoreElement != null) ? homeScoreElement.text().trim() : "?";
            String awayScore = (awayScoreElement != null) ? awayScoreElement.text().trim() : "?";

            Element detailedScoreElement = doc.selectFirst(".p0c-soccer-match-details-header__detailed-score");
            String halfTimeScore = "0-0";

            if (detailedScoreElement != null) {
                String detailedScore = detailedScoreElement.text().trim();
                if (detailedScore.contains("İY")) {
                    String htScoreText = detailedScore.replace("(İY", "").replace(")", "").trim();
                    if (!htScoreText.isEmpty()) {
                        halfTimeScore = htScoreText;
                    }
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
        File file = new File("mackolik2.txt");

        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    public static void writeMatchesToExcel() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Result-Home");
        headerRow.createCell(1).setCellValue("Result-Score");
        headerRow.createCell(2).setCellValue("Result-Away");

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

        headerRow.createCell(15).setCellValue("HT / FT");

        int rowNum = 1;
        for (List<String> teamMatches : allData) {
            Row row = sheet.createRow(rowNum++);

            if (teamMatches.size() >= 16) {
                for (int i = 0; i < 16; i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
            } else {
                System.out.println("Warning: Not enough match data for row " + rowNum);
                // Fill available data
                for (int i = 0; i < teamMatches.size(); i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
                // Fill remaining cells with N/A
                for (int i = teamMatches.size(); i < 16; i++) {
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