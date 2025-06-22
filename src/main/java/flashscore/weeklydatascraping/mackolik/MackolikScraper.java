package flashscore.weeklydatascraping.mackolik;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MackolikScraper {

    // 1. Adım: Puan durumu sayfasından takım ID'lerini çeken metod
    public static List<String> getTeamIds() throws IOException {
        String standingsUrl = "https://arsiv.mackolik.com/Puan-Durumu/15/";
        List<String> teamIds = new ArrayList<>();

        System.out.println("Puan durumu sayfasından takım ID'leri çekiliyor...");
        System.out.println("URL: " + standingsUrl);

        // Jsoup ile URL'e bağlanıp HTML'i alıyoruz
        Document doc = Jsoup.connect(standingsUrl).get();

        // CSS seçicisi ile istediğimiz linkleri buluyoruz.
        // id'si 'tblStanding' olan tablonun içindeki, class'ı 'td-team-name' olan
        // hücrelerin içindeki 'a' (link) etiketlerini seçiyoruz.
        Elements teamLinks = doc.select("#tblStanding .td-team-name a");

        // Her bir link elemanı için
        for (Element link : teamLinks) {
            String href = link.attr("href"); // Linkin href özelliğini al (örn: //arsiv.mackolik.com/Takim/171/Atalanta/...)

            // href içinden takım ID'sini (sayısal değeri) regex ile güvenli bir şekilde alalım
            Pattern pattern = Pattern.compile("/Takim/(\\d+)/");
            Matcher matcher = pattern.matcher(href);

            if (matcher.find()) {
                String teamId = matcher.group(1); // Eşleşen ilk grup (yani sayılar)
                teamIds.add(teamId);
                System.out.println("Bulunan Takım: " + link.text().trim() + " -> ID: " + teamId);
            }
        }

        System.out.println("\nToplam " + teamIds.size() + " adet takım ID'si bulundu.\n");
        return teamIds;
    }

    // 2. Adım: Belirtilen takım ID'si için takım sayfasını ziyaret eden metod
    public static void processTeamPage(String teamId) {
        String teamUrl = "https://arsiv.mackolik.com/Takim/" + teamId + "/";

        try {
            System.out.println("ID " + teamId + " için istek gönderiliyor -> URL: " + teamUrl);

            // Jsoup ile takımın sayfasına bağlanıyoruz
            Document teamPage = Jsoup.connect(teamUrl).get();

            // Örnek olarak takım sayfasının başlığını alalım
            // Sen bu kısımda takım sayfasının HTML'ini inceleyip istediğin veriyi (örn: oyuncu listesi) çekebilirsin.
            String pageTitle = teamPage.title();

            System.out.println("  -> Başarılı! Sayfa başlığı: " + pageTitle);

        } catch (IOException e) {
            System.err.println("ID " + teamId + " için sayfa alınırken hata oluştu: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        try {
            // 1. Adım: Tüm takım ID'lerini al
            List<String> allTeamIds = getTeamIds();

            // 2. Adım: Her bir ID için takım sayfasını işle
            for (String teamId : allTeamIds) {
                processTeamPage(teamId);

                // Sunucuyu yormamak için her istek arasına küçük bir bekleme eklemek iyi bir pratiktir.
                Thread.sleep(500); // 0.5 saniye bekle
            }

            System.out.println("\nTüm işlemler tamamlandı.");

        } catch (IOException e) {
            System.err.println("Ana işlem sırasında bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("Bekleme sırasında kesinti oldu: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}