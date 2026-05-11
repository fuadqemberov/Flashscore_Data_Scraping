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

        // Sabit sütun sayısı: CODE-KOD, HT-IY, FT-MS, HOME, AWAY = 5
        final int FIXED = 5;

        // ---------- 0. SATIR: Grup başlıkları (sabit kısım boş bırakılacak) ----------
        Row groupRow = sheet.createRow(0);
        // ---------- 1. SATIR: Alt başlıklar ----------
        Row subRow = sheet.createRow(1);

        // Sabit alt başlıklar
        setCell(subRow, 0, "CODE-KOD", hStyle);
        setCell(subRow, 1, "HT-IY", hStyle);
        setCell(subRow, 2, "FT-MS", hStyle);
        setCell(subRow, 3, "HOME - EV SAHIBI", hStyle);
        setCell(subRow, 4, "AWAY - DEPLASMAN", hStyle);

        // Sütun genişlikleri
        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 10 * 256);
        sheet.setColumnWidth(3, 22 * 256);
        sheet.setColumnWidth(4, 22 * 256);

        // 0. satırda sabit başlıklar için birleştir (isteğe bağlı, boş bırakıyorum)
        for (int i = 0; i < FIXED; i++) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, i, i));
        }

        // Oran sütunlarını yaz
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
        // Son grubu birleştir
        if (currentGroup != null && groupStartCol < col - 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, groupStartCol, col - 1));
        }

        // En sona COUNTRY/LEAGUE ve DATE/TIME ekle
        int leagueCol = col;
        sheet.setColumnWidth(leagueCol, 16 * 256);
        setCell(subRow, leagueCol, "COUNTRY/LEAGUE", hStyle);
        int dateCol = col + 1;
        sheet.setColumnWidth(dateCol, 18 * 256);
        setCell(subRow, dateCol, "DATE/TIME", hStyle);

        // 0. satırda tarih/lig için alan bırak (birleştirmezsek de olur)
        sheet.addMergedRegion(new CellRangeAddress(0, 0, leagueCol, leagueCol));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, dateCol, dateCol));

        // ---------- VERİ SATIRLARI (2. satırdan başlar) ----------
        int rowNum = 2;
        for (MatchData md : data) {
            if (md.oddsMap.isEmpty()) continue;

            Row row = sheet.createRow(rowNum);
            CellStyle cs = (rowNum % 2 == 0) ? altStyle : null;

            // Sabit sütun değerleri
            setCell(row, 0, md.matchId, cs);                // CODE-KOD
            setCell(row, 1, md.htScore, cs);                // HT-IY
            setCell(row, 2, md.ftScore, cs);                // FT-MS
            setCell(row, 3, md.homeTeam, cs);               // HOME
            setCell(row, 4, md.awayTeam, cs);               // AWAY

            int dcol = FIXED;
            for (ScraperConstants.ColumnDef def : ScraperConstants.COLUMN_DEFS) {
                String val = md.oddsMap.getOrDefault(def.dataKey, "-");
                setCell(row, dcol, val, cs);
                dcol++;
            }

            // Lig ve Tarih
            setCell(row, leagueCol, "", cs);                // COUNTRY/LEAGUE - şimdilik boş
            setCell(row, dateCol, md.date, cs);             // DATE/TIME

            rowNum++;
        }

        // İlk 2 satırı dondur
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