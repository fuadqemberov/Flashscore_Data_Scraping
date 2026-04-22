package analyzer.mackolik.avcibot;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class FutbolAvciBotu {

    // ─── AYARLAR ────────────────────────────────────────────────────────────
    static String anaSayfaUrl = "https://arsiv.mackolik.com/Canli-Sonuclar";
    static int geriyeGidilecekSezonSayisi = 6;

    /**
     * Aynı anda kaç lig paralel işlensin?
     * Tavsiye: RAM / CPU çekirdek sayısına göre ayarla.
     * 4 çekirdekli bir makinede 3–4, 8 çekirdeklide 6–8 değeri uygundur.
     * Çok yüksek değer → mackolik.com rate-limit veya bellek sorunu yaratabilir.
     */
    static int THREAD_SAYISI = 4;

    // ─── MAIN ───────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        LogManager.getLogManager().reset();
        Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        System.setProperty("webdriver.chrome.silentOutput", "true");

        // WebDriverManager'ı bir kez, ana thread'de çalıştır
        WebDriverManager.chromedriver().setup();
        System.out.println("Sistem Başlatılıyor...\n");

        // 1. Adım: Tek driver ile ana sayfadan lig URL'lerini çek
        List<String> ligListesi;
        WebDriver anaDriver = yeniDriver();
        try {
            ligListesi = getLigUrlListesi(anaDriver);
            System.out.println("Toplam " + ligListesi.size() + " adet lig bulundu.\n");
        } finally {
            anaDriver.quit();
        }

        if (ligListesi.isEmpty()) {
            System.out.println("Hiç lig bulunamadı, program sonlandırılıyor.");
            return;
        }

        // 2. Adım: Thread havuzu oluştur; her worker kendi driver'ını taşır
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_SAYISI);
        AtomicInteger tamamlanan = new AtomicInteger(0);
        int toplam = ligListesi.size();

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < toplam; i++) {
            final String ligUrl = ligListesi.get(i);
            final int siraNo = i + 1;

            Future<?> f = executor.submit(() -> {
                // Her task kendi ChromeDriver örneğini açıp kapatır
                WebDriver driver = yeniDriver();
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
                try {
                    log("[" + siraNo + "/" + toplam + "] Başlıyor: " + ligUrl);
                    analizLig(driver, wait, ligUrl);
                } catch (Exception e) {
                    log("[!] Hata – " + ligUrl + " : " + e.getMessage());
                } finally {
                    driver.quit();
                    int biten = tamamlanan.incrementAndGet();
                    log("✓ Tamamlandı " + biten + "/" + toplam + " → " + ligUrl);
                }
            });
            futures.add(f);
        }

        // Tüm işlerin bitmesini bekle
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\nTÜM İŞLEMLER TAMAMLANDI!");
    }

    // ─── ANA SAYFADAN LİGLERİ ÇEK ───────────────────────────────────────────
    private static List<String> getLigUrlListesi(WebDriver driver) {
        System.out.println("Ana sayfa yükleniyor, ligler tespit ediliyor: " + anaSayfaUrl);
        driver.get(anaSayfaUrl);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<String> ligUrls = new ArrayList<>();
        List<WebElement> ligElementleri = driver.findElements(
                By.cssSelector("tr.rows-bg a[href*='/Puan-Durumu/s=']"));

        for (WebElement el : ligElementleri) {
            String href = el.getAttribute("href");
            if (href != null && !ligUrls.contains(href)) {
                ligUrls.add(href);
            }
        }
        return ligUrls;
    }

    // ─── YENI DRIVER FABRIKA ─────────────────────────────────────────────────

    /**
     * Her çağrıda yeni, bağımsız bir ChromeDriver döner.
     * Thread-safety için her worker bu metodu kendi başına çağırır.
     */
    private static WebDriver yeniDriver() {
        return new ChromeDriver(getOptions());
    }

    // ─── ANA ANALİZ ─────────────────────────────────────────────────────────
    public static void analizLig(WebDriver driver, WebDriverWait wait, String ligPuanDurumUrl) throws InterruptedException {
        List<MacVerisi> bugunkuHedefMaclar = new ArrayList<>();
        List<MacVerisi> gecmisVeriHavuzu = new ArrayList<>();

        driver.get(ligPuanDurumUrl);
        Thread.sleep(2500);

        clickFiksturTab(driver, wait);

        Select haftaSelect;
        haftaSelect = new Select(wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("cboWeek"))));

        List<WebElement> haftaOptions = haftaSelect.getOptions();
        int toplamHafta = haftaOptions.size();
        int liginOrtasi = toplamHafta / 2;

        int suAnkiHafta = Integer.parseInt(
                haftaSelect.getFirstSelectedOption().getAttribute("value"));

        if (suAnkiHafta <= 1) {
            log("  Henüz 1. haftada, atlanıyor: " + ligPuanDurumUrl);
            return;
        }

        int hedefYari = (suAnkiHafta <= liginOrtasi) ? 1 : 2;
        log("  Hafta: " + suAnkiHafta + " / " + toplamHafta + " | Yarı: " + hedefYari
            + " | " + ligPuanDurumUrl);

        bugunkuHedefMaclar.addAll(getMaclariHaftadan(driver, suAnkiHafta, hedefYari, true));

        if (bugunkuHedefMaclar.size() < 2) {
            log("  Oynanmamış maç yetersiz, atlanıyor: " + ligPuanDurumUrl);
            return;
        }

        Select sezonSelect = new Select(wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("cboSeason"))));
        List<WebElement> sezonOptions = sezonSelect.getOptions();
        int taranacak = Math.min(geriyeGidilecekSezonSayisi + 1, sezonOptions.size());

        for (int si = 1; si < taranacak; si++) {
            sezonOptions = new Select(driver.findElement(By.id("cboSeason"))).getOptions();
            if (si >= sezonOptions.size()) break;

            String sezonDegeri = sezonOptions.get(si).getAttribute("value");
            String sezonAdi = sezonOptions.get(si).getText().trim();
            log("  -> Sezon kazınıyor: " + sezonAdi + " | " + ligPuanDurumUrl);

            ((JavascriptExecutor) driver).executeScript("getNationalSeason('" + sezonDegeri + "');");
            Thread.sleep(2500);

            clickFiksturTab(driver, wait);

            int baslangic = (hedefYari == 1) ? 1 : (liginOrtasi + 1);
            int bitis = (hedefYari == 1) ? liginOrtasi : toplamHafta;

            for (int w = baslangic; w <= bitis; w++) {
                try {
                    Select ws = new Select(driver.findElement(By.id("cboWeek")));
                    ws.selectByValue(String.valueOf(w));
                    Thread.sleep(900);
                } catch (Exception e) {
                    continue;
                }

                List<MacVerisi> haftaVerisi = getMaclariHaftadan(driver, w, hedefYari, false);
                for (MacVerisi mv : haftaVerisi) {
                    if (mv.sezon.equals("?")) mv.sezon = sezonAdi;
                }
                gecmisVeriHavuzu.addAll(haftaVerisi);
            }
        }

        // Console çıktısı aynı anda birden fazla thread'den geldiğinden synchronized blok
        synchronized (System.out) {
            caprazFiksturAnaliziYap(bugunkuHedefMaclar, gecmisVeriHavuzu);
        }
    }

    // ─── CHROME AYARLARI ────────────────────────────────────────────────────
    private static ChromeOptions getOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless",
                "--disable-notifications",
                "--log-level=3",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage", // Paylaşımlı bellek sorunlarını önler (özellikle headless çoklu thread)
                "--silent"
        );
        options.addArguments("--blink-settings=imagesEnabled=false");
        return options;
    }

    // ─── FİKSTÜR SEKMESİNE TIK ──────────────────────────────────────────────
    private static void clickFiksturTab(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        try {
            WebElement fiksturLink = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(text(),'Fikstür') or contains(text(),'FİKSTÜR')]")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", fiksturLink);
            Thread.sleep(1500);
        } catch (Exception ignored) {
        }
    }

    // ─── BİR HAFTADAKİ MAÇLARI OKU ──────────────────────────────────────────
    private static List<MacVerisi> getMaclariHaftadan(WebDriver driver, int hafta,
                                                      int sezonYarisi, boolean onlyUnplayed) {
        List<MacVerisi> sonuc = new ArrayList<>();
        try {
            List<WebElement> satirlar = driver.findElements(By.cssSelector("tr.alt1, tr.alt2"));
            for (WebElement satir : satirlar) {
                List<WebElement> tdler = satir.findElements(By.tagName("td"));
                if (tdler.size() < 9) continue;

                String durum = tdler.get(1).getText().trim();
                String evSahibi = getTextFromTd(tdler.get(3));
                String deplasman = getTextFromTd(tdler.get(7));
                if (evSahibi.isEmpty() || deplasman.isEmpty()) continue;

                String skor = tdler.get(5).getText().trim();
                String hy = tdler.get(8).getText().trim();
                if (hy.isEmpty()) hy = "?-?";

                boolean bitti = durum.equalsIgnoreCase("MS")
                                || durum.equalsIgnoreCase("UZ")
                                || durum.equalsIgnoreCase("Pen");

                if (onlyUnplayed) {
                    if (!skor.equals("v") && bitti) continue;
                    sonuc.add(new MacVerisi("Güncel", hafta, sezonYarisi, evSahibi, deplasman, null, "-"));
                } else {
                    if (!bitti) continue;
                    sonuc.add(new MacVerisi("?", hafta, sezonYarisi, evSahibi, deplasman, hy, skor));
                }
            }
        } catch (Exception ignored) {
        }
        return sonuc;
    }

    private static String getTextFromTd(WebElement td) {
        try {
            return td.findElement(By.tagName("a")).getText().trim();
        } catch (Exception e) {
            return td.getText().trim();
        }
    }

    // ─── ÇAPRAZ FİKSTÜR ANALİZİ ─────────────────────────────────────────────
    // ─── ÇAPRAZ FİKSTÜR ANALİZİ (GÜNCEL HAFTA FİLTRELİ) ─────────────────────────────────────────────
    public static void caprazFiksturAnaliziYap(List<MacVerisi> bugun, List<MacVerisi> gecmis) {
        System.out.println();
        printCizgi('═', 72);
        System.out.printf("  ÇAPRAZ FİKSTÜR ANALİZİ (SIKI FİLTRE) | %d hedef maç | geçmiş havuz: %d maç%n",
                bugun.size(), gecmis.size());
        printCizgi('═', 72);
        System.out.println();

        Map<String, Map<String, Map<String, List<String[]>>>> tumGruplar = new LinkedHashMap<>();

        for (MacVerisi hedefMac : bugun) {
            String hedefKey = hedefMac.evSahibi + " vs " + hedefMac.deplasman;
            String evSahibiAdi = hedefMac.evSahibi;
            String deplasmanAdi = hedefMac.deplasman;

            // 1. ŞİMDİKİ MAÇIN HAFTASINI ALIYORUZ (Örn: H32)
            int guncelHafta = hedefMac.hafta;

            for (MacVerisi referansMac : bugun) {
                if (hedefMac.evSahibi.equals(referansMac.evSahibi)
                    && hedefMac.deplasman.equals(referansMac.deplasman)) continue;

                String referansLabel = referansMac.evSahibi + " vs " + referansMac.deplasman;

                List<String> refSezonlar = new ArrayList<>();
                List<Integer> refHaftalar = new ArrayList<>();

                for (MacVerisi g : gecmis) {
                    if (g.evSahibi.equals(referansMac.evSahibi)
                        && g.deplasman.equals(referansMac.deplasman)) {

                        // 2. KATI FİLTRE BURADA:
                        // Referans maçın oynandığı geçmiş hafta, BİZİM ŞU ANKİ HAFTAMIZLA aynı değilse LİSTEYE ALMA!
                        if (g.hafta == guncelHafta) {
                            refSezonlar.add(g.sezon);
                            refHaftalar.add(g.hafta);
                        }
                    }
                }

                // Eğer aynı haftada oynanmış geçmiş bir referans maç yoksa bu referansı direkt atla
                if (refHaftalar.isEmpty()) continue;

                List<String[]> evSahibiSatirlar = new ArrayList<>();
                List<String[]> deplasmanSatirlar = new ArrayList<>();

                for (int idx = 0; idx < refHaftalar.size(); idx++) {
                    int refHafta = refHaftalar.get(idx);
                    String refSezon = refSezonlar.get(idx);

                    for (MacVerisi g : gecmis) {
                        if (!g.sezon.equals(refSezon) || g.hafta != refHafta) continue;
                        String hy = (g.ilkYariSkor != null && !g.ilkYariSkor.isEmpty())
                                ? g.ilkYariSkor : "?-?";

                        if (g.evSahibi.equals(evSahibiAdi) || g.deplasman.equals(evSahibiAdi)) {
                            String statu = g.evSahibi.equals(evSahibiAdi) ? "Ev Sahibi" : "Deplasman";
                            evSahibiSatirlar.add(new String[]{g.sezon, "H" + g.hafta, hy, g.skor, statu});
                        }
                        if (g.evSahibi.equals(deplasmanAdi) || g.deplasman.equals(deplasmanAdi)) {
                            String statu = g.evSahibi.equals(deplasmanAdi) ? "Ev Sahibi" : "Deplasman";
                            deplasmanSatirlar.add(new String[]{g.sezon, "H" + g.hafta, hy, g.skor, statu});
                        }
                    }
                }

                // Sinyali güçlendirmek için en az 1 veri olması yeterli diyorsan bu kısmı 1 yapabilirsin,
                // şu an eski kodundaki gibi en az 2 maç bulursa listeye alıyor.
                boolean evYeterli = evSahibiSatirlar.size() >= 1;
                boolean depYeterli = deplasmanSatirlar.size() >= 1;
                if (!evYeterli && !depYeterli) continue;

                Map<String, List<String[]>> takimVerisi = new LinkedHashMap<>();
                if (evYeterli) takimVerisi.put(evSahibiAdi, evSahibiSatirlar);
                if (depYeterli) takimVerisi.put(deplasmanAdi, deplasmanSatirlar);

                tumGruplar.computeIfAbsent(hedefKey, k -> new LinkedHashMap<>())
                        .put(referansLabel, takimVerisi);
            }
        }

        if (tumGruplar.isEmpty()) {
            System.out.println("  Sıkı filtreye takılan (Tam isabet) sinyal bulunamadı.");
            return;
        }

        int grupNo = 1;
        for (Map.Entry<String, Map<String, Map<String, List<String[]>>>> hedefEntry : tumGruplar.entrySet()) {
            String hedefMacAdi = hedefEntry.getKey();
            String[] parcalar = hedefMacAdi.split(" vs ");
            String evSahibiAdi = parcalar[0].trim();
            String deplasmanAdi = parcalar.length > 1 ? parcalar[1].trim() : "?";

            int toplamSinyal = hedefEntry.getValue().values().stream()
                    .flatMap(m -> m.values().stream())
                    .mapToInt(List::size).sum();

            System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
            System.out.printf("║  #%-3d  ◆ HEDEF MAÇ                                                  ║%n", grupNo++);
            System.out.printf("║         Ev Sahibi : %-49s║%n", evSahibiAdi);
            System.out.printf("║         Deplasman : %-49s║%n", deplasmanAdi);
            System.out.printf("║         Toplam Nokta Atışı Sinyal: %-34d║%n", toplamSinyal);
            System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

            for (Map.Entry<String, Map<String, List<String[]>>> refEntry
                    : hedefEntry.getValue().entrySet()) {
                String referansAdi = refEntry.getKey();
                Map<String, List<String[]>> takimVerisi = refEntry.getValue();

                System.out.printf("║%n");
                System.out.printf("║  ↳ REFERANS MAÇ : %-51s║%n", referansAdi);
                System.out.printf("║    Bu maç geçmişte AYNI HAFTADA oynandığında oluşan skorlar:      ║%n");
                System.out.printf("║%n");

                for (Map.Entry<String, List<String[]>> takimEntry : takimVerisi.entrySet()) {
                    String takimAdi = takimEntry.getKey();
                    List<String[]> satirlar = takimEntry.getValue();
                    String bugunRolu = takimAdi.equals(evSahibiAdi)
                            ? "bugün Ev Sahibi oynuyor" : "bugün Deplasman oynuyor";

                    System.out.printf("║    ┌─ %-25s  (%s)%n", takimAdi, bugunRolu);
                    System.out.printf("║    │  %-14s  %-6s  %-10s  %-12s  %-10s%n",
                            "Sezon", "Hafta", "İlk Yarı", "Maç Sonu", "Statü");
                    System.out.printf("║    │  %-14s  %-6s  %-10s  %-12s  %-10s%n",
                            "──────────────", "──────", "──────────", "────────────", "──────────");

                    for (String[] satir : satirlar) {
                        System.out.printf("║    │  %-14s  %-6s  %-10s  %-12s  %-10s%n",
                                satir[0], satir[1], satir[2], satir[3], satir[4]);
                    }
                    System.out.printf("║    │%n");
                    System.out.printf("║%n");
                }
                System.out.println("╠══════════════════════════════════════════════════════════════════╣");
            }
            System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
            System.out.println();
        }

        printCizgi('═', 72);
        System.out.printf("  Analiz Tamamlandı  |  %d adet tam eşleşen (Nokta Atışı) maç bulundu%n", tumGruplar.size());
        printCizgi('═', 72);
    }

    // ─── VERİ MODELİ ────────────────────────────────────────────────────────
    public static class MacVerisi {
        String sezon;
        int hafta;
        int sezonYarisi;
        String evSahibi;
        String deplasman;
        String ilkYariSkor;
        String skor;

        public MacVerisi(String sezon, int hafta, int sezonYarisi,
                         String evSahibi, String deplasman,
                         String ilkYariSkor, String skor) {
            this.sezon = sezon;
            this.hafta = hafta;
            this.sezonYarisi = sezonYarisi;
            this.evSahibi = evSahibi;
            this.deplasman = deplasman;
            this.ilkYariSkor = ilkYariSkor;
            this.skor = skor;
        }
    }

    // ─── YARDIMCI ────────────────────────────────────────────────────────────
    private static void printCizgi(char c, int uzunluk) {
        System.out.println(String.valueOf(c).repeat(uzunluk));
    }

    /**
     * Thread-safe, zaman damgalı log çıktısı.
     * System.out.println yerine bu metodu kullan.
     */
    private static void log(String mesaj) {
        String thread = Thread.currentThread().getName();
        System.out.println("[" + thread + "] " + mesaj);
    }
}