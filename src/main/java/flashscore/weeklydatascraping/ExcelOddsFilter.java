package flashscore.weeklydatascraping;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class ExcelOddsFilter {

    public static void main(String[] args) {
        String excelFilePath = "C:\\Users\\FD\\Desktop\\bet365.xlsx";
        String outputFilePath = "C:\\Users\\FD\\Desktop\\filtered_report.txt";

        // 20 farklı kombinasyon otomatik üretildi (örnek)
        List<List<String>> targetOddsList = Arrays.asList(
                Arrays.asList("1.50", "4.10", "5.75"),
                Arrays.asList("1.60", "3.80", "5.20"),
                Arrays.asList("1.33", "5.00", "7.00"),
                Arrays.asList("2.00", "3.00", "3.50"),
                Arrays.asList("1.62", "3.60", "4.50"),
                Arrays.asList("1.70", "4.00", "4.20"),
                Arrays.asList("1.53", "4.00", "5.00"),
                Arrays.asList("2.30", "2.75", "3.50"),
                Arrays.asList("2.20", "3.00", "3.10"),
                Arrays.asList("2.05", "2.80", "4.00"),
                Arrays.asList("2.45", "2.70", "3.10"),
                Arrays.asList("2.40", "3.25", "2.75"),
                Arrays.asList("1.85", "3.50", "3.90"),
                Arrays.asList("1.91", "3.50", "3.80"),
                Arrays.asList("3.00", "2.88", "2.30"),
                Arrays.asList("3.50", "3.25", "2.10"),
                Arrays.asList("4.33", "4.00", "1.73"),
                Arrays.asList("3.00", "3.60", "2.20"),
                Arrays.asList("2.00", "3.40", "3.90"),
                Arrays.asList("1.85", "3.50", "3.90")
        );

        try (FileInputStream file = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(file);
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {

            Sheet sheet = workbook.getSheetAt(0);
            int matchCount = 0;

            for (List<String> odds : targetOddsList) {
                String val1 = odds.get(0);
                String valx = odds.get(1);
                String val2 = odds.get(2);

                writer.write("===> Kombinasyon: 1: " + val1 + ", X: " + valx + ", 2: " + val2);
                writer.newLine();

                boolean matchFound = false;

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String home = getCellValue(row.getCell(0));
                    String away = getCellValue(row.getCell(1));
                    String ftHome = getCellValue(row.getCell(2));
                    String ftAway = getCellValue(row.getCell(3));
                    String htHome = getCellValue(row.getCell(4));
                    String htAway = getCellValue(row.getCell(5));
                    String c1 = getFormattedNumericCell(row.getCell(6));
                    String cx = getFormattedNumericCell(row.getCell(7));
                    String c2 = getFormattedNumericCell(row.getCell(8));

                    if (c1.equals(val1) && cx.equals(valx) && c2.equals(val2)) {
                        String line = String.format("→ %s vs %s | FT: %s-%s | HT: %s-%s | 1: %s, X: %s, 2: %s",
                                home, away, ftHome, ftAway, htHome, htAway, c1, cx, c2);
                        writer.write(line);
                        writer.newLine();
                        matchFound = true;
                        matchCount++;
                    }
                }

                if (!matchFound) {
                    writer.write("→ Eşleşen maç bulunamadı.");
                    writer.newLine();
                }

                writer.write("------------------------------------------------------");
                writer.newLine();
            }

            System.out.println("✅ Analiz tamamlandı. Toplam eşleşen maç sayısı: " + matchCount);
            System.out.println("📁 Rapor dosyası: " + outputFilePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getFormattedNumericCell(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.format(Locale.US, "%.2f", cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().replace(",", ".").trim();
        }
        return "";
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((int) cell.getNumericCellValue());
        return "";
    }
}
