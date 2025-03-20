package flashscore.weeklydatascraping.mackolik.yeni;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class MackolikScraper {

    public static void main(String[] args) throws InterruptedException {

        WebDriver driver = initializeDriver();
        driver.get("https://arsiv.mackolik.com/Canli-Sonuclar");
        Thread.sleep(2000);
        WebElement calendarIcon = driver.findElement(By.xpath("//*[@id=\"live-matches\"]/div[1]/div[1]/div[1]/div[1]/div[1]/div[2]/input"));
        calendarIcon.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        // Using a combination of current-day and the day number, to locate the correct 1 for December
        WebElement dec1Element =
                wait.until(ExpectedConditions
                .presenceOfElementLocated(By.xpath("//td[@data-month='12']//a[text()='1']")));

        String dec1 = dec1Element.getText().trim();

        System.out.println("Dec 1st: " + dec1);

        dec1Element.click(); // If you want to select the date, you should click this



        WebElement tableBody = driver.findElement(By.xpath("//*[@id=\"dvScores\"]/table/tbody"));
        List<WebElement> matchRows = tableBody.findElements(By.xpath(".//tr[contains(@class, 'mac-status')]"));

        for (WebElement matchRow : matchRows) {
            System.out.println(matchRow.getText());
        }
        driver.quit();
    }


    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        return new ChromeDriver(options);
    }

}
