package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static flashscore.Data.flashDataScraper.getOddsDataFromJson;
import static flashscore.MatchID.flashMatchId.*;
import static flashscore.Util.flashUtill.exceleYazdir;
import static flashscore.Util.flashUtill.getChromeDriver;

public class weekmain {
    public static List<String> matchIddnew = new ArrayList<>();
    public static void main(String[] args) throws InterruptedException, IOException {


        WebDriver driver = getChromeDriver();
        driver.get("https://www.flashscore.com/");
        acceptCookies(driver);
        Thread.sleep(2000);
        goTo7DayBefore(driver);
        for(int i =1;i<=7;i++){
            getMathcIdFromSite(driver);
            if(i!=7){
             goToNextDay(driver);
            }
        }
        writeMatchIdsToFile2(matchIddnew);
        for(String matchId:matchIddnew){
            getOddsDataFromJson(matchId);
        }
        exceleYazdir();
    }
    private static void goToNextDay(WebDriver driver) throws InterruptedException {
        driver.findElement(By.xpath("//*[@id=\"live-table\"]/div[1]/div[2]/div/button[2]")).click();
        Thread.sleep(1000);
    }

    private static void getMathcIdFromSite(WebDriver driver){
        List<WebElement> elementList = driver.findElements(By.xpath(MATCH_DETAIL_XPATH));
        for (WebElement element : elementList) {
            String link = element.getAttribute("aria-describedby");
            if (link != null && link.startsWith(MATCH_ID_PREFIX)) {
                String matchId = link.substring(MATCH_ID_PREFIX.length());
                matchIddnew.add(matchId);
            }
        }
    }

    private static void writeMatchIdsToFile2(List<String> matchIds) {
        try (FileWriter writer = new FileWriter(FILE_NAME, true)) {
            for (String matchId : matchIds) {
                writer.write(matchId + " ");
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    private static void goTo7DayBefore(WebDriver driver)  throws InterruptedException{
        driver.findElement(By.xpath("//*[@id=\"calendarMenu\"]")).click();
        Thread.sleep(1000);
        driver.findElement(By.xpath("//*[@id=\"live-table\"]/div[1]/div[2]/div/ul/li[1]")).click();
        acceptCookies(driver);
        clickAllElements(driver, "//span[contains(text(), 'display matches (')]");
        Thread.sleep(1000);


    }

    public static void clickAllElements(WebDriver driver, String xpath) {
        while (true) {
            List<WebElement> elements = driver.findElements(By.xpath(xpath));

            if (elements.isEmpty()) {
                break; // Exit the loop if no more elements are found
            }

            for (WebElement element : elements) {
                element.click();

                // Optional: Add a short wait to avoid rapid clicking
                try {
                    Thread.sleep(1000); // 1 second
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Refresh the list of elements after clicking
            elements = driver.findElements(By.xpath(xpath));

            // Break the loop if no elements are left to click
            if (elements.isEmpty()) {
                break;
            }
        }

    }

}