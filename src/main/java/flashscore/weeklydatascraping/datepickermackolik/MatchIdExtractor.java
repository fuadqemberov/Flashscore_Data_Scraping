package flashscore.weeklydatascraping.datepickermackolik;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchIdExtractor {

    public static void main(String[] args) {

        // 2. WebDriver örneğini oluşturma
        WebDriver driver = initializeDriver();

        // 3.  Hedef URL'ye gitme
        driver.get("https://www.footballant.com/");

        // 4. maç Id'lerini saklayacak bir liste oluşturma
        List<String> matchIds = new ArrayList<>();

        // 5.  Önce maçları içeren ana div'in yüklenmesini bekleme
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20)); // Daha uzun bekleme süresi gerekebilir
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".matchDiv.matchDiv2"))); // Ana div'i bekliyoruz


        // 6.  Maçları içeren div elementlerini bulma. CSS selector daha güvenilir gibi görünüyor.
        List<WebElement> matchDivs = driver.findElements(By.cssSelector(".matchDiv.matchDiv2"));

        // 7. Regex pattern tanımlama
        Pattern pattern = Pattern.compile("/matches/(\\d+)");

        // 8. Her bir maç div'i içindeki linkleri bulma ve ID'leri çıkarma
        for (WebElement matchDiv : matchDivs) {
            List<WebElement> links = matchDiv.findElements(By.xpath(".//a[@href]")); // Sadece bu div içindeki linkleri bul

            for (WebElement link : links) {
                String href = link.getAttribute("href");

                if (href != null && href.contains("/matches/")) {
                    Matcher matcher = pattern.matcher(href);
                    if(matcher.find()){
                        matchIds.add(matcher.group(1));
                    }

                }
            }
        }

        // 9.  Maç Id'lerini yazdırma
        System.out.println("Bulunan Maç ID'leri:");
        for (String id : matchIds) {
            System.out.println(id);
        }

        // 10. Web sürücüsünü kapatma
        driver.quit();
    }


    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36");
        return new ChromeDriver(options);
    }
}
