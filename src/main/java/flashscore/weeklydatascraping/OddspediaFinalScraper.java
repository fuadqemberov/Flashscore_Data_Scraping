package flashscore.weeklydatascraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;

public class OddspediaFinalScraper {

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Actions actions = new Actions(driver);

        String[] targetMarkets = {"Full Time Result", "Total Goals", "Both Teams to Score", "Double Chance"};

        try {
            List<WebElement> elements = driver.findElements(By.cssSelector("a.match-url"));
            Set<String> urls = new LinkedHashSet<>();
            for (WebElement el : elements) {
                if (el.getAttribute("href") != null)
                    urls.add(el.getAttribute("href").split("/predictions")[0]);
            }

            for (String url : urls) {
                try {
                    System.out.println("\n===== MAÇ: " + url + " =====");
                    driver.get(url);
                    Thread.sleep(2000);

                    WebElement oddsTab = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(@class,'page-nav-item__link') and contains(.,'Odds')]")));
                    actions.moveToElement(oddsTab).click().perform();
                    Thread.sleep(1500);

                    WebElement compareBtn = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[.//span[contains(text(),'Compare odds')]]")));
                    actions.moveToElement(compareBtn).click().perform();
                    Thread.sleep(1500);

                    for (String marketName : targetMarkets) {
                        try {
                            System.out.println("--- Market seçiliyor: " + marketName);
                            boolean marketSelected = selectMarket(driver, wait, js, actions, marketName);
                            if (!marketSelected) {
                                System.out.println("    ATLANDI: " + marketName);
                                continue;
                            }
                            Thread.sleep(1500);

                            if (marketName.equals("Total Goals")) {
                                processTotalGoals(driver, wait, js, actions);
                            } else {
                                processPeriods(driver, wait, js, actions, marketName, "");
                            }

                        } catch (Exception e) {
                            System.out.println("Market hatası: " + marketName + " -> " + e.getMessage());
                            try { driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE); } catch (Exception ignored) {}
                        }
                    }

                } catch (Exception e) {
                    System.out.println("Maç hatası: " + url + " -> " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Market dropdown seçimi ──
    private static boolean selectMarket(WebDriver driver, WebDriverWait wait,
                                        JavascriptExecutor js, Actions actions, String marketName) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement marketDD = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.id("matchup-odds-comparison-nav-market-dd")));
                js.executeScript("arguments[0].scrollIntoView({block:'center'});", marketDD);
                Thread.sleep(500);

                if (attempt == 1) {
                    js.executeScript("arguments[0].click();", marketDD);
                } else if (attempt == 2) {
                    actions.moveToElement(marketDD).click().perform();
                } else {
                    js.executeScript("arguments[0].dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}));", marketDD);
                }
                Thread.sleep(1000);

                List<WebElement> items = driver.findElements(
                        By.xpath("//div[contains(@class,'dropdown__menu') and not(contains(@style,'display: none'))]" +
                                "//div[contains(@class,'dropdown-menu-item')]"));

                if (items.isEmpty()) {
                    System.out.println("    Dropdown açılmadı, deneme " + attempt);
                    continue;
                }

                WebElement targetItem = null;
                for (WebElement item : items) {
                    if (item.getText().trim().equals(marketName)) {
                        targetItem = item;
                        break;
                    }
                }

                if (targetItem == null) {
                    driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
                    return false;
                }

                js.executeScript("arguments[0].scrollIntoView({block:'center'});", targetItem);
                Thread.sleep(200);
                js.executeScript("arguments[0].click();", targetItem);
                Thread.sleep(500);
                return true;

            } catch (Exception e) {
                System.out.println("    selectMarket deneme " + attempt + " hata: " + e.getMessage());
            }
        }
        return false;
    }

    // ── Line dropdown seçimi ──
    private static boolean selectLine(WebDriver driver, WebDriverWait wait,
                                      JavascriptExecutor js, String line) {
        try {
            WebElement lineDD = wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("matchup-odds-comparison-nav-handicap-dd")));
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", lineDD);
            Thread.sleep(300);
            js.executeScript("arguments[0].click();", lineDD);
            Thread.sleep(800);

            List<WebElement> items = driver.findElements(
                    By.xpath("//div[@aria-labelledby='matchup-odds-comparison-nav-handicap-dd']" +
                            "//div[contains(@class,'dropdown-menu-item')]"));

            for (WebElement item : items) {
                if (item.getText().trim().equals(line)) {
                    js.executeScript("arguments[0].click();", item);
                    Thread.sleep(1200);
                    System.out.println("      Line seçildi: " + line);
                    return true;
                }
            }

            // Bulunamazsa kapat
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            Thread.sleep(300);
            System.out.println("      Line bulunamadı: " + line);
            return false;

        } catch (Exception e) {
            System.out.println("      selectLine hatası: " + line + " -> " + e.getMessage());
            return false;
        }
    }

    // ── Period butonu tıklama ──
    private static boolean selectPeriod(WebDriver driver, WebDriverWait wait,
                                        JavascriptExecutor js, String period) {
        try {
            WebElement prdBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[./span[@data-text='" + period + "']]")));
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", prdBtn);
            Thread.sleep(300);
            js.executeScript("arguments[0].click();", prdBtn);
            Thread.sleep(1500);

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[contains(@class,'matchup-odds-comparison-table-row')]")));
            return true;
        } catch (Exception e) {
            System.out.println("      Period seçilemedi: " + period + " -> " + e.getMessage());
            return false;
        }
    }

    // ── Bet365 opening odd'ını çek ──
    private static void fetchBet365Odds(WebDriver driver, WebDriverWait wait,
                                        JavascriptExecutor js, String period, String info) {
        try {
            List<WebElement> b365Buttons = driver.findElements(
                    By.xpath("//div[contains(@class,'matchup-odds-comparison-table-row') and " +
                            ".//img[contains(@alt,'bet365')]]//button[contains(@class,'movement-button')]"));

            if (b365Buttons.isEmpty()) {
                System.out.println("      [" + period + "] Bet365 bulunamadi.");
                return;
            }

            WebElement b365Btn = b365Buttons.get(0);
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", b365Btn);
            Thread.sleep(300);
            js.executeScript("arguments[0].click();", b365Btn);

            WebElement activeModal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[contains(@class,'old-modal') and .//div[text()='Odds movement']]")));

            wait.until(d -> {
                List<WebElement> check = activeModal.findElements(
                        By.className("matchup-odd-movement__odd-box--opening"));
                return !check.isEmpty() && !check.get(0).getText().trim().isEmpty();
            });

            List<WebElement> labels   = activeModal.findElements(By.cssSelector(".color-white.font-brand.fs-400"));
            List<WebElement> openings = activeModal.findElements(By.className("matchup-odd-movement__odd-box--opening"));

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < openings.size(); i++) {
                String label = (i < labels.size()) ? labels.get(i).getText().trim() : "Unknown";
                String val   = openings.get(i).getText().trim();
                if (!val.isEmpty()) result.append(label).append(": ").append(val).append(" | ");
            }

            System.out.println("      [" + period + "] " + info + " -> " + result.toString());

            WebElement closeBtn = activeModal.findElement(By.xpath(".//button[@data-e2e='modal-close-btn']"));
            js.executeScript("arguments[0].click();", closeBtn);
            wait.until(ExpectedConditions.invisibilityOf(activeModal));
            Thread.sleep(800);

        } catch (Exception e) {
            System.out.println("      [" + period + "] fetchBet365 hata: " + e.getMessage());
        }
    }

    private static void processTotalGoals(WebDriver driver, WebDriverWait wait,
                                          JavascriptExecutor js, Actions actions) {
        // 0.5 ve 1.5 -> Full Time + 1st Half + 2nd Half
        // 2.5, 3.5, 4.5 -> sadece Full Time
        // Her period değişiminde line dropdown'dan tekrar seçim zorunlu!

        String[] fullOnlyLines = {"2.5", "3.5", "4.5"};
        String[] allPeriodLines = {"0.5", "1.5"};

        // ── GRUP 1: 0.5 ve 1.5 → Full Time + 1st Half + 2nd Half ──
        for (String line : allPeriodLines) {
            System.out.println("   === Total Goals Line: " + line + " ===");

            // ── Full Time ──
            System.out.println("      Период: Full Time");
            if (selectPeriod(driver, wait, js, "Full Time")) {
                if (selectLine(driver, wait, js, line)) {
                    fetchBet365Odds(driver, wait, js, "Full Time", "Line " + line);
                }
            }

            // ── 1st Half ──
            // Önce period değiştir, SONRA line'ı tekrar seç
            System.out.println("      Период: 1st Half");
            if (selectPeriod(driver, wait, js, "1st Half")) {
                // Period değişti -> line dropdown sıfırlandı -> tekrar seç
                if (selectLine(driver, wait, js, line)) {
                    fetchBet365Odds(driver, wait, js, "1st Half", "Line " + line);
                }
            }

            // ── 2nd Half ──
            System.out.println("      Период: 2nd Half");
            if (selectPeriod(driver, wait, js, "2nd Half")) {
                // Period değişti -> line dropdown sıfırlandı -> tekrar seç
                if (selectLine(driver, wait, js, line)) {
                    fetchBet365Odds(driver, wait, js, "2nd Half", "Line " + line);
                }
            }
        }

        // ── GRUP 2: 2.5, 3.5, 4.5 → Sadece Full Time ──
        for (String line : fullOnlyLines) {
            System.out.println("   === Total Goals Line: " + line + " (sadece Full Time) ===");

            if (selectPeriod(driver, wait, js, "Full Time")) {
                if (selectLine(driver, wait, js, line)) {
                    fetchBet365Odds(driver, wait, js, "Full Time", "Line " + line);
                }
            }
        }
    }

    private static void processPeriods(WebDriver driver, WebDriverWait wait,
                                       JavascriptExecutor js, Actions actions,
                                       String market, String info) {
        String[] periods = {"Full Time", "1st Half", "2nd Half"};
        for (String prd : periods) {
            if (selectPeriod(driver, wait, js, prd)) {
                fetchBet365Odds(driver, wait, js, prd, info);
            }
        }
    }
}