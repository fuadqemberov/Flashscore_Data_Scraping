package analyzer.flashscore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class MatchDetailScraper {

    // 1. DÜZELTME: HTTP_1_1 zorunlu kılındı. "too many concurrent streams" hatasını kesin çözer.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void scrapeMatch(MatchData md) {
        try {
            extractHTFTFromAPI(md);
            extractOddsFromJson(md);
        } catch (Exception e) {
            AppLogger.log("   [ERR] " + md.matchId + " -> Hata: " + e.getMessage());
        }
    }

    private static void extractHTFTFromAPI(MatchData md) {
        String url = "https://5.flashscore.ninja/5/x/feed/df_sui_1_" + md.matchId;

        // HT/FT için 3 defa deneme hakkı veriyoruz (Anlık bağlantı kopmalarına karşı)
        for (int i = 0; i < 3; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "*/*")
                        .header("x-fsign", "SW9D1eZo")
                        .header("Referer", "https://www.flashscore.co.uk/")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body() != null) {
                    parseHTFTImproved(md, response.body());
                    break; // Başarılıysa döngüden çık
                } else {
                    Thread.sleep(1000); // Başarısızsa 1 saniye bekle tekrar dene
                }
            } catch (Exception ignored) {
                try { Thread.sleep(1000); } catch (Exception e) {}
            }
        }
    }

    private static void parseHTFTImproved(MatchData md, String body) {
        String htHome = "-", htAway = "-";
        String ftHome = "-", ftAway = "-";

        String[] sections = body.split("~");

        for (String section : sections) {
            String[] parts = section.split("¬");

            String halfLabel = null;
            String ig = null, ih = null;

            for (String part : parts) {
                if (part.startsWith("AC÷")) halfLabel = part.substring(3).trim();
                else if (part.startsWith("IG÷")) ig = part.substring(3).trim();
                else if (part.startsWith("IH÷")) ih = part.substring(3).trim();
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

        if (!htHome.equals("-") && !htAway.equals("-")) md.htScore = htHome + "-" + htAway;
        if (!ftHome.equals("-") && !ftAway.equals("-")) md.ftScore = ftHome + "-" + ftAway;
    }

    private static void extractOddsFromJson(MatchData md) {
        String url = String.format(ScraperConstants.ODDS_API_URL, md.matchId);
        String jsonBody = null;

        // 2. DÜZELTME: Anti-Ban Retry Sistemi (JSON Parse hatasını çözer)
        for (int i = 0; i < 3; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Accept", "application/json") // JSON istediğimizi özellikle belirtiyoruz
                        .GET().build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                // Kod 200 (OK) ise ve Gelen Metin "{" (JSON objesi) ile başlıyorsa kabul et
                if (response.statusCode() == 200 && response.body() != null && response.body().trim().startsWith("{")) {
                    jsonBody = response.body();
                    break; // Başarılı
                } else {
                    // Sunucu bizi blokladı veya HTML gönderdi, 1.5 saniye bekle ve tekrar dene
                    Thread.sleep(1500);
                }
            } catch (Exception ignored) {
                try { Thread.sleep(1500); } catch (Exception e) {}
            }
        }

        // 3 denemede de JSON alınamadıysa, programın çökmemesi için sessizce çık
        if (jsonBody == null) {
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonBody);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return;

            JSONObject oddsData = data.optJSONObject("findOddsByEventId");
            if (oddsData == null) return;

            JSONArray oddsList = oddsData.optJSONArray("odds");
            if (oddsList == null) return;

            String homeParticipantId = null;
            String awayParticipantId = null;

            for (int i = 0; i < oddsList.length(); i++) {
                JSONObject entry = oddsList.getJSONObject(i);
                if (entry.getInt("bookmakerId") == ScraperConstants.BET365_ID
                    && "HOME_DRAW_AWAY".equals(entry.getString("bettingType"))
                    && "FULL_TIME".equals(entry.getString("bettingScope"))) {

                    JSONArray items = entry.getJSONArray("odds");
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject item = items.getJSONObject(j);
                        if (!item.isNull("eventParticipantId")) {
                            if (homeParticipantId == null) homeParticipantId = item.getString("eventParticipantId");
                            else if (awayParticipantId == null && !item.getString("eventParticipantId").equals(homeParticipantId))
                                awayParticipantId = item.getString("eventParticipantId");
                        }
                    }
                    break;
                }
            }

            for (int i = 0; i < oddsList.length(); i++) {
                JSONObject entry = oddsList.getJSONObject(i);
                if (entry.getInt("bookmakerId") != ScraperConstants.BET365_ID) continue;

                String bettingType = entry.getString("bettingType");
                String scope = entry.getString("bettingScope");
                String period = scopeToPeriod(scope);
                if (period == null) continue;

                JSONArray items = entry.getJSONArray("odds");

                switch (bettingType) {
                    case "HOME_DRAW_AWAY" -> processHDA(md, items, period, homeParticipantId, awayParticipantId);
                    case "OVER_UNDER"     -> processOU(md, items, period);
                    case "BOTH_TEAMS_TO_SCORE" -> processBTTS(md, items, period);
                    case "DOUBLE_CHANCE"  -> processDC(md, items, period, homeParticipantId, awayParticipantId);
                    case "CORRECT_SCORE"  -> processCS(md, items, period);
                }
            }
        } catch (Exception e) {
            // Sadece logla, uygulamayı patlatma
        }
    }

    private static String scopeToPeriod(String scope) {
        return switch (scope) {
            case "FULL_TIME"    -> "Full Time";
            case "FIRST_HALF"   -> "1st Half";
            case "SECOND_HALF"  -> "2nd Half";
            default             -> null;
        };
    }

    private static void processHDA(MatchData md, JSONArray items, String period, String homeId, String awayId) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String val = getValue(item);
            String pid = item.isNull("eventParticipantId") ? null : item.getString("eventParticipantId");
            if (pid == null) md.oddsMap.put("1x2|" + period + "|Draw", val);
            else if (pid.equals(homeId)) md.oddsMap.put("1x2|" + period + "|Home", val);
            else md.oddsMap.put("1x2|" + period + "|Away", val);
        }
    }

    private static void processOU(MatchData md, JSONArray items, String period) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.isNull("handicap")) continue;
            double threshold = item.getJSONObject("handicap").getDouble("value");
            if (!ScraperConstants.OU_THRESHOLDS.contains(threshold)) continue;
            String selection = item.isNull("selection") ? null : item.getString("selection");
            String val = getValue(item);
            if ("OVER".equals(selection)) md.oddsMap.put("Over/Under|" + period + "|O " + threshold, val);
            else if ("UNDER".equals(selection)) md.oddsMap.put("Over/Under|" + period + "|U " + threshold, val);
        }
    }

    private static void processBTTS(MatchData md, JSONArray items, String period) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.isNull("bothTeamsToScore")) continue;
            boolean yes = item.getBoolean("bothTeamsToScore");
            md.oddsMap.put("Both teams|" + period + "|" + (yes ? "Yes" : "No"), getValue(item));
        }
    }

    private static void processDC(MatchData md, JSONArray items, String period, String homeId, String awayId) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String val = getValue(item);
            String pid = item.isNull("eventParticipantId") ? null : item.getString("eventParticipantId");
            if (pid == null) md.oddsMap.put("Double chance|" + period + "|12", val);
            else if (pid.equals(homeId)) md.oddsMap.put("Double chance|" + period + "|1X", val);
            else md.oddsMap.put("Double chance|" + period + "|X2", val);
        }
    }

    private static void processCS(MatchData md, JSONArray items, String period) {
        if ("2nd Half".equals(period)) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.isNull("score")) continue;
            String score = item.getString("score").replace(" ", "");
            if (ScraperConstants.CORRECT_SCORES.contains(score)) {
                md.oddsMap.put("Correct score|" + period + "|" + score, getValue(item));
            }
        }
    }

    private static String getValue(JSONObject item) {
        try {
            if (!item.isNull("opening")) return item.getString("opening");
            if (!item.isNull("value")) return item.getString("value");
        } catch (Exception ignored) {}
        return "-";
    }
}