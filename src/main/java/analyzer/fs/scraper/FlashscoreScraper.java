package analyzer.fs.scraper;


import analyzer.fs.model.*;
import analyzer.fs.util.FlashscoreConfig;
import analyzer.fs.util.FlashscoreParser;
import analyzer.fs.util.ProgressBar;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class FlashscoreScraper implements AutoCloseable {

    private final FlashscoreHttpClient httpClient;
    private final ExecutorService executor;

    public FlashscoreScraper() {
        this.httpClient = new FlashscoreHttpClient();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // ========== ÜLKELER ==========
    public List<Country> fetchCountries() throws Exception {
        System.out.println("🌍 Ülkeler çekiliyor...");
        String html = httpClient.getHtml(FlashscoreConfig.DOMAIN + "/football/");
        List<Country> countries = FlashscoreParser.parseCountries(html);
        System.out.println("✅ " + countries.size() + " ülke bulundu.");
        return countries;
    }

    // ========== LİGLER (PARALEL) ==========
    public List<League> fetchLeagues(List<Country> countries) {
        System.out.println("⚽ Ligler çekiliyor (" + countries.size() + " ülke)...");

        List<CompletableFuture<List<League>>> futures = countries.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String html = httpClient.getHtml(c.url());
                        return FlashscoreParser.parseLeagues(html, c.code());
                    } catch (Exception e) {
                        System.err.println("❌ Lig hatası [" + c.code() + "]: " + e.getMessage());
                        return List.<League>of();
                    }
                }, executor))
                .toList();

        List<League> leagues = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        System.out.println("✅ " + leagues.size() + " lig bulundu.");
        return leagues;
    }

    // ========== SEZONLAR (PARALEL) ==========
    public List<Season> fetchSeasons(List<League> leagues) {
        System.out.println("📅 Sezonlar çekiliyor (" + leagues.size() + " lig)...");

        List<CompletableFuture<List<Season>>> futures = leagues.stream()
                .map(l -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String html = httpClient.getHtml(l.url());
                        return FlashscoreParser.parseSeasons(html, l.id(), l.url());
                    } catch (Exception e) {
                        return List.<Season>of();
                    }
                }, executor))
                .toList();

        List<Season> seasons = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        System.out.println("✅ " + seasons.size() + " sezon bulundu.");
        return seasons;
    }

    // ========== MAÇLAR (PARALEL BATCH) ==========
    public List<Match> fetchMatches(List<Season> seasons) {
        System.out.println("🏟️ Maçlar çekiliyor (" + seasons.size() + " sezon)...");

        List<Match> allMatches = Collections.synchronizedList(new ArrayList<>());
        ProgressBar pb = new ProgressBar(seasons.size(), "Sezonlar");

        List<CompletableFuture<Void>> futures = seasons.stream()
                .map(s -> CompletableFuture.runAsync(() -> {
                    try {
                        String html = httpClient.getHtml(s.url() + "results/");
                        List<String> matchIds = FlashscoreParser.parseMatchIds(html);
                        List<Match> matches = fetchMatchDetailsBatch(matchIds, s.id(), s.leagueId(), "");
                        allMatches.addAll(matches);
                    } catch (Exception e) {
                        // Hata durumunu sessizce geç
                    } finally {
                        pb.increment();
                    }
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        System.out.println("✅ " + allMatches.size() + " maç bulundu.");
        return allMatches;
    }

    // ========== MAÇ DETAYLARI BATCH ==========
    private List<Match> fetchMatchDetailsBatch(List<String> matchIds, String seasonId, String leagueId, String countryCode) {
        List<Match> matches = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = matchIds.stream()
                .map(id -> CompletableFuture.runAsync(() -> {
                    try {
                        String data = httpClient.get("/dc_1_" + id);
                        Match match = FlashscoreParser.parseMatchDetails(id, data, seasonId, leagueId, countryCode);
                        if (match != null) matches.add(match);
                    } catch (Exception e) {
                        // Skip failed
                    }
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return matches;
    }

    // ========== ORANLAR (PARALEL BATCH) ==========
    public Map<String, Odds> fetchOdds(List<Match> matches) {
        System.out.println("💰 Oranlar çekiliyor (" + matches.size() + " maç)...");

        Map<String, Odds> oddsMap = new ConcurrentHashMap<>();
        ProgressBar pb = new ProgressBar(matches.size(), "Oranlar");

        int batchSize = FlashscoreConfig.BATCH_SIZE;
        for (int i = 0; i < matches.size(); i += batchSize) {
            List<Match> batch = matches.subList(i, Math.min(i + batchSize, matches.size()));

            List<CompletableFuture<Void>> futures = batch.stream()
                    .map(m -> CompletableFuture.runAsync(() -> {
                        try {
                            String data = httpClient.get("/df_dos_1_" + m.id());
                            Odds odds = FlashscoreParser.parseOdds(m.id(), data);
                            if (odds != null) oddsMap.put(m.id(), odds);
                        } catch (Exception e) {
                            // Skip failed
                        } finally {
                            pb.increment();
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        System.out.println("✅ " + oddsMap.size() + " oran bulundu.");
        return oddsMap;
    }

    // ========== TÜM VERİYİ ÇEK ==========
    public ScrapeResult scrapeAll() throws Exception {
        long start = System.currentTimeMillis();

        List<Country> countries = fetchCountries();
        List<League> leagues = fetchLeagues(countries);
        List<Season> seasons = fetchSeasons(leagues);
        List<Match> matches = fetchMatches(seasons);
        Map<String, Odds> odds = fetchOdds(matches);

        long duration = System.currentTimeMillis() - start;
        System.out.println("\n⏱️ Toplam süre: " + (duration / 1000) + " saniye");

        return new ScrapeResult(countries, leagues, seasons, matches, odds);
    }

    // ========== BELİRLİ BİR LİG İÇİN SEZONLARI ÇEK ==========
    public List<Season> fetchSeasonsForLeague(String countryCode, String leagueSlug) throws Exception {
        String leagueUrl = FlashscoreConfig.DOMAIN + "/football/" + countryCode + "/" + leagueSlug + "/";
        String html = httpClient.getHtml(leagueUrl);
        return FlashscoreParser.parseSeasons(html, leagueSlug, leagueUrl);
    }

    // ========== BELİRLİ SEZONLARLA LİG ÇEK ==========
    public ScrapeResult scrapeLeagueWithSeasons(String countryCode, String leagueSlug, List<Season> targetSeasons) throws Exception {
        List<Match> matches = fetchMatches(targetSeasons);
        Map<String, Odds> odds = fetchOdds(matches);
        return new ScrapeResult(List.of(), List.of(), targetSeasons, matches, odds);
    }

    // ========== BELİRLİ BİR LİG İÇİN ÇEK (tüm sezonlar - eski davranış) ==========
    public ScrapeResult scrapeLeague(String countryCode, String leagueSlug) throws Exception {
        List<Season> seasons = fetchSeasonsForLeague(countryCode, leagueSlug);
        List<Match> matches = fetchMatches(seasons);
        Map<String, Odds> odds = fetchOdds(matches);
        return new ScrapeResult(List.of(), List.of(), seasons, matches, odds);
    }

    @Override
    public void close() {
        httpClient.close();
        executor.shutdown();
    }
}