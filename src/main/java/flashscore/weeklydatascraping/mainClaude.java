package flashscore.weeklydatascraping;


import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
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

public class mainClaude {
    static WebDriver driverr = null;
    static List<String> homeMatches = new ArrayList<>();
    static List<String> awayMatches = new ArrayList<>();
    static List<List<String>> allmatches = new ArrayList<>();

    public static void main(String[] args) {
        WebDriver driver = getChromeDriver();
        getMatchDatas(driver);
        writeMatchesToExcel(allmatches);
        driver.quit();
    }

    public static void getMatchDatas(WebDriver driver) {
        String matchUrl = "https://live.nowgoal23.com/match/h2h-";
        for (String id : getMatchIds(driver)) {
            driver.get(matchUrl + id);
            try {
                Thread.sleep(1000); // Sayfanın yüklenmesi için kısa bekleme

                driver.findElement(By.xpath("//*[@id='checkboxleague2']")).click();
                driver.findElement(By.xpath("//*[@id='checkboxleague1']")).click();

                try {
                    // Select dropdowns
                    Select dropdown1 = new Select(driver.findElement(By.id("selectMatchCount1")));
                    dropdown1.selectByValue("2");

                    Select dropdown2 = new Select(driver.findElement(By.id("selectMatchCount2")));
                    dropdown2.selectByValue("2");

                    Thread.sleep(500); // Dropdown değişikliğinin yansıması için bekleme

                    // Önce maçları ekle
                    addGamesHome(driver);

                    // Sonra sonucu ekle
                    if (!homeMatches.isEmpty()) {
                        String result = getResult(driver);
                        if (result != null && !result.isEmpty()) {
                            homeMatches.add(result);
                        }
                    }

                    // Debug için
                    System.out.println("Home matches size: " + homeMatches.size());
                    for (String match : homeMatches) {
                        System.out.println("Home match: " + match);
                    }

                    List<String> temp1 = new ArrayList<>(homeMatches);
                    allmatches.add(temp1);
                    homeMatches.clear();

                    // Away maçları için aynı işlem
                    addGamesAway(driver);

                    if (!awayMatches.isEmpty()) {
                        String result = getResult(driver);
                        if (result != null && !result.isEmpty()) {
                            awayMatches.add(result);
                        }
                    }

                    // Debug için
                    System.out.println("Away matches size: " + awayMatches.size());
                    for (String match : awayMatches) {
                        System.out.println("Away match: " + match);
                    }

                    List<String> temp2 = new ArrayList<>(awayMatches);
                    allmatches.add(temp2);
                    awayMatches.clear();

                } catch (Exception e) {
                    System.out.println(id + "  Oyunun liqada son 2 oyunu yoxdu veya veri çekilemedi: " + e.getMessage());
                    continue;
                }
            } catch (InterruptedException e) {
                System.out.println("Bekleme sırasında hata: " + e.getMessage());
            }
        }
    }

    public static String getResult(WebDriver driver) {
        try {
            // İlk olarak elementlerin var olduğundan emin olalım
            WebElement htElement = driver.findElement(By.xpath("//span[@title='Score 1st Half']"));
            WebElement ft1Element = driver.findElement(By.xpath("//div[@id='mScore']//div[@class='end']//div[@class='score'][1]"));
            WebElement ft2Element = driver.findElement(By.xpath("//div[@id='mScore']//div[@class='end']//div[@class='score'][2]"));

            String ht = htElement.getText();
            String ft1 = ft1Element.getText();
            String ft2 = ft2Element.getText();

            // Stringlerin boş olmadığından emin olalım
            if (ht != null && !ht.isEmpty() && ft1 != null && !ft1.isEmpty() && ft2 != null && !ft2.isEmpty()) {
                String ft = ft1.concat(ft2);

                // String uzunluk kontrolü
                if (ft.length() >= 2) {
                    return ht + " / " + ft.charAt(0) + "-" + ft.charAt(1);
                }
            }

            return "N/A"; // Eğer veriler eksik veya hatalıysa

        } catch (Exception e) {
            System.out.println("Sonuç alınırken hata oluştu: " + e.getMessage());
            return "N/A";
        }
    }

    public static List<String> getMatchIds(WebDriver driver) {
        List<String> matchIds2 = new ArrayList<>();
        String baseUrl = "https://live.nowgoal23.com/football/fixture?f=ft";
        for (int i = 1; i < 2; i++) {
            String url = baseUrl + i;
            driver.get(url);
            List<WebElement> trElements = driver.findElements(By.xpath("//tr[not(@style) or @style='']"));
            for (WebElement element : trElements) {
                String matchId = element.getAttribute("matchid");
                if (matchId != null && !matchId.isEmpty()) {
                    matchIds2.add(matchId);
                }
            }
        }
        return matchIds2;
    }

    public static void addGamesHome(WebDriver driver) {
        String xpath = "//tr[starts-with(@id, 'tr1_') and (not(@style) or @style='')]";
        List<WebElement> elements = driver.findElements(By.xpath(xpath));
        for (WebElement element : elements) {
            try {
                String td = element.findElements(By.tagName("td")).get(3).getText();
                if (!td.isEmpty()) {
                    homeMatches.add(td);
                }
            } catch (Exception e) {
                System.out.println("Home maç eklenirken hata: " + e.getMessage());
            }
        }
    }

    public static void addGamesAway(WebDriver driver) {
        String xpath = "//tr[starts-with(@id, 'tr2_') and (not(@style) or @style='')]";
        List<WebElement> elements = driver.findElements(By.xpath(xpath));
        for (WebElement element : elements) {
            try {
                String td = element.findElements(By.tagName("td")).get(3).getText();
                if (!td.isEmpty()) {
                    awayMatches.add(td);
                }
            } catch (Exception e) {
                System.out.println("Away maç eklenirken hata: " + e.getMessage());
            }
        }
    }

    public static void writeMatchesToExcel(List<List<String>> allmatches) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Match 1");
        headerRow.createCell(1).setCellValue("Match 2");
        headerRow.createCell(2).setCellValue("Result ht / ft");

        int rowNum = 1;

        for (int i = 0; i < allmatches.size(); i += 2) {
            List<String> homeMatches = allmatches.get(i);

            if (homeMatches.size() < 2) {
                System.out.println("Skipping row due to insufficient home match data");
                continue;
            }

            try {
                String match1 = homeMatches.size() > 0 ?
                        (homeMatches.get(0).length() >= 3 ? homeMatches.get(0).substring(0, 3) : homeMatches.get(0)) :
                        "N/A";

                String match2 = homeMatches.size() > 1 ?
                        (homeMatches.get(1).length() >= 3 ? homeMatches.get(1).substring(0, 3) : homeMatches.get(1)) :
                        "N/A";

                String result = homeMatches.size() > 2 ? homeMatches.get(2) : "N/A";

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(match1);
                row.createCell(1).setCellValue(match2);
                row.createCell(2).setCellValue(result);

                if (i + 1 < allmatches.size()) {
                    List<String> awayMatches = allmatches.get(i + 1);
                    if (awayMatches.size() >= 2) {
                        match1 = awayMatches.size() > 0 ?
                                (awayMatches.get(0).length() >= 3 ? awayMatches.get(0).substring(0, 3) : awayMatches.get(0)) :
                                "N/A";

                        match2 = awayMatches.size() > 1 ?
                                (awayMatches.get(1).length() >= 3 ? awayMatches.get(1).substring(0, 3) : awayMatches.get(1)) :
                                "N/A";

                        result = awayMatches.size() > 2 ? awayMatches.get(2) : "N/A";

                        row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(match1);
                        row.createCell(1).setCellValue(match2);
                        row.createCell(2).setCellValue(result);
                    }
                }
            } catch (Exception e) {
                System.out.println("Error processing row: " + e.getMessage());
                continue;
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream("match_results.xlsx")) {
            workbook.write(fileOut);
            System.out.println("Excel file created successfully!");
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        if (driverr == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--ignore-certificate-errors");
            options.addArguments("--silent");

            driverr = new ChromeDriver(options);
        }
        return driverr;
    }
}