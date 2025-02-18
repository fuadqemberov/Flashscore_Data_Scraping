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

import java.io.BufferedReader;
import java.io.File;
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
    private static List<List<String>> allData = new ArrayList<>();
    private static List<String> oneRowData = new ArrayList<>();

    static {
        try {
            writer = new FileWriter("mackolik.txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        WebDriver driver = getChromeDriver();
        getMatchesByDateRange(driver, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));
        if (!links.isEmpty()) {
            for (String link : readFile()) {
                getData(link, driver);
            }
        }
        driver.quit();
        writeMatchesToExcel();
    }

    private static void getData(String link, WebDriver driver) throws InterruptedException {
        try {
            driver.get(link);
            Thread.sleep(1500);
        } catch (Exception ex) {
            System.out.println("Sayfa yüklenemedi: " + ex.getMessage());
            return;
        }

        try {

            Thread.sleep(2000);
            driver.findElement(By.xpath("//a[span[text()='Karşılaştırma']]")).click();
            Thread.sleep(4500);
        } catch (Exception ex) {
            System.out.println("Karşılaştırma butonu bulunamadı veya tıklanamadı: " + ex.getMessage());
            return;
        }

        String htFt = extractFormattedScore(link, driver);
        for (int j = 1; j < 3; j++) {
            List<String> teamList = new ArrayList<>();

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

                    teamList.add(home);
                    teamList.add(score);
                    teamList.add(away);

                    if(i==6){
                        teamList.add(htFt);
                    }
                    System.out.println(home + " " + score + " " + away);
                } catch (Exception ex) {
                    System.out.println("Veri alınamadı (j=" + j + ", i=" + i + "): " + ex.getMessage());
                }
            }

            allData.add(new ArrayList<>(teamList));
        }

    }

    public static String extractFormattedScore(String url , WebDriver driver) {
        String formattedScore = "";

        try {


            // Wait for the score elements to be visible
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.className("p0c-soccer-match-details-header__score-home")));

            // Extract full time scores
            String homeScore = driver.findElement(
                    By.cssSelector(".p0c-soccer-match-details-header__score-home")).getText().trim();
            String awayScore = driver.findElement(
                    By.cssSelector(".p0c-soccer-match-details-header__score-away")).getText().trim();

            // Extract half time score
            String detailedScore = driver.findElement(
                    By.className("p0c-soccer-match-details-header__detailed-score")).getText().trim();

            // Parse half time score
            String halfTimeScore = "0-0"; // Default if parsing fails
            if (detailedScore.contains("İY")) {
                String htScoreText = detailedScore.replace("(İY", "").replace(")", "").trim();
                if (!htScoreText.isEmpty()) {
                    halfTimeScore = htScoreText;
                }
            }

            // Format as "HalfTimeScore/FullTimeScore"
            formattedScore = halfTimeScore + "/" + homeScore + "-" + awayScore;

        } catch (Exception e) {
            System.err.println("Error extracting score data: " + e.getMessage());
            e.printStackTrace();
        }

        return formattedScore;
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
            Thread.sleep(500);
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

    public static void writeMatchesToExcel() {
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
        for (List<String> teamMatches : allData) {
            Row row = sheet.createRow(rowNum++);

            // Each team has 4 matches with 3 pieces of info each (home, score, away)
            if (teamMatches.size() >= 13) {  // Ensure we have at least 4 matches
                for (int i = 0; i < 13; i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
            } else {
                System.out.println("Warning: Not enough match data for row " + rowNum);
                // Fill available data
                for (int i = 0; i < teamMatches.size(); i++) {
                    row.createCell(i).setCellValue(teamMatches.get(i));
                }
            }

        }

        try (FileOutputStream fileOut = new FileOutputStream("mackolik_results.xlsx")) {
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