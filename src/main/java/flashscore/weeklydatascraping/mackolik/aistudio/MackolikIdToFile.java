package flashscore.weeklydatascraping.mackolik.aistudio;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MackolikIdToFile {

    private static String LEAGUE_ID = "21";

    public static void main(String[] args) {
        // WebDriver kurulumu
        WebDriverManager.chromedriver().setup();

        // Tarayıcı ayarları
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);

        List<String> teamIds = new ArrayList<>();

        try {
            // URL'ye git
            String url = "https://arsiv.mackolik.com/Puan-Durumu/"+LEAGUE_ID+"/";
            System.out.println("Selenium ile sayfaya gidiliyor: " + url);
            driver.get(url);

            // Tablonun yüklenmesini bekle
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            System.out.println("Puan tablosunun yüklenmesi bekleniyor...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#tblStanding .td-team-name a")));
            System.out.println("Tablo başarıyla yüklendi!");

            // Takım linklerini bul ve ID'leri topla
            List<WebElement> teamLinks = driver.findElements(By.cssSelector("#tblStanding .td-team-name a"));
            System.out.println(teamLinks.size() + " adet takım bulundu. ID'ler toplanıyor...");
            for (WebElement link : teamLinks) {
                String href = link.getAttribute("href");
                String[] parts = href.split("/");
                if (parts.length > 4 && parts[3].equalsIgnoreCase("Takim")) {
                    String teamId = parts[4];
                    teamIds.add(teamId);
                }
            }

        } catch (Exception e) {
            System.err.println("Veri çekme sırasında bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Tarayıcıyı kapat
            if (driver != null) {
                driver.quit();
                System.out.println("Tarayıcı kapatıldı.");
            }
        }

        // --- DEĞİŞİKLİK BURADA BAŞLIYOR ---

        // Toplanan ID'leri dosyaya yaz
        if (!teamIds.isEmpty()) {
            try {
                // 1. Toplanan ID listesini, aralarına virgül koyarak tek bir String'e dönüştür.
                String commaSeparatedIds = String.join(",", teamIds);

                // 2. Dosyanın adını ve yolunu belirle.
                Path file = Paths.get("takim_idleri.txt");

                // 3. Oluşturulan tek satırlık String'i dosyaya yaz.
                //    .getBytes() metodu, metni dosyaya yazılabilir formata (byte dizisine) çevirir.
                Files.write(file, commaSeparatedIds.getBytes());

                System.out.println("Tüm ID'ler başarıyla ve yan yana '" + file.toAbsolutePath() + "' dosyasına yazıldı.");

            } catch (IOException e) {
                System.err.println("Dosyaya yazma sırasında bir hata oluştu: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Yazılacak ID bulunamadı, dosya oluşturulmadı.");
        }
    }
}