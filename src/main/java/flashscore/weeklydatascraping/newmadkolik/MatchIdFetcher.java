package flashscore.weeklydatascraping.newmadkolik;


import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bu klas, veb səhifəsindən analiz ediləcək matç ID-lərini avtomatik olaraq çəkmək üçündür.
 */
public class MatchIdFetcher {

    private final String url;
    private final ChromeOptions chromeOptions;

    public MatchIdFetcher(String url, ChromeOptions options) {
        this.url = url;
        this.chromeOptions = options;
    }

    /**
     * Vebsəhifədəki matçların ID-lərini çəkən metod.
     * @return Matç ID-lərinin siyahısı (List<Integer>)
     */
    public List<Integer> fetchMatchIds() {
        System.out.println(AnsiColor.CYAN + "Matç ID-ləri çəkilir: " + url + AnsiColor.RESET);
        WebDriver driver = new ChromeDriver(chromeOptions);
        List<Integer> matchIds = new ArrayList<>();
        // ID-ləri 'tr1_2861087' formatından çıxarmaq üçün regex
        Pattern idPattern = Pattern.compile("tr1_(\\d+)");

        try {
            driver.get(url);

            // Səhifənin tam yüklənməsini və 'tbody' elementinin mövcud olmasını gözlə
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement tbody = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("tbody")));

            // `id` atributu 'tr1_' ilə başlayan bütün `<tr>` elementlərini tap
            List<WebElement> matchRows = tbody.findElements(By.xpath(".//tr[starts-with(@id, 'tr1_')]"));

            System.out.println(AnsiColor.YELLOW + matchRows.size() + " ədəd matç sətri tapıldı." + AnsiColor.RESET);

            for (WebElement row : matchRows) {
                String rowId = row.getAttribute("id");
                Matcher matcher = idPattern.matcher(rowId);
                if (matcher.find()) {
                    try {
                        int matchId = Integer.parseInt(matcher.group(1));
                        matchIds.add(matchId);
                    } catch (NumberFormatException e) {
                        System.err.println("ID rəqəmə çevrilə bilmədi: " + matcher.group(1));
                    }
                }
            }

            // Mükerrer (duplicate) ID-ləri aradan qaldır
            List<Integer> distinctMatchIds = matchIds.stream().distinct().collect(Collectors.toList());
            System.out.println(AnsiColor.GREEN + "Analiz üçün " + distinctMatchIds.size() + " unikal matç ID-si tapıldı." + AnsiColor.RESET);
            return distinctMatchIds;

        } catch (Exception e) {
            System.err.println("Matç ID-lərini çəkərkən xəta baş verdi: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>(); // Xəta zamanı boş siyahı qaytar
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println(AnsiColor.CYAN + "ID çəkmə üçün WebDriver sessiyası bağlandı." + AnsiColor.RESET);
            }
        }
    }
}