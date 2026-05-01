package analyzer.fs;

import analyzer.fs.exporter.ExcelExporter;
import analyzer.fs.model.Match;
import analyzer.fs.model.Odds;
import analyzer.fs.model.ScrapeResult;
import analyzer.fs.model.Season;
import analyzer.fs.scraper.FlashscoreHttpClient;

import java.util.*;
import java.util.concurrent.*;

public class QuickTestScraper {

    public static void main(String[] args) throws Exception {
        // ============================================
        // 🔧 BURAYA KENDİ LİGİNİ YAZ
        // ============================================
        String countryCode;
        String leagueSlug;
        String seasonName;  // null = mevcut sezon
        // ============================================

        if (args.length >= 1) countryCode = args[0];
        else {
            countryCode = "england";
        }
        if (args.length >= 2) leagueSlug = args[1];
        else {
            leagueSlug = "premier-league";
        }
        if (args.length >= 3) seasonName = args[2];
        else {
            seasonName = "2024-2025";
        }

        System.out.println("⚡ Test: " + countryCode + "/" + leagueSlug +
                           " | Sezon: " + (seasonName != null ? seasonName : "MEVCUT"));

        try (FlashscoreHttpClient client = new FlashscoreHttpClient()) {

            // 1. Lig sayfası
            String leagueUrl = FlashscoreConfig.DOMAIN + "/football/" + countryCode + "/" + leagueSlug + "/";
            System.out.println("🔗 URL: " + leagueUrl);

            String leagueHtml = client.getHtml(leagueUrl);
            System.out.println("📄 HTML uzunluğu: " + leagueHtml.length() + " karakter");

            // 🔍 DEBUG: HTML'den sezon bilgisi ara
            if (leagueHtml.contains("data-season")) {
                System.out.println("✅ data-season bulundu!");
            } else if (leagueHtml.contains("2024-2025") || leagueHtml.contains("2024/2025")) {
                System.out.println("✅ 2024-2025 sezonu bulundu!");
            } else {
                System.out.println("⚠️ Sezon pattern'i bulunamadı, HTML içeriği kontrol ediliyor...");
                // İlk 500 karakteri göster
                System.out.println(leagueHtml.substring(0, Math.min(500, leagueHtml.length())));
            }

            List<Season> seasons = FlashscoreParser.parseSeasons(leagueHtml, leagueSlug, leagueUrl);

            if (seasons.isEmpty()) {
                System.out.println("❌ Sezon bulunamadı!");
                System.out.println("💡 Lig URL'sini kontrol et: " + leagueUrl);
                System.out.println("💡 Örnek: england/premier-league, spain/laliga, germany/bundesliga");
                return;
            }

            System.out.println("📋 Bulunan sezonlar:");
            seasons.forEach(s -> System.out.println("   • " + s.name() + " (ID: " + s.id() + ")"));

            // Hedef sezonu seç
            Season targetSeason;
            if (seasonName != null) {
                targetSeason = seasons.stream()
                        .filter(s -> s.name().contains(seasonName) ||
                                     s.name().replace("/", "-").equals(seasonName))
                        .findFirst()
                        .orElse(seasons.get(0));
            } else {
                targetSeason = seasons.get(0);
            }

            System.out.println("🎯 Hedef sezon: " + targetSeason.name());

            // 2. Maç ID'leri
            String seasonUrl = targetSeason.url() + "results/";
            System.out.println("🔗 Sezon URL: " + seasonUrl);

            String seasonHtml = client.getHtml(seasonUrl);
            List<String> matchIds = FlashscoreParser.parseMatchIds(seasonHtml);
            System.out.println("📊 " + matchIds.size() + " maç bulundu");

            if (matchIds.isEmpty()) {
                System.out.println("❌ Maç bulunamadı!");
                return;
            }

            // 3. PARALEL: Maç detayları + Oranlar
            System.out.println("🏃 Maçlar çekiliyor...");
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<Match> matches = Collections.synchronizedList(new ArrayList<>());
            Map<String, Odds> oddsMap = new ConcurrentHashMap<>();

            int batchSize = 50;
            for (int i = 0; i < matchIds.size(); i += batchSize) {
                List<String> batch = matchIds.subList(i, Math.min(i + batchSize, matchIds.size()));

                List<CompletableFuture<Void>> futures = batch.stream()
                        .map(id -> CompletableFuture.runAsync(() -> {
                            try {
                                String matchData = client.get("/dc_1_" + id);
                                Match match = FlashscoreParser.parseMatchDetails(
                                        id, matchData, targetSeason.id(), leagueSlug, countryCode
                                );
                                if (match != null) {
                                    matches.add(match);
                                    try {
                                        String oddsData = client.get("/df_dos_1_" + id);
                                        Odds odds = FlashscoreParser.parseOdds(id, oddsData);
                                        if (odds != null) oddsMap.put(id, odds);
                                    } catch (Exception e) {}
                                }
                            } catch (Exception e) {}
                        }, executor))
                        .toList();

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                System.out.print("\r✅ Maç: " + matches.size() + " | Oran: " + oddsMap.size());
            }

            executor.shutdown();
            System.out.println();

            // 4. Excel Export
            ScrapeResult result = new ScrapeResult(
                    List.of(), List.of(), List.of(targetSeason), matches, oddsMap
            );

            String filename = "test_" + countryCode + "_" + leagueSlug + "_" +
                              targetSeason.name().replace("/", "-") + ".xlsx";
            new ExcelExporter().export(result, filename);

            System.out.println("🎉 " + filename + " oluşturuldu!");

        } catch (Exception e) {
            System.err.println("❌ HATA: " + e.getMessage());
            e.printStackTrace();
        }
    }
}