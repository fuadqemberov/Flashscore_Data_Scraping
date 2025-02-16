package flashscore.weeklydatascraping.fffffff;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;

public class DatePickerAutomation {
    private static WebDriver driverr = null;

    public static void main(String[] args) throws InterruptedException {
           WebDriver driver = getChromeDriver();
            getLastYearBegining(driver);
    }

    private static void getLastYearBegining(WebDriver driver) throws InterruptedException {
        driver.get("https://www.mackolik.com/canli-sonuclar");
        Thread.sleep(2000);
        driver.findElement(By.className("widget-dateslider__datepicker-toggle")).click();
        Thread.sleep(1000);
        driver.findElement(By.xpath("/html/body/div[5]/div/main/div[1]/div[1]/div[1]/div[1]/div[1]/div[2]/div/div[1]/div[2]/div[1]")).click();
        driver.findElement(By.xpath("/html/body/div[5]/div/main/div[1]/div[1]/div[1]/div[1]/div[1]/div[2]/div/div[1]/div[1]/div[1]")).click();
        driver.findElement(By.xpath("//td[@data-date='2024-01-01']")).click();
        Thread.sleep(2000);
        List<WebElement> elementList = driver.findElements(
                By.xpath("//a[contains(@class, 'match-row__score')]"));

        for (WebElement element : elementList) {
            try {
                String href = element.getAttribute("href");
                System.out.println("Link: " + href);
                Thread.sleep(50);
            } catch (Exception ex) {
                System.out.println("yoxdur");
            }
        }

    }



    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chrome\\chromedriver.exe");
        if (driverr == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--start-maximized");
            driverr = new ChromeDriver(options);
        }
        return driverr;
    }


}