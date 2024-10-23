package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class main {
    static WebDriver driverr = null;
    static  List<String> matchIds;
    static {
        matchIds = new ArrayList<>(List.of(new String[]{"2613314"}));
    }

    public static void main(String[] args) {
        WebDriver driver = getChromeDriver();
        //getMatchIds(driver);
        getMatchDatas(driver);
        driver.quit();
    }

      public static void getMatchDatas(WebDriver driver) {
        String matchUrl = "https://live.nowgoal23.com/match/h2h-";
        for(String id : matchIds){
            driver.get(matchUrl + id);
            driver.findElement(By.xpath("//*[@id='checkboxleague2']")).click();
            driver.findElement(By.xpath("//*[@id='checkboxleague1']")).click();

            // Select dropdowns
            Select dropdown1 = new Select(driver.findElement(By.id("selectMatchCount1")));
            dropdown1.selectByValue("2"); // Select "Last 2"

            Select dropdown2 = new Select(driver.findElement(By.id("selectMatchCount2")));
            dropdown2.selectByValue("2"); // Select "Last 2"

            printGamesHome(driver);
            printGamesAway(driver);
            String ht = driver.findElement(By.xpath("//*[@id=\"mScore\"]/div/div[2]/span/span[1]")).getText();
            String ft = driver.
                    findElement(By.xpath("//*[@id=\"mScore\"]/div/div[1]"))
                    .getText()
                    .concat(driver.findElement(By.xpath("//*[@id=\"mScore\"]/div/div[3]")).getText());

            System.out.print(ht + " / " + ft.charAt(0)+"-"+ft.charAt(1));
        }
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

    public static void printGamesHome(WebDriver driver) {
        for (int i = 1; i < 4; i++) {
            String idz = "1_" + i;
            String xpath = "//tr[@id='tr" + idz + "']"; // Create the XPath

            try {
                WebElement element = driver.findElement(By.xpath(xpath)); // Find the element using the XPath
                String td =  element.findElements(By.tagName("td")).get(3).getText();
                if(!td.isEmpty() && Objects.nonNull(td)){
                    System.out.println("Score : "+td);
                }

            } catch (Exception e) {
                System.out.println("Element not found for id: " + idz);
                continue;
            }
        }
    }
    public static void printGamesAway(WebDriver driver) {
        for (int i = 1; i < 4; i++) {
            String idz = "2_" + i;
            String xpath = "//tr[@id='tr" + idz + "']"; // Create the XPath

            try {
                WebElement element = driver.findElement(By.xpath(xpath)); // Find the element using the XPath
                String td =  element.findElements(By.tagName("td")).get(3).getText();
                if(!td.isEmpty() && Objects.nonNull(td)){
                    System.out.println("Score : "+td);
                }

            } catch (Exception e) {
                System.out.println("Element not found for id: " + idz);
                continue;
            }
        }
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
