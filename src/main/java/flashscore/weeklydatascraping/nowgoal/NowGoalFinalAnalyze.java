package flashscore.weeklydatascraping.nowgoal;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NowGoalFinalAnalyze {

    static String baseHost = "https://live5.nowgoal26.com/";
    static int geriyeGidilecekSezonSayisi = 5; // Sezon sayını buradan tənzimlə

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();

        List<String> ligIdListesi = getLeagueIdsFromHome();
        System.out.println("Analiz başladı. Virtual Thread-lər işə düşür. Liqa sayısı: " + ligIdListesi.size() + "\n");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String ligId : ligIdListesi) {
                executor.submit(() -> {
                    try {
                        analizLig(ligId);
                    } catch (Exception ignored) {}
                });
            }
        }
        System.out.println("\n>>> Analiz tamamlandı.");
    }

    private static List<String> getLeagueIdsFromHome() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-notifications");
        WebDriver driver = new ChromeDriver(options);
        List<String> ids = new ArrayList<>();
        ids.add("8");
        try {
            driver.get(baseHost);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            WebElement hotFilter = wait.until(ExpectedConditions.elementToBeClickable(By.id("li_FilterHot")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", hotFilter);
            Thread.sleep(3000);

            List<WebElement> ligler = driver.findElements(By.cssSelector(".Leaguestitle.fbHead"));
//            for (WebElement lig : ligler) {
//                String id = lig.getAttribute("sclassid");
//                if (id != null && !id.isEmpty()) ids.add(id);
//            }
        } catch (Exception e) { e.printStackTrace(); }
        finally { driver.quit(); }
        return ids;
    }

    public static void analizLig(String ligId) throws InterruptedException {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-notifications");
        WebDriver threadDriver = new ChromeDriver(options);
        WebDriverWait threadWait = new WebDriverWait(threadDriver, Duration.ofSeconds(20));

        String leagueUrl = "https://football.nowgoal26.com/league/" + ligId;

        try {
            threadDriver.get(leagueUrl);
            Thread.sleep(4000);

            WebElement roundActive = threadWait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#schedule .round .current.on")));
            int guncelHafta = Integer.parseInt(roundActive.getAttribute("round"));
            String ligAdi = threadDriver.findElement(By.id("title")).getText();

            if (guncelHafta <= 1) return;

            // Cari həftədəki OYNANMAMIŞ maçları yaddaşa al
            List<String[]> targetMatches = new ArrayList<>();
            List<WebElement> rows = threadDriver.findElements(By.className("schedulis"));
            for (WebElement row : rows) {
                if (row.findElement(By.className("score")).getText().trim().equals("-")) {
                    String h = row.findElement(By.cssSelector(".home span")).getText().trim();
                    String a = row.findElement(By.cssSelector(".away span")).getText().trim();
                    targetMatches.add(new String[]{h, a});
                }
            }

            if (targetMatches.isEmpty()) return;

            // Cari Köprüleri Tap (H-1)
            Map<String, String> currentH1Map = getRoundData(threadDriver, guncelHafta - 1);

            for (String[] match : targetMatches) {
                String ev = match[0];
                String dep = match[1];
                String b1 = currentH1Map.get(ev);
                String b2 = currentH1Map.get(dep);

                if (b1 == null || b2 == null) continue;

                for (int s = 1; s <= geriyeGidilecekSezonSayisi; s++) {
                    checkHistory(threadDriver, s, guncelHafta, b1, b2, ev, dep, ligAdi, leagueUrl);
                }
            }
        } finally {
            threadDriver.quit();
        }
    }

    public static void checkHistory(WebDriver driver, int sIdx, int targetWeek, String b1, String b2, String curEv, String curDep, String ligAdi, String leagueUrl) {
        try {
            driver.get(leagueUrl);
            Thread.sleep(3000);

            List<WebElement> seasonList = driver.findElements(By.cssSelector("#seasonBox ul li"));
            if (sIdx >= seasonList.size()) return;

            WebElement targetSeasonEl = seasonList.get(sIdx);
            String seasonLabel = targetSeasonEl.getAttribute("innerText").trim();
            String seasonVal = targetSeasonEl.getAttribute("onclick").replace("changeSeason('", "").replace("')", "");

            ((JavascriptExecutor) driver).executeScript("changeSeason('" + seasonVal + "')");
            Thread.sleep(5000);

            Map<String, String> pastH1 = getRoundData(driver, targetWeek - 1);
            String oldA = pastH1.get(b1);
            String oldB = pastH1.get(b2);

            if (oldA != null && oldB != null) {
                WebElement rBtn = driver.findElement(By.cssSelector("span[round='" + targetWeek + "']"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", rBtn);
                Thread.sleep(3000);

                List<WebElement> rows = driver.findElements(By.className("schedulis"));
                for (WebElement row : rows) {
                    String h = row.findElement(By.cssSelector(".home span")).getText().trim();
                    String a = row.findElement(By.cssSelector(".away span")).getText().trim();

                    if ((h.equalsIgnoreCase(oldA) && a.equalsIgnoreCase(oldB)) || (h.equalsIgnoreCase(oldB) && a.equalsIgnoreCase(oldA))) {
                        String ftScore = row.findElement(By.className("score")).getText();
                        String matchId = row.getAttribute("matchid");

                        // SƏNİN İSTƏDİYİN URL FORMATI İLƏ HT SKORU ÇƏKMƏK
                        String htFinal = getHTResult(driver, matchId);

                        synchronized (System.out) {
                            System.out.println("\n******************************************************************");
                            System.out.println("[LİG]: " + ligAdi + " | [Hədəf Hafta]: " + targetWeek);
                            System.out.println("[MÖVCUD MAÇ]: " + curEv + " vs " + curDep);
                            System.out.println("[GÜNCEL KÖPRÜLƏR]: " + b1 + " & " + b2);
                            System.out.println("[TAPILAN SEZON]: " + seasonLabel);
                            System.out.println("[KEÇMİŞ NƏTİCƏ]: " + h + " " + ftScore + " " + a + " | HT: (" + htFinal + ")");
                            System.out.println("******************************************************************");
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getHTResult(WebDriver d, String mId) {
        try {
            // URL formatını sənin dediyin kimi (live-{id}) etdim
            d.get(baseHost + "match/live-" + mId);
            WebDriverWait wait = new WebDriverWait(d, Duration.ofSeconds(12));
            // Sənin verdiyin HTML-dəki title="Score 1st Half" selektoru
            WebElement htEl = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//span[@title='Score 1st Half']")));
            return htEl.getText().trim();
        } catch (Exception e) {
            return "N/A";
        }
    }

    public static Map<String, String> getRoundData(WebDriver d, int round) throws InterruptedException {
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
}