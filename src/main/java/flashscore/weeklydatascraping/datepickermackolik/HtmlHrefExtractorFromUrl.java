package flashscore.weeklydatascraping.datepickermackolik;

import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class HtmlHrefExtractorFromUrl {

    public static void main(String[] args) {
        WebDriver driver = initializeDriver();

        try {
            // Sayfaya git
            driver.get("https://www.footballant.com/"); // Buraya hedef URL'yi yaz
            Thread.sleep(2000);
            // 1. Datepicker ikonuna tıkla
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement datepicker = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//input[@class=\"el-input__inner\" and @placeholder=\"\" and @autocomplete=\"off\"]")));
            datepicker.click();

// 2. Yılı seç
            WebElement yearLabel = driver.findElement(By.xpath("//div[contains(@class,'el-date-picker')]//span[contains(@class,'el-date-picker__header-label')][1]"));
            yearLabel.click();
            Thread.sleep(300);

// 3. 2025 yılını seç
            WebElement year2025 = driver.findElement(By.xpath("//table[contains(@class,'el-year-table')]//span[text()='2025']"));
            year2025.click();
            Thread.sleep(300);

// 4. January ayını seç
            WebElement january = driver.findElement(By.xpath("//table[contains(@class,'el-month-table')]//span[text()='Jan']"));
            january.click();
            Thread.sleep(300);

// 5. 1 Ocak’ı seç
            WebElement day1 = driver.findElement(By.xpath("//table[contains(@class,'el-date-table')]//span[text()='1']"));
            day1.click();

            // XPath'e uyan tüm <a> elementlerini bul
            List<WebElement> matchLinks = driver.findElements(By.xpath("//a[contains(@href, '/matches/') and contains(@title, 'vs')]"));

            // href değerlerini tutacak liste
            List<String> hrefList = new ArrayList<>();

            // Her bir elementi döngüyle gez ve href değerini listeye ekle
            for (WebElement link : matchLinks) {
                String href = link.getAttribute("href");
                hrefList.add(href);
            }

            // Sonuçları yazdır
            for (String href : hrefList) {
                System.out.println(href);
            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // Tarayıcıyı kapat
            driver.quit();
        }
    }


    private static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src/chromee/chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--disable-extensions", "--disable-notifications");
        options.addArguments("--window-size=1920,1080");
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return driver;
    }

}