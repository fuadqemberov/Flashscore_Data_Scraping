package analyzer.fs.exporter;


import analyzer.fs.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;

public class ExcelExporter {

    public void export(ScrapeResult result, String filename) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {

            // 1. ÜLKELER Sayfası
            if (!result.countries().isEmpty()) {
                Sheet sheet = wb.createSheet("Countries");
                createHeaderRow(sheet, "Code", "Name", "URL");
                int row = 1;
                for (Country c : result.countries()) {
                    createRow(sheet, row++, c.code(), c.name(), c.url());
                }
                autoSizeColumns(sheet, 3);
            }

            // 2. LİGLER Sayfası
            if (!result.leagues().isEmpty()) {
                Sheet sheet = wb.createSheet("Leagues");
                createHeaderRow(sheet, "ID", "Name", "Country", "URL", "Slug");
                int row = 1;
                for (League l : result.leagues()) {
                    createRow(sheet, row++, l.id(), l.name(), l.countryCode(), l.url(), l.slug());
                }
                autoSizeColumns(sheet, 5);
            }

            // 3. SEZONLAR Sayfası
            if (!result.seasons().isEmpty()) {
                Sheet sheet = wb.createSheet("Seasons");
                createHeaderRow(sheet, "ID", "Name", "League ID", "URL");
                int row = 1;
                for (Season s : result.seasons()) {
                    createRow(sheet, row++, s.id(), s.name(), s.leagueId(), s.url());
                }
                autoSizeColumns(sheet, 4);
            }

            // 4. MAÇLAR Sayfası (ANA SAYFA)
            Sheet matchesSheet = wb.createSheet("Matches");
            createHeaderRow(matchesSheet,
                "Match ID", "Date", "Home Team", "Away Team",
                "Home Score", "Away Score", "Score", "Winner",
                "Season ID", "League ID", "Country",
                "Bet365 Home", "Bet365 Draw", "Bet365 Away"
            );

            int rowIdx = 1;
            for (Match m : result.matches()) {
                Odds odds = result.oddsMap().get(m.id());

                Row row = matchesSheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(m.id());
                row.createCell(col++).setCellValue(m.getFormattedDate());
                row.createCell(col++).setCellValue(m.homeTeam());
                row.createCell(col++).setCellValue(m.awayTeam());
                row.createCell(col++).setCellValue(m.homeScore() != null ? m.homeScore() : 0);
                row.createCell(col++).setCellValue(m.awayScore() != null ? m.awayScore() : 0);
                row.createCell(col++).setCellValue(m.getScore());
                row.createCell(col++).setCellValue(m.getWinner());
                row.createCell(col++).setCellValue(m.seasonId());
                row.createCell(col++).setCellValue(m.leagueId());
                row.createCell(col++).setCellValue(m.countryCode());

                if (odds != null) {
                    row.createCell(col++).setCellValue(odds.homeOdd());
                    row.createCell(col++).setCellValue(odds.drawOdd());
                    row.createCell(col++).setCellValue(odds.awayOdd());
                } else {
                    row.createCell(col++).setCellValue("-");
                    row.createCell(col++).setCellValue("-");
                    row.createCell(col++).setCellValue("-");
                }
            }
            autoSizeColumns(matchesSheet, 15);

            // 5. ORANLAR Sayfası
            if (!result.oddsMap().isEmpty()) {
                Sheet sheet = wb.createSheet("Odds");
                createHeaderRow(sheet, "Match ID", "Bookmaker", "Home Odd", "Draw Odd", "Away Odd", "Timestamp");
                int row = 1;
                for (Odds o : result.oddsMap().values()) {
                    createRow(sheet, row++, o.matchId(), o.bookmaker(),
                        String.valueOf(o.homeOdd()), String.valueOf(o.drawOdd()),
                        String.valueOf(o.awayOdd()), String.valueOf(o.timestamp()));
                }
                autoSizeColumns(sheet, 6);
            }

            // Kaydet
            try (FileOutputStream fos = new FileOutputStream(filename)) {
                wb.write(fos);
            }

            System.out.println("📊 Excel dosyası oluşturuldu: " + filename);
            System.out.println("   • Ülkeler: " + result.countries().size());
            System.out.println("   • Ligler: " + result.leagues().size());
            System.out.println("   • Sezonlar: " + result.seasons().size());
            System.out.println("   • Maçlar: " + result.matches().size());
            System.out.println("   • Oranlar: " + result.oddsMap().size());
        }
    }

    private void createHeaderRow(Sheet sheet, String... headers) {
        Row row = sheet.createRow(0);
        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void createRow(Sheet sheet, int rowNum, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
