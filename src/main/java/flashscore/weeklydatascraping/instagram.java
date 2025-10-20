package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class instagram {
    static WebDriver driverr = null;
    static String username = "";
    static String password = "";
    static String link = "https://www.instagram.com/p/";
    static List<String> links = new ArrayList<>();
    static String text = "";

    static {
        links.addAll(Arrays.asList(
                ""));
    }

    public static void main(String[] args) throws InterruptedException {
        WebDriver driver = getChromeDriver();
        driver.get("https://www.instagram.com");
        Thread.sleep(1000);
        driver.findElement(By.xpath("//input[@name='username']")).sendKeys(username);
        driver.findElement(By.xpath("//input[@name='password']")).sendKeys(password);
        driver.findElement(By.xpath("//button[@type='submit']//div[text()='Log in']")).click();
        Thread.sleep(3000);
        for (String link1 : links) {
            driver.switchTo().newWindow(WindowType.TAB);
            driver.get(link + link1 + "/");
            Thread.sleep(200);
            driver.findElement(By.xpath("//textarea[@aria-label='Add a comment…']")).click();
            Thread.sleep(100);
            driver.findElement(By.xpath("//textarea[@aria-label='Add a comment…']")).sendKeys(text);
        }
    }


    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chrome\\chromedriver.exe");
        if (driverr == null) {
            driverr = new ChromeDriver();
        }
        return driverr;
    }
}
