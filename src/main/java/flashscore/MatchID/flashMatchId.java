package flashscore.MatchID;


import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class flashMatchId {

    public static final String ACCEPT_COOKIES_BUTTON_XPATH = "//*[@id='onetrust-accept-btn-handler']";
    public static final String SHOW_MORE_MATCHES_BUTTON_XPATH = "//a[text()='Show more matches']";
    public static final String MATCH_DETAIL_XPATH = "//*[@title='Click for match detail!']";
    public static final String FILE_NAME = "matchIds.txt";
    public static final String MATCH_ID_PREFIX = "g_1_";



    public static void getMatchIdsFromSite(WebDriver driver, String linkPart,int year) throws InterruptedException {
        List<String> matchIds = new ArrayList<>();
        for(int i =year;i<2025;i++){
            driver.get(linkPart.substring(0,linkPart.length()-1)+"-"+i+"-"+(i+1)+"/results/");
            try{
                driver.findElement(By.xpath("/html/body/main/p/text()"));
            }
            catch (Exception ex){
                driver.get(linkPart.substring(0,linkPart.length()-1)+"-"+i+"/results/");

            }
            acceptCookies(driver);
            showMoreMatches(driver);

        // Collect match IDs
        List<WebElement> elementList = driver.findElements(By.xpath(MATCH_DETAIL_XPATH));
        for (WebElement element : elementList) {
            String link = element.getAttribute("aria-describedby");
            if (link != null && link.startsWith(MATCH_ID_PREFIX)) {
                String matchId = link.substring(MATCH_ID_PREFIX.length());
                matchIds.add(matchId);
            }
        }
}
        // Write match IDs to file
        writeMatchIdsToFile(matchIds);
    }

    public static void acceptCookies(WebDriver driver) {
        try {
            WebElement acceptButton = driver.findElement(By.xpath(ACCEPT_COOKIES_BUTTON_XPATH));
            acceptButton.click();
            Thread.sleep(1000); // Wait for the click to be processed
        } catch (Exception e) {
            System.out.println("Cookies accept button not found or not clickable.");
        }
    }

    public static void showMoreMatches(WebDriver driver) throws InterruptedException {
        boolean moreMatches = true;
        while (moreMatches) {
            try {
                WebElement showMoreButton = driver.findElement(By.xpath(SHOW_MORE_MATCHES_BUTTON_XPATH));
                showMoreButton.click();
                Thread.sleep(1000); // Wait for the new matches to load
            } catch (Exception e) {
                moreMatches = false; // Exit the loop if the button is not found
            }
        }
    }

    private static void writeMatchIdsToFile(List<String> matchIds) {
        try (FileWriter writer = new FileWriter(FILE_NAME, true)) {
            for (String matchId : matchIds) {
                writer.write(matchId + System.lineSeparator());
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    public static List<String> getMatchIdsFromFile(String fileName){
        List<String> matches = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                matches.add(line);
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }

        return matches;
    }

    private static void writeMatchIdsToFile2(List<String> matchIds) {
        try (FileWriter writer = new FileWriter(FILE_NAME, true)) {
            for (String matchId : matchIds) {
                writer.write(matchId + " ");
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    public static List<String> getMatchIdsFromFile2(String fileName) {
        List<String> matches = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Boşluk ile ayrılmış değerleri ayır
                String[] matchIds = line.split(" ");
                for (String matchId : matchIds) {
                    if (!matchId.trim().isEmpty()) { // Boş değerleri atla
                        matches.add(matchId);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }

        return matches;
    }

}
