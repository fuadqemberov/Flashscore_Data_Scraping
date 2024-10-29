package flashscore.weeklydatascraping.gg;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ff {
    static WebDriver driverr = null;
    static String username = "ehtirasssli__oglannn";
    static String password = "Ehtirasli#1997";
    static String link = "https://www.instagram.com/p/";
    static List<String> links = new ArrayList<>();
    static String text = "real yaladan gelin ya xalalar noqte atsin)";
    static{
        links.addAll(Arrays.asList(
                "CutoKObsFKt",
                "CMv38SknzGr",
                "CB_YOTPAfTC",
                "BVU8cSoAMTA",
                "B75vnCWlD03",
                "B3u5EtfBdYX",
                "BneTovon-N1",
                "CMRrfsTFbJf",
                "CCnkuFbDKoe",
                "B60PXxVpAZG",
                "C_7rSDFoO5O"));
    }

    public static void main(String[] args) throws InterruptedException {
        WebDriver driver = getChromeDriver();
        driver.get("https://www.instagram.com");
        Thread.sleep(1000);
        driver.findElement(By.xpath("//input[@name='username']")).sendKeys(username);
        driver.findElement(By.xpath("//input[@name='password']")).sendKeys(password);
        driver.findElement(By.xpath("//button[@type='submit']//div[text()='Log in']")).click();
        Thread.sleep(3000);
        for(String link1 : links) {
            driver.switchTo().newWindow(WindowType.TAB);
            driver.get(link+link1+"/");
            Thread.sleep(200);
            driver.findElement(By.xpath("//textarea[@aria-label='Add a comment…']")).click();
            Thread.sleep(100);
            driver.findElement(By.xpath("//textarea[@aria-label='Add a comment…']")).sendKeys(text);
        }
    }



    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chr\\chromedriver.exe");
        if (driverr == null) {
            driverr = new ChromeDriver();
        }

        return driverr;
    }
}
