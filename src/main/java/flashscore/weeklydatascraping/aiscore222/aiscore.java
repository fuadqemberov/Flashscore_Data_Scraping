package flashscore.weeklydatascraping.aiscore222;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class aiscore {
    public static void main(String[] args) throws InterruptedException, IOException {
        WebDriver driver = initializeDriver();
        List<String> macthes = readIdsFromFile();
        for(String link : macthes){
            driver.get(link);
            List<WebElement> elements = driver.findElements(By.xpath("//span[text()='This league']"));
            elements.forEach(WebElement::click);

        }
    }


    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless");  // Uncomment for headless mode

        // Mimic real user agent
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36");

        // Add additional options to make the scraper more robust
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--start-maximized");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

        return driver;
    }

    public static List<String> readIdsFromFile() throws IOException {
        // Dosyayı okuma
        try (BufferedReader reader = new BufferedReader(new FileReader("aiscore_match_links.txt"))) {
            String line = reader.readLine(); // İlk satırı oku
            if (line != null && !line.isEmpty()) {
                // Virgülle ayrılmış ID'leri listeye dönüştür
                return Arrays.asList(line.split(",\\s*")); // Virgül ve boşluklara göre böl
            }
        }
        return List.of(); // Boş liste döndür
    }
}