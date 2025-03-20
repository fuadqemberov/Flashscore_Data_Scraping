package flashscore.weeklydatascraping.aiscore222;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class testttttt {
    private static String HOME_TEAM = "";
    private static String AWAY_TEAM = "";
    static List<List<String>> allMatches = new ArrayList<>();

    public static void main(String[] args) {
        WebDriver driver = initializeDriver();
        List<String> ff = new ArrayList<>();
        List<String> list = Arrays.asList("https://www.aiscore.com/match-brentford-arsenal/69759ilnndycgk2/h2h",
                "https://www.aiscore.com/match-auckland-fc-melbourne-victory/ezk96ixjmx5b1kn/h2h");
        try {
            for(String link : list) {
                // Sayfaya git
                driver.get(link);
                // Sayfanın tamamen yüklenmesini bekle (WebDriverWait ile)
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//ul[@class='matchBox']/li")));

                // "This league" butonuna tıklama
                List<WebElement> leagueElements = driver.findElements(By.xpath("//span[text()='This league']"));
                if (!leagueElements.isEmpty()) {
                    leagueElements.forEach(WebElement::click);
                }
                HOME_TEAM = driver.findElement(By.xpath("//*[@id=\"app\"]/div[3]/div[2]/div[2]/div[1]/div/a")).getText();
                AWAY_TEAM = driver.findElement(By.xpath("//*[@id=\"app\"]/div[3]/div[2]/div[2]/div[3]/div/a")).getText();

                // Tüm maç kutularını yakala
                List<WebElement> matchElements = driver.findElements(By.xpath("//ul[@class='matchBox']/li"));

                // Sadece belirtilen takımların maçlarını filtrele
                for (WebElement match : matchElements) {

                    List<WebElement> teamNames = match.findElements(By.xpath(".//span[@class='teamName']"));
                    if (teamNames.size() == 2) {
                        String homeTeam = teamNames.get(0).getText();
                        String awayTeam = teamNames.get(1).getText();

                        // Belirtilen takımların KENDI ARALARINDAKI maçları dışla
                        if (!((homeTeam.contains(HOME_TEAM) && awayTeam.contains(AWAY_TEAM)) ||
                                (homeTeam.contains(AWAY_TEAM) && awayTeam.contains(HOME_TEAM)))) {

                            // Maç sonu skoru
                            List<WebElement> fullTimeScore = match.findElements(By.xpath(".//p[@class='scoreBox'][2]/span"));
                            if (fullTimeScore.size() == 2) {
                                String fullTimeHome = fullTimeScore.get(0).getText();
                                String fullTimeAway = fullTimeScore.get(1).getText();

                                // Ekrana yazdır
                               // System.out.println(homeTeam + " " + fullTimeHome + " - " + fullTimeAway + " " + awayTeam);
                                ff.addAll(Arrays.asList(homeTeam, fullTimeHome, fullTimeAway, awayTeam));

                            }
                        }
                    }
                }
                allMatches.add(ff);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println(allMatches);
            driver.quit();
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
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

        return driver;
    }
}