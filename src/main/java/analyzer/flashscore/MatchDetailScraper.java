package analyzer.flashscore;

import com.microsoft.playwright.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchDetailScraper {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void scrapeMatch(MatchData md) {
        int maxRetry = 3;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                if (attempt > 1) Thread.sleep(2000 + (long)(Math.random() * 3000));

                // 1. Playwright ilə yalnız əsas məlumatları (Adlar, FT, HT) çək
                extractMatchWithPlaywright(md);

                // 2. JSON API'dən Bet365 oranlarını çək (Sənin ideal metodun)
                extractOddsFromJson(md);

                return; // Uğurludursa çıx

            } catch (Exception e) {
                if (attempt == maxRetry) {
                    AppLogger.log("   [ERR] " + md.matchId + " -> " + e.getMessage());
                } else {
                    AppLogger.log("   [RETRY " + attempt + "/" + maxRetry + "] " + md.matchId);
                }
            }
        }
    }

    private static void extractMatchWithPlaywright(MatchData md) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"));

            Page page = context.newPage();
            String url = "https://www.flashscore.co.uk/match/" + md.matchId + "/#/match-summary";

            try {
                page.navigate(url);

                // 1. Gözləmə: Hesab divi görünənə qədər gözlə (Arsenal matçı üçün vacibdir)
                try {
                    page.waitForSelector(".detailScore__wrapper", new Page.WaitForSelectorOptions().setTimeout(10000));
                } catch (Exception ignored) {}

                // Səhifənin tam oturması üçün yarım saniyəlik süni pauza
                page.waitForTimeout(1000);

                // 2. Komanda Adları
                md.homeTeam = page.locator(".duelParticipant__home .participant__participantName").first().innerText().trim();
                md.awayTeam = page.locator(".duelParticipant__away .participant__participantName").first().innerText().trim();

                // 3. Maç Tarixi
                Locator startTime = page.locator(".duelParticipant__startTime div").first();
                if (startTime.count() > 0) md.matchDateTime = startTime.innerText().trim();

                // 4. Skorlar (FT Score) - Alternativli
                Locator scoreWrapper = page.locator(".detailScore__wrapper").first();
                if (scoreWrapper.count() > 0) {
                    String scoreText = scoreWrapper.innerText().replaceAll("\\s+", "");
                    if (scoreText.contains("-")) md.ftScore = scoreText;
                } else {
                    // Əgər wrapper tapılmasa (Arsenal matçı kimi), tək-tək skorları tutmağa çalış
                    Locator homeS = page.locator(".detailScore__home").first();
                    Locator awayS = page.locator(".detailScore__away").first();
                    if (homeS.count() > 0 && awayS.count() > 0) {
                        md.ftScore = homeS.innerText().trim() + "-" + awayS.innerText().trim();
                    }
                }

                // 5. İlk Yarı Skoru (HT Score) - MULTI-METHOD axtarış
                try {
                    // Üsul A: Sənin göndərdiyin wcl-header daxilində axtar
                    Locator htContainer = page.locator("[data-testid='wcl-headerSection-text']");
                    String foundHt = "-";

                    for(int i=0; i < htContainer.count(); i++) {
                        String txt = htContainer.nth(i).innerText();
                        Pattern p = Pattern.compile("(\\d+\\s*-\\s*\\d+)");
                        Matcher m = p.matcher(txt);
                        if (m.find()) {
                            foundHt = m.group(1).trim();
                            break;
                        }
                    }

                    // Üsul B: Əgər hələ də tapılmadısa, Flashscore-un "incidents" başlıqlarına bax
                    if (foundHt.equals("-")) {
                        Locator incidentHeaders = page.locator(".smv__incidentsHeader");
                        for (int i = 0; i < incidentHeaders.count(); i++) {
                            String txt = incidentHeaders.nth(i).innerText();
                            if (txt.contains("-") && txt.matches(".*\\d.*")) {
                                foundHt = txt.replaceAll("[^0-9\\- ]", "").trim();
                                break;
                            }
                        }
                    }
                    md.htScore = foundHt;
                } catch (Exception ignored) {}

                // 6. Ölkə və Liqa
                Locator breadcrumbs = page.locator(".wcl-breadcrumbItem_8btmf span[data-testid='wcl-scores-overline-03']");
                if (breadcrumbs.count() >= 3) {
                    md.country = breadcrumbs.nth(1).innerText().replace(">", "").trim();
                    md.league = breadcrumbs.nth(2).innerText().replace(">", "").trim();
                }

            } catch (Exception e) {
                System.err.println("Playwright Error for " + md.matchId + ": " + e.getMessage());
            } finally {
                browser.close();
            }
        } catch (Exception e) {
            System.err.println("Playwright System Error: " + e.getMessage());
        }
    }

    private static void extractOddsFromJson(MatchData md) throws IOException, InterruptedException {
        String url = String.format(ScraperConstants.ODDS_API_URL, md.matchId);
        String json = httpGet(url);

        JSONObject root = new JSONObject(json);
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

    private static String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}