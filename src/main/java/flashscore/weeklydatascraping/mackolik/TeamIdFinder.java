package flashscore.weeklydatascraping.mackolik;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TeamIdFinder {

    static List<String> teamIdList = new ArrayList<>();
    static List<String> leagueIdList = new ArrayList<>();
    static WebDriver driver = initializeDriver();
    public static String txt = "team_ids.txt";

    static {
//        leagueIdList.addAll(Arrays.asList(
//                "3,4,5,1164","7","8","486","9","12","416","417","5","6"
//                ,"895","13292","111","17","120","24","25","104","105","414","26","106","107"
//                ,"20","21","15","16","458","459","6640","29","4180","594","13411","531","14"
//                ,"709","4716","78","2","1","1708","641","644","110","927","109","802","77"
//                ,"79","9940","19","474","28","739")); // Lig ID'leri

        leagueIdList.add("67261");
    }

    public static void main(String[] args) {
        try {
            for (String leagueId : leagueIdList) {
                driver.get("https://arsiv.mackolik.com/Puan-Durumu/s=" + leagueId + "/");
                List<WebElement> teamLinks = driver.findElements(By.xpath("//a[contains(@class, 'style3')]"));

                for (WebElement teamLink : teamLinks) {
                    // Takım ID'sini href attribute'undan al
                    String href = teamLink.getAttribute("href");
                    String teamId = href.split("/")[4]; // ID href içindeki 4. segment

                    // ID'yi listeye ekle
                    teamIdList.add(teamId);
                }
            }

            // Takım ID'lerini bir .txt dosyasına yaz
            writeIdsToFile();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit(); // Tarayıcıyı kapat
        }
    }

    static WebDriver initializeDriver() {
        System.setProperty("webdriver.chrome.driver", "src\\chrome\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Tarayıcıyı başsız modda çalıştır
        return new ChromeDriver(options);
    }

    static void writeIdsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(txt))) {
            // ID'leri virgülle birleştirip dosyaya yaz
            String ids = String.join(", ", teamIdList);
            writer.write(ids);
            System.out.println("Takım ID'leri dosyaya yazıldı: " + txt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> readIdsFromFile() throws IOException {
        // Dosyayı okuma
        try (BufferedReader reader = new BufferedReader(new FileReader(txt))) {
            String line = reader.readLine(); // İlk satırı oku
            if (line != null && !line.isEmpty()) {
                // Virgülle ayrılmış ID'leri listeye dönüştür
                return Arrays.asList(line.split(",\\s*")); // Virgül ve boşluklara göre böl
            }
        }
        return List.of(); // Boş liste döndür
    }
}
