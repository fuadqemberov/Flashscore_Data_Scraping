package flashscore.weeklydatascraping;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OddsportalOpeningOdds {

    public static void main(String[] args) {
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        Actions actions = new Actions(driver);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            // Sayfayı aç
            driver.get("https://www.oddsportal.com/football/world/world-cup-2026/northern-ireland-germany-AmorliEa/#1X2;2");
            Thread.sleep(5000); // Yüklenme bekle

            // 1xBet satırını bul
            System.out.println("🔍 1xBet satırı aranıyor...");
            List<WebElement> bookmakerRows = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                    By.cssSelector("div.flex.h-9.border-b")));
            WebElement xbetRow = null;

            for (WebElement row : bookmakerRows) {
                String html = row.getAttribute("innerHTML").toLowerCase();
                if (html.contains("1xbet")) {
                    xbetRow = row;
                    System.out.println("✓ 1xBet satırı bulundu!");
                    break;
                }
            }

            if (xbetRow == null) {
                System.out.println("✗ 1xBet satırı bulunamadı!");
                return;
            }

            // Güncel oranları al
            List<WebElement> oddsContainers = xbetRow.findElements(By.cssSelector("div[data-testid='odd-container']"));
            if (oddsContainers.size() < 3) {
                System.out.println("✗ Yeterli oran bulunamadı!");
                return;
            }

            System.out.println("\n=== GÜNCEL ORANLAR ===");
            String[] currentOdds = new String[3];
            String[] labels = {"1 (Ev Sahibi)", "X (Beraberlik)", "2 (Deplasman)"};

            for (int i = 0; i < 3; i++) {
                currentOdds[i] = oddsContainers.get(i).findElement(By.cssSelector("p.odds-text")).getText();
                System.out.println(labels[i] + ": " + currentOdds[i]);
            }

            // Açılış oranlarını al (WebHarvy-inspired: JS mouseover + regex)
            System.out.println("\n=== 1xBET AÇILIŞ ORANLARI (WebHarvy Yöntemiyle) ===");
            String[] openingOdds = new String[3];

            for (int i = 0; i < 3; i++) {
                try {
                    WebElement oddCell = oddsContainers.get(i);
                    String currentOdd = currentOdds[i];

                    // Elemente scroll et
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", oddCell);
                    Thread.sleep(1500);

                    // WebHarvy gibi: JS ile mouseover tetikle
                    System.out.println("⏳ " + labels[i] + " için popup tetikleniyor (JS)...");
                    js.executeScript(
                            "var event = new MouseEvent('mouseover', {bubbles: true, cancelable: true, view: window});" +
                                    "arguments[0].dispatchEvent(event);",
                            oddCell
                    );
                    actions.moveToElement(oddCell).perform();
                    Thread.sleep(4000); // Popup yüklenmesi için

                    // Popup'ı bekle (geniş XPath)
                    WebElement popup = wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'opening odds') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'odds movement')]")
                    ));
                    System.out.println("✓ Popup açıldı!");
                    Thread.sleep(1500);

                    // Popup metnini al ve regex ile parse et
                    String popupText = popup.getText();
                    System.out.println("Popup Metni: " + popupText); // Debug için

                    // Regex: "Opening odds: DD MMM, HH:MM X.XX" formatı
                    Pattern pattern = Pattern.compile("Opening odds:\\s*\\d{2}\\s+[A-Za-z]{3},\\s*\\d{2}:\\d{2}\\s*(\\d+\\.\\d+)");
                    Matcher matcher = pattern.matcher(popupText);
                    if (matcher.find()) {
                        openingOdds[i] = matcher.group(1);
                        System.out.println("✓ " + labels[i] + " Açılış Oranı: " + openingOdds[i]);
                    } else {
                        // Alternatif: Herhangi bir oran bul (güncelden farklı)
                        Pattern oddPattern = Pattern.compile("\\b(\\d+\\.\\d+)\\b");
                        Matcher oddMatcher = oddPattern.matcher(popupText);
                        while (oddMatcher.find()) {
                            String foundOdd = oddMatcher.group(1);
                            if (!foundOdd.equals(currentOdd) && Double.parseDouble(foundOdd) > 0) {
                                openingOdds[i] = foundOdd;
                                System.out.println("✓ " + labels[i] + " Açılış Oranı (alternatif): " + openingOdds[i]);
                                break;
                            }
                        }
                        if (openingOdds[i] == null) {
                            System.out.println("✗ " + labels[i] + " için oran parse edilemedi!");
                        }
                    }

                    // Popup'ı kapat
                    js.executeScript(
                            "var event = new MouseEvent('mouseout', {bubbles: true, cancelable: true, view: window});" +
                                    "arguments[0].dispatchEvent(event);",
                            oddCell
                    );
                    actions.moveByOffset(100, 100).perform();
                    Thread.sleep(2000);

                } catch (Exception e) {
                    System.out.println("✗ " + labels[i] + " hatası: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Sonuç özeti
            System.out.println("\n" + "=".repeat(50));
            System.out.println("ÖZET - 1xBET ORANLARI (WebHarvy Uyarlaması)");
            System.out.println("=".repeat(50));
            for (int i = 0; i < 3; i++) {
                System.out.printf("%-20s | Güncel: %-6s | Açılış: %s%n",
                        labels[i], currentOdds[i], openingOdds[i] != null ? openingOdds[i] : "Bulunamadı");
            }
            System.out.println("=".repeat(50));

        } catch (Exception e) {
            System.out.println("Genel hata: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\n⏸ Tarayıcı 5 saniye içinde kapanacak...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
            driver.quit();
        }
    }
}