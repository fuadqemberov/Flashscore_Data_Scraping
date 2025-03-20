package flashscore.weeklydatascraping.mackolik;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NowgoalLastMatchesJSoup {
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final List<List<String>> allMatches = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Queue<Long> idQueue = new ConcurrentLinkedQueue<>();

        long startId = 2738509;
        long endId = 2738511;

        for (long id = startId; id < endId; id++) {
            idQueue.add(id);
        }

        System.out.println("Starting to process " + (endId - startId) + " matches");

        // Progress tracking thread
        executor.submit(() -> {
            int totalMatches = (int)(endId - startId);
            int lastReported = 0;
            int lastFailed = 0;

            while (!idQueue.isEmpty() || (lastReported + lastFailed) < totalMatches) {
                int current = successCount.get();
                int failed = failCount.get();
                if (current > lastReported || failed > lastFailed) {
                    int processed = current + failed;
                    System.out.printf("Progress: %d/%d (%.1f%%) - Success: %d, Failed: %d%n",
                            processed, totalMatches,
                            (double)processed/totalMatches*100,
                            current, failed);
                    lastReported = current;
                    lastFailed = failed;
                }

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        // Worker threads
        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            executor.submit(() -> {
                while (!idQueue.isEmpty()) {
                    Long id = idQueue.poll();
                    if (id != null) {
                        try {
                            processMatch(id);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            System.err.println("Error processing match " + id + ": " + e.getMessage());
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.MINUTES)) {
                System.err.println("Executor timed out before completion");
                executor.shutdownNow();
            }

            writeMatchesToExcel(allMatches);

            long endTime = System.currentTimeMillis();
            System.out.println("Completed in " + (endTime - startTime) / 1000 + " seconds");
            System.out.println("Successfully processed " + successCount.get() + " matches");
            System.out.println("Failed to process " + failCount.get() + " matches");
            System.out.println("Results written to match_results.xlsx");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Process interrupted: " + e.getMessage());
            executor.shutdownNow();
        }
    }

    private static void processMatch(long matchId) {
        try {
            Document doc = Jsoup.connect("https://live14.nowgoal25.com/match/h2h-" + matchId)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(30000)
                    .get();

            List<String> homeData = extractMatchData(doc, "tr1_");
            List<String> awayData = extractMatchData(doc, "tr2_");
            String result = extractResult(doc);
            String league = extractLeague(doc);

            if (league.equals("N/A") || homeData.isEmpty() || awayData.isEmpty()) return;

            synchronized (allMatches) {
                List<String> homeRow = new ArrayList<>(homeData);
                homeRow.add(result);
                homeRow.add(league);
                homeRow.add(String.valueOf(matchId));
                allMatches.add(homeRow);

                List<String> awayRow = new ArrayList<>(awayData);
                awayRow.add(result);
                awayRow.add(league);
                awayRow.add(String.valueOf(matchId));
                allMatches.add(awayRow);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing match " + matchId, e);
        }
    }

    private static List<String> extractMatchData(Document doc, String rowPrefix) {
        List<String> matches = new ArrayList<>();
        Elements rows = doc.select("tr[id^=" + rowPrefix + "]");

        for (Element row : rows) {
            Elements tds = row.select("td");
            if (tds.size() > 3) {
                String scoreText = tds.get(3).text();
                matches.add(scoreText.length() >= 3 ? scoreText.substring(0, 3) : scoreText);
            }
        }
        return matches;
    }

    private static String extractResult(Document doc) {
        Element ht = doc.selectFirst("div#mScore span[title='Score 1st Half']");
        Elements ft = doc.select("div#mScore div.end div.score");
        return (ht != null ? ht.text() : "N/A") + " / " +
                (ft.size() > 1 ? ft.get(0).text() + "-" + ft.get(1).text() : "N/A");
    }

    private static String extractLeague(Document doc) {
        Element league = doc.selectFirst("#fbheader > div:first-child > span:first-child > span");
        return league != null ? league.text() : "N/A";
    }

    private static void writeMatchesToExcel(List<List<String>> data) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Match Results");
            createHeaderRow(sheet);

            int rowNum = 1;
            for (List<String> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < rowData.size(); i++) {
                    row.createCell(i).setCellValue(rowData.get(i));
                }
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream("match_results.xlsx")) {
                workbook.write(fos);
            }
        } catch (IOException e) {
            System.err.println("Excel write error: " + e.getMessage());
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {"Match 1", "Match 2", "Match 3", "Result", "League", "Match ID"};
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }
}