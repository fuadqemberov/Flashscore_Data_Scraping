package analyzer.flashscore.gui;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ChromeDriver Pool — driverlər bir dəfə yaranır, işi bitən driver
 * pool-a qaytarılır və növbəti task tərəfindən yenidən istifadə edilir.
 */
public class DriverPool {

    private static final Logger LOG = Logger.getLogger(DriverPool.class.getName());

    // RAM şişməsini əngəlləmək üçün bir driver MAX neçə dəfə istifadə oluna bilər?
    private static final int MAX_USES_PER_DRIVER = 15;

    private final BlockingQueue<WebDriver> available;
    private final List<WebDriver>          allDrivers;

    // Hər driver-in neçə dəfə istifadə olunduğunu yadda saxlamaq üçün Map
    private final ConcurrentHashMap<WebDriver, Integer> usageMap;

    private DriverPool(int size) throws InterruptedException {
        available   = new ArrayBlockingQueue<>(size);
        allDrivers  = new ArrayList<>(size);
        usageMap    = new ConcurrentHashMap<>();

        System.out.println("[DriverPool] " + size + " ChromeDriver basladilir...");
        for (int i = 0; i < size; i++) {
            WebDriver driver = createAndSetupDriver(i + 1, size);
            if (driver != null) {
                available.put(driver);
                allDrivers.add(driver);
                usageMap.put(driver, 0); // İstifadə sayı 0 olaraq başlayır
            }
        }
        System.out.println("[DriverPool] Butun driverler hazirdir.");
    }

    public static DriverPool create(int size) throws InterruptedException {
        return new DriverPool(size);
    }

    public WebDriver borrow() throws InterruptedException {
        return available.take();
    }

    /**
     * İşi bitmiş driveri pool-a qaytar.
     * 1. Əgər 15 dəfədən çox istifadə edilibsə -> Öldür, yenisini yarat (RAM təmizliyi)
     * 2. Əgər çöksə (Session id xətası) -> Öldür, yenisini yarat
     * 3. Sağlamdırsa -> İstifadə sayını artır və pool-a qaytar.
     */
    public void returnDriver(WebDriver driver) {
        try {
            // 1. Sürücünün hələ də canlı olduğunu sürətlə yoxla
            driver.getWindowHandle();

            // 2. İstifadə sayını yoxla
            int currentUses = usageMap.getOrDefault(driver, 0) + 1;

            if (currentUses >= MAX_USES_PER_DRIVER) {
                System.out.println("[DriverPool] Driver " + MAX_USES_PER_DRIVER + " defe istifade olundu. RAM təmizliyi ucun yenilenir...");
                replaceWithFreshDriver(driver);
            } else {
                usageMap.put(driver, currentUses);
                available.offer(driver);
            }

        } catch (Exception e) {
            // Driver ölüb (Məsələn: invalid session id) — yenisini yarat və pool-a qoy
            System.out.println("[DriverPool] Driver cokdu (Crash), yenisi yaranir...");
            replaceWithFreshDriver(driver);
        }
    }

    public void shutdown() {
        System.out.println("[DriverPool] Butun driverler baglanir...");
        for (WebDriver d : allDrivers) {
            try { d.quit(); } catch (Exception ignored) {}
        }
        allDrivers.clear();
        available.clear();
        usageMap.clear();
    }

    // ==========================================================
    // HELPER METODLAR
    // ==========================================================

    /** Köhnə driveri silib tamamilə yenisi ilə əvəz edir */
    private void replaceWithFreshDriver(WebDriver oldDriver) {
        // Köhnəni təmizlə
        try { oldDriver.quit(); } catch (Exception ignored) {}
        usageMap.remove(oldDriver);

        synchronized (allDrivers) {
            allDrivers.remove(oldDriver);
        }

        // Yenisini yarat
        WebDriver freshDriver = createAndSetupDriver(0, 0);
        if (freshDriver != null) {
            synchronized (allDrivers) {
                allDrivers.add(freshDriver);
            }
            usageMap.put(freshDriver, 0);
            available.offer(freshDriver);
        } else {
            System.out.println("[DriverPool-CRITICAL] Yeni driver yaradila bilmedi! Pool olcusu kuculdu.");
        }
    }

    /** Təkrar kod yazmamaq üçün Driver yaratma və Cookie/Odds setup metodu */
    private WebDriver createAndSetupDriver(int index, int total) {
        WebDriver driver = null;
        try {
            driver = DriverFactory.createHeadlessDriver();
            driver.get(ScraperConstants.BASE_URL);

            // Cookie qebul et
            try {
                WaitActionUtils.smartClick(driver, By.id("onetrust-accept-btn-handler"), 3);
            } catch (Exception ignored) {} // Cookie gəlməsə davam et

            // Decimal odds ayarı
            OddsConfigurator.configureDecimalOdds(driver);

            if (index > 0) {
                System.out.println("  [Pool] Driver " + index + "/" + total + " hazirdir.");
            }
            return driver;
        } catch (Exception e) {
            if (index > 0) {
                System.out.println("  [Pool] Driver " + index + " setup xetasi: " + e.getMessage());
            }
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
            return null;
        }
    }
}