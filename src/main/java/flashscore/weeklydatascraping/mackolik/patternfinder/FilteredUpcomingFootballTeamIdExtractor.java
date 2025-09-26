package flashscore.weeklydatascraping.mackolik.patternfinder;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilteredUpcomingFootballTeamIdExtractor {
    public static Set<String> upcomingFootballTeamIds = new HashSet<>();
    public static String txt = "team_ids.txt";

     public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();

        // Tarayıcı ayarları
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.manage().window().maximize();
            driver.get("https://arsiv.mackolik.com/Canli-Sonuclar");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30)); // Bekleme süresi artırıldı
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Çerez bildirimini kabul et
            try {
                WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(@class, 'accept-all-btn')]")));
                js.executeScript("arguments[0].click();", acceptCookiesButton);
                System.out.println("Çerezler kabul edildi.");
                // Çerez bandının kaybolmasını beklemek daha güvenli
                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//div[contains(@class,'cookie-consent-rectangle-wrapper')]")));
                System.out.println("Çerez bandı kayboldu.");
                Thread.sleep(500); // Ekstra küçük bekleme
            } catch (Exception e) { // Daha genel hata yakalama
                System.out.println("Çerez kabul butonu ile ilgili işlem yapılamadı veya zaten yoktu.");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }

            // YENİ: Önce sağa sonra sola tıklamak için date picker'ı işlem yap
            try {
                System.out.println("Date picker işlemleri başlıyor...");

                // Sağ ok elementini bul ve tıkla
                WebElement rightArrow = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".date-right-coll")));
                System.out.println("Sağ ok elementine tıklanıyor...");
                js.executeScript("arguments[0].click();", rightArrow);
                Thread.sleep(1000); // İşlemin tamamlanması için bekle
                System.out.println("Sağ ok elementine tıklandı");

                // Sol ok elementini bul ve tıkla
                WebElement leftArrow = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".date-left-coll")));
                System.out.println("Sol ok elementine tıklanıyor...");
                js.executeScript("arguments[0].click();", leftArrow);
                Thread.sleep(1000); // İşlemin tamamlanması için bekle
                System.out.println("Sol ok elementine tıklandı");

                System.out.println("Date picker işlemleri tamamlandı.");
            } catch (Exception e) {
                System.err.println("Date picker işlemleri sırasında hata oluştu: " + e.getMessage());
                e.printStackTrace();
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }


            try {
                WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(10));
                WebElement rightDateNav = wait2.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//span[@class='date-right-coll' and @onclick='gotoDate(+1);']")));
                System.out.println("Clicking on right date navigation...");
                rightDateNav.click();
            } catch (Exception e) {
                System.out.println("Regular click failed, trying alternative methods...");

                // Solution 2: Use JavaScript click if regular click fails
                try {
                    WebElement rightDateNav = driver.findElement(By.xpath("//span[@class='date-right-coll' and @onclick='gotoDate(+1);']"));
                    JavascriptExecutor js3 = (JavascriptExecutor) driver;
                    js3.executeScript("arguments[0].click();", rightDateNav);
                    System.out.println("JavaScript click succeeded");
                } catch (Exception e2) {
                    System.out.println("JavaScript click failed, trying direct function call...");

                    // Solution 3: Execute the JavaScript function directly
                    JavascriptExecutor js2 = (JavascriptExecutor) driver;
                    js2.executeScript("gotoDate(+1);");
                    System.out.println("Direct function call executed");
                }
            }

            // Wait a bit to see the effect
            Thread.sleep(2000);

            // 2. Then click on the left date navigation element
            WebElement leftDateNav = driver.findElement(By.cssSelector("span.date-left-coll[onclick='gotoDate(-1);']"));
            System.out.println("Clicking on left date navigation...");
            leftDateNav.click();

            Thread.sleep(1000);

            // 2. "Futbol" sekmesinin seçili olduğundan emin ol
            try {
                WebElement futbolCheckbox = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("chkSport1")));
                wait.until(ExpectedConditions.elementToBeClickable(futbolCheckbox));
                if (!futbolCheckbox.getAttribute("class").contains("selected")) {
                    System.out.println("Futbol seçili değil, tıklanıyor...");
                    js.executeScript("arguments[0].click();", futbolCheckbox);
                    wait.until(ExpectedConditions.attributeContains(futbolCheckbox, "class", "selected"));
                    System.out.println("Futbol sekmesi seçildi.");
                    Thread.sleep(1000); // İçeriğin yüklenmesi için bekle
                } else {
                    System.out.println("Futbol sekmesi zaten seçili.");
                }
            } catch (Exception e) {
                System.err.println("Hata: Futbol sekmesi (chkSport1) ile ilgili işlem yapılamadı! " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // 3. "Basketbol" sekmesi seçiliyse, seçimini kaldır
            try {
                WebElement basketbolCheckbox = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("chkSport2")));
                wait.until(ExpectedConditions.elementToBeClickable(basketbolCheckbox));
                if (basketbolCheckbox.getAttribute("class").contains("selected")) {
                    System.out.println("Basketbol seçili, seçimi kaldırmak için tıklanıyor...");
                    js.executeScript("arguments[0].click();", basketbolCheckbox);
                    wait.until(ExpectedConditions.not(ExpectedConditions.attributeContains(basketbolCheckbox, "class", "selected")));
                    System.out.println("Basketbol sekmesinin seçimi kaldırıldı.");
                    Thread.sleep(1000); // İçeriğin yüklenmesi için bekle
                } else {
                    System.out.println("Basketbol sekmesi zaten seçili değil.");
                }
            } catch (Exception e) {
                System.err.println("Uyarı: Basketbol sekmesi (chkSport2) ile ilgili işlem yapılamadı! " + e.getMessage());
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }

            // 4. YENİ: "İDDAA" checkbox'ının seçili olduğundan emin ol
            try {
                WebElement iddaaCheckbox = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("chkIddaa")));
                wait.until(ExpectedConditions.elementToBeClickable(iddaaCheckbox));
                // IDDAA checkbox'ının 'selected' class'ı YOKSA tıkla
                if (!iddaaCheckbox.getAttribute("class").contains("selected")) {
                    System.out.println("İDDAA checkbox'ı seçili değil, tıklanıyor...");
                    js.executeScript("arguments[0].click();", iddaaCheckbox);
                    wait.until(ExpectedConditions.attributeContains(iddaaCheckbox, "class", "selected"));
                    System.out.println("İDDAA checkbox'ı seçildi.");
                    Thread.sleep(1500); // Filtrelemenin uygulanması için bekle
                } else {
                    System.out.println("İDDAA checkbox'ı zaten seçili.");
                }
            } catch (Exception e) {
                System.err.println("Hata: İDDAA checkbox'ı (chkIddaa) ile ilgili işlem yapılamadı! " + e.getMessage());
                e.printStackTrace();
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                // Devam etmeyi deneyebiliriz, ama sonuçlar İDDAA filtresiz olabilir
            }

            // 5. "Tarihe Göre" butonuna tıkla (Eğer zaten seçili değilse)
            WebElement tariheGoreButton = null;
            try {
                tariheGoreButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("aOrderByDate")));
                wait.until(ExpectedConditions.elementToBeClickable(tariheGoreButton));
                if (!tariheGoreButton.getAttribute("class").contains("selected")) {
                    System.out.println("'Tarihe Göre' butonuna tıklanıyor...");
                    js.executeScript("arguments[0].click();", tariheGoreButton);
                    wait.until(ExpectedConditions.attributeContains(tariheGoreButton, "class", "selected"));
                    System.out.println("'Tarihe Göre' butonuna tıklandı ve seçildi.");
                    Thread.sleep(1500); // Filtrelemenin tam uygulanması için bekle
                } else {
                    System.out.println("'Tarihe Göre' butonu zaten seçili.");
                }
                // İçeriğin yüklendiğini doğrulamak için bekleme (en az bir futbol maçı satırı)
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='dvScores']//tr[@sport='1']")));
                System.out.println("'Tarihe Göre' filtresi aktif ve futbol içeriği mevcut.");

            } catch (Exception e) {
                System.err.println("Hata: 'Tarihe Göre' butonu (aOrderByDate) ile ilgili işlem yapılamadı! " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // 6. GÜNCELLENMİŞ XPath: Sadece sport="1" VE mac-status-0 olan satırlardaki takım linklerini bul
            String upcomingFootballTeamLinkXPath = "//div[@id='dvScores']//tr[@sport='1' and contains(@class, 'mac-status-0')]//div[@class='teamDiv']/a[contains(@href, '/Takim/')]";
            List<WebElement> upcomingFootballTeamLinks;

            // Elementlerin yüklenmesi için son bir bekleme (mac-status-0'lı ilk satırın görünür olmasını bekle)
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@id='dvScores']//tr[@sport='1' and contains(@class, 'mac-status-0')]")));
                System.out.println("Başlamamış futbol maçları bulundu.");
                upcomingFootballTeamLinks = driver.findElements(By.xpath(upcomingFootballTeamLinkXPath));
            } catch (TimeoutException e) {
                System.out.println("Belirtilen filtrelerle (futbol, başlamamış, iddaa, tarihe göre) maç bulunamadı.");
                upcomingFootballTeamLinks = new ArrayList<>(); // Boş liste ata
            }

            System.out.println("Toplam " + upcomingFootballTeamLinks.size() + " adet başlamamış futbol takımı linki işlenecek.");

            // 7. Her linkin href'inden takım ID'sini çıkar ve Set'e ekle
            int linkCounter = 0;
            for (WebElement link : upcomingFootballTeamLinks) {
                linkCounter++;
                try {
                    String href = link.getAttribute("href");
                    if (href != null && href.contains("/Takim/")) {
                        String partAfterTakim = href.substring(href.indexOf("/Takim/") + "/Takim/".length());
                        String potentialId = partAfterTakim.split("/")[0];

                        if (potentialId.matches("\\d+")) {
                            upcomingFootballTeamIds.add(potentialId);
                        } else {
                            System.out.println("Uyarı (Link " + linkCounter + "): ID adayı sayısal değil: '" + potentialId + "' href içinde: " + href);
                        }
                    } else if (href != null) {
                        System.out.println("Uyarı (Link " + linkCounter + "): Link '/Takim/' içermiyor: " + href);
                    }
                } catch (StaleElementReferenceException sere) {
                    System.out.println("StaleElementReferenceException (Link " + linkCounter + "): Link atlanıyor.");
                } catch (Exception e) {
                    System.out.println("Bir link işlenirken hata oluştu (Link " + linkCounter + "): " + e.getMessage());
                    try { System.out.println(" -> Sorunlu link metni: " + link.getText()); } catch (Exception innerEx) { /* ignore */ }
                    try { System.out.println(" -> Sorunlu link href: " + link.getAttribute("href")); } catch (Exception innerEx) { /* ignore */ }
                }
            }

            // 8. Toplanan benzersiz ID'leri yazdır
            System.out.println("\n--- Bulunan Başlamamış Futbol Takımı ID'leri (Benzersiz ve Filtrelenmiş) ---");
            if (upcomingFootballTeamIds.isEmpty()) {
                System.out.println("Listede hiç başlamamış futbol takımı ID'si bulunamadı.");
            } else {
                // ID'leri sıralayıp yazdır
                List<String> sortedIds = new ArrayList<>(upcomingFootballTeamIds);
                try {
                    // ID'leri integer'a çevirip sırala
                    sortedIds.sort(Comparator.comparingInt(Integer::parseInt));
                } catch (NumberFormatException nfe) {
                    System.err.println("Uyarı: ID listesi sıralanamadı, bazı ID'ler sayısal olmayabilir.");
                    // Sayısal olmayanları ayıklayarak sıralamayı deneyebilir veya alfabetik sıralayabiliriz.
                    // Şimdilik doğal (eklenme) sırasıyla yazdıralım.
                    sortedIds = new ArrayList<>(upcomingFootballTeamIds);
                }

                for (String id : sortedIds) {
                    System.out.println(id);
                }
                System.out.println("Toplam " + upcomingFootballTeamIds.size() + " adet benzersiz başlamamış futbol takımı ID'si bulundu.");
            }

        } catch (Exception e) {
            System.err.println("Ana işlem sırasında bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("Tarayıcı kapatıldı.");
            }
        }
        writeIdsToFile();
    }

    public static void writeIdsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(txt))) {
            // ID'leri virgülle birleştirip dosyaya yaz
            String ids = String.join(", ", upcomingFootballTeamIds);
            writer.write(ids);
            System.out.println("Takım ID'leri dosyaya yazıldı: " + txt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> readIdsFromFile() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(txt))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                return Arrays.asList(line.split(",\\s*"));
            }
        }
        return List.of();
    }
}