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

public class NowGoalHTFTAnalyzerAllLeague {

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

            // Cari (Güncel Sezon) H-1 Köprüleri
            Map<String, String> currentH1Map = getRoundData(driver, targetWeek - 1);

            for (String[] match : targetMatches) {
                String ev = match[0];
                String dep = match[1];
                String curOdds = match[2];
                String b1 = currentH1Map.get(ev); // Ev sahibinin köprüsü (Gorica)
                String b2 = currentH1Map.get(dep); // Deplasmanın köprüsü (Hajduk)

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

            List<String> taranacakHaftalar = new ArrayList<>();
            taranacakHaftalar.add(String.valueOf(week)); // Önce güncel hafta

            List<WebElement> roundButtons = d.findElements(By.cssSelector("span[round]"));
            for (WebElement btn : roundButtons) {
                String rAttr = btn.getAttribute("round");
                if (rAttr != null && !rAttr.equals(String.valueOf(week)) && !taranacakHaftalar.contains(rAttr)) {
                    taranacakHaftalar.add(rAttr);
                }
            }

            boolean matchFound = false;

            // PERFORMANS İÇİN ÖNBELLEK (CACHE) SİSTEMİ EKLENDİ
            // Geçmiş sezonlarda haftalar arasında gezerken aynı veriyi 2 kere indirmesin diye.
            Map<Integer, Map<String, String>> roundCache = new HashMap<>();

            // Haftaları sırayla gez
            for (String currentRoundStr : taranacakHaftalar) {
                try {
                    int pastTargetWeek = Integer.parseInt(currentRoundStr);
                    if (pastTargetWeek <= 1) continue; // 1. haftanın köprüsü (0. hafta) olamayacağı için atla

                    int bridgeWeek = pastTargetWeek - 1;

                    // 1. ADIM: HEDEFLENEN HAFTANIN "BİR ÖNCEKİ HAFTASINI" BUL VE O GÜNKÜ KÖPRÜLERİ ÇEK
                    Map<String, String> pastH1;
                    if (roundCache.containsKey(bridgeWeek)) {
                        pastH1 = roundCache.get(bridgeWeek);
                    } else {
                        pastH1 = getRoundData(d, bridgeWeek);
                        roundCache.put(bridgeWeek, pastH1);
                    }

                    // b1(Gorica) ve b2(Hajduk)'un o haftadaki rakiplerini bul
                    String oldA = pastH1.get(b1);
                    String oldB = pastH1.get(b2);

                    // Eğer Gorica veya Hajduk o hafta oynamadıysa eşleşme arama
                    if (oldA == null || oldB == null) continue;

                    // 2. ADIM: HEDEF HAFTAYA TIKLA VE OLDA İLE OLDB OYNAMIŞ MI DİYE KONTROL ET
                    WebElement rBtn = d.findElement(By.cssSelector("span[round='" + pastTargetWeek + "']"));
                    ((JavascriptExecutor) d).executeScript("arguments[0].click();", rBtn);
                    Thread.sleep(1500);

                    List<WebElement> rows = d.findElements(By.className("schedulis"));
                    for (WebElement row : rows) {
                        String h = row.findElement(By.cssSelector(".home span")).getText().trim();
                        String a = row.findElement(By.cssSelector(".away span")).getText().trim();

                        if ((h.equalsIgnoreCase(oldA) && a.equalsIgnoreCase(oldB)) || (h.equalsIgnoreCase(oldB) && a.equalsIgnoreCase(oldA))) {
                            String ftScore = row.findElement(By.className("score")).getText();
                            String matchId = row.getAttribute("matchid");
                            String pastOdds = getOddsText(row);

                            // HT Score çəkilməsi
                            String htVal = getHTResult(d, matchId);

                            // HT/FT CRAZY COMEBACK KONTROLÜ
                            String comebackTipi = getComebackType(ftScore, htVal);

                            // EĞER COMEBACK VARSA EKRANA YAZDIR
                            if (!comebackTipi.equals("NONE")) {
                                String haftaMesaji = (pastTargetWeek == week) ? ">>>> [AYNI HAFTA] <<<<" : ">>>> [FARKLI HAFTA] (Hafta: " + pastTargetWeek + ") <<<<";

                                synchronized (System.out) {
                                    System.out.println("\n******************************************************************");
                                    System.out.println("[LİG]: " + ligNm + " | [GÜNCEL HAFTA]: " + week);
                                    System.out.println("[GÜNCEL OYUN]: " + cEv + " vs " + cDep + " | 1X2: " + cOdds);
                                    System.out.println("[KÖPRÜLƏR]: " + b1 + " & " + b2);
                                    System.out.println("[TAPILAN SEZON]: " + sLabel + " | " + haftaMesaji);
                                    System.out.println("🔥🔥🔥 [CRAZY COMEBACK HT/FT: " + comebackTipi + "] 🔥🔥🔥");
                                    System.out.println("[KEÇMİŞ NƏTİCƏ]: " + h + " " + ftScore + " " + a + " | HT: (" + htVal + ") | 1X2: " + pastOdds);
                                    System.out.println("******************************************************************");
                                }
                            }

                            matchFound = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {}

                if (matchFound) break;
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
            Thread.sleep(2500); // Sayfanın yüklenmesi için 2.5 saniye bekle
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
            // Sayfanın tamamen yüklenmesi için kısa bir bekletme eklenebilir
            Thread.sleep(5000);

            // Tüm lig satırlarını al (DOM'daki her şeyi çeker)
            List<WebElement> ligler = driver.findElements(By.cssSelector(".Leaguestitle.fbHead"));
            System.out.println("Toplam lig (DOM'da bulunan): " + ligler.size());

            for (WebElement lig : ligler) {
                // isDisplayed() kontrolünü kaldırdık, böylece gizli (hidden) ligleri de alacak
                String id = lig.getAttribute("sclassid");

                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }

            System.out.println("Toplanan toplam lig ID sayısı: " + ids.size());

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

    // İLK YARI / MAÇ SONU (HT/FT) KONTROLÜ (1/2 veya 2/1)
    private static String getComebackType(String ftScore, String htScore) {
        try {
            ftScore = ftScore.replace(" ", "");
            htScore = htScore.replace(" ", "");

            if (!ftScore.contains("-") || !htScore.contains("-")) return "NONE";

            String[] ftParts = ftScore.split("-");
            String[] htParts = htScore.split("-");

            int ftEv = Integer.parseInt(ftParts[0]);
            int ftDep = Integer.parseInt(ftParts[1]);

            int htEv = Integer.parseInt(htParts[0]);
            int htDep = Integer.parseInt(htParts[1]);

            if (htEv > htDep && ftEv < ftDep) {
                return "1/2";
            }
            if (htEv < htDep && ftEv > ftDep) {
                return "2/1";
            }
        } catch (Exception e) {}
        return "NONE";
    }
}