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

        final int FIXED = 6;   // Date, Match ID, Home, Away, FT Score, HT Score

        // ------------------- 0. SATIR: Sabit başlıklar (ilk satır) -------------------
        Row headerRow0 = sheet.createRow(0);
        setCell(headerRow0, 0, "Date", hStyle);
        setCell(headerRow0, 1, "Match ID", hStyle);
        setCell(headerRow0, 2, "Home", hStyle);
        setCell(headerRow0, 3, "Away", hStyle);
        setCell(headerRow0, 4, "FT Score", hStyle);
        setCell(headerRow0, 5, "HT Score", hStyle);

        // ------------------- 1. SATIR: Grup üst başlıkları -------------------
        Row groupRow = sheet.createRow(1);
        // ------------------- 2. SATIR: Alt başlıklar -------------------
        Row subRow = sheet.createRow(2);

        // Sütun genişlikleri (sabit)
        sheet.setColumnWidth(0, 15 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 20 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        sheet.setColumnWidth(5, 12 * 256);

        int col = FIXED;
        String currentGroup = null;
        int groupStartCol = FIXED;

        for (ScraperConstants.ColumnDef def : ScraperConstants.COLUMN_DEFS) {
            sheet.setColumnWidth(col, 13 * 256);

            // Grup değiştiyse önceki grubu birleştir
            if (!def.groupLabel.equals(currentGroup)) {
                if (currentGroup != null && groupStartCol < col - 1) {
                    sheet.addMergedRegion(new CellRangeAddress(1, 1, groupStartCol, col - 1));
                }
                currentGroup = def.groupLabel;
                groupStartCol = col;
            }

            setCell(groupRow, col, def.groupLabel, tStyle);
            setCell(subRow, col, def.subLabel, hStyle);
            col++;
        }
        // Son grubu birleştir
        if (currentGroup != null && groupStartCol < col - 1) {
            sheet.addMergedRegion(new CellRangeAddress(1, 1, groupStartCol, col - 1));
        }

        // ----- DÜZELTME: Sabit sütunlarda 0-2 satırlarını birleştirerek boşluk kalmamasını sağla -----
        for (int i = 0; i < FIXED; i++) {
            sheet.addMergedRegion(new CellRangeAddress(0, 2, i, i));
        }

        // ------------------- VERİ SATIRLARI -------------------
        int rowNum = 3;   // veri satırı başlangıcı (0,1,2 başlık)
        for (MatchData md : data) {
            if (md.oddsMap.isEmpty()) {
                continue;
            }

            Row row = sheet.createRow(rowNum);
            CellStyle cs = (rowNum % 2 == 0) ? altStyle : null;

            setCell(row, 0, md.date, cs);
            setCell(row, 1, md.matchId, cs);
            setCell(row, 2, md.homeTeam, cs);
            setCell(row, 3, md.awayTeam, cs);
            setCell(row, 4, md.ftScore, cs);
            setCell(row, 5, md.htScore, cs);

            int dcol = FIXED;
            for (ScraperConstants.ColumnDef def : ScraperConstants.COLUMN_DEFS) {
                String val = md.oddsMap.getOrDefault(def.dataKey, "-");
                setCell(row, dcol, val, cs);
                dcol++;
            }
            rowNum++;
        }

        // Bölmeyi dondur (ilk 3 satır sabit)
        sheet.createFreezePane(0, 3);

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wb.write(fos);
        }
        wb.close();

        AppLogger.log("Excel'e sadece oranları olan toplam " + (rowNum - 3) + " maç yazıldı.");
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