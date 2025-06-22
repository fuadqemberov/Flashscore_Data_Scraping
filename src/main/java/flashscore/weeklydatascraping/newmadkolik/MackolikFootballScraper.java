package flashscore.weeklydatascraping.newmadkolik;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MackolikFootballScraper {

    public static void main(String[] args) {
        // WebDriverManager kullanarak ChromeDriver'ı otomatik olarak ayarla
        WebDriverManager.chromedriver().setup();

        // Selenium WebDriver için Chrome seçeneklerini ayarla
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless"); // Tarayıcıyı göstermeden çalıştırmak için bu satırı etkinleştirin
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1200");

        WebDriver driver = new ChromeDriver(options);
        // Bekleme süresini tanımla (Maksimum 20 saniye)
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        String url = "https://www.mackolik.com/canli-sonuclar";
        String outputFileName = "football_match_ids.txt";

        try {
            // 1. Belirtilen URL'e git
            System.out.println("Mackolik canlı sonuçlar sayfasına gidiliyor...");
            driver.get(url);

            // 2. Çerezleri kabul et butonu için bekle ve tıkla
            try {
                System.out.println("Çerez kabul butonu bekleniyor...");
                WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("accept-all-button")));
                acceptCookiesButton.click();
                System.out.println("Çerezler kabul edildi.");
            } catch (Exception e) {
                System.out.println("Çerez kabul butonu bulunamadı veya zaten kabul edilmiş.");
            }

            // 3. "Futbol" sekmesine tıkla
            System.out.println("Futbol sekmesi aranıyor ve tıklanıyor...");
            // CSS Selector kullanarak elementi buluyoruz: data-sport="soccer" olan <a> etiketi
            WebElement footballTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[data-sport='soccer']")));
            footballTab.click();
            System.out.println("Futbol sekmesine tıklandı.");

            // 4. Sayfanın futbol maçlarıyla güncellenmesi için bekle
            // Güvenilir bir bekleme için, futbol maçlarına özgü bir elementin görünür olmasını bekleyebiliriz.
            // Örneğin, data-sport="S" olan ilk maç satırı.
            System.out.println("Futbol maçlarının yüklenmesi bekleniyor...");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.match-row div[data-sport='S']")));
            // Ekstra garanti için kısa bir bekleme daha eklenebilir.
            Thread.sleep(2000);

            // 5. Maç ID'lerini topla
            // Tüm maç satırlarını (`div` elementi ve `match-row` class'ı) bul
            List<WebElement> matchRows = driver.findElements(By.cssSelector("div.match-row"));

            if (matchRows.isEmpty()) {
                System.out.println("Hiç futbol maçı bulunamadı.");
                return;
            }

            System.out.println(matchRows.size() + " adet maç satırı bulundu.");
            List<String> matchIds = new ArrayList<>();

            for (WebElement matchRow : matchRows) {
                // Sadece futbol maçlarını aldığımızdan emin olalım (data-sport="S")
                // Zaten futbol sekmesine tıkladık ama bu bir kontrol katmanıdır.
                try {
                    WebElement matchContent = matchRow.findElement(By.cssSelector(".match-row__match-content[data-sport='S']"));
                    String matchId = matchRow.getAttribute("data-match-id");
                    if (matchId != null && !matchId.isEmpty()) {
                        matchIds.add(matchId);
                    }
                } catch (Exception e) {
                    // Bu satır bir futbol maçı değilse görmezden gel.
                }
            }

            System.out.println(matchIds.size() + " adet futbol maçı ID'si bulundu.");

            // 6. Maç ID'lerini dosyaya yaz
            if (!matchIds.isEmpty()) {
                writeToFile(matchIds, outputFileName);
                System.out.println("Maç ID'leri '" + outputFileName + "' dosyasına başarıyla yazıldı.");
            } else {
                System.out.println("Yazılacak futbol maçı ID'si bulunamadı.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Tarayıcıyı kapat
            if (driver != null) {
                driver.quit();
                System.out.println("Tarayıcı kapatıldı.");
            }
        }
    }

    /**
     * Verilen listeyi belirtilen dosyaya yazan metot.
     * @param lines Yazılacak satırların listesi
     * @param fileName Dosya adı
     */
    private static void writeToFile(List<String> lines, String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine(); // Her ID'den sonra yeni bir satıra geç
            }
        } catch (IOException e) {
            System.err.println("Dosyaya yazarken bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        }
    }
}