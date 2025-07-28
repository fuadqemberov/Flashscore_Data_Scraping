package flashscore.weeklydatascraping.aiscore;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MatchAnalyzer implements Runnable {
    private final String matchBaseUrl;

    public MatchAnalyzer(String matchUrl) {
        this.matchBaseUrl = matchUrl;
    }

    @Override
    public void run() {
        // Her thread için de logları sustur
        suppressWarnings();

        ChromeOptions options = AiscoreScraper.createChromeOptions();
        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            String oddsUrl = matchBaseUrl + "/odds";

            driver.get(oddsUrl);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.newOdds")));

            List<WebElement> companyRows = driver.findElements(By.cssSelector("div.content[data-v-07c8a370] > div.flex.w100"));
            WebElement bet365Row = null;
            for (WebElement row : companyRows) {
                try {
                    WebElement logo = row.findElement(By.cssSelector("img.logo"));
                    if (logo.getAttribute("src").contains("fe8aec51afeb2de633c9")) {
                        bet365Row = row;
                        break;
                    }
                } catch (Exception e) { /* Logo yoksa devam et */ }
            }

            if (bet365Row == null) return;

            String matchTitle = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1.text"))).getText().split(" betting odds")[0].trim();

            WebElement openingOddsContainer = bet365Row.findElement(By.cssSelector("div.openingBg1 .oddsItemBox"));
            List<WebElement> openingOddsElements = openingOddsContainer.findElements(By.cssSelector("div.oddItems span"));

            WebElement preMatchOddsContainer = bet365Row.findElement(By.cssSelector("div.preMatchBg1 .oddsItemBox"));
            List<WebElement> preMatchOddsElements = preMatchOddsContainer.findElements(By.cssSelector("div.oddItems span"));

            if (openingOddsElements.size() < 3 || preMatchOddsElements.size() < 3) return;

            double open1 = parseDouble(openingOddsElements.get(1).getText());
            double openX = parseDouble(openingOddsElements.get(2).getText());
            double open2 = parseDouble(openingOddsElements.get(4).getText());

            double pre1 = parseDouble(preMatchOddsElements.get(1).getText());
            double preX = parseDouble(preMatchOddsElements.get(2).getText());
            double pre2 = parseDouble(preMatchOddsElements.get(4).getText());

            if (open1 == 0.0 || pre1 == 0.0) return;

            checkPatterns(matchTitle, oddsUrl, open1, openX, open2, pre1, preX, pre2);

        } catch (Exception e) {
            // Hata durumlarında artık konsola hiçbir şey yazdırmıyoruz
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private void suppressWarnings() {
        // Her yeni thread için de uyarıları sustur
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder").setLevel(Level.OFF);
        Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver").setLevel(Level.OFF);
    }

    private void checkPatterns(String matchTitle, String url, double o1, double oX, double o2, double p1, double pX, double p2) {
        String report = "";

        if (p1 == 2.62 || p2 == 2.62) {
            report += String.format("    -> PATTERN 1 - SİHİRLİ 2.62%n");
        }
        if (pX == p2 || p1 == pX) {
            report += String.format("    -> PATTERN 2 - ORAN TEKRARI%n");
        }
        boolean homeWasFavoriteLostIt = (o1 < o2) && (p1 > p2);
        boolean awayWasFavoriteLostIt = (o2 < o1) && (p2 > p1);
        if (homeWasFavoriteLostIt || awayWasFavoriteLostIt) {
            report += String.format("    -> PATTERN 3 - FAVORİ DEĞİŞİMİ%n");
        }
        boolean lowHighPattern1 = (p1 >= 1.50 && p1 <= 1.59) && (pX >= 4.00) && (p2 >= 5.00);
        boolean lowHighPattern2 = (p2 >= 1.50 && p2 <= 1.59) && (pX >= 4.00) && (p1 >= 5.00);
        if(lowHighPattern1 || lowHighPattern2) {
            report += String.format("    -> PATTERN 4 - DÜŞÜK FAVORİ / YÜKSEK SÜRPRİZ%n");
        }
        boolean newPatternHomeFav = (p1 >= 1.60 && p1 <= 1.69) && (pX >= 4.00) && (p2 >= 4.70 && p2 <= 4.79);
        boolean newPatternAwayFav = (p2 >= 1.60 && p2 <= 1.69) && (pX >= 4.00) && (p1 >= 4.70 && p1 <= 4.79);
        if(newPatternHomeFav || newPatternAwayFav) {
            report += String.format("    -> PATTERN 5 - 1.6x / 4.xx / 4.7x ORANI%n");
        }

        if (!report.isEmpty()) {
            synchronized (System.out) {
                System.out.println("\n------------------ [!] PATTERN(LER) BULUNDU: " + matchTitle + " ------------------");
                System.out.print(report);
                System.out.printf("    URL: %s%n", url);
                System.out.printf("    Açılış Oranları: %.2f | %.2f | %.2f%n", o1, oX, o2);
                System.out.printf("    Kapanış Oranları: %.2f | %.2f | %.2f%n", p1, pX, p2);
                System.out.println("----------------------------------------------------------------------------------\n");
            }
        }
    }

    private double parseDouble(String text) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                return Double.parseDouble(text.trim().replace(',', '.'));
            }
        } catch (NumberFormatException e) {}
        return 0.0;
    }
}