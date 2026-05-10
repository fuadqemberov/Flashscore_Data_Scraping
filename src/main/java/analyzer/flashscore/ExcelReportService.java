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

        // Sabit sütun sayısı: FT Score, HT Score, Home, Away = 4
        final int FIXED = 4;

        // ---------- 0. SATIR: Grup üst başlıkları (sadece oranlar için) ----------
        Row groupRow = sheet.createRow(0);
        // ---------- 1. SATIR: Alt başlıklar ----------
        Row subRow = sheet.createRow(1);

        // YENİ SIRALAMA: FT Score (0), HT Score (1), Home (2), Away (3)
        setCell(subRow, 0, "FT Score", hStyle);
        setCell(subRow, 1, "HT Score", hStyle);
        setCell(subRow, 2, "Home", hStyle);
        setCell(subRow, 3, "Away", hStyle);

        // Sütun genişlikleri
        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 20 * 256);

        // Oran sütunlarını yaz
        int col = FIXED;
        String currentGroup = null;
        int groupStartCol = FIXED;

        for (ScraperConstants.ColumnDef def : ScraperConstants.COLUMN_DEFS) {
            sheet.setColumnWidth(col, 13 * 256);

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

        // Date sütunu en sona
        int dateCol = col;
        sheet.setColumnWidth(dateCol, 15 * 256);
        setCell(subRow, dateCol, "Date", hStyle);

        // ---------- VERİ SATIRLARI (2. satırdan başlar) ----------
        int rowNum = 2;
        for (MatchData md : data) {
            if (md.oddsMap.isEmpty()) {
                continue;
            }

            Row row = sheet.createRow(rowNum);
            CellStyle cs = (rowNum % 2 == 0) ? altStyle : null;

            // Yeni sıralamaya göre verileri yaz
            setCell(row, 0, md.ftScore, cs);
            setCell(row, 1, md.htScore, cs);
            setCell(row, 2, md.homeTeam, cs);
            setCell(row, 3, md.awayTeam, cs);

            int dcol = FIXED;
            for (ScraperConstants.ColumnDef def : ScraperConstants.COLUMN_DEFS) {
                String val = md.oddsMap.getOrDefault(def.dataKey, "-");
                setCell(row, dcol, val, cs);
                dcol++;
            }

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