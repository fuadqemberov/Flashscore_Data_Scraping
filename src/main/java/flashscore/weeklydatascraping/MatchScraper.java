package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static flashscore.Util.flashUtill.getChromeDriver;

public class MatchScraper {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM");

    public static void main(String[] args) throws ParseException {
        WebDriver driver = getChromeDriver();
        String url = "https://arsiv.mackolik.com/Mac/4170271/Rigas-Skola-APOEL-Nicosia#karsilastirma";
        driver.get(url);

        // Maç tarihini getir
        String matchDateString = getMatchDate(driver);

        // Geçmiş maçları tablo üzerinden al
        List<WebElement> pastMatchesH = getPastMatchesHome(driver);
        List<WebElement> pastMatchesA = getPastMatchesAway(driver);

        // Maçları tarihe göre filtrele
        List<WebElement> filteredMatchesHome = filterMatchesByDate(pastMatchesH, matchDateString);
        List<WebElement> filteredMatchesAway = filterMatchesByDate(pastMatchesA, matchDateString);

        // Son 3 maçı ekrana yazdır
        List<WebElement> lastThreeMatchesH = getLastThreeMatches(filteredMatchesHome);
        List<WebElement> lastThreeMatchesA = getLastThreeMatches(filteredMatchesAway);

        printMatches(lastThreeMatchesH);
        printMatches(lastThreeMatchesA);
    }

    // Maç tarihini almak için metod
    private static String getMatchDate(WebDriver driver) {
        return driver.findElement(By.xpath("//div[@class='match-info-date']")).getText().substring(8, 13);
    }

    // Geçmiş maçları tablo üzerinden almak için metod
    private static List<WebElement> getPastMatchesHome(WebDriver driver) {
        WebElement table = driver.findElement(By.xpath("//*[@id=\"compare-right-coll\"]/div[3]/table"));
        return table.findElements(By.tagName("tr"));
    }

    private static List<WebElement> getPastMatchesAway(WebDriver driver) {
        WebElement table = driver.findElement(By.xpath("//*[@id=\"compare-right-coll\"]/div[3]/table"));
        return table.findElements(By.tagName("tr"));
    }


    // Maçları verilen tarihe göre filtrelemek için metod
    private static List<WebElement> filterMatchesByDate(List<WebElement> matches, String matchDateString) throws ParseException {
        List<WebElement> filteredList = new ArrayList<>();
        for (WebElement element : matches) {
            List<WebElement> tds = element.findElements(By.tagName("td"));
            if (tds.size() > 1) {
                String dateText = tds.get(1).getText();
                if (sdf.parse(dateText).before(sdf.parse(matchDateString))) {
                    filteredList.add(element);
                }
            }
        }
        return filteredList;
    }

    // Son 3 maçı almak için metod
    private static List<WebElement> getLastThreeMatches(List<WebElement> matches) {
        return matches.subList(Math.max(matches.size() - 3, 0), matches.size());
    }

    // Maçları ekrana yazdırmak için metod
    private static void printMatches(List<WebElement> matches) {
        for (WebElement match : matches) {
            System.out.println(match.getText());
        }
    }
}
