package analyzer.flashscore;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelReportService {

    public static void generateReport(List<MatchData> data, String filename) throws IOException {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Bet365 Odds");

        CellStyle hStyle = makeStyle(wb, IndexedColors.DARK_BLUE, IndexedColors.WHITE, true);
        CellStyle tStyle = makeStyle(wb, IndexedColors.DARK_TEAL, IndexedColors.WHITE, true);
        CellStyle altStyle = wb.createCellStyle();
        altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final int FIXED = 5;

        Row groupRow = sheet.createRow(0);
        Row subRow = sheet.createRow(1);

        setCell(subRow, 0, "CODE-KOD", hStyle);
        setCell(subRow, 1, "HT-IY", hStyle);
        setCell(subRow, 2, "FT-MS", hStyle);
        setCell(subRow, 3, "HOME - EV SAHIBI", hStyle);
        setCell(subRow, 4, "AWAY - DEPLASMAN", hStyle);

        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 10 * 256);
        sheet.setColumnWidth(3, 22 * 256);
        sheet.setColumnWidth(4, 22 * 256);

        // ✅ Tek hücre merge KALDIRILDI

        int col = FIXED;
        String currentGroup = null;
        int groupStartCol = FIXED;

        for (ScraperConstants.ColumnDef def : ScraperConstants.COLUMN_DEFS) {
            sheet.setColumnWidth(col, 14 * 256);

            if (!def.groupLabel.equals(currentGroup)) {
                if (currentGroup != null && groupStartCol < col - 1) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, groupStartCol, col - 1));
                }
                currentGroup = def.groupLabel;
                groupStartCol = col;
            }

            setCell(groupRow, col, def.groupLabel, tStyle);
            setCell(subRow, col, def.subLabel, hStyle);
            col++;
        }

        if (currentGroup != null && groupStartCol < col - 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, groupStartCol, col - 1));
        }

        int leagueCol = col;
        sheet.setColumnWidth(leagueCol, 16 * 256);
        setCell(subRow, leagueCol, "COUNTRY/LEAGUE", hStyle);
        int dateCol = col + 1;
        sheet.setColumnWidth(dateCol, 18 * 256);
        setCell(subRow, dateCol, "DATE/TIME", hStyle);

        // ✅ leagueCol ve dateCol için tek hücre merge KALDIRILDI

        int rowNum = 2;
        for (MatchData md : data) {
            if (md.oddsMap.isEmpty()) continue;

            Row row = sheet.createRow(rowNum);
            CellStyle cs = (rowNum % 2 == 0) ? altStyle : null;

            setCell(row, 0, md.matchId, cs);
            setCell(row, 1, md.htScore, cs);
            setCell(row, 2, md.ftScore, cs);
            setCell(row, 3, md.homeTeam, cs);
            setCell(row, 4, md.awayTeam, cs);

            int dcol = FIXED;
            for (ScraperConstants.ColumnDef def : ScraperConstants.COLUMN_DEFS) {
                String val = md.oddsMap.getOrDefault(def.dataKey, "-");
                setCell(row, dcol, val, cs);
                dcol++;
            }

            setCell(row, leagueCol, "", cs);
            setCell(row, dateCol, md.date, cs);

            rowNum++;
        }

        sheet.createFreezePane(0, 2);

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wb.write(fos);
        }
        wb.close();

        AppLogger.log("Excel'e sadece oranları olan toplam " + (rowNum - 2) + " maç yazıldı.");
    }

    static CellStyle makeStyle(Workbook wb, IndexedColors bg, IndexedColors fg, boolean bold) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(bold);
        f.setColor(fg.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(bg.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    static void setCell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        if (style != null) c.setCellStyle(style);
    }
}