package flashscore.weeklydatascraping.nowgoal;

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
import java.util.*;

public class nowgoalLastMatches {
    static List<String> homeMatches = new ArrayList<>();
    static List<String> awayMatches = new ArrayList<>();
    static List<List<String>> allmatches = new ArrayList<>();

    public static void main(String[] args) {
        WebDriver driver = initializeDriver();
        getMatchDatas(driver);
        writeMatchesToExcel(allmatches);
        driver.quit();
    }

    public static void getMatchDatas(WebDriver driver) {
        String matchUrl = "https://live15.nowgoal29.com/match/h2h-";
        for (long id = 2500000; id < 2500002; id++) {
            driver.get(matchUrl + id);
            driver.findElement(By.xpath("//*[@id='checkboxleague2']")).click();
            driver.findElement(By.xpath("//*[@id='checkboxleague1']")).click();

            try {
                // Select dropdowns
                Select dropdown1 = new Select(driver.findElement(By.id("selectMatchCount1")));
                dropdown1.selectByValue("3"); // Select "Last 2"

                Select dropdown2 = new Select(driver.findElement(By.id("selectMatchCount2")));
                dropdown2.selectByValue("3"); // Select "Last 2"
            } catch (Exception e) {
                continue;
            }
             String result = results(driver);
             String liga = driver.findElement(By.xpath("//*[@id=\"fbheader\"]/div[1]/span[1]/span")).getText();
            if (homeMatches.size() == awayMatches.size()) {
                addGamesHome(driver,result,liga);
                List<String> temp1 = new ArrayList<>(homeMatches);
                allmatches.add(temp1);
                homeMatches.clear();

                addGamesAway(driver,result,liga);
                List<String> temp2 = new ArrayList<>(awayMatches);
                allmatches.add(temp2);
                awayMatches.clear();
            }

        }
    }

    public static String results(WebDriver driver) {
        String result = null;
        try {
            // İlk olarak elementlerin var olduğundan emin olalım
            WebElement htElement = driver.findElement(By.xpath("//div[@id='mScore']//span[@title='Score 1st Half']"));
            WebElement ft1Element = driver.findElement(By.xpath("//div[@id='mScore']//div[@class='end']//div[@class='score'][1]"));
            WebElement ft2Element = driver.findElement(By.xpath("//div[@id='mScore']//div[@class='end']//div[@class='score'][2]"));

            // getText() çağrılarını yapalım ve null kontrolü ekleyelim
            String ht = htElement.getText();
            String ft1 = ft1Element.getText();
            String ft2 = ft2Element.getText();
            

            // Stringlerin boş olmadığından emin olalım
            if (ht != null && !ht.isEmpty() && ft1 != null && !ft1.isEmpty() && ft2 != null && !ft2.isEmpty()) {
                String ft = ft1 + "-" + ft2;
                result = ht + " / " + ft;
            } else {
                System.out.println("One of the score elements is empty");
            }

        } catch (NoSuchElementException e) {
            System.out.println("Element not found: " + e.getMessage());
            return "N/A";
        } catch (Exception e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
        
        return result;
    }

    public static void addGamesHome(WebDriver driver,String result,String liga) {

        String xpath = "//tr[starts-with(@id, 'tr1_') and (not(@style) or @style='')]";

        try {
            List<WebElement> elements = driver.findElements(By.xpath(xpath));// Find the element using the XPath
            for (WebElement element : elements) {
                String td = element.findElements(By.tagName("td")).get(3).getText();
                if (!td.isEmpty() && Objects.nonNull(td)) {
                    homeMatches.add(td);
                }
            }
        } catch (Exception e) {
            homeMatches.add("N/A");
        }
        homeMatches.add(result);
        homeMatches.add(liga);
    }

    public static void addGamesAway(WebDriver driver,String result,String liga) {

        String xpath = "//tr[starts-with(@id, 'tr2_') and (not(@style) or @style='')]";
        try {
            List<WebElement> elements = driver.findElements(By.xpath(xpath));// Find the element using the XPath
            for (WebElement element : elements) {
                String td = element.findElements(By.tagName("td")).get(3).getText();
                if (!td.isEmpty() && Objects.nonNull(td)) {
                    awayMatches.add(td);
                }
            }
        } catch (Exception e) {
            awayMatches.add("N/A");
        }
        awayMatches.add(result);
        awayMatches.add(liga);
    }


    public static void writeMatchesToExcel(List<List<String>> allmatches) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Match 1");
        headerRow.createCell(1).setCellValue("Match 2");
        headerRow.createCell(2).setCellValue("Match 3");
        headerRow.createCell(3).setCellValue("Result ht / ft");
        headerRow.createCell(4).setCellValue("Liga");

        int rowNum = 1;


        for (int i = 0; i < allmatches.size(); i++) {
            List<String> matches = allmatches.get(i);
            System.out.println(matches);

            String match1 = matches.get(0).substring(0, 3);
            String match2 = matches.get(1).substring(0, 3);
            String match3 = matches.get(2).substring(0, 3);
            String liga = matches.get(3);
            String result = matches.get(4);

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(match1);
            row.createCell(1).setCellValue(match2);
            row.createCell(2).setCellValue(match3);
            row.createCell(3).setCellValue(liga);
            row.createCell(4).setCellValue(result);

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

    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Başsız modda çalıştır
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        return driver;
    }
}