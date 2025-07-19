package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlashscoreScraperFinal {

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        // Bekleme süresini biraz artıralım, ağ bağlantısı yavaş olabilir.
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        Set<String> allMatchIds = new HashSet<>();
        String previousDate = "";

        try {
            driver.get("https://www.flashscore.com/");
            System.out.println("Flashscore sitesine gidildi.");

            // Çerezleri kabul et
            try {
                WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
                acceptCookiesButton.click();
                System.out.println("Çerez bildirimi kabul edildi.");
            } catch (Exception e) {
                System.out.println("Çerez bildirimi bulunamadı veya zaten kabul edilmiş.");
            }

            // Sayfanın ilk yüklemesinin tamamlanması için ana maç konteynerini bekle
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".sportName.soccer")));

            String prevDayButtonSelector = "//button[@aria-label='Previous day']";
            // Yükleme animasyonunu (skeleton) belirtmek için kullanılan CSS selector
            By skeletonLoader = By.cssSelector("div.sk");

            System.out.println("Geriye doğru tüm günler taranmaya başlanıyor...");

            while (true) {
                // 1. MEVCUT SAYFADAKİ VERİYİ TOPLA
                WebElement dateButton = driver.findElement(By.cssSelector("[data-testid='wcl-dayPickerButton']"));
                String currentDate = dateButton.getText();

                // Eğer sayfa takılır ve aynı tarihte kalırsa döngüyü kır
                if(currentDate.equals(previousDate)) {
                    System.out.println("Sayfa ilerlemiyor, döngü sonlandırılıyor.");
                    break;
                }
                System.out.println("\nİşleniyor: " + currentDate);
                previousDate = currentDate;

                List<WebElement> matchElements = driver.findElements(By.cssSelector("div[id^='g_1_']"));
                if (!matchElements.isEmpty()) {
                    System.out.println("-> Bu sayfada " + matchElements.size() + " maç bulundu ve eklendi.");
                    String prefixToRemove = "g_1_";
                    for (WebElement matchElement : matchElements) {
                        String fullId = matchElement.getAttribute("id");
                        if (fullId != null && fullId.startsWith(prefixToRemove)) {
                            allMatchIds.add(fullId.substring(prefixToRemove.length()));
                        }
                    }
                } else {
                    System.out.println("-> Bu sayfada maç bulunamadı.");
                }

                // 2. BİR ÖNCEKİ GÜNE GEÇ
                WebElement prevButton;
                try {
                    prevButton = driver.findElement(By.xpath(prevDayButtonSelector));
                } catch (Exception e) {
                    System.out.println("Önceki gün butonu bulunamadı. İşlem bitti.");
                    break;
                }

                if (prevButton.isEnabled()) {
                    // Butona tıkla
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", prevButton);

                    // 3. YÜKLEMENİN BİTMESİNİ BEKLE (EN ÖNEMLİ KISIM)
                    try {
                        // Önce yükleme animasyonunun görünmesini bekle (opsiyonel ama sağlamlaştırır)
                        wait.until(ExpectedConditions.visibilityOfElementLocated(skeletonLoader));
                        // Sonra yükleme animasyonunun kaybolmasını bekle
                        wait.until(ExpectedConditions.invisibilityOfElementLocated(skeletonLoader));
                    } catch (org.openqa.selenium.TimeoutException e) {
                        // Eğer yükleme animasyonu hiç görünmezse (sayfa çok hızlı yüklenirse),
                        // bu bir hata değildir, devam et. Sadece loglayalım.
                        System.out.println("   (Yükleme animasyonu algılanmadı, sayfa hızlı yüklendi)");
                    }
                } else {
                    System.out.println("\nEn eski tarihe ulaşıldı. Döngü sonlandırılıyor.");
                    break;
                }
            }

            System.out.println("\n-------------------------------------------");
            System.out.println("Tüm Günlerden Toplanan Benzersiz Maç ID'leri");
            System.out.println("Toplam Benzersiz ID Sayısı: " + allMatchIds.size());
            System.out.println("-------------------------------------------");

            int count = 1;
            for (String id : allMatchIds) {
                System.out.println(count + ". " + id);
                count++;
            }

            System.out.println("\nİşlem başarıyla tamamlandı.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}