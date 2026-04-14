package flashscore.weeklydatascraping.mackolik.before_after_pattern;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MackolikHalfTimePatternFinder {
    // Thread-safe koleksiyonlar
    public static Set<String> upcomingMatchIds = Collections.synchronizedSet(new LinkedHashSet<>());
    public static List<String> matchedPatterns = Collections.synchronizedList(new ArrayList<>());
    
    // Virtual Thread Executor ve Driver Pool
    private static final int DRIVER_POOL_SIZE = 8; // Paralel işlem sayısı
    private static final BlockingQueue<WebDriver> driverPool = new LinkedBlockingQueue<>(DRIVER_POOL_SIZE);
    private static volatile boolean shutdownRequested = false;

    static {
        // Driver pool'unu başlat
        initializeDriverPool();
    }

    /**
     * WebDriver pool'unu başlatır - her thread için ayrı driver instance
     */
    private static void initializeDriverPool() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        for (int i = 0; i < DRIVER_POOL_SIZE; i++) {
            try {
                WebDriver driver = new ChromeDriver(options);
                driver.manage().window().maximize();
                if (!driverPool.offer(driver)) {
                    System.err.println("❌ Driver pool dolu, driver kapatılıyor: " + (i + 1));
                    driver.quit();
                } else {
                    System.out.println("✓ Driver #" + (i + 1) + " pool'a eklendi");
                }
            } catch (Exception e) {
                System.err.println("❌ Driver oluşturulurken hata (ID: " + i + "): " + e.getMessage());
            }
        }
    }

    /**
     * Pool'dan driver al - thread-safe
     */
    private static WebDriver acquireDriver() throws InterruptedException {
        if (shutdownRequested) {
            throw new IllegalStateException("Driver pool kapatılmıştır");
        }
        return driverPool.take();
    }

    /**
     * Driver'ı pool'a geri döndür
     */
    private static void releaseDriver(WebDriver driver) {
        if (driver != null && !shutdownRequested) {
            try {
                driverPool.put(driver);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Pool'u kapatır - tüm driver'ları temizle
     */
    private static void shutdownDriverPool() {
        shutdownRequested = true;
        WebDriver driver;
        while ((driver = driverPool.poll()) != null) {
            try {
                driver.quit();
                System.out.println("✓ Driver kapatıldı");
            } catch (Exception e) {
                System.err.println("⚠️ Driver kapatılırken hata: " + e.getMessage());
            }
        }
        System.out.println("✓ Tüm driver'lar pool'dan temizlendi");
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        System.out.println("🚀 Sistem başlatıldı (Virtual Threads desteği aktif)");
        System.out.println("📊 Paralel işlem sayısı: " + DRIVER_POOL_SIZE + "\n");

        WebDriver mainDriver = null;
        ExecutorService virtualExecutor = null;

        try {
            // Ana driver ile maç ID'lerini topla
            mainDriver = acquireDriver();
            System.out.println("📍 Maç ID'leri toplanıyor...\n");
            getDailyUpcomingMatchIds(mainDriver);
            releaseDriver(mainDriver);
            mainDriver = null;

            System.out.println("\n================================");
            System.out.println("📋 Toplanan Maç ID Sayısı: " + upcomingMatchIds.size());
            System.out.println("================================\n");

            if (upcomingMatchIds.isEmpty()) {
                System.out.println("❌ Hiç maç bulunamadı!");
                return;
            }

            System.out.println("⚙️  SİSTEM ANALİZİ BAŞLIYOR (VIRTUAL THREADS İLE PARALEL)...\n");

            // Virtual Thread Executor oluştur
            virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

            // Her maç analizi için ayrı task oluştur
            List<Future<?>> futures = new ArrayList<>();
            for (String matchId : upcomingMatchIds) {
                Future<?> future = virtualExecutor.submit(() -> analyzeMatchFormWithDriver(matchId));
                futures.add(future);
            }

            // Tüm task'ları tamamlanana kadar bekle
            System.out.println("⏳ Tüm maç analizleri tamamlanıyor...");
            int completedCount = 0;
            for (Future<?> future : futures) {
                try {
                    future.get(120, TimeUnit.SECONDS);
                    completedCount++;
                    printProgress(completedCount, upcomingMatchIds.size());
                } catch (java.util.concurrent.TimeoutException te) {
                    future.cancel(true);
                    System.out.println("⚠️ Task timeout. Devam ediliyor...");
                } catch (Exception e) {
                    System.out.println("⚠️ Task hatasında devam ediliyor...");
                }
            }

            System.out.println("\n\n==== BÜTÜN MAÇLARIN ANALİZİ TAMAMLANDI ====");
            System.out.println("\n=======================================================");
            System.out.println("🔥 SONUÇ: SİSTEM TAKTİKLERİNE UYAN MAÇLAR 🔥");
            System.out.println("=======================================================");

            if (matchedPatterns.isEmpty()) {
                System.out.println("❌ Maalesef bugün için patternlere uyan maç bulunamadı.");
            } else {
                for (String result : matchedPatterns) {
                    System.out.println(result);
                    System.out.println("-------------------------------------------------------");
                }
                System.out.println("\n✅ Toplam Bulunan Sinyal Sayısı: " + matchedPatterns.size());
            }
            System.out.println("=======================================================\n");

        } catch (InterruptedException ie) {
            System.err.println("❌ Ana işlem kesintiye uğradı: " + ie.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("❌ Ana işlemde hata oluştu: " + e.getMessage());
        } finally {
            // Executor'ü graceful shutdown yap
            if (virtualExecutor != null && !virtualExecutor.isShutdown()) {
                virtualExecutor.shutdown();
                try {
                    if (!virtualExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        System.out.println("⚠️ Executor timeout, force shutdown yapılıyor...");
                        virtualExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    virtualExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // Driver pool'unu temizle
            shutdownDriverPool();

            // Şu ana kadar yayınlanan driver'ı da kapatabilir
            if (mainDriver != null) {
                try {
                    mainDriver.quit();
                } catch (Exception e) {
                    // ignore
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("\n⏱️  Toplam çalışma süresi: " + formatDuration(elapsed));
            System.out.println("✓ Sistem başarılı şekilde kapatıldı.\n");
        }
    }

    /**
     * Süreyi insan okunabilir formata çevir
     */
    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0) {
            return String.format("%d dakika %d saniye", minutes, secs);
        }
        return String.format("%d saniye", secs);
    }

    /**
     * İlerleme durumunu göster
     */
    private static void printProgress(int completed, int total) {
        int percentage = (int) ((completed * 100.0) / total);
        System.out.print("\r  ✓ İlerleme: " + completed + "/" + total + " (" + percentage + "%)");
    }

    /**
     * Virtual Thread için maç analizi - driver pool'dan driver al
     */
    private static void analyzeMatchFormWithDriver(String matchId) {
        WebDriver driver = null;
        try {
            driver = acquireDriver();
            analyzeMatchForm(driver, matchId);
        } catch (InterruptedException ie) {
            System.out.println("⚠️ Maç " + matchId + " - Thread kesintiye uğradı");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("⚠️ Maç " + matchId + " - Hata: " + e.getClass().getSimpleName());
        } finally {
            if (driver != null) {
                releaseDriver(driver);
            }
        }
    }

    //...existing code...

    private static void getDailyUpcomingMatchIds(WebDriver driver) {
        try {
            driver.get("https://arsiv.mackolik.com/Canli-Sonuclar");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Çerez bildirimini kabul et
            try {
                WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(@class, 'accept-all-btn')]")));
                js.executeScript("arguments[0].click();", acceptCookiesButton);
                System.out.println("Çerezler kabul edildi.");
                try {
                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//div[contains(@class,'cookie-consent-rectangle-wrapper')]")));
                    System.out.println("Çerez bandı kayboldu.");
                } catch (org.openqa.selenium.TimeoutException te) {
                    System.out.println("Çerez bandı kaybolması için bekleme zaman aşımı. Devam ediliyor...");
                }
                Thread.sleep(500);
            } catch (org.openqa.selenium.TimeoutException te) {
                System.out.println("Çerez butonu timeout. Devam ediliyor...");
            } catch (Exception e) {
                System.out.println("Çerez kabul butonu ile ilgili işlem yapılamadı veya zaten yoktu.");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }

            // 2. Date picker işlemleri
            try {
                System.out.println("Date picker işlemleri başlıyor...");
                WebElement rightArrow = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".date-right-coll")));
                System.out.println("Sağ ok elementine tıklanıyor...");
                js.executeScript("arguments[0].click();", rightArrow);
                Thread.sleep(1000);
                System.out.println("Sağ ok elementine tıklandı");

                WebElement leftArrow = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".date-left-coll")));
                System.out.println("Sol ok elementine tıklanıyor...");
                js.executeScript("arguments[0].click();", leftArrow);
                Thread.sleep(1000);
                System.out.println("Sol ok elementine tıklandı");
                System.out.println("Date picker işlemleri tamamlandı.");
            } catch (org.openqa.selenium.TimeoutException te) {
                System.err.println("Date picker timeout. Devam ediliyor...");
            } catch (Exception e) {
                System.err.println("Date picker işlemleri sırasında hata: " + e.getMessage() + ". Devam ediliyor...");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }

            // 3. Sağdaki tarih navigasyonuna tıkla (reklam iframe engeli aşılarak)
            try {
                // Önce reklam iframe'lerini DOM'dan kaldır
                js.executeScript(
                        "document.querySelectorAll('iframe[id*=\"google_ads\"], " +
                        "iframe[src*=\"safeframe\"], iframe[src*=\"googlesyndication\"]')" +
                        ".forEach(el => el.remove());"
                );
                Thread.sleep(300);
                System.out.println("Reklam iframe'leri kaldırıldı.");

                // Doğrudan JS fonksiyonu çağır (en güvenilir yöntem)
                js.executeScript("gotoDate(+1);");
                System.out.println("gotoDate(+1) çağrıldı.");
                Thread.sleep(1500);

            } catch (Exception e) {
                System.out.println("Sağ tarih navigasyonunda hata: " + e.getMessage() + ". Devam ediliyor...");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }

            Thread.sleep(2000);


            // 4. Soldaki tarih navigasyonuna tıkla
            try {
                js.executeScript(
                        "document.querySelectorAll('iframe[id*=\"google_ads\"], " +
                        "iframe[src*=\"safeframe\"], iframe[src*=\"googlesyndication\"]')" +
                        ".forEach(el => el.remove());"
                );
                Thread.sleep(300);

                js.executeScript("gotoDate(-1);");
                System.out.println("gotoDate(-1) çağrıldı.");
                Thread.sleep(1000);

            } catch (Exception e) {
                System.out.println("Soldaki tarih navigasyonunda hata: " + e.getMessage() + ". Devam ediliyor...");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }

            // 5. Futbol sekmesinin seçili olduğundan emin ol
            try {
                WebElement futbolCheckbox = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("chkSport1")));
                wait.until(ExpectedConditions.elementToBeClickable(futbolCheckbox));
                if (!futbolCheckbox.getAttribute("class").contains("selected")) {
                    System.out.println("Futbol seçili değil, tıklanıyor...");
                    js.executeScript("arguments[0].click();", futbolCheckbox);
                    try {
                        wait.until(ExpectedConditions.attributeContains(futbolCheckbox, "class", "selected"));
                    } catch (org.openqa.selenium.TimeoutException te) {
                        System.out.println("Futbol seçimi confirm timeout. Devam ediliyor...");
                    }
                    System.out.println("Futbol sekmesi seçildi.");
                    Thread.sleep(1000);
                } else {
                    System.out.println("Futbol sekmesi zaten seçili.");
                }
            } catch (org.openqa.selenium.TimeoutException te) {
                System.err.println("Futbol sekmesi timeout. Devam ediliyor...");
            } catch (Exception e) {
                System.err.println("Futbol sekmesi hatası: " + e.getMessage() + ". Devam ediliyor...");
            }

            // 6. Basketbol sekmesi seçiliyse, seçimini kaldır
            try {
                WebElement basketbolCheckbox = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("chkSport2")));
                wait.until(ExpectedConditions.elementToBeClickable(basketbolCheckbox));
                if (basketbolCheckbox.getAttribute("class").contains("selected")) {
                    System.out.println("Basketbol seçili, seçimi kaldırmak için tıklanıyor...");
                    js.executeScript("arguments[0].click();", basketbolCheckbox);
                    try {
                        wait.until(ExpectedConditions.not(ExpectedConditions.attributeContains(basketbolCheckbox, "class", "selected")));
                    } catch (org.openqa.selenium.TimeoutException te) {
                        System.out.println("Basketbol seçimi kaldırma confirm timeout. Devam ediliyor...");
                    }
                    System.out.println("Basketbol sekmesinin seçimi kaldırıldı.");
                    Thread.sleep(1000);
                } else {
                    System.out.println("Basketbol sekmesi zaten seçili değil.");
                }
            } catch (org.openqa.selenium.TimeoutException te) {
                System.err.println("Basketbol sekmesi timeout. Devam ediliyor...");
            } catch (Exception e) {
                System.err.println("Basketbol sekmesi hatası: " + e.getMessage() + ". Devam ediliyor...");
            }

            // 7. İDDAA checkbox'ının seçili olduğundan emin ol
            try {
                WebElement iddaaCheckbox = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("chkIddaa")));
                wait.until(ExpectedConditions.elementToBeClickable(iddaaCheckbox));
                if (!iddaaCheckbox.getAttribute("class").contains("selected")) {
                    System.out.println("İDDAA checkbox'ı seçili değil, tıklanıyor...");
                    js.executeScript("arguments[0].click();", iddaaCheckbox);
                    try {
                        wait.until(ExpectedConditions.attributeContains(iddaaCheckbox, "class", "selected"));
                    } catch (org.openqa.selenium.TimeoutException te) {
                        System.out.println("İDDAA seçimi confirm timeout. Devam ediliyor...");
                    }
                    System.out.println("İDDAA checkbox'ı seçildi.");
                    Thread.sleep(1500);
                } else {
                    System.out.println("İDDAA checkbox'ı zaten seçili.");
                }
            } catch (org.openqa.selenium.TimeoutException te) {
                System.err.println("İDDAA checkbox timeout. Devam ediliyor...");
            } catch (Exception e) {
                System.err.println("İDDAA checkbox hatası: " + e.getMessage() + ". Devam ediliyor...");
            }

            // 8. Tarihe Göre butonuna tıkla
            try {
                WebElement tariheGoreButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("aOrderByDate")));
                wait.until(ExpectedConditions.elementToBeClickable(tariheGoreButton));
                if (!tariheGoreButton.getAttribute("class").contains("selected")) {
                    System.out.println("'Tarihe Göre' butonuna tıklanıyor...");
                    js.executeScript("arguments[0].click();", tariheGoreButton);
                    try {
                        wait.until(ExpectedConditions.attributeContains(tariheGoreButton, "class", "selected"));
                    } catch (org.openqa.selenium.TimeoutException te) {
                        System.out.println("Tarihe Göre seçimi confirm timeout. Devam ediliyor...");
                    }
                    System.out.println("'Tarihe Göre' butonuna tıklandı ve seçildi.");
                    Thread.sleep(1500);
                } else {
                    System.out.println("'Tarihe Göre' butonu zaten seçili.");
                }
                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='dvScores']//tr[@sport='1']")));
                    System.out.println("'Tarihe Göre' filtresi aktif ve futbol içeriği mevcut.");
                } catch (org.openqa.selenium.TimeoutException te) {
                    System.out.println("Futbol içeriği yükleme timeout. Devam ediliyor...");
                }
            } catch (org.openqa.selenium.TimeoutException te) {
                System.err.println("'Tarihe Göre' butonu timeout. Devam ediliyor...");
            } catch (Exception e) {
                System.err.println("'Tarihe Göre' butonu hatası: " + e.getMessage() + ". Devam ediliyor...");
            }

            // 9. Maç linklerini bul ve ID'leri çıkar
            try {
                List<WebElement> matchLinks = driver.findElements(By.xpath("//td[@class='score']//a[contains(@href, '/Mac/') and contains(text(), 'v')]"));
                System.out.println("MATCH LINKS SIZE : " + matchLinks.size());
                
                for (WebElement link : matchLinks) {
                    try {
                        String href = link.getAttribute("href");
                        if (href != null && href.contains("/Mac/")) {
                            String matchId = href.split("/Mac/")[1].split("/")[0];
                            upcomingMatchIds.add(matchId);
                        }
                    } catch (StaleElementReferenceException sere) {
                        System.out.println("Stale element reference. Maç linki atlanıyor...");
                    } catch (Exception e) {
                        System.out.println("Maç ID'si çıkarılırken hata: " + e.getMessage());
                    }
                }
                
                System.out.println("Toplam " + upcomingMatchIds.size() + " adet maç ID'si bulundu.");
            } catch (Exception e) {
                System.err.println("Maç linklerini bulurken hata: " + e.getMessage() + ". Devam ediliyor...");
            }

        } catch (org.openqa.selenium.TimeoutException te) {
            System.err.println("getDailyUpcomingMatchIds metodunda timeout hatası. Devam ediliyor...");
        } catch (Exception e) {
            System.err.println("getDailyUpcomingMatchIds metodunda hata: " + e.getMessage() + ". Devam ediliyor...");
        }
    }
    
    private static void analyzeMatchForm(WebDriver driver, String matchId) {
        String url = "https://arsiv.mackolik.com/Mac/" + matchId + "/#karsilastirma";
        
        // URL'ye gitme işlemini try-catch ile koru
        try {
            driver.get(url);
        } catch (org.openqa.selenium.TimeoutException te) {
            System.out.println("⚠️ Maç ID " + matchId + " - Sayfa yüklenirken timeout (URL erişimi başarısız). Atlanıyor...");
            return;
        } catch (Exception e) {
            System.out.println("⚠️ Maç ID " + matchId + " - URL açılırken hata: " + e.getMessage() + ". Atlanıyor...");
            return;
        }
        
        try {
            Thread.sleep(2500);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
            String formDivXPath = "//div[contains(@class, 'md') and .//div[contains(@class, 'detail-title') and contains(., 'Form Durumu')]]";
            
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(formDivXPath)));
            } catch (org.openqa.selenium.TimeoutException te) {
                System.out.println("⚠️ Maç ID " + matchId + " - Form Durumu tablosu yüklenemedi. Atlanıyor...");
                return;
            }
            
            List<WebElement> formDivs = driver.findElements(By.xpath(formDivXPath));
            if (formDivs.size() >= 2) {
                TableAnalysis home = analyzeTableForTeamAndOpponents(formDivs.get(0), matchId);
                TableAnalysis away = analyzeTableForTeamAndOpponents(formDivs.get(1), matchId);
                System.out.println("\n🔍 Maç ID: " + matchId);
                System.out.println("  🏠 " + home.teamName);
                System.out.println("     [-2] 2 Önceki : " + (home.prev2 != null ? home.prev2 : "-"));
                System.out.println("[-1] 1 Önceki : " + (home.prev1 != null ? home.prev1 : "-"));
                System.out.println("     [+1] 1 Sonraki: " + (home.next1 != null ? home.next1 : "-"));
                System.out.println("[+2] 2 Sonraki: " + (home.next2 != null ? home.next2 : "-"));
                System.out.println("  ✈️ " + away.teamName);
                System.out.println("     [-2] 2 Önceki : " + (away.prev2 != null ? away.prev2 : "-"));
                System.out.println("     [-1] 1 Önceki : " + (away.prev1 != null ? away.prev1 : "-"));
                System.out.println("     [+1] 1 Sonraki: " + (away.next1 != null ? away.next1 : "-"));
                System.out.println("     [+2] 2 Sonraki: " + (away.next2 != null ? away.next2 : "-"));
                // ==========================================
                // PATTERN 1: BİLGİ-3 (2'Lİ KESİŞİM)
                // ==========================================
                Set<String> matchType1 = new HashSet<>(home.prevOpponents);
                matchType1.retainAll(away.nextOpponents);
                Set<String> matchType2 = new HashSet<>(home.nextOpponents);
                matchType2.retainAll(away.prevOpponents);
                if (matchType1.size() >= 2) {
                    recordMatch(url, home.teamName, away.teamName, matchType1, "BİLGİ-3 (Havuz Eşleşmesi)", "A'nın Önceki 2 Maçı = B'nin Sonraki 2 Maçı", "🟢");
                }
                if (matchType2.size() >= 2) {
                    recordMatch(url, home.teamName, away.teamName, matchType2, "BİLGİ-3 (Havuz Eşleşmesi)", "A'nın Sonraki 2 Maçı = B'nin Önceki 2 Maçı", "🟢");
                }
                // ==========================================
                // PATTERN 2: YENİ İLAVE PATTERN (BİREBİR ÇAPRAZ)
                // ==========================================
                // Mesafe 1 Kontrolleri (-1 ile +1)
                boolean c1A = (home.prev1 != null && away.next1 != null && home.prev1.equals(away.next1));
                boolean c1B = (home.next1 != null && away.prev1 != null && home.next1.equals(away.prev1));
                // Mesafe 2 Kontrolleri (-2 ile +2)
                boolean c2A = (home.prev2 != null && away.next2 != null && home.prev2.equals(away.next2));
                boolean c2B = (home.next2 != null && away.prev2 != null && home.next2.equals(away.prev2));
                if (c1A && c1B) {
                    recordMatch(url, home.teamName, away.teamName, Set.of(home.prev1, home.next1), "🔥 KUSURSUZ X ÇAPRAZ (Mesafe 1)", "Ev[-1] = Dep[+1] VE Ev[+1] = Dep[-1]", "🔥");
                } else {
                    if (c1A) recordMatch(url, home.teamName, away.teamName, Set.of(home.prev1), "YENİ PATTERN (1. Mesafe Çapraz)", "Ev Sahibinin 1 Önceki Rakibi [-1] = Deplasmanın 1 Sonraki Rakibi [+1]", "🔵");
                    if (c1B) recordMatch(url, home.teamName, away.teamName, Set.of(home.next1), "YENİ PATTERN (1. Mesafe Çapraz)", "Ev Sahibinin 1 Sonraki Rakibi [+1] = Deplasmanın 1 Önceki Rakibi [-1]", "🔵");
                }
                if (c2A && c2B) {
                    recordMatch(url, home.teamName, away.teamName, Set.of(home.prev2, home.next2), "🔥 KUSURSUZ X ÇAPRAZ (Mesafe 2)", "Ev[-2] = Dep[+2] VE Ev[+2] = Dep[-2]", "🔥");
                } else {
                    if (c2A) recordMatch(url, home.teamName, away.teamName, Set.of(home.prev2), "YENİ PATTERN (2. Mesafe Çapraz)", "Ev Sahibinin 2 Önceki Rakibi [-2] = Deplasmanın 2 Sonraki Rakibi[+2]", "🔵");
                    if (c2B) recordMatch(url, home.teamName, away.teamName, Set.of(home.next2), "YENİ PATTERN (2. Mesafe Çapraz)", "Ev Sahibinin 2 Sonraki Rakibi [+2] = Deplasmanın 2 Önceki Rakibi [-2]", "🔵");
                }
            } else {
                System.out.println("⚠️ Maç ID " + matchId + " - Tablolar okunamadı.");
            }
        } catch (org.openqa.selenium.TimeoutException te) {
            System.out.println("⚠️ Maç ID " + matchId + " - Zaman aşımı hatası. Sayfada beklenmeyen bir gecikme. Atlanıyor...");
        } catch (InterruptedException ie) {
            System.out.println("⚠️ Maç ID " + matchId + " - İş parçacığı kesintiye uğradı. Atlanıyor...");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("⚠️ Maç ID " + matchId + " işlenirken hata: " + e.getClass().getSimpleName() + " - " + e.getMessage() + ". Atlanıyor...");
        }
    }
    private static void recordMatch(String url, String home, String away, Set<String> common, String patternName, String matchDesc, String emoji) {
        String resultMsg = String.format("%s [%s] %s vs %s\n   Açıklama: %s\n   Eşleşen Takım(lar): %s\n   Taktik: 2/1 VEYA 1/2 Oyna!\n   Link: %s",
                emoji, patternName, home, away, matchDesc, common, url);
        System.out.println("  " + emoji + " ŞARTLAR SAĞLANDI! SİSTEM YAKALADI -> " + patternName);
        System.out.println("     ↳ Ortak Takım: " + common);
        matchedPatterns.add(resultMsg);
    }
    static class TableAnalysis {
        String teamName;
        String prev2, prev1, next1, next2;
        Set<String> prevOpponents = new HashSet<>();
        Set<String> nextOpponents = new HashSet<>();
        TableAnalysis(String teamName, String prev2, String prev1, String next1, String next2) {
            this.teamName = teamName;
            this.prev2 = prev2;
            this.prev1 = prev1;
            this.next1 = next1;
            this.next2 = next2;
            if (prev2 != null) prevOpponents.add(prev2);
            if (prev1 != null) prevOpponents.add(prev1);
            if (next1 != null) nextOpponents.add(next1);
            if (next2 != null) nextOpponents.add(next2);
        }
    }
    private static TableAnalysis analyzeTableForTeamAndOpponents(WebElement wrapperDiv, String targetMatchId) {
        String teamName = "Bilinmeyen Takım";
        String prev2 = null, prev1 = null, next1 = null, next2 = null;
        try {
            WebElement titleElement = wrapperDiv.findElement(By.xpath(".//div[contains(@class, 'detail-title')]"));
            String fullTitle = titleElement.getText();
            if (fullTitle.contains("-")) {
                teamName = fullTitle.split("-")[0].trim();
            } else {
                teamName = fullTitle.replace("Form Durumu", "").trim();
            }
            WebElement table = wrapperDiv.findElement(By.tagName("table"));
            List<WebElement> rows = table.findElements(By.xpath(".//tr[contains(@class, 'row')]"));
            int targetIndex = -1;
            for (int i = 0; i < rows.size(); i++) {
                List<WebElement> scoreLinks = rows.get(i).findElements(By.xpath(".//td[4]//a"));
                if (!scoreLinks.isEmpty() && scoreLinks.get(0).getText().trim().equalsIgnoreCase("v")
                    && scoreLinks.get(0).getAttribute("href").contains(targetMatchId)) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex != -1) {
                prev2 = getOpponentFromRow(rows, targetIndex - 2);
                prev1 = getOpponentFromRow(rows, targetIndex - 1);
                next1 = getOpponentFromRow(rows, targetIndex + 1);
                next2 = getOpponentFromRow(rows, targetIndex + 2);
            }
        } catch (Exception e) {}
        return new TableAnalysis(teamName, prev2, prev1, next1, next2);
    }
    private static String getOpponentFromRow(List<WebElement> rows, int index) {
        if (index >= 0 && index < rows.size()) {
            try {
                WebElement row = rows.get(index);
                List<WebElement> opponentLinks = row.findElements(By.xpath("./td[3]//a[contains(@href, '/Takim/')] | ./td[5]//a[contains(@href, '/Takim/')]"));
                if (!opponentLinks.isEmpty()) {
                    return opponentLinks.get(0).getText().trim();
                }
            } catch (Exception e) {}
        }
        return null;
    }
}