package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static flashscore.Data.flashDataScraper.getOddsDataFromJson;
import static flashscore.MatchID.flashMatchId.*;
import static flashscore.Util.flashUtill.exceleYazdir;
import static flashscore.Util.flashUtill.getChromeDriver;

public class weekmain {
    public static void main(String[] args) throws InterruptedException, IOException {
        List<String> matchIddnew = new ArrayList<>();

        WebDriver driver = getChromeDriver();
        driver.get("https://www.flashscore.com/");
        acceptCookies(driver);
        Thread.sleep(2000);
        driver.findElement(By.xpath("//*[@id=\"calendarMenu\"]")).click();
        Thread.sleep(1000);
        driver.findElement(By.xpath("//*[@id=\"live-table\"]/div[1]/div[2]/div/ul/li[1]")).click();
        acceptCookies(driver);
        clickAllElements(driver, "//span[contains(text(), 'display matches (')]");
        Thread.sleep(1000);

        List<WebElement> elementList = driver.findElements(By.xpath(MATCH_DETAIL_XPATH));
        for (WebElement element : elementList) {
            String link = element.getAttribute("aria-describedby");
            if (link != null && link.startsWith(MATCH_ID_PREFIX)) {
                String matchId = link.substring(MATCH_ID_PREFIX.length());
                matchIddnew.add(matchId);
            }
        }

        for(String matchId:matchIddnew){
            getOddsDataFromJson(matchId);
        }

        exceleYazdir();
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