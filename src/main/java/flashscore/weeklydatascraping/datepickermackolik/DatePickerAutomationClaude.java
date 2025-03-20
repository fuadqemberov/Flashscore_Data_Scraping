package flashscore.weeklydatascraping.datepickermackolik;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatePickerAutomationClaude {

    private static List<String> links = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws InterruptedException, IOException {
        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.SEVERE);
        long startTime = System.currentTimeMillis();
        LocalDate startDate = LocalDate.of(2024,8,1);
        LocalDate endDate = LocalDate.now();

        WebDriver driver = getChromeDriver();
        getMatchesByDateRange(driver, startDate, endDate);
        driver.quit();

        long endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) / 1000 + " seconds");
    }

    private static void getMatchesByDateRange(WebDriver driver, LocalDate startDate, LocalDate endDate)
            throws InterruptedException, IOException {
        driver.get("https://www.mackolik.com/canli-sonuclar");
        Thread.sleep(3000);

        try (FileWriter writer = new FileWriter("mackolik3.txt")) {
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                String dateStr = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                try {
                    driver.findElement(By.className("widget-dateslider__datepicker-toggle")).click();
                    Thread.sleep(1000);

                    WebElement yearMonthSelector = driver.findElement(By.xpath("//div[@class='widget-datepicker__header']"));
                    yearMonthSelector.click();
                    Thread.sleep(1000);

                    adjustYear(driver, currentDate.getYear());
                    adjustMonth(driver, currentDate.getMonthValue());

                    driver.findElement(By.xpath("//td[@data-date='" + dateStr + "']")).click();
                    Thread.sleep(3000);

                    List<WebElement> elementList = driver.findElements(
                            By.xpath("//a[contains(@class, 'match-row__score')]"));

                    for (WebElement element : elementList) {
                        String href = element.getAttribute("href");
                        if (href != null && !href.contains("basketbol")) {
                            links.add(href);
                            writer.write(href + System.lineSeparator());
                            writer.flush();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to process date " + dateStr + ": " + e.getMessage());
                }

                currentDate = currentDate.plusDays(1);
                Thread.sleep(1000);
            }
        }
    }

    private static void adjustYear(WebDriver driver, int targetYear) throws InterruptedException {
        WebElement yearElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__value']"));
        int currentYear = Integer.parseInt(yearElement.getText());

        while (currentYear != targetYear) {
            if (currentYear > targetYear) {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__nav widget-datepicker__nav--previous']")).click();
            } else {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__nav widget-datepicker__nav--next']")).click();
            }
            Thread.sleep(500);
            yearElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--year']/div[@class='widget-datepicker__value']"));
            currentYear = Integer.parseInt(yearElement.getText());
        }
    }

    private static void adjustMonth(WebDriver driver, int targetMonth) throws InterruptedException {
        WebElement monthElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__value']"));
        int currentMonth = convertMonthNameToNumber(monthElement.getText());

        while (currentMonth != targetMonth) {
            if (currentMonth > targetMonth) {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__nav widget-datepicker__nav--previous']")).click();
            } else {
                driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__nav widget-datepicker__nav--next']")).click();
            }
            Thread.sleep(500);
            monthElement = driver.findElement(By.xpath("//div[@class='widget-datepicker__selector widget-datepicker__selector--month']/div[@class='widget-datepicker__value']"));
            currentMonth = convertMonthNameToNumber(monthElement.getText());
        }
    }

    private static int convertMonthNameToNumber(String monthName) {
        switch (monthName.toLowerCase()) {
            case "oca":
                return 1;
            case "şub":
                return 2;
            case "mar":
                return 3;
            case "nis":
                return 4;
            case "may":
                return 5;
            case "haz":
                return 6;
            case "tem":
                return 7;
            case "ağu":
                return 8;
            case "eyl":
                return 9;
            case "eki":
                return 10;
            case "kas":
                return 11;
            case "ara":
                return 12;
            default:
                throw new IllegalArgumentException("Unknown month: " + monthName);
        }
    }


    public static WebDriver getChromeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--start-maximized");
        options.addArguments("--headless");
        return new ChromeDriver(options);
    }

}