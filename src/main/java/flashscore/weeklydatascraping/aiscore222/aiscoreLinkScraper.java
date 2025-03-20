package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class aiscoreLinkScraper {
    private static final int SCROLL_DISTANCE = 800;
    private static final long SCROLL_WAIT_MS = 500;
    private static final int MAX_SCROLL_ATTEMPTS = 100;
    private static final int MAX_NO_NEW_LINKS = 5;

    public static void main(String[] args) {
        // Configure start and end dates
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 3); // Change this to your desired end date

        // Scrape data for date range
        Set<String> allLinks = scrapeForDateRange(startDate, endDate);

        // Save results to file
        saveLinksToFile(allLinks, "aiscore_match_links.txt");

        System.out.println("Scraping completed. Total unique links collected: " + allLinks.size());
    }

    private static Set<String> scrapeForDateRange(LocalDate startDate, LocalDate endDate) {
        Set<String> allLinks = new HashSet<>();
        WebDriver driver = initializeDriver();

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate currentDate = startDate;

            while (!currentDate.isAfter(endDate)) {
                String dateString = currentDate.format(formatter);
                String url = "https://www.aiscore.com/" + dateString;

                System.out.println("Scraping date: " + currentDate);
                Set<String> dateLinks = scrapeLinksForDate(driver, url);

                // Add links to master set
                allLinks.addAll(dateLinks);
                System.out.println("Links for " + currentDate + ": " + dateLinks.size());
                System.out.println("Total unique links so far: " + allLinks.size());

                // Move to next date
                currentDate = currentDate.plusDays(1);
            }
        } finally {
            driver.quit();
        }

        return allLinks;
    }

    private static Set<String> scrapeLinksForDate(WebDriver driver, String url) {
        Set<String> hrefSet = new HashSet<>();

        try {
            driver.get(url);

            // Wait for page to load and click the element
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[@id=\"app\"]/div[3]/div[2]/div[1]/div[2]/div[2]/div[1]/div[1]/span[1]")));
            driver.findElement(
                    By.xpath("//*[@id=\"app\"]/div[3]/div[2]/div[1]/div[2]/div[2]/div[1]/div[1]/span[1]")).click();

            // Scroll and collect links
            scrollAndCollectLinks(driver, hrefSet);

        } catch (Exception e) {
            System.err.println("Error scraping " + url + ": " + e.getMessage());
        }

        return hrefSet;
    }

    private static void scrollAndCollectLinks(WebDriver driver, Set<String> hrefSet) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        int noNewLinksCount = 0;
        int lastLinkCount = 0;

        System.out.println("Starting scroll and collect...");

        for (int scrollAttempt = 0; scrollAttempt < MAX_SCROLL_ATTEMPTS; scrollAttempt++) {
            // Scroll down
            js.executeScript("window.scrollBy(0, " + SCROLL_DISTANCE + ")");

            // Wait for content to load
            Thread.sleep(SCROLL_WAIT_MS);

            // Collect links after scrolling
            int beforeSize = hrefSet.size();
            collectMatchLinks(driver, hrefSet);
            int newLinksFound = hrefSet.size() - beforeSize;

            System.out.println("Scroll #" + (scrollAttempt + 1) +
                    " - Total links: " + hrefSet.size() +
                    " - New links: " + newLinksFound);

            // Check if we're at the bottom of the page
            boolean isAtBottom = (boolean) js.executeScript(
                    "return (window.innerHeight + window.pageYOffset) >= document.body.scrollHeight;");

            // Check if we found new links
            if (hrefSet.size() == lastLinkCount) {
                noNewLinksCount++;
                if (noNewLinksCount >= MAX_NO_NEW_LINKS) {
                    // Try one final aggressive scroll
                    if (!tryAggressiveScroll(js, driver, hrefSet, lastLinkCount)) {
                        break;
                    } else {
                        noNewLinksCount = 0;
                        lastLinkCount = hrefSet.size();
                    }
                }
            } else {
                noNewLinksCount = 0;
                lastLinkCount = hrefSet.size();
            }

            // If we're at the bottom, try aggressive scroll
            if (isAtBottom) {
                if (!tryAggressiveScroll(js, driver, hrefSet, lastLinkCount)) {
                    break;
                } else {
                    lastLinkCount = hrefSet.size();
                }
            }
        }

        System.out.println("Scrolling completed. Links found: " + hrefSet.size());
    }

    private static boolean tryAggressiveScroll(JavascriptExecutor js, WebDriver driver,
                                               Set<String> hrefSet, int lastLinkCount) throws InterruptedException {
        System.out.println("Executing aggressive scroll...");
        js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
        Thread.sleep(2000);

        int beforeSize = hrefSet.size();
        collectMatchLinks(driver, hrefSet);

        if (hrefSet.size() == lastLinkCount) {
            System.out.println("No new links found after aggressive scroll.");
            return false;
        } else {
            System.out.println("Found " + (hrefSet.size() - beforeSize) + " new links after aggressive scroll.");
            return true;
        }
    }

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
            }
        }
    }

    private static void saveLinksToFile(Set<String> links, String fileName) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            List<String> sortedLinks = new ArrayList<>(links);
            sortedLinks.sort(String::compareTo);

            for (String link : sortedLinks) {
                writer.println(link);
            }
            System.out.println("Successfully saved " + links.size() + " links to " + fileName);
        } catch (IOException e) {
            System.err.println("Error saving links to file: " + e.getMessage());
        }
    }

    private static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();

        // Mimic real user agent
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36");

        // Add additional options for robust scraping
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--start-maximized");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

        return driver;
    }
}