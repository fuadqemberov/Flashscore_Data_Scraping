package analyzer.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TeamIdsFetcher {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static List<String> fetchUnstartedTeamIds() {
        Set<String> teamIds = new HashSet<>();

        try {
            String apiUrl = "https://vd.mackolik.com/livedata?group=0";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            int mIndex = body.indexOf("\"m\":[[");
            if (mIndex == -1) return new ArrayList<>();
            String matchesPart = body.substring(mIndex);
            Pattern p = Pattern.compile("\\[\\d+,(\\d+),\"[^\"]*\",(\\d+),\"[^\"]*\",0,\"\",");
            Matcher m = p.matcher(matchesPart);

            while (m.find()) {
                teamIds.add(m.group(1)); // Ev sahibi ID
                teamIds.add(m.group(2)); // Deplasman ID
            }

            System.out.println("Başlamamış maçlardan toplam " + teamIds.size() + " adet benzersiz Takım ID'si alındı.");

        } catch (Exception e) {
            System.err.println("❌ API Hatası: " + e.getMessage());
        }
        return new ArrayList<>(teamIds);
    }

}