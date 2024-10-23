package flashscore.weeklydatascraping;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class main {
    static WebDriver driverr = null;
    static List<String> matchIds = new ArrayList<>();
    static List<String> homeMatches = new ArrayList<>();
    static List<String> awayMatches = new ArrayList<>();
    static List<List<String>> allmatches = new ArrayList<>();

    public static void main(String[] args) {
        WebDriver driver = getChromeDriver();
        getMatchIds(driver);
        getMatchDatas(driver);
        writeMatchesToExcel(allmatches);
        driver.quit();
    }

      public static void getMatchDatas(WebDriver driver) {
        String matchUrl = "https://live.nowgoal23.com/match/h2h-";
        for(String id : matchIds){
            driver.get(matchUrl + id);
            driver.findElement(By.xpath("//*[@id='checkboxleague2']")).click();
            driver.findElement(By.xpath("//*[@id='checkboxleague1']")).click();

            try{
                // Select dropdowns
                Select dropdown1 = new Select(driver.findElement(By.id("selectMatchCount1")));
                dropdown1.selectByValue("2"); // Select "Last 2"

                Select dropdown2 = new Select(driver.findElement(By.id("selectMatchCount2")));
                dropdown2.selectByValue("2"); // Select "Last 2"
            } catch (Exception e){
                System.out.println("Liqada son 2 oyunu yoxdu !");
                continue;
            }


            addGamesHome(driver);
           List<String> temp1 = new ArrayList<>(homeMatches);
            allmatches.add(temp1);
            homeMatches.clear();

            addGamesAway(driver);
            List<String> temp2 = new ArrayList<>(awayMatches);
           allmatches.add(temp2);
            awayMatches.clear();
        }
      }

    public static void results(WebDriver driver,List<String> list) {
        String ht = driver.findElement(By.xpath("//*[@id=\"mScore\"]/div/div[2]/span/span[1]")).getText();
        String ft = driver.
                findElement(By.xpath("//*[@id=\"mScore\"]/div/div[1]"))
                .getText()
                .concat(driver.findElement(By.xpath("//*[@id=\"mScore\"]/div/div[3]")).getText());
        list.add(ht + " / " + ft.charAt(0)+"-"+ft.charAt(1));
    }

    public static void getMatchIds(WebDriver driver) {
        String baseUrl = "https://live.nowgoal23.com/football/fixture?f=ft";
        for (int i = 1; i < 8; i++) {
            String url = baseUrl + i;
            driver.get(url);
            List<WebElement> trElements = driver.findElements(By.xpath("//tr[not(@style) or @style='']"));
            for (WebElement element : trElements) {
                String matchId = element.getAttribute("matchid"); // Get the match ID
                if (matchId != null && !matchId.isEmpty()) { // Check for null or empty match ID
                    matchIds.add(matchId); // Add the match ID to the list
                }
            }
        }

    }

    public static void addGamesHome(WebDriver driver) {
        for (int i = 1; i < 4; i++) {
            String idz = "1_" + i;
            String xpath = "//tr[@id='tr" + idz + "']";

            try {
                WebElement element = driver.findElement(By.xpath(xpath)); // Find the element using the XPath
                String td =  element.findElements(By.tagName("td")).get(3).getText();
                if(!td.isEmpty() && Objects.nonNull(td)){
                    System.out.println("Score : "+td);
                    homeMatches.add(td);
                }


            } catch (Exception e) {

                continue;
            }
        }
        results(driver, homeMatches);
    }
    public static void addGamesAway(WebDriver driver) {
        for (int i = 1; i < 4; i++) {
            String idz = "2_" + i;
            String xpath = "//tr[@id='tr" + idz + "']";

            try {
                WebElement element = driver.findElement(By.xpath(xpath)); // Find the element using the XPath
                String td =  element.findElements(By.tagName("td")).get(3).getText();
                if(!td.isEmpty() && Objects.nonNull(td)){
                    System.out.println("Score : "+td);
                    awayMatches.add(td);
                }

            } catch (Exception e) {
                System.out.println("Element not found for id: " + idz);
                continue;
            }
        }
        results(driver, awayMatches);
    }

    public static void writeMatchesToExcel(List<List<String>> allmatches) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Match 1");
        headerRow.createCell(1).setCellValue("Match 2");
        headerRow.createCell(2).setCellValue("Result ht / ft");

        int rowNum = 1;


        for (int i = 0; i < allmatches.size(); i++) {
            List<String> matches = allmatches.get(i); // Get the matches for this matchId

            // Check that there are at least 2 matches available
            String match1 =  matches.get(0).substring(0, 3);
            String match2 =  matches.get(1).substring(0, 3);
            String result =  matches.get(2);


            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(match1);
            row.createCell(1).setCellValue(match2);
            row.createCell(2).setCellValue(result);
        }


        try (FileOutputStream fileOut = new FileOutputStream("match_results.xlsx")) {
            workbook.write(fileOut);
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Excel file created successfully!");
    }


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
}
