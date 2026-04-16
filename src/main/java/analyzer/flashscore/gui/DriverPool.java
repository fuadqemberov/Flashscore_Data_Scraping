package analyzer.flashscore.gui;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ChromeDriver Pool — driverlər bir dəfə yaranır, işi bitən driver
 * pool-a qaytarılır və növbəti task tərəfindən yenidən istifadə edilir.
 *
 * İstifadə:
 *   DriverPool pool = DriverPool.create(5);   // 5 driver yarat
 *   WebDriver driver = pool.borrow();          // al
 *   pool.returnDriver(driver);                 // qaytar
 *   pool.shutdown();                           // proqram bitəndə bağla
 */
public class DriverPool {

    private static final Logger LOG = Logger.getLogger(DriverPool.class.getName());

    private final BlockingQueue<WebDriver> available;
    private final List<WebDriver>          allDrivers;

    private DriverPool(int size) throws InterruptedException {
        available   = new ArrayBlockingQueue<>(size);
        allDrivers  = new ArrayList<>(size);

        System.out.println("[DriverPool] " + size + " ChromeDriver basladilir...");
        for (int i = 0; i < size; i++) {
            WebDriver driver = DriverFactory.createHeadlessDriver();

            // Hər driver üçün ilkin setup (cookie qəbul + decimal odds)
            try {
                driver.get(ScraperConstants.BASE_URL);
                WaitActionUtils.smartClick(driver, By.id("onetrust-accept-btn-handler"), 3);
                OddsConfigurator.configureDecimalOdds(driver);
                System.out.println("  [Pool] Driver " + (i + 1) + "/" + size + " hazirdir.");
            } catch (Exception e) {
                System.out.println("  [Pool] Driver " + (i + 1) + " setup xetasi (davam edilir): " + e.getMessage());
            }

            available.put(driver);
            allDrivers.add(driver);
        }
        System.out.println("[DriverPool] Butun driverler hazirdir.");
    }

    /** Pool yarat (bloklanma olmadan) */
    public static DriverPool create(int size) throws InterruptedException {
        return new DriverPool(size);
    }

    /**
     * Pool-dan driver al. Pool boşdursa, bir driver azad olana qədər gözlə.
     */
    public WebDriver borrow() throws InterruptedException {
        return available.take();
    }

    /**
     * İşi bitmiş driveri pool-a qaytar.
     * Driver sağlamdırsa birbaşa qaytar; xətalıdırsa yenisi ilə əvəzlə.
     */
    public void returnDriver(WebDriver driver) {
        try {
            // Sürücünün hələ də canlı olduğunu sürətlə yoxla
            driver.getWindowHandle();
            available.offer(driver);
        } catch (Exception e) {
            // Driver ölüb — yenisini yarat və pool-a qoy
            System.out.println("[DriverPool] Driver oldu, yenisi yaranir...");
            try {
                WebDriver fresh = DriverFactory.createHeadlessDriver();
                driver.get(ScraperConstants.BASE_URL);
                OddsConfigurator.configureDecimalOdds(fresh);
                available.offer(fresh);
                // allDrivers listini güncəllə
                synchronized (allDrivers) {
                    allDrivers.remove(driver);
                    allDrivers.add(fresh);
                }
            } catch (Exception ex) {
                System.out.println("[DriverPool] Yeni driver yaranmadi: " + ex.getMessage());
            }
        }
    }

    /** Bütün driverləri bağla (proqram sonunda çağır) */
    public void shutdown() {
        System.out.println("[DriverPool] Butun driverler baglanir...");
        for (WebDriver d : allDrivers) {
            try { d.quit(); } catch (Exception ignored) {}
        }
        allDrivers.clear();
        available.clear();
    }
}
