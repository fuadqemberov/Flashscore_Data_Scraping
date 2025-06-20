package flashscore.weeklydatascraping.mackolik.aistudio;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExcelGeneratorFromIds {

    // Programın ana klasöründe bulunan "excel" adında bir klasör hedefliyoruz.
    private static final String OUTPUT_FOLDER = "excel";

    public static void main(String[] args) {
        // 1. ADIM: "excel" klasörünün var olduğundan emin ol, yoksa oluştur.
        createOutputDirectory();

        // 2. ADIM: Dosyadan takım ID'lerini oku.
        List<String> teamIds = readTeamIdsFromFile("takim_idleri.txt");

        if (teamIds.isEmpty()) {
            System.out.println("ID listesi boş veya dosya okunamadı. İşlem sonlandırılıyor.");
            return;
        }

        System.out.println(teamIds.size() + " adet takım ID'si bulundu. İşlem başlıyor: " + teamIds);

        // 3. ADIM: Her bir ID için döngüye gir ve Excel dosyasını oluştur.
        for (String currentTeamId : teamIds) {
            // Trim metodu, ID'nin başında veya sonunda olabilecek boşlukları temizler.
            processAndGenerateExcelForTeam(currentTeamId.trim());
        }

        System.out.println("\nTüm işlemler tamamlandı. Excel dosyaları '" + OUTPUT_FOLDER + "' klasörüne kaydedildi.");
    }

    /**
     * Belirtilen takım ID'si için veri toplama ve Excel oluşturma işlemlerini yönetir.
     * @param teamId İşlenecek takımın ID'si.
     */
    private static void processAndGenerateExcelForTeam(String teamId) {
        System.out.println("--------------------------------------------------");
        System.out.println("Takım ID: " + teamId + " için işlem başlatıldı...");

        // Bu kısımda senin Selenium ile veri çekme kodun yer alacak.
        // Örnek olarak, çektiğimizi varsaydığımız basit veriler oluşturalım:
        // List<MatchData> matchData = fetchMatchDataForTeam(teamId); // Senin metodun

        // Excel dosyasını oluşturma ve yazma işlemi
        try (Workbook workbook = new XSSFWorkbook()) { // .xlsx formatı için XSSFWorkbook
            Sheet sheet = workbook.createSheet("Lig Analizi " + teamId);

            // Başlık satırını oluştur (Örnek)
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Takım ID");
            headerRow.createCell(1).setCellValue("Sezon");
            headerRow.createCell(2).setCellValue("Maç Sonucu");
            headerRow.createCell(3).setCellValue("Gol Sayısı");

            // Örnek Veri Yazma (Sen bu kısmı kendi veri döngünle dolduracaksın)
            for (int i = 1; i <= 5; i++) { // Örnek olarak 5 satır veri yazalım
                Row dataRow = sheet.createRow(i);
                dataRow.createCell(0).setCellValue(teamId);
                dataRow.createCell(1).setCellValue("2023/2024");
                dataRow.createCell(2).setCellValue("3 - " + (i % 3));
                dataRow.createCell(3).setCellValue(3 + (i % 3));
            }

            System.out.println("Takım ID: " + teamId + " için veriler Excel'e yazılmaya hazır.");

            // Dosya adını ve yolunu dinamik olarak oluştur
            String fileName = "Analiz_Sadece_Lig_Takim_" + teamId + "_2015-2025.xlsx";
            Path outputPath = Paths.get(OUTPUT_FOLDER, fileName);

            // Workbook'u dosyaya yaz
            try (FileOutputStream fileOut = new FileOutputStream(outputPath.toString())) {
                workbook.write(fileOut);
            }

            System.out.println("Başarılı: " + outputPath.toAbsolutePath() + " dosyası oluşturuldu.");

        } catch (Exception e) {
            System.err.println("HATA: Takım ID " + teamId + " için Excel dosyası oluşturulurken bir sorun oluştu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Belirtilen dosyadan virgülle ayrılmış ID'leri okur ve bir liste olarak döndürür.
     * @param fileName Okunacak dosyanın adı.
     * @return String listesi olarak takım ID'leri.
     */
    private static List<String> readTeamIdsFromFile(String fileName) {
        try {
            Path path = Paths.get(fileName);
            if (!Files.exists(path)) {
                System.err.println("HATA: '" + fileName + "' dosyası bulunamadı!");
                return Collections.emptyList(); // Boş liste döndür
            }
            String content = new String(Files.readAllBytes(path));
            if (content.trim().isEmpty()) {
                System.err.println("HATA: '" + fileName + "' dosyası boş!");
                return Collections.emptyList();
            }
            return Arrays.asList(content.split(","));
        } catch (IOException e) {
            System.err.println("HATA: Dosya okunurken bir sorun oluştu: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Programın ana dizininde "excel" adında bir klasör oluşturur. Zaten varsa bir şey yapmaz.
     */
    private static void createOutputDirectory() {
        try {
            Path path = Paths.get(OUTPUT_FOLDER);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("'" + OUTPUT_FOLDER + "' klasörü oluşturuldu.");
            }
        } catch (IOException e) {
            System.err.println("HATA: Çıktı klasörü oluşturulamadı: " + e.getMessage());
            // Programı sonlandırabiliriz çünkü dosyaları kaydedemeyeceğiz.
            System.exit(1);
        }
    }
}