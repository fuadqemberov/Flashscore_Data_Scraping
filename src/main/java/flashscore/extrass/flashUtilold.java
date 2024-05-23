package flashscore.extrass;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static flashscore.extrass.Main5.datas;

public class flashUtilold {

      static WebDriver driverr =null;

    public static WebDriver getChromeDriver(){
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        if(driverr==null){
            driverr = new ChromeDriver();
        }

        return driverr;
    }

    public static List<String> readUrlsFromFile(String fileName) {
        List<String> urls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                urls.add(line);
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
        return urls;
    }

    public static void exceleYazdir() {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Odds");

        // Create a header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Draw-FH");
        headerRow.createCell(1).setCellValue("Home-FH");
        headerRow.createCell(2).setCellValue("Away-FH");

        headerRow.createCell(3).setCellValue("Draw -FT");
        headerRow.createCell(4).setCellValue("Home-FT");
        headerRow.createCell(5).setCellValue("Away-FT");

        headerRow.createCell(6).setCellValue("Draw -SH");
        headerRow.createCell(7).setCellValue("Home-SH");
        headerRow.createCell(8).setCellValue("Away-SH");

        headerRow.createCell(9).setCellValue("12c");
        headerRow.createCell(10).setCellValue("1xc");
        headerRow.createCell(11).setCellValue("2x");

        headerRow.createCell(12).setCellValue("Asian 1 (-1)");
        headerRow.createCell(13).setCellValue("Asian 2 (-1)");

        headerRow.createCell(14).setCellValue("Eurohandicap x");
        headerRow.createCell(15).setCellValue("Eurohandicap 1");
        headerRow.createCell(16).setCellValue("Eurohandicap 2");
        headerRow.createCell(17).setCellValue("1/X");
        headerRow.createCell(18).setCellValue("1/2");
        headerRow.createCell(19).setCellValue("2/1");
        headerRow.createCell(20).setCellValue("2/X");
        headerRow.createCell(21).setCellValue("1/1");

        headerRow.createCell(22).setCellValue("2/2");
        headerRow.createCell(23).setCellValue("X/X");
        headerRow.createCell(24).setCellValue("X/2");
        headerRow.createCell(25).setCellValue("X/1");

        headerRow.createCell(26).setCellValue("2:1");
        headerRow.createCell(27).setCellValue("3:1");
        headerRow.createCell(28).setCellValue("1:0");
        headerRow.createCell(29).setCellValue("2:0");
        headerRow.createCell(30).setCellValue("4:1");
        headerRow.createCell(31).setCellValue("Liga");
        headerRow.createCell(32).setCellValue("Tarix");
        headerRow.createCell(33).setCellValue("Ht");
        headerRow.createCell(34).setCellValue("FT");
        headerRow.createCell(35).setCellValue("Home");
        headerRow.createCell(36).setCellValue("Away");

        headerRow.createCell(37).setCellValue("BTS-YES-HT");
        headerRow.createCell(38).setCellValue("BTS-NO-HT");
        headerRow.createCell(39).setCellValue("BTS-YES-FT");
        headerRow.createCell(40).setCellValue("BTS-NO-FT");

        headerRow.createCell(41).setCellValue("DRAW-NO-BET-HOME");
        headerRow.createCell(42).setCellValue("DRAW-NO-BET-AWAY");

        headerRow.createCell(43).setCellValue("OU-0.5-over");
        headerRow.createCell(44).setCellValue("OU-0.5-under");
        headerRow.createCell(45).setCellValue("OU-1.5-over");
        headerRow.createCell(46).setCellValue("OU-1.5-under");

        // Write the data to the Excel file
        for (int i = 0; i < datas.size(); i++) {
            Row row = sheet.createRow(i + 1);
            for(int j=0;j<47;j++){
                row.createCell(j).setCellValue(datas.get(i).get(j));
            }
        }

        // Save the workbook
        try (FileOutputStream fileOut = new FileOutputStream("src\\bet365.xlsx")) {
            workbook.write(fileOut);
            fileOut.close();
        }
        catch (Exception ex){
            System.out.println(ex.getLocalizedMessage());
        }


    }

}
