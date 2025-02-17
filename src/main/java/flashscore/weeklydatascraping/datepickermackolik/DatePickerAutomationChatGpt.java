package flashscore.weeklydatascraping.datepickermackolik;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import scala.tools.nsc.doc.html.HtmlTags;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
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
        getMatchesByDateRange(driver, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));
        List<String> linkler = readFile();
        System.out.println(linkler.get(0));
        if (!linkler.isEmpty()) {
            for (String link : linkler) {
                getData2(link, driver);
            }
        }

    }

    private static void getData2(String link, WebDriver driver) throws InterruptedException {
        WebDriver driver2 = getChromeDriver();
        driver2.get(link);
    }

    private static void getData(String link, WebDriver driver) throws InterruptedException {
        try {
            driver.get(link);
            Thread.sleep(1500);
        } catch (Exception ex) {
            System.out.println("Sayfa yüklenemedi: " + ex.getMessage());
            return; // Sayfa yüklenmezse işlemi sonlandır
        }

        try {
            // "Karşılaştırma" butonunun yüklenmesini bekle
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement compareButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[span[text()='Karşılaştırma']]")));
            compareButton.click();
            Thread.sleep(3500);
        } catch (Exception ex) {
            System.out.println("Karşılaştırma butonu bulunamadı veya tıklanamadı: " + ex.getMessage());
            return;
        }

        for (int j = 1; j < 3; j++) {
            for (int i = 3; i < 7; i++) {
                try {
                    String xpathHome = "/html/body/div[5]/div[2]/main/div[1]/div[2]/div/div[3]/div/div/div[2]/div[" + j + "]/div[" + i + "]/div/a[1]/div/span";
                    String xpathScore = "/html/body/div[5]/div[2]/main/div[1]/div[2]/div/div[3]/div/div/div[2]/div[" + j + "]/div[" + i + "]/div/a[2]/div";
                    String xpathAway = "/html/body/div[5]/div[2]/main/div[1]/div[2]/div/div[3]/div/div/div[2]/div[" + j + "]/div[" + i + "]/div/a[3]/div/span[2]";

                    WebElement homeElement = driver.findElement(By.xpath(xpathHome));
                    WebElement scoreElement = driver.findElement(By.xpath(xpathScore));
                    WebElement awayElement = driver.findElement(By.xpath(xpathAway));

                    String home = homeElement.getText();
                    String score = scoreElement.getText();
                    String away = awayElement.getText();

                    System.out.println(home + " " + score + " " + away);
                } catch (Exception ex) {
                    System.out.println("Veri alınamadı (j=" + j + ", i=" + i + "): " + ex.getMessage());
                }
            }
        }
    }


    public static List<String> readFile() throws IOException {
        List<String> lines = new ArrayList<>();
        File file = new File("mackolik.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
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
                        writer.flush();
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
        if (driver == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--start-maximized");
            driver = new ChromeDriver(options);
        }
        return driver;
    }

    public static void writeMatchesToExcel(List<List<String>> allmatches) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Match Results");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Home-4");
        headerRow.createCell(1).setCellValue("Score-4");
        headerRow.createCell(2).setCellValue("Away-4");

        headerRow.createCell(3).setCellValue("Home-3");
        headerRow.createCell(4).setCellValue("Score-3");
        headerRow.createCell(5).setCellValue("Away-3");

        headerRow.createCell(6).setCellValue("Home-2");
        headerRow.createCell(7).setCellValue("Score-2");
        headerRow.createCell(8).setCellValue("Away-2");

        headerRow.createCell(9).setCellValue("Home-1");
        headerRow.createCell(10).setCellValue("Score-1");
        headerRow.createCell(11).setCellValue("Away-1");

        headerRow.createCell(12).setCellValue("HT / FT");


        int rowNum = 1;


        for (int i = 0; i < allmatches.size(); i++) {
            List<String> matches = allmatches.get(i);
            System.out.println(matches);

            String match1 = matches.get(0).substring(0, 3);
            String match2 = matches.get(1).substring(0, 3);
            String match3 = matches.get(2).substring(0, 3);
            String liga = matches.get(3);
            String result = matches.get(4);

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(match1);
            row.createCell(1).setCellValue(match2);
            row.createCell(2).setCellValue(match3);
            row.createCell(3).setCellValue(liga);
            row.createCell(4).setCellValue(result);

        }
        try (FileOutputStream fileOut = new FileOutputStream("match_results.xlsx")) {
            workbook.write(fileOut);
        } catch (IOException e) {
            System.err.println("Error while writing to Excel file: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Excel file created successfully!");

    }
}