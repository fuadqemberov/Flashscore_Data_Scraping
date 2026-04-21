package analyzer.flashscore;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static analyzer.flashscore.ScraperConstants.STATIC_COLUMN_KEYS;


public class ExcelReportService {
    public static void generateReport(List<MatchData> data, String filename) throws IOException {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Bet365 Odds");

        CellStyle hStyle = makeStyle(wb, IndexedColors.DARK_BLUE, IndexedColors.WHITE, true);
        CellStyle tStyle = makeStyle(wb, IndexedColors.DARK_TEAL, IndexedColors.WHITE, true);
        CellStyle altStyle = wb.createCellStyle();
        altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final int FIXED = 8;

        Row grpRow = sheet.createRow(0);
        setCell(grpRow, 0, "Date/Time", hStyle);
        sheet.setColumnWidth(0, 18 * 256);
        setCell(grpRow, 1, "Match ID", hStyle);
        sheet.setColumnWidth(1, 14 * 256);
        setCell(grpRow, 2, "Country", hStyle);
        sheet.setColumnWidth(2, 15 * 256);
        setCell(grpRow, 3, "League", hStyle);
        sheet.setColumnWidth(3, 25 * 256);
        setCell(grpRow, 4, "Home", hStyle);
        sheet.setColumnWidth(4, 20 * 256);
        setCell(grpRow, 5, "Away", hStyle);
        sheet.setColumnWidth(5, 20 * 256);
        setCell(grpRow, 6, "FT Score", hStyle);
        sheet.setColumnWidth(6, 12 * 256);
        setCell(grpRow, 7, "HT Score", hStyle);
        sheet.setColumnWidth(7, 12 * 256);

        String lastGrp = "";
        int grpStartCol = FIXED;
        for (int i = 0; i < STATIC_COLUMN_KEYS.size(); i++) {
            String key = STATIC_COLUMN_KEYS.get(i);
            String[] p = key.split("\\|");
            String grp = p[0] + " | " + p[1];
            int col = FIXED + i;
            sheet.setColumnWidth(col, 13 * 256);

            if (!grp.equals(lastGrp)) {
                if (!lastGrp.isEmpty() && grpStartCol < col - 1)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, grpStartCol, col - 1));
                setCell(grpRow, col, grp, tStyle);
                lastGrp = grp;
                grpStartCol = col;
            }
        }
        if (!lastGrp.isEmpty()) {
            int lastDataCol = FIXED + STATIC_COLUMN_KEYS.size() - 1;
            if (grpStartCol < lastDataCol) sheet.addMergedRegion(new CellRangeAddress(0, 0, grpStartCol, lastDataCol));
        }

        Row lblRow = sheet.createRow(1);
        setCell(lblRow, 0, "Date/Time", hStyle);
        setCell(lblRow, 1, "Match ID", hStyle);
        setCell(lblRow, 2, "Country", hStyle);
        setCell(lblRow, 3, "League", hStyle);
        setCell(lblRow, 4, "Home", hStyle);
        setCell(lblRow, 5, "Away", hStyle);
        setCell(lblRow, 6, "FT Score", hStyle);
        setCell(lblRow, 7, "HT Score", hStyle);
        for (int i = 0; i < STATIC_COLUMN_KEYS.size(); i++) {
            String label = STATIC_COLUMN_KEYS.get(i).split("\\|")[2];
            setCell(lblRow, FIXED + i, label, hStyle);
        }

        int rowNum = 2;
        for (MatchData md : data) {
            Row row = sheet.createRow(rowNum);
            CellStyle cs = (rowNum % 2 == 0) ? altStyle : null;

            setCell(row, 0, (md.matchDateTime != null && !md.matchDateTime.equals("-")) ? md.matchDateTime : md.date, cs);
            setCell(row, 1, md.matchId, cs);
            setCell(row, 2, md.country, cs);
            setCell(row, 3, md.league, cs);
            setCell(row, 4, md.homeTeam, cs);
            setCell(row, 5, md.awayTeam, cs);
            setCell(row, 6, md.ftScore, cs);
            setCell(row, 7, md.htScore, cs);

            for (int i = 0; i < STATIC_COLUMN_KEYS.size(); i++) {
                String val = md.oddsMap.getOrDefault(STATIC_COLUMN_KEYS.get(i), "-");
                setCell(row, FIXED + i, val, cs);
            }
            rowNum++;
        }

        sheet.createFreezePane(0, 2);
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wb.write(fos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        wb.close();
        System.out.println("Toplam " + (rowNum - 2) + " mac, " + STATIC_COLUMN_KEYS.size() + " sutun yazildi.");
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
