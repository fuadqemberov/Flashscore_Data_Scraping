package flashscore.weeklydatascraping.aiscore;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AiscoreUrlScraper {
    private static final String OUTPUT_URL_FILE = "aiscore_urls.txt";
    private static final Pattern URL_PATTERN = Pattern.compile("(https://www\\.aiscore\\.com/match-[^/]+/[^/]+)");

    public static void main(String[] args) {
        // Suppress Selenium logging
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        System.setProperty("webdriver.chrome.silentOutput", "true");

        System.out.println("Aiscore URL scraper started. Collecting match links...");

        WebDriver driver = null;
        try {
            ChromeOptions options = createChromeOptions();
            WebDriverManager.chromedriver().setup();
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            driver.get("https://www.aiscore.com/");
            handleCookieBanner(driver, wait);
            clickScheduledTab(driver, wait);
            Set<String> matchLinks = scrollAndCollectAllMatchLinks(driver);

            if (!matchLinks.isEmpty()) {
                System.out.println(matchLinks.size() + " unique match links found. Writing to '" + OUTPUT_URL_FILE + "'...");
                writeLinksToFile(matchLinks);
                System.out.println("All links successfully written to '" + OUTPUT_URL_FILE + "'.");
            } else {
                System.out.println("No match links found. No file created.");
            }

        } catch (Exception e) {
            System.err.println("Critical error during URL collection: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        System.out.println("\n-- URL COLLECTION COMPLETED --");
    }

    private static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--log-level=3");
        options.addArguments("--silent");
        options.addArguments("--disable-logging");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-dev-tools");
        options.addArguments("--disable-features=VizDisplayCompositor");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1200");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }

    private static void handleCookieBanner(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement acceptButton = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler"))
            );
            acceptButton.click();
        } catch (Exception e) {
            // Continue if no banner or click fails
        }
    }

    private static void clickScheduledTab(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement scheduledButton = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.xpath("//span[contains(@class, 'changeItem') and contains(text(), 'Scheduled')]")
                    )
            );
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", scheduledButton);
            Thread.sleep(3000); // Wait for tab content to load
        } catch (Exception e) {
            System.out.println("Error clicking Scheduled tab. Continuing...");
        }
    }

    private static Set<String> scrollAndCollectAllMatchLinks(WebDriver driver) throws InterruptedException {
        Actions actions = new Actions(driver);
        Set<String> matchLinks = new HashSet<>();

        System.out.println("Scrolling page to collect all match links...");
        Thread.sleep(5000); // Initial load wait
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        Thread.sleep(2000);

        WebElement body = driver.findElement(By.tagName("body"));
        actions.moveToElement(body).perform();

        int maxScrollAttempts = 200;
        int stableCount = 0;
        int lastLinkCount = 0;

        for (int scrollAttempt = 1; scrollAttempt <= maxScrollAttempts; scrollAttempt++) {
            List<WebElement> currentMatchElements = driver.findElements(
                    By.cssSelector("a.match-container, a[href*='/match-']")
            );

            for (WebElement element : currentMatchElements) {
                String fullHref = element.getAttribute("href");
                if (fullHref != null) {
                    if (fullHref.startsWith("/match-")) {
                        fullHref = "https://www.aiscore.com" + fullHref;
                    }
                    Matcher matcher = URL_PATTERN.matcher(fullHref);
                    if (matcher.find()) {
                        matchLinks.add(matcher.group(1));
                    }
                }
            }

            actions.scrollByAmount(0, 300).perform();
            Thread.sleep(500); // Wait for content to load

            if (scrollAttempt % 20 == 0) {
                System.out.println("Scroll attempt: " + scrollAttempt + "/" + maxScrollAttempts +
                        " | Unique links collected: " + matchLinks.size());
            }

            if (matchLinks.size() == lastLinkCount) {
                stableCount++;
                if (stableCount >= 15) {
                    System.out.println("No new links found, stopping scroll.");
                    break;
                }
            } else {
                stableCount = 0;
                lastLinkCount = matchLinks.size();
            }

            Boolean isAtBottom = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "return (window.innerHeight + window.scrollY) >= document.body.scrollHeight - 100"
            );
            if (Boolean.TRUE.equals(isAtBottom) && stableCount > 10) {
                System.out.println("Reached page bottom, stopping scroll.");
                break;
            }
        }

        System.out.println("Scrolling and link collection completed.");
        return matchLinks;
    }

    private static void writeLinksToFile(Set<String> matchLinks) throws IOException {
        try (FileWriter writer = new FileWriter(OUTPUT_URL_FILE)) {
            for (String link : matchLinks) {
                writer.write(link + "\n");
            }
        }
    }
}