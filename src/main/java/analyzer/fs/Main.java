package analyzer.fs;

import analyzer.fs.exporter.ExcelExporter;
import analyzer.fs.model.ScrapeResult;
import analyzer.fs.scraper.FlashscoreScraper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         ⚡ FLASHSCORE SCRAPER v1.0 - Console App          ║");
        System.out.println("║     Ultra Fast | Virtual Threads | Excel Export            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        System.out.println("Seçenekler:");
        System.out.println("  [1] Tüm dünyadaki tüm ligleri çek (ÇOK UZUN SÜREBİLİR)");
        System.out.println("  [2] Belirli bir lig çek (örn: İngiltere Premier Lig)");
        System.out.println("  [3] Sadece ülkeleri listele");
        System.out.print(" Seçiminiz (1/2/3): ");

        String choice = scanner.nextLine().trim();

        try (FlashscoreScraper scraper = new FlashscoreScraper()) {
            ScrapeResult result;
            String filename;

            switch (choice) {
                case "1" -> {
                    System.out.println("🚀 TÜM DÜNYA LİGLERİ ÇEKİLİYOR...");
                    System.out.println("⚠️ Bu işlem saatler sürebilir!");
                    result = scraper.scrapeAll();
                    filename = "flashscore_all_" + timestamp() + ".xlsx";
                }
                case "2" -> {
                    System.out.print(" Ülke kodu (örn: england): ");
                    String country = scanner.nextLine().trim().toLowerCase();
                    System.out.print("Lig slug (örn: premier-league): ");
                    String league = scanner.nextLine().trim().toLowerCase();

                    System.out.println("🚀 " + country + "/" + league + " çekiliyor...");
                    result = scraper.scrapeLeague(country, league);
                    filename = "flashscore_" + country + "_" + league + "_" + timestamp() + ".xlsx";
                }
                case "3" -> {
                    var countries = scraper.fetchCountries();
                    System.out.println("📋 ÜLKELER:");
                    countries.forEach(c -> System.out.println("  • " + c));
                    return;
                }
                default -> {
                    System.out.println("❌ Geçersiz seçim!");
                    return;
                }
            }

            // Excel'e export
            System.out.println("📊 Excel'e aktarılıyor...");
            new ExcelExporter().export(result, filename);

            System.out.println("✅ İŞLEM TAMAMLANDI!");
            System.out.println("📁 Dosya: " + filename);

        } catch (Exception e) {
            System.err.println("❌ HATA: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}
