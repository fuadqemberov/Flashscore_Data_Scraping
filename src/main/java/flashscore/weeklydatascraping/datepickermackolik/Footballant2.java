package flashscore.weeklydatascraping.datepickermackolik;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Footballant2 {
    public static void main(String[] args) {
        // Initialize the driver
        WebDriver driver = initializeDriver();
        Set<String> allMatchIds = new HashSet<>();

        try {
            // Get date range from user
            Date[] dateRange = getDateRangeFromUser();
            Date startDate = dateRange[0];
            Date endDate = dateRange[1];

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            System.out.println("Starting scraping from " + dateFormat.format(startDate) + " to " + dateFormat.format(endDate));

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);

            // Initial website navigation
            driver.get("https://www.footballant.com/");
            System.out.println("Navigating to website...");
            Thread.sleep(4000);

            // Loop through each day in the range
            while (!calendar.getTime().after(endDate)) {
                Date currentDate = calendar.getTime();
                System.out.println("\nProcessing date: " + dateFormat.format(currentDate));

                // Process current date
                Set<String> currentDateMatchIds = processDate(driver, currentDate);

                // Add current date match IDs to the all match IDs set
                allMatchIds.addAll(currentDateMatchIds);

                // Print current date match IDs
                System.out.println("Match IDs for " + dateFormat.format(currentDate) + ":");
                for (String id : currentDateMatchIds) {
                    System.out.println(id);
                }
                System.out.println("Total matches found for this date: " + currentDateMatchIds.size());

                // Move to next day
                calendar.add(Calendar.DATE, 1);
            }

            // Print all unique match IDs at the end
            System.out.println("\n======= ALL UNIQUE MATCH IDs =======");
            System.out.println("Total unique matches found across all dates: " + allMatchIds.size());
            for (String id : allMatchIds) {
                System.out.println(id);
            }

        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    private static Set<String> processDate(WebDriver driver, Date date) {
        Set<String> matchIds = new HashSet<>();
        try {
            // Instead of reloading the site each time, we'll scroll to top and use the date picker
            // Scroll to the top of the page
            scrollToTop(driver);
            Thread.sleep(1000);

            // Open date picker and select date
            openDatePickerAndSelectDate(driver, date);

            // Wait for the page to load after date selection
            Thread.sleep(3000);

            // Scroll down page to load all content
            scrollDownPage(driver);

            // Get match IDs for this date
            matchIds = scrapeMatchIds(driver);

        } catch (Exception e) {
            System.out.println("Error processing date " + new SimpleDateFormat("yyyy-MM-dd").format(date) + ": " + e.getMessage());
            e.printStackTrace();
        }
        return matchIds;
    }

    /**
     * Opens date picker and selects the desired date
     */
    private static void openDatePickerAndSelectDate(WebDriver driver, Date date) throws InterruptedException {
        // Find and click the date picker element
        String datePickerXPath = "//*[@id=\"el-id-1024-10\"]";
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement datePickerElement = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(datePickerXPath)));
        datePickerElement.click();
        System.out.println("Date picker opened");

        // Navigate to and select the desired date
        selectDateFromDatePicker(driver, date);
    }

    /**
     * Scrolls to the top of the page
     */
    private static void scrollToTop(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(0, 0)");
        System.out.println("Scrolled to top of page");
    }

    private static void scrollDownPage(WebDriver driver) throws InterruptedException {
        // JavaScriptExecutor for scrolling
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Scroll slowly function
        int scrollAmount = 400; // Amount to scroll each time
        boolean pageEndReached = false;

        while (!pageEndReached) {
            // Get current scroll position
            long initialScrollTop = (long) js.executeScript("return document.documentElement.scrollTop || document.body.scrollTop");

            // Scroll down by the specified amount
            js.executeScript("window.scrollBy(0," + scrollAmount + ")");

            // Wait for content to load
            Thread.sleep(1000);

            // Get new scroll position
            long newScrollTop = (long) js.executeScript("return document.documentElement.scrollTop || document.body.scrollTop");

            // If scroll position hasn't changed, assume we've reached the end
            if (newScrollTop == initialScrollTop) {
                pageEndReached = true;
                System.out.println("Reached end of page.");
            } else {
                System.out.println("Scrolling down...");
            }
        }
    }

    private static Set<String> scrapeMatchIds(WebDriver driver) {
        Set<String> matchIds = new HashSet<>();
        try {
            // Wait for match divs to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".matchDiv.matchDiv2")));

            // Find match div elements
            List<WebElement> matchDivs = driver.findElements(By.cssSelector(".matchDiv.matchDiv2"));

            // Regex pattern for match IDs
            Pattern pattern = Pattern.compile("/matches/(\\d+)");

            // Extract match IDs from each div
            for (WebElement matchDiv : matchDivs) {
                List<WebElement> links = matchDiv.findElements(By.xpath(".//a[@href]"));

                for (WebElement link : links) {
                    String href = link.getAttribute("href");

                    if (href != null && href.contains("/matches/")) {
                        Matcher matcher = pattern.matcher(href);
                        if (matcher.find()) {
                            matchIds.add(matcher.group(1));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error scraping match IDs: " + e.getMessage());
        }
        return matchIds;
    }

    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless");  // Uncomment for headless mode

        // Mimic real user agent
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36");

        return new ChromeDriver(options);
    }

    static Date[] getDateRangeFromUser() {
        Scanner scanner = new Scanner(System.in);
        Date startDate = null;
        Date endDate = null;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        // Get start date
        while (startDate == null) {
            try {
                System.out.println("Enter the start date (format: yyyy-MM-dd): ");
                String dateInput = scanner.nextLine();
                startDate = dateFormat.parse(dateInput);
            } catch (Exception e) {
                System.out.println("Invalid date format. Please try again.");
            }
        }

        // Get end date
        while (endDate == null) {
            try {
                System.out.println("Enter the end date (format: yyyy-MM-dd): ");
                String dateInput = scanner.nextLine();
                endDate = dateFormat.parse(dateInput);

                // Validate end date is after or equal to start date
                if (endDate.before(startDate)) {
                    System.out.println("End date must be after or equal to start date. Please try again.");
                    endDate = null;
                }
            } catch (Exception e) {
                System.out.println("Invalid date format. Please try again.");
            }
        }

        return new Date[]{startDate, endDate};
    }

    static void selectDateFromDatePicker(WebDriver driver, Date selectedDate) throws InterruptedException {
        // Get selected date details
        Calendar selectedCal = Calendar.getInstance();
        selectedCal.setTime(selectedDate);

        // Get year and month to navigate to
        int targetYear = selectedCal.get(Calendar.YEAR);
        int targetMonth = selectedCal.get(Calendar.MONTH);
        int targetDay = selectedCal.get(Calendar.DAY_OF_MONTH);

        // Get current displayed year and month
        WebElement yearElement = driver.findElement(By.cssSelector(".el-date-picker__header-label:nth-child(2)"));
        WebElement monthElement = driver.findElement(By.cssSelector(".el-date-picker__header-label:nth-child(3)"));

        int displayedYear = Integer.parseInt(yearElement.getText().trim());
        String displayedMonthStr = monthElement.getText().trim();
        int displayedMonth = getMonthNumber(displayedMonthStr);

        // Navigate to the correct year and month
        navigateToYearAndMonth(driver, displayedYear, displayedMonth, targetYear, targetMonth);

        // Wait for the calendar to update
        Thread.sleep(500);

        // Select the day
        String dayXPath = String.format(
                "//td[contains(@class, 'available') and not(contains(@class, 'prev-month')) and not(contains(@class, 'next-month'))]//span[text()='%d']",
                targetDay
        );

        WebElement dayElement = driver.findElement(By.xpath(dayXPath));
        dayElement.click();

        System.out.println("Selected date: " + new SimpleDateFormat("yyyy-MM-dd").format(selectedDate));
    }

    static void navigateToYearAndMonth(WebDriver driver, int displayedYear, int displayedMonth, int targetYear, int targetMonth) throws InterruptedException {
        // Navigate to correct year
        while (displayedYear != targetYear) {
            if (displayedYear < targetYear) {
                // Click next year button
                driver.findElement(By.cssSelector(".d-arrow-right")).click();
                displayedYear++;
            } else {
                // Click previous year button
                driver.findElement(By.cssSelector(".d-arrow-left")).click();
                displayedYear--;
            }
            Thread.sleep(200);
        }

        // Navigate to correct month
        while (displayedMonth != targetMonth) {
            if (displayedMonth < targetMonth) {
                // Click next month button
                driver.findElement(By.cssSelector(".arrow-right")).click();
                displayedMonth++;
            } else {
                // Click previous month button
                driver.findElement(By.cssSelector(".arrow-left")).click();
                displayedMonth--;
            }
            Thread.sleep(200);
        }
    }

    static int getMonthNumber(String monthName) {
        switch (monthName.toLowerCase()) {
            case "january":
                return 0;
            case "february":
                return 1;
            case "march":
                return 2;
            case "april":
                return 3;
            case "may":
                return 4;
            case "june":
                return 5;
            case "july":
                return 6;
            case "august":
                return 7;
            case "september":
                return 8;
            case "october":
                return 9;
            case "november":
                return 10;
            case "december":
                return 11;
            default:
                return -1;
        }
    }
}