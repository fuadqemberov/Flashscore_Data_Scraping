package analyzer.fs;

import analyzer.fs.model.Country;
import analyzer.fs.model.League;
import analyzer.fs.model.Match;
import analyzer.fs.model.Odds;
import analyzer.fs.model.Season;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlashscoreParser {

    private static final Pattern MATCH_ID_PATTERN = Pattern.compile("g_1_([A-Za-z0-9]+)");
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("href=\"/football/([^/]+)/\"[^>]*>([^<]+)</a>");
    private static final Pattern LEAGUE_PATTERN = Pattern.compile("href=\"/football/[^/]+/([^/]+)/\"[^>]*>([^<]+)</a>");

    // 🔧 DÜZELTİLMİŞ SEZON PATTERN'LERİ (Java'da çalışan hali)
    private static final Pattern SEASON_PATTERN_1 = Pattern.compile("data-season=\"([^\"]+)\"[^>]*>([^<]+)</a>");
    private static final Pattern SEASON_PATTERN_2 = Pattern.compile("href=\"[^\"]*(\\d{4}-\\d{4})/\"[^>]*>([^<]+)</a>");
    private static final Pattern SEASON_PATTERN_3 = Pattern.compile("href=\"[^\"]*(\\d{4})/\"[^>]*>([^<]+)</a>");

    private static final Pattern FSIGN_PATTERN = Pattern.compile("feed_sign\":\"([^\"]+)\"");

    // ========== ÜLKELER ==========
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

    // ========== LİGLER ==========
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

    // ========== SEZONLAR (3 FARKLI PATTERN DENE) ==========
    public static List<Season> parseSeasons(String html, String leagueId, String leagueUrl) {
        List<Season> seasons = new ArrayList<>();

        // Pattern 1: data-season attribute
        Matcher m1 = SEASON_PATTERN_1.matcher(html);
        while (m1.find()) {
            String seasonId = m1.group(1);
            String seasonName = cleanText(m1.group(2));
            String baseUrl = leagueUrl.endsWith("/") ? leagueUrl.substring(0, leagueUrl.length() - 1) : leagueUrl;
            seasons.add(new Season(seasonId, seasonName, leagueId, baseUrl + "-" + seasonName.replace("/", "-") + "/"));
        }

        // Pattern 2: YYYY-YYYY formatı URL'de
        if (seasons.isEmpty()) {
            Matcher m2 = SEASON_PATTERN_2.matcher(html);
            while (m2.find()) {
                String yearRange = m2.group(1);
                String seasonName = cleanText(m2.group(2));
                String seasonId = yearRange;
                String baseUrl = leagueUrl.endsWith("/") ? leagueUrl.substring(0, leagueUrl.length() - 1) : leagueUrl;
                seasons.add(new Season(seasonId, seasonName, leagueId, baseUrl + "-" + yearRange + "/"));
            }
        }

        // Pattern 3: YYYY formatı URL'de
        if (seasons.isEmpty()) {
            Matcher m3 = SEASON_PATTERN_3.matcher(html);
            while (m3.find()) {
                String year = m3.group(1);
                String seasonName = cleanText(m3.group(2));
                String seasonId = year;
                String baseUrl = leagueUrl.endsWith("/") ? leagueUrl.substring(0, leagueUrl.length() - 1) : leagueUrl;
                seasons.add(new Season(seasonId, seasonName, leagueId, baseUrl + "-" + year + "/"));
            }
        }

        return seasons;
    }

    // ========== MAÇ ID'LERİ ==========
    public static List<String> parseMatchIds(String html) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = MATCH_ID_PATTERN.matcher(html);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return new ArrayList<>(ids);
    }

    // ========== MAÇ DETAYLARI ==========
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

    // ========== ORANLAR ==========
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

    // ========== FSIGN TOKEN ==========
    public static String parseFsignToken(String html) {
        Matcher m = FSIGN_PATTERN.matcher(html);
        return m.find() ? m.group(1) : FlashscoreConfig.DEFAULT_FSIGN;
    }

    // ========== YARDIMCI METOTLAR ==========
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
}