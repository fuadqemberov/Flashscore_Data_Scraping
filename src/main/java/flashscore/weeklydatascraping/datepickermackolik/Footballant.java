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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Footballant {
    public static void main(String[] args) throws InterruptedException {
        // Initialize the driver
        WebDriver driver = initializeDriver();

        try {
            // Navigate to the website
            driver.get("https://www.footballant.com/");
            System.out.println("Navigating to website...");
            Thread.sleep(4000);

            // Find and click the date picker element
            String datePickerXPath = "//*[@id=\"el-id-1024-10\"]";
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement datePickerElement = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(datePickerXPath)));
            datePickerElement.click();
            System.out.println("Date picker opened");

            // Get desired date from user
            Date selectedDate = getDesiredDateFromUser();

            // Navigate to and select the desired date
            selectDateFromDatePicker(driver, selectedDate);

            // Optional: Wait to see the result
            Thread.sleep(3000);


           // 4. JavaScriptExecutor oluşturma
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 5. Sayfayı yavaş yavaş kaydırma fonksiyonu
            int scrollAmount = 400; // Her seferinde kaydırılacak piksel miktarı (küçük bir değer)
            boolean pageEndReached = false;

            while (!pageEndReached) {
                // Mevcut kaydırma pozisyonunu al
                long initialScrollTop = (long) js.executeScript("return document.documentElement.scrollTop || document.body.scrollTop");

                // Belirli bir miktarda aşağı kaydır
                js.executeScript("window.scrollBy(0," + scrollAmount + ")");

                // Yükleme için bekle
                try {
                    Thread.sleep(1000); // Daha kısa bekleme süresi (1 saniye)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Yeni kaydırma pozisyonunu al
                long newScrollTop = (long) js.executeScript("return document.documentElement.scrollTop || document.body.scrollTop");

                // Kaydırma pozisyonu değişmediyse, sayfa sonuna ulaşıldığını varsay
                if (newScrollTop == initialScrollTop) {
                    pageEndReached = true;
                    System.out.println("Sayfa sonuna ulaşıldı.");
                } else {
                    System.out.println("Sayfa kaydırılıyor.");
                }

            }

            // 4. maç Id'lerini saklayacak bir liste oluşturma
            Set<String> matchIds = new HashSet<>();

            // 5.  Önce maçları içeren ana div'in yüklenmesini bekleme
            WebDriverWait wait3 = new WebDriverWait(driver, Duration.ofSeconds(20)); // Daha uzun bekleme süresi gerekebilir
            wait3.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".matchDiv.matchDiv2"))); // Ana div'i bekliyoruz


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
                        if (matcher.find()) {
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
            // Perform any actions after date selection
            System.out.println("Date selected successfully. Performing subsequent actions...");


        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chromee\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless");  // Uncomment for headless mode

        // Mimic real user agent
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36");

        return new ChromeDriver(options);
    }

    static Date getDesiredDateFromUser() {
        Scanner scanner = new Scanner(System.in);
        Date selectedDate = null;

        while (selectedDate == null) {
            try {
                System.out.println("Enter the date you want to select (format: yyyy-MM-dd): ");
                String dateInput = scanner.nextLine();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                selectedDate = dateFormat.parse(dateInput);
            } catch (Exception e) {
                System.out.println("Invalid date format. Please try again.");
            }
        }

        return selectedDate;
    }

    static void selectDateFromDatePicker(WebDriver driver, Date selectedDate) throws InterruptedException {
        // Get current date and selected date details
        Calendar currentCal = Calendar.getInstance();
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
        // Find all available days (not in prev-month or next-month)
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