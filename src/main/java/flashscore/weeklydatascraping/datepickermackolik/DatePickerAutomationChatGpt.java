package flashscore.weeklydatascraping.datepickermackolik;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DatePickerAutomationChatGpt {

    private static WebDriver driver = null;
    private static List<String> links = new ArrayList<>();
    private static FileWriter writer;

    static {
        try {
            writer = new FileWriter("mackolik.txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        WebDriver driver = getChromeDriver();
        getMatchesByDateRange(driver, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 17));
    }

    private static void getMatchesByDateRange(WebDriver driver, LocalDate startDate, LocalDate endDate)
            throws InterruptedException, IOException {
        driver.get("https://www.mackolik.com/canli-sonuclar");
        Thread.sleep(2000);

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Format date for xpath
            String dateStr = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Open date picker
            driver.findElement(By.className("widget-dateslider__datepicker-toggle")).click();
            Thread.sleep(1000);

            // Select year/month view
            WebElement yearMonthSelector = driver.findElement(By.xpath("//div[@class='widget-datepicker__header']"));
            yearMonthSelector.click();

            // Adjust year if necessary
            adjustYear(driver, currentDate.getYear());

            // Adjust month if necessary
            adjustMonth(driver, currentDate.getMonthValue());

            // Select the date
            driver.findElement(By.xpath("//td[@data-date='" + dateStr + "']")).click();
            Thread.sleep(5000);

            // Collect links for current date
            List<WebElement> elementList = driver.findElements(
                    By.xpath("//a[contains(@class, 'match-row__score')]"));

            for (WebElement element : elementList) {
                try {
                    String href = element.getAttribute("href");
                    if (!href.contains("basketbol")) {
                        links.add(href);
                        writer.write(href + System.lineSeparator());
                        writer.flush(); // Flush after each write to save progress
                    }
                    Thread.sleep(50);
                } catch (Exception ex) {
                    System.out.println("Error processing element on " + dateStr + ": " + ex.getMessage());
                }
            }

            // Move to next date
            currentDate = currentDate.plusDays(1);
            Thread.sleep(2000); // Wait between date changes
        }

        writer.close();
        driver.quit();
    }

    private static void adjustYear(WebDriver driver, int targetYear) throws InterruptedException {
        while (true) {
            WebElement yearElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__value']"));
            int currentYear = Integer.parseInt(yearElement.getText());
            if (currentYear == targetYear) {
                break;
            } else if (currentYear > targetYear) {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__nav widget-datepicker__nav--previous']")).click();
            } else {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__nav widget-datepicker__nav--next']")).click();
            }
            Thread.sleep(500); // Wait for the year to change
        }
    }

    private static void adjustMonth(WebDriver driver, int targetMonth) throws InterruptedException {
        while (true) {
            WebElement monthElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__value']"));
            int currentMonth = convertMonthNameToNumber(monthElement.getText());
            if (currentMonth == targetMonth) {
                break;
            } else if (currentMonth > targetMonth) {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__nav widget-datepicker__nav--previous']")).click();
            } else {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__nav widget-datepicker__nav--next']")).click();
            }
            Thread.sleep(500); // Wait for the month to change
        }
    }

    private static int convertMonthNameToNumber(String monthName) {
        switch (monthName.toLowerCase()) {
            case "oca": return 1;
            case "şub": return 2;
            case "mar": return 3;
            case "nis": return 4;
            case "may": return 5;
            case "haz": return 6;
            case "tem": return 7;
            case "ağu": return 8;
            case "eyl": return 9;
            case "eki": return 10;
            case "kas": return 11;
            case "ara": return 12;
            default: throw new IllegalArgumentException("Unknown month: " + monthName);
        }
    }

    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        if (driver == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--start-maximized");
            driver = new ChromeDriver(options);
        }
        return driver;
    }
}