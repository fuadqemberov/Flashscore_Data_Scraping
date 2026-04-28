package analyzer.util;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TeamIdsFetcher {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();



    public static List<String> fetchMatchIdsFromAPI() {
        List<String> ids = new ArrayList<>();
        try {
            String apiUrl = "https://vd.mackolik.com/livedata?group=0";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Pattern p = Pattern.compile("\\[(\\d{7}),");
            Matcher m = p.matcher(response.body());
            while (m.find()) {
                ids.add(m.group(1));
            }
            System.out.println("Toplam " + ids.size() + " adet maç ID'si bulundu.");
        } catch (Exception e) {
            System.err.println("❌ API Hatası: " + e.getMessage());
        }
        return ids;
    }
}
