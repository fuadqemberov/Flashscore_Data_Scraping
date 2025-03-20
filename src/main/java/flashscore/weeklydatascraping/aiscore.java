package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class aiscore {
    public static void main(String[] args) throws InterruptedException {
        WebDriver driver = initializeDriver();
        driver.get("https://www.aiscore.com/20250101");

        // Wait for page to load and click the element
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"app\"]/div[3]/div[2]/div[1]/div[2]/div[2]/div[1]/div[1]/span[1]")));
        driver.findElement(By.xpath("//*[@id=\"app\"]/div[3]/div[2]/div[1]/div[2]/div[2]/div[1]/div[1]/span[1]")).click();

        // Use Set to avoid duplicate links
        Set<String> hrefSet = new HashSet<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        int maxScrollAttempts = 100; // Maximum number of scroll attempts
        int noNewLinksCount = 0;
        int maxNoNewLinks = 5; // Stop after 5 consecutive scrolls with no new links
        int lastLinkCount = 0;

        System.out.println("Starting simultaneous scroll and collect...");

        for (int scrollAttempt = 0; scrollAttempt < maxScrollAttempts; scrollAttempt++) {
            // Scroll down a bit
            js.executeScript("window.scrollBy(0, 800)");

            // Short wait for content to load
            Thread.sleep(500);

            // Immediately collect links after scrolling
            int beforeSize = hrefSet.size();
            collectMatchLinks(driver, hrefSet);
            int newLinksFound = hrefSet.size() - beforeSize;

            System.out.println("Scroll #" + (scrollAttempt + 1) +
                    " - Position: " + js.executeScript("return window.pageYOffset") +
                    " - Total links: " + hrefSet.size() +
                    " - New links: " + newLinksFound);

            // Check if we're at the bottom of the page
            boolean isAtBottom = (boolean) js.executeScript(
                    "return (window.innerHeight + window.pageYOffset) >= document.body.scrollHeight;");

            // Check if we found new links
            if (hrefSet.size() == lastLinkCount) {
                noNewLinksCount++;
                if (noNewLinksCount >= maxNoNewLinks) {
                    // Try one final aggressive scroll to the bottom
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
                    Thread.sleep(2000);
                    collectMatchLinks(driver, hrefSet);

                    if (hrefSet.size() == lastLinkCount) {
                        System.out.println("No new links found after " + maxNoNewLinks +
                                " consecutive scrolls. Stopping.");
                        break;
                    } else {
                        // Reset if we found new links after aggressive scroll
                        noNewLinksCount = 0;
                        lastLinkCount = hrefSet.size();
                    }
                }
            } else {
                noNewLinksCount = 0;
                lastLinkCount = hrefSet.size();
            }

            // If we're at the bottom and haven't found new links, try one more aggressive scroll
            if (isAtBottom) {
                System.out.println("Reached bottom of current content. Executing aggressive scroll...");
                js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
                Thread.sleep(2000);
                collectMatchLinks(driver, hrefSet);

                if (hrefSet.size() == lastLinkCount) {
                    System.out.println("No new links found after aggressive scroll. Stopping.");
                    break;
                } else {
                    lastLinkCount = hrefSet.size();
                }
            }
        }

        System.out.println("Scrolling and collection completed. Total unique match links found: " + hrefSet.size());

        // Convert set to list for easier handling
        List<String> hrefList = new ArrayList<>(hrefSet);

        System.out.println("First 10 match links (of " + hrefList.size() + " total):");
        for (int i = 0; i < hrefList.size(); i++) {
            System.out.println((i+1) + ": " + hrefList.get(i));
        }

        driver.quit();
    }

    // Method to collect match links
    private static void collectMatchLinks(WebDriver driver, Set<String> hrefSet) {
        List<WebElement> links = driver.findElements(By.tagName("a"));

        for (WebElement link : links) {
            try {
                String href = link.getAttribute("href");
                if (href != null && !href.isEmpty() && href.contains("match")) {
                    hrefSet.add(href);
                }
            } catch (Exception e) {
                // Ignore stale element exceptions and continue
                continue;
            }
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
}