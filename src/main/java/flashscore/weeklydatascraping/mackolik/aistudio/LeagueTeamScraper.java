package flashscore.weeklydatascraping.mackolik.aistudio;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bu sınıf, mackolik.com'un arşivlenmiş puan durumu sayfasından
 * bir ligdeki tüm takımların ID'lerini ve isimlerini çeker.
 */
public class LeagueTeamScraper {

    // ****** DEĞİŞİKLİK ******
    // Temel URL'ye sezon için de bir yer tutucu eklendi (%s).
    private static final String STANDINGS_URL_TEMPLATE = "https://arsiv.mackolik.com/Puan-Durumu/%s/INGILTERE-Premier-Lig";


    public static Map<String, String> fetchTeamIdsAndNames(String leagueId) {
        Map<String, String> teams = new LinkedHashMap<>();


        String url = String.format(STANDINGS_URL_TEMPLATE, leagueId);

        System.out.println("Takım ID'leri çekiliyor: " + url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Elements teamLinks = doc.select("table#tblStanding td.td-team-name a");

            for (Element link : teamLinks) {
                String href = link.attr("href").replaceFirst("^/+", ""); // baştaki / veya // silinir
                String teamName = link.text().trim();

                // href şimdi: arsiv.mackolik.com/Takim/20/Arsenal/2025%2F2026
                String[] parts = href.split("/");

                // parts[1] = "Takim", parts[2] = takım ID
                if (parts.length > 3 && parts[1].equals("Takim")) {
                    String teamId = parts[2];
                    teams.put(teamId, teamName);
                }
            }

        } catch (IOException e) {
            System.err.println("URL'ye bağlanırken bir hata oluştu: " + url);
            e.printStackTrace();
        }

        return teams;
    }

    /**
     * Bu sınıfın nasıl çalıştığını gösteren ana metod.
     */
    public static void main(String[] args) {
        String premierLeagueId = "24";

        Map<String, String> premierLeagueTeams = fetchTeamIdsAndNames(premierLeagueId);

        if (!premierLeagueTeams.isEmpty()) {
            System.out.println("\n" + premierLeagueTeams.size() + " adet takım bulundu ");
            for (Map.Entry<String, String> entry : premierLeagueTeams.entrySet()) {
                System.out.println("Takım: " + entry.getValue() + ", ID: " + entry.getKey());
            }

            // Sadece takım ID'lerini yazdır
            System.out.println("\nTakım ID'leri:");
            for (String teamId : premierLeagueTeams.keySet()) {
                System.out.println(teamId);
            }
        } else {
            System.out.println("Belirtilen lig için takım bulunamadı veya bir hata oluştu.");
        }

        System.out.println("\n----------------------------------\n");
    }
}