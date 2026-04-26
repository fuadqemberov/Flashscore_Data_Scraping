package analyzer.flashscore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class MatchDetailScraper {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build();

    public static void scrapeMatch(MatchData md) {
        int maxRetry = 3;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                if (attempt > 1) Thread.sleep(2000 + (long)(Math.random() * 3000));

                // 1. HTTP GET ile maç sayfasından skor ve takım adlarını çek
                extractMatchInfo(md);

                // 2. JSON API'den Bet365 oranlarını çek
                extractOddsFromJson(md);

                return; // Başarılı

            } catch (Exception e) {
                if (attempt == maxRetry) {
                    AppLogger.log("   [ERR] " + md.matchId + " -> " + e.getMessage());
                } else {
                    AppLogger.log("   [RETRY " + attempt + "/" + maxRetry + "] " + md.matchId);
                }
            }
        }
    }

    // ─── Maç Bilgisi: HTTP GET + JSoup HTML Parse ────────────────────────────

    private static void extractMatchInfo(MatchData md) throws IOException, InterruptedException {
        String url = ScraperConstants.MATCH_URL_PREFIX + md.matchId;
        String html = httpGet(url);
        Document doc = Jsoup.parse(html);

        // 1. Takım İsimleri (Alternatif seçicilerle daha sağlam hale getirildi)
        Element homeTeamElem = doc.selectFirst(".duelParticipant__home .participant__participantName, .duelParticipant__home .participant__nm, .duelParticipant__home a[class*='participantLink']");
        Element awayTeamElem = doc.selectFirst(".duelParticipant__away .participant__participantName, .duelParticipant__away .participant__nm, .duelParticipant__away a[class*='participantLink']");

        if (homeTeamElem != null) md.homeTeam = homeTeamElem.text().trim();
        if (awayTeamElem != null) md.awayTeam = awayTeamElem.text().trim();

        // 2. Maç Tarihi/Saati
        Element startTime = doc.selectFirst(".duelParticipant__startTime, .startTime, div[class*='startTime']");
        if (startTime != null) {
            md.matchDateTime = startTime.text().trim();
        }

        // 3. Skorlar (FT Score)
        // Flashscore bazen skoru ayrı spanlarda, bazen tek bir div içinde tutar
        Element scoreWrapper = doc.selectFirst(".detailScore__wrapper");
        if (scoreWrapper != null) {
            String fullScore = scoreWrapper.text().replace("\n", "").trim();
            // Eğer "1-0" gibi değilse spanları tek tek birleştir
            Elements scoreSpans = scoreWrapper.select("span");
            if (scoreSpans.size() >= 3) {
                md.ftScore = scoreSpans.get(0).text() + " - " + scoreSpans.get(2).text();
            } else {
                md.ftScore = fullScore;
            }
        }

        // 4. İlk Yarı Skoru (HT Score)
        // Genelde "1st Half (1 - 0)" şeklinde bir yapıda bulunur
        Element htElement = doc.selectFirst("span[data-testid='wcl-scores-overline-02'], .detailScore__status .smv__incidentsHeader");
        if (htElement != null) {
            String htText = htElement.text().trim();
            // Sadece parantez içini veya skoru temizle
            md.htScore = htText.replaceAll("[^0-9\\- ]", "").trim();
        }

        // 5. Ülke ve Lig (Breadcrumb yapısı)
        // Flashscore'da hiyerarşi: Football > Country > League
        Elements breadcrumbs = doc.select(".breadcrumb__item, .wcl-breadcrumbItem_8btmf, span[data-testid='wcl-scores-overline-03']");
        if (breadcrumbs.size() >= 3) {
            // Genelde index 1 Ülke, index 2 Lig'dir
            md.country = breadcrumbs.get(1).text().replace(">", "").trim();
            md.league = breadcrumbs.get(2).text().replace(">", "").trim();
        } else if (breadcrumbs.size() == 2) {
            md.league = breadcrumbs.get(1).text().trim();
        }

        // Eğer hala boşsa Tournament header'a bak (B Planı)
        if (md.league.equals("-")) {
            Element tournamentLink = doc.selectFirst(".tournamentHeader__country a");
            if (tournamentLink != null) {
                String fullTour = tournamentLink.text();
                if (fullTour.contains(":")) {
                    String[] parts = fullTour.split(":");
                    md.country = parts[0].trim();
                    md.league = parts[1].trim();
                } else {
                    md.league = fullTour;
                }
            }
        }
    }

    // ─── Odds: JSON API ──────────────────────────────────────────────────────

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

        // Önce eventParticipantId → home/away eşlemesini bul
        // homeTeam'in participant ID'sini belirlemek için settings'deki ilk odds'a bakıyoruz
        // Basit yaklaşım: ilk HOME_DRAW_AWAY FULL_TIME'daki ilk odds item = home
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
                        if (homeParticipantId == null) {
                            homeParticipantId = item.getString("eventParticipantId");
                        } else if (awayParticipantId == null
                                && !item.getString("eventParticipantId").equals(homeParticipantId)) {
                            awayParticipantId = item.getString("eventParticipantId");
                        }
                    }
                }
                break;
            }
        }

        // Şimdi tüm Bet365 odds'larını işle
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

    // ─── Scope → Period ──────────────────────────────────────────────────────

    private static String scopeToPeriod(String scope) {
        return switch (scope) {
            case "FULL_TIME"    -> "Full Time";
            case "FIRST_HALF"   -> "1st Half";
            case "SECOND_HALF"  -> "2nd Half";
            default             -> null;
        };
    }

    // ─── 1X2 ─────────────────────────────────────────────────────────────────

    private static void processHDA(MatchData md, JSONArray items, String period,
                                   String homeId, String awayId) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String val = getValue(item);
            String pid = item.isNull("eventParticipantId") ? null : item.getString("eventParticipantId");

            if (pid == null) {
                md.oddsMap.put("1x2|" + period + "|Draw", val);
            } else if (pid.equals(homeId)) {
                md.oddsMap.put("1x2|" + period + "|Home", val);
            } else {
                md.oddsMap.put("1x2|" + period + "|Away", val);
            }
        }
    }

    // ─── Over/Under ──────────────────────────────────────────────────────────

    private static void processOU(MatchData md, JSONArray items, String period) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.isNull("handicap")) continue;

            double threshold = item.getJSONObject("handicap").getDouble("value");
            if (!ScraperConstants.OU_THRESHOLDS.contains(threshold)) continue;

            String selection = item.isNull("selection") ? null : item.getString("selection");
            String val = getValue(item);

            if ("OVER".equals(selection)) {
                md.oddsMap.put("Over/Under|" + period + "|O " + threshold, val);
            } else if ("UNDER".equals(selection)) {
                md.oddsMap.put("Over/Under|" + period + "|U " + threshold, val);
            }
        }
    }

    // ─── Both Teams To Score ─────────────────────────────────────────────────

    private static void processBTTS(MatchData md, JSONArray items, String period) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.isNull("bothTeamsToScore")) continue;

            boolean yes = item.getBoolean("bothTeamsToScore");
            String val = getValue(item);
            md.oddsMap.put("Both teams|" + period + "|" + (yes ? "Yes" : "No"), val);
        }
    }

    // ─── Double Chance ────────────────────────────────────────────────────────

    private static void processDC(MatchData md, JSONArray items, String period,
                                  String homeId, String awayId) {
        // DC: 3 item: draw+home (1X), home+away (12), draw+away (X2)
        // JSON'da: eventParticipantId=null → 12, homeId → 1X, awayId → X2
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String val = getValue(item);
            String pid = item.isNull("eventParticipantId") ? null : item.getString("eventParticipantId");

            if (pid == null) {
                md.oddsMap.put("Double chance|" + period + "|12", val);
            } else if (pid.equals(homeId)) {
                md.oddsMap.put("Double chance|" + period + "|1X", val);
            } else {
                md.oddsMap.put("Double chance|" + period + "|X2", val);
            }
        }
    }

    // ─── Correct Score ────────────────────────────────────────────────────────

    private static void processCS(MatchData md, JSONArray items, String period) {
        if ("2nd Half".equals(period)) return; // 2. yarı CS yok

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.isNull("score")) continue;

            String score = item.getString("score").replace(":", ":");
            // JSON'da "3:1" gibi geliyor, normalize et
            String normalized = normalizeScore(score);

            if (ScraperConstants.CORRECT_SCORES.contains(normalized)) {
                md.oddsMap.put("Correct score|" + period + "|" + normalized, getValue(item));
            }
        }
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    /** opening (ilk) oranı döndürür, yoksa "-" */
    private static String getValue(JSONObject item) {
        try {
            if (!item.isNull("opening")) {
                return item.getString("opening");
            }
            if (!item.isNull("value")) {
                return item.getString("value");
            }
        } catch (Exception ignored) {}
        return "-";
    }

    /** JSON skor formatını normalize et: "3:1" → "3:1" */
    private static String normalizeScore(String raw) {
        return raw.replace(" ", "");
    }

    // ─── HTTP GET ─────────────────────────────────────────────────────────────

    private static String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
                .header("Accept-Language", "en-GB,en;q=0.9")
                .header("Referer", "https://www.flashscore.co.uk/")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for: " + url);
        }
        return response.body();
    }
}