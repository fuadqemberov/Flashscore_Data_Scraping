package analyzer.nowgoal;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class NowGoalOddsAnalyze {

    static String baseHost = "https://live5.nowgoal26.com/";
    static int geriyeGidilecekSezonSayisi = 10;
    static final Semaphore BROWSER_SEMAPHORE = new Semaphore(5);

    public static void main(String[] args) {
        // Logları söndürmək
        LogManager.getLogManager().reset();
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        System.setProperty("webdriver.chrome.silentOutput", "true");

        WebDriverManager.chromedriver().setup();

        List<String> ligIdListesi = getLeagueIdsFromHome();
        System.out.println(">>> Analiz başladı. Liqa sayı: " + ligIdListesi.size() + "\n");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String ligId : ligIdListesi) {
                executor.submit(() -> {
                    try {
                        BROWSER_SEMAPHORE.acquire();
                        try {
                            analizLig(ligId);
                        } finally {
                            BROWSER_SEMAPHORE.release();
                        }
                    } catch (Exception ignored) {}
                });
            }
        }
        System.out.println("\n>>> Analiz tamamlandı.");
    }

    public static void analizLig(String ligId) throws InterruptedException {
        ChromeOptions options = getOptions();
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        String leagueUrl = "https://football.nowgoal26.com/league/" + ligId;

        try {
            driver.get(leagueUrl);
            Thread.sleep(4000);

            // 1. HAFTA 1 KONTROLU
            WebElement roundActive = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#schedule .round .current.on")));
            int targetWeek = Integer.parseInt(roundActive.getAttribute("round"));
            if (targetWeek <= 1) return;

            String ligAdi = driver.findElement(By.id("title")).getText();

            // 2. 1X2 EMSALLARINI AKTIV ET
            set1X2View(driver);

            // 3. OYNANMAMISH MACLARI VE EMSALLARINI YADDASHA AL
            List<String[]> targetMatches = new ArrayList<>();
            List<WebElement> rows = driver.findElements(By.className("schedulis"));
            for (WebElement row : rows) {
                if (row.findElement(By.className("score")).getText().trim().equals("-")) {
                    String h = row.findElement(By.cssSelector(".home span")).getText().trim();
                    String a = row.findElement(By.cssSelector(".away span")).getText().trim();
                    String odds = getOddsText(row);
                    targetMatches.add(new String[]{h, a, odds});
                }
            }

            if (targetMatches.isEmpty()) return;

            // Cari H-1 Köprüleri
            Map<String, String> currentH1Map = getRoundData(driver, targetWeek - 1);

            for (String[] match : targetMatches) {
                String ev = match[0];
                String dep = match[1];
                String curOdds = match[2];
                String b1 = currentH1Map.get(ev);
                String b2 = currentH1Map.get(dep);

                if (b1 == null || b2 == null) continue;

                for (int s = 1; s <= geriyeGidilecekSezonSayisi; s++) {
                    checkHistory(driver, s, targetWeek, b1, b2, ev, dep, curOdds, ligAdi, leagueUrl);
                }
            }
        } finally {
            driver.quit();
        }
    }

    public static void checkHistory(WebDriver d, int sIdx, int week, String b1, String b2, String cEv, String cDep, String cOdds, String ligNm, String lUrl) {
        try {
            d.get(lUrl);
            Thread.sleep(3000);

            List<WebElement> seasonList = d.findElements(By.cssSelector("#seasonBox ul li"));
            if (sIdx >= seasonList.size()) return;

            WebElement sEl = seasonList.get(sIdx);
            String sLabel = sEl.getAttribute("innerText").trim();
            String sVal = sEl.getAttribute("onclick").replace("changeSeason('", "").replace("')", "");

            ((JavascriptExecutor) d).executeScript("changeSeason('" + sVal + "')");
            Thread.sleep(5000);

            // Sezon daxilində 1X2 aktiv et
            set1X2View(d);

            Map<String, String> pastH1 = getRoundData(d, week - 1);
            String oldA = pastH1.get(b1);
            String oldB = pastH1.get(b2);

            if (oldA != null && oldB != null) {
                WebElement rBtn = d.findElement(By.cssSelector("span[round='" + week + "']"));
                ((JavascriptExecutor) d).executeScript("arguments[0].click();", rBtn);
                Thread.sleep(3000);

                List<WebElement> rows = d.findElements(By.className("schedulis"));
                for (WebElement row : rows) {
                    String h = row.findElement(By.cssSelector(".home span")).getText().trim();
                    String a = row.findElement(By.cssSelector(".away span")).getText().trim();

                    if ((h.equalsIgnoreCase(oldA) && a.equalsIgnoreCase(oldB)) || (h.equalsIgnoreCase(oldB) && a.equalsIgnoreCase(oldA))) {
                        String ftScore = row.findElement(By.className("score")).getText();
                        String matchId = row.getAttribute("matchid");
                        String pastOdds = getOddsText(row);

                        // HT Score üçün detallı səhifəyə
                        String htVal = getHTResult(d, matchId);

                        synchronized (System.out) {
                            System.out.println("\n******************************************************************");
                            System.out.println("[LİG]: " + ligNm + " | [HAFTA]: " + week);
                            System.out.println("[GÜNCEL OYUN]: " + cEv + " vs " + cDep + " | 1X2: " + cOdds);
                            System.out.println("[KÖPRÜLƏR]: " + b1 + " & " + b2);
                            System.out.println("[TAPILAN SEZON]: " + sLabel);
                            System.out.println("[KEÇMİŞ NƏTİCƏ]: " + h + " " + ftScore + " " + a + " | HT: (" + htVal + ") | 1X2: " + pastOdds);
                            System.out.println("******************************************************************");
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // 1X2 Görünüşünü seçən funksiya
    private static void set1X2View(WebDriver d) {
        try {
            WebElement oddsBox = d.findElement(By.cssSelector(".odds.selectbox"));
            ((JavascriptExecutor) d).executeScript("arguments[0].click();", oddsBox);
            Thread.sleep(1000);
            WebElement option1X2 = d.findElement(By.cssSelector("li[type='O']"));
            ((JavascriptExecutor) d).executeScript("arguments[0].click();", option1X2);
            Thread.sleep(2000);
        } catch (Exception e) {}
    }

    // Satırdan 1X2 əmsallarını çəkən funksiya
    private static String getOddsText(WebElement row) {
        try {
            List<WebElement> oddSpans = row.findElements(By.cssSelector(".odds span"));
            if (oddSpans.size() >= 3) {
                return "[" + oddSpans.get(0).getText() + " - " + oddSpans.get(1).getText() + " - " + oddSpans.get(2).getText() + "]";
            }
        } catch (Exception e) {}
        return "[N/A]";
    }

    private static String getHTResult(WebDriver d, String mId) {
        try {
            d.get(baseHost + "match/live-" + mId);
            WebDriverWait wait = new WebDriverWait(d, Duration.ofSeconds(12));
            WebElement htEl = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//span[@title='Score 1st Half']")));
            return htEl.getText().trim();
        } catch (Exception e) { return "N/A"; }
    }

    public static Map<String, String> getRoundData(WebDriver d, int round) {
        Map<String, String> map = new HashMap<>();
        try {
            WebElement rBtn = d.findElement(By.cssSelector("span[round='" + round + "']"));
            ((JavascriptExecutor) d).executeScript("arguments[0].click();", rBtn);
            Thread.sleep(3000);
            List<WebElement> rows = d.findElements(By.className("schedulis"));
            for (WebElement row : rows) {
                String h = row.findElement(By.cssSelector(".home span")).getText().trim();
                String a = row.findElement(By.cssSelector(".away span")).getText().trim();
                map.put(h, a);
                map.put(a, h);
            }
        } catch (Exception e) {}
        return map;
    }

    private static List<String> getLeagueIdsFromHome() {
        ChromeOptions options = getOptions();
        WebDriver driver = new ChromeDriver(options);
        List<String> ids = new ArrayList<>();
        try {
            driver.get(baseHost);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // Hot butonuna tıkla
            WebElement hotFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("li_FilterHot")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", hotFilter);
            Thread.sleep(3000);

            // Tüm lig satırlarını al
            List<WebElement> ligler = driver.findElements(By.cssSelector(".Leaguestitle.fbHead"));
            System.out.println("Toplam lig (DOM'da): " + ligler.size());

            for (WebElement lig : ligler) {
                boolean displayed = lig.isDisplayed();
                String id = lig.getAttribute("sclassid");
                if (displayed && id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }

            System.out.println("Görünür (Hot) lig sayısı: " + ids.size());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
        return ids;
    }

    private static ChromeOptions getOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-notifications", "--log-level=3", "--disable-gpu", "--no-sandbox", "--silent");
        options.addArguments("--blink-settings=imagesEnabled=false");
        return options;
    }
}