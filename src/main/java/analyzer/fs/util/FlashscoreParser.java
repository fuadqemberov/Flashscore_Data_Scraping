package analyzer.fs.util;

import analyzer.fs.MatchData;
import analyzer.fs.model.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlashscoreParser {

    private static final Pattern MATCH_ID_PATTERN = Pattern.compile("g_1_([A-Za-z0-9]+)");
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("href=\"/football/([^/]+)/\"[^>]*>([^<]+)</a>");
    private static final Pattern LEAGUE_PATTERN = Pattern.compile("href=\"/football/[^/]+/([^/]+)/\"[^>]*>([^<]+)</a>");

    private static final Pattern FSIGN_PATTERN = Pattern.compile("feed_sign\":\"([^\"]+)\"");

    public static List<Country> parseCountries(String html) {
        List<Country> countries = new ArrayList<>();
        Matcher m = COUNTRY_PATTERN.matcher(html);
        while (m.find()) {
            String code = m.group(1);
            String name = cleanText(m.group(2));
            if (!code.isEmpty() && !name.isEmpty()) {
                countries.add(new Country(code, name, FlashscoreConfig.DOMAIN + "/football/" + code + "/"));
            }
        }
        return countries;
    }

    public static List<League> parseLeagues(String html, String countryCode) {
        List<League> leagues = new ArrayList<>();
        Matcher m = LEAGUE_PATTERN.matcher(html);
        while (m.find()) {
            String slug = m.group(1);
            String name = cleanText(m.group(2));
            if (!slug.isEmpty() && !name.isEmpty()) {
                String id = slug.replaceAll("[^a-zA-Z0-9]", "-");
                leagues.add(new League(id, name, countryCode,
                        FlashscoreConfig.DOMAIN + "/football/" + countryCode + "/" + slug + "/", slug));
            }
        }
        return leagues;
    }

    // ARŞİV MANTIĞI - Düzeltilmiş Sezon Parser (Çift sezon veya 2025 hatası yok)
    public static List<Season> parseSeasons(String html, String leagueSlug, String leagueUrl) {
        List<Season> seasons = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements links = doc.select("a[href*='" + leagueSlug + "']");
        Set<String> seenYears = new LinkedHashSet<>();
        Pattern yearPattern = Pattern.compile("-(\\d{4}-\\d{4}|\\d{4})(?:/|$)");

        for (Element link : links) {
            String href = link.attr("href");
            String text = link.text().trim();

            Matcher m = yearPattern.matcher(href);
            if (m.find()) {
                String yearPart = m.group(1);

                if (!seenYears.contains(yearPart)) {
                    seenYears.add(yearPart);
                    String fullUrl = href.startsWith("http") ? href : FlashscoreConfig.DOMAIN + href;
                    if (!fullUrl.endsWith("/")) fullUrl += "/";
                    if (text.isEmpty()) text = yearPart;

                    seasons.add(new Season(yearPart, text, leagueSlug, fullUrl));
                }
            }
        }

        // Eğer hiçbir sezon bulunamazsa (Sayfa yüklenemediyse vs.) ana sayfayı varsayılan olarak ekle
        if (seasons.isEmpty()) {
            String currentSeasonUrl = leagueUrl.replace("archive/", "");
            seasons.add(new Season("current", "Current Season", leagueSlug, currentSeasonUrl));
        }

        return seasons;
    }

    public static List<String> parseMatchIds(String html) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = MATCH_ID_PATTERN.matcher(html);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return new ArrayList<>(ids);
    }

    public static Match parseMatchDetails(String matchId, String data, String seasonId, String leagueId, String countryCode) {
        String[] parts = data.split("¬");
        String homeTeam = null, awayTeam = null;
        Integer homeScore = null, awayScore = null;
        Long timestamp = null;
        for (String part : parts) {
            String[] kv = part.split("÷", 2);
            if (kv.length == 2) {
                switch (kv[0]) {
                    case "HG" -> homeTeam = decodeText(kv[1]);
                    case "AG" -> awayTeam = decodeText(kv[1]);
                    case "HS" -> homeScore = parseIntSafe(kv[1]);
                    case "AS" -> awayScore = parseIntSafe(kv[1]);
                    case "TU" -> timestamp = parseLongSafe(kv[1]);
                }
            }
        }
        if (homeTeam == null || awayTeam == null) return null;
        return new Match(matchId, homeTeam, awayTeam, homeScore, awayScore, timestamp, seasonId, leagueId, countryCode);
    }

    public static Odds parseOdds(String matchId, String data) {
        String[] bookmakers = data.split("~");
        for (String bm : bookmakers) {
            if (bm.isEmpty()) continue;
            String[] parts = bm.split("¬");
            if (parts.length < 4) continue;
            String bmId = parts[0];
            if (bmId.equals("16") || bmId.toLowerCase().contains("bet365")) {
                Double home = parseDoubleSafe(parts[1]);
                Double draw = parseDoubleSafe(parts[2]);
                Double away = parseDoubleSafe(parts[3]);
                if (home != null && draw != null && away != null) {
                    return new Odds(matchId, "bet365", home, draw, away, System.currentTimeMillis());
                }
            }
        }
        return null;
    }

    public static String parseFsignToken(String html) {
        Matcher m = FSIGN_PATTERN.matcher(html);
        return m.find() ? m.group(1) : FlashscoreConfig.DEFAULT_FSIGN;
    }

    private static String cleanText(String text) {
        return text.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }

    private static String decodeText(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    private static Integer parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private static Long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }

    private static Double parseDoubleSafe(String s) {
        try { return Double.parseDouble(s.replace(",", ".").trim()); } catch (Exception e) { return null; }
    }

    public static List<MatchData> parseMatchDataFromResultsHtml(String html, String seasonId,
                                                                String leagueId, String countryCode) {
        List<MatchData> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements matchRows = doc.select("[id^=g_1_]");

        for (Element row : matchRows) {
            String id = row.attr("id").replace("g_1_", "");
            if (id.isEmpty()) continue;

            String date = row.select(".event__time").text();

            String home = getTextBySelectors(row,
                    ".event__participant--home .participant__participantName",
                    ".event__participant--home a",
                    "[class*='HomeTeam'] [class*='participantName']",
                    "[class*='homeTeam'] [class*='name']",
                    ".event__participant--home .event__participantName",
                    ".event__participant--home span",
                    ".event__participant--home");

            String away = getTextBySelectors(row,
                    ".event__participant--away .participant__participantName",
                    ".event__participant--away a",
                    "[class*='AwayTeam'] [class*='participantName']",
                    "[class*='awayTeam'] [class*='name']",
                    ".event__participant--away .event__participantName",
                    ".event__participant--away span",
                    ".event__participant--away");

            String homeScore = row.select(".event__score--home").text();
            String awayScore = row.select(".event__score--away").text();

            if (home.isEmpty() || away.isEmpty()) {
                Elements participants = row.select("[class*='participant']");
                List<String> names = participants.stream()
                        .map(Element::text).filter(t -> !t.isEmpty()).distinct().toList();
                if (home.isEmpty() && names.size() >= 1) home = names.get(0);
                if (away.isEmpty() && names.size() >= 2) away = names.get(1);
            }

            MatchData md = new MatchData(id, date, home, away);
            if (!homeScore.isEmpty() && !awayScore.isEmpty()) {
                md.ftScore = homeScore + "-" + awayScore;
            }
            list.add(md);
        }
        return list;
    }

    private static String getTextBySelectors(Element row, String... selectors) {
        for (String sel : selectors) {
            String text = row.select(sel).text();
            if (!text.isEmpty()) return text;
        }
        return "";
    }
}