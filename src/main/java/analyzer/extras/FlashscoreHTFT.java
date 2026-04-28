package analyzer.extras;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FlashscoreHTFT {

    public static void main(String[] args) {

        String matchId = "vNHjRaYj";   // öz matç ID-n ilə dəyişdir

        String url = "https://5.flashscore.ninja/5/x/feed/df_sui_1_" + matchId;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .header("x-fsign", "SW9D1eZo")
                    .header("Referer", "https://www.flashscore.co.uk/")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Status Code: " + response.statusCode());

            if (response.statusCode() == 200) {
                parseHTFTImproved(response.body());
            } else {
                System.out.println("Sorğu xətası: " + response.statusCode());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseHTFTImproved(String body) {
        String htHome = "?", htAway = "?";
        String ftHome = "?", ftAway = "?";

        String[] sections = body.split("~");

        for (String section : sections) {
            String[] parts = section.split("¬");

            // Find which half this section belongs to
            String halfLabel = null;
            String ig = null, ih = null;

            for (String part : parts) {
                if (part.startsWith("AC÷")) {
                    halfLabel = part.substring(3).trim();
                } else if (part.startsWith("IG÷")) {
                    ig = part.substring(3).trim();
                } else if (part.startsWith("IH÷")) {
                    ih = part.substring(3).trim();
                }
            }

            if (halfLabel == null || ig == null || ih == null) continue;

            if (halfLabel.equals("1st Half")) {
                htHome = ig;
                htAway = ih;
            } else if (halfLabel.equals("2nd Half")) {
                try {
                    ftHome = String.valueOf(Integer.parseInt(htHome) + Integer.parseInt(ig));
                    ftAway = String.valueOf(Integer.parseInt(htAway) + Integer.parseInt(ih));
                } catch (Exception ignored) {
                    ftHome = ig;
                    ftAway = ih;
                }
            }
        }

        System.out.println("\n══════════════════════════════");
        System.out.println("          HT SKOR :  " + htHome + " - " + htAway);
        System.out.println("          FT SKOR :  " + ftHome + " - " + ftAway);
        System.out.println("══════════════════════════════");
    }
}