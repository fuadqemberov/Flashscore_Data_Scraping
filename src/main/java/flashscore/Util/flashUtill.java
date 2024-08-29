package flashscore.Util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static flashscore.Data.flashDataScraper.datas;
import static flashscore.Data.flashDataScraper.macthes;


public class flashUtill {

      static WebDriver driverr =null;

    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        if (driverr == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless"); // Run Chrome in headless mode
            options.addArguments("--disable-gpu"); // Applicable to Windows OS to prevent an error related to headless mode
            options.addArguments("--window-size=1920,1080"); // Optional: Set the window size for the headless browser
            options.addArguments("--ignore-certificate-errors"); // Optional: Ignore certificate errors
            options.addArguments("--silent"); // Optional: Suppress console output

            driverr = new ChromeDriver(options);
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


        headerRow.createCell(31).setCellValue("BTS-YES-HT");
        headerRow.createCell(32).setCellValue("BTS-NO-HT");
        headerRow.createCell(33).setCellValue("BTS-YES-FT");
        headerRow.createCell(34).setCellValue("BTS-NO-FT");

        headerRow.createCell(35).setCellValue("DRAW-NO-BET-HOME");
        headerRow.createCell(36).setCellValue("DRAW-NO-BET-AWAY");

        headerRow.createCell(37).setCellValue("OU-0.5-over");
        headerRow.createCell(38).setCellValue("OU-0.5-under");
        headerRow.createCell(39).setCellValue("OU-1.5-over");
        headerRow.createCell(40).setCellValue("OU-1.5-under");

        headerRow.createCell(41).setCellValue("Liga");
        headerRow.createCell(42).setCellValue("Tarix");
        headerRow.createCell(43).setCellValue("Ht");
        headerRow.createCell(44).setCellValue("FT");
        headerRow.createCell(45).setCellValue("Home");
        headerRow.createCell(46).setCellValue("Away");

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
    public static void exceleYazdir2() {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Matches");

        // Create a header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Home1");
        headerRow.createCell(1).setCellValue("Home2");
        headerRow.createCell(2).setCellValue("Home3");
        headerRow.createCell(3).setCellValue("Away1 ");
        headerRow.createCell(4).setCellValue("Away2");
        headerRow.createCell(5).setCellValue("Away3");
        headerRow.createCell(6).setCellValue("result FT");
        headerRow.createCell(7).setCellValue("result HT");




        for (int i = 0; i < datas.size(); i++) {
            Row row = sheet.createRow(i + 1);
            for(int j=0;j<8;j++){
                row.createCell(j).setCellValue(datas.get(i).get(j));
            }
        }

        // Save the workbook
        try (FileOutputStream fileOut = new FileOutputStream("src\\lasmathes.xlsx")) {
            workbook.write(fileOut);
            fileOut.close();
        }
        catch (Exception ex){
            System.out.println(ex.getLocalizedMessage());
        }


    }

}
