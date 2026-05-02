package analyzer.fs;

import analyzer.fs.model.Country;
import analyzer.fs.model.League;
import analyzer.fs.model.Season;
import analyzer.fs.scraper.FlashscoreHttpClient;
import analyzer.fs.util.FlashscoreConfig;
import analyzer.fs.util.FlashscoreParser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         ⚡ FLASHSCORE SCRAPER v1.0 - Console App          ║");
        System.out.println("║     Ultra Fast | Virtual Threads | Excel Export            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        System.out.println("Seçenekler:");
        System.out.println("  [1] Tüm dünyadaki tüm ligleri çek (ÇOK UZUN SÜREBİLİR)");
        System.out.println("  [2] Belirli bir lig çek (örn: İngiltere Premier Lig)");
        System.out.println("  [3] Sadece ülkeleri listele");
        System.out.print(" Seçiminiz (1/2/3): ");
        System.out.flush();

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1" -> scrapeAllLeaguesInteractive(scanner);
            case "2" -> scrapeLeagueInteractive(scanner);
            case "3" -> listCountries();
            default  -> System.out.println("❌ Geçersiz seçim!");
        }
    }

    private static void listCountries() {
        try (FlashscoreHttpClient client = new FlashscoreHttpClient()) {
            String html = client.getHtml(FlashscoreConfig.DOMAIN + "/football/");
            var countries = FlashscoreParser.parseCountries(html);
            System.out.println("📋 ÜLKELER:");
            countries.forEach(c -> System.out.println("  • " + c.code() + " — " + c.name()));
        } catch (Exception e) {
            System.err.println("❌ HATA: " + e.getMessage());
        }
    }

    private static void scrapeAllLeaguesInteractive(Scanner scanner) {

        // ── Kaç sezon? ──────────────────────────────────────────────────────────
        int howMany;
        while (true) {
            System.out.print("📅 Her lig için kaç sezon çekilsin? (1 = sadece güncel): ");
            System.out.flush();
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) input = "1";
            try {
                howMany = Integer.parseInt(input);
                if (howMany >= 1) break;
                System.out.println("   ⚠️  En az 1 olmalı.");
            } catch (NumberFormatException e) {
                System.out.println("   ⚠️  Geçersiz giriş, lütfen bir sayı yazın.");
            }
        }
        final int seasonLimit = howMany;

        List<MatchData> globalMatches = new ArrayList<>();

        try (FlashscoreHttpClient client = new FlashscoreHttpClient()) {

            // ── 1. Ülkeleri çek ─────────────────────────────────────────────────
            System.out.println("\n🌍 Ülkeler çekiliyor...");
            System.out.flush();
            String footballHtml = client.getHtmlSimple(FlashscoreConfig.DOMAIN + "/football/");
            List<Country> countries = FlashscoreParser.parseCountries(footballHtml);
            System.out.println("✅ " + countries.size() + " ülke bulundu.");

            int totalLeaguesProcessed = 0;

            // ── 2. Her ülke için ligleri çek, her lig için maçları çek ──────────
            for (int ci = 0; ci < countries.size(); ci++) {
                Country country = countries.get(ci);
                System.out.println("\n══════════════════════════════════════════════════");
                System.out.printf("🌍 [%d/%d] Ülke: %s%n", ci + 1, countries.size(), country.name());

                // Ülke sayfasından ligleri parse et
                List<League> leagues;
                try {
                    String countryHtml = client.getHtmlSimple(country.url());
                    leagues = FlashscoreParser.parseLeagues(countryHtml, country.code());
                } catch (Exception e) {
                    System.out.println("   ⚠️  Ülke sayfası yüklenemedi, geçiliyor: " + e.getMessage());
                    continue;
                }

                if (leagues.isEmpty()) {
                    System.out.println("   ℹ️  Bu ülkede lig bulunamadı.");
                    continue;
                }
                System.out.println("   ⚽ " + leagues.size() + " lig bulundu.");

                // ── 3. Her lig için sezonları çek ve maçları al ─────────────────
                for (int li = 0; li < leagues.size(); li++) {
                    League league = leagues.get(li);
                    totalLeaguesProcessed++;
                    System.out.printf("\n   📋 [Lig %d/%d] %s%n", li + 1, leagues.size(), league.name());

                    // Arşiv sayfasından sezonları çek
                    List<Season> seasons;
                    try {
                        String archiveUrl = league.url() + "archive/";
                        String archiveHtml = client.getHtmlWithSeasonDropdown(archiveUrl);
                        seasons = FlashscoreParser.parseSeasons(archiveHtml, league.slug(), league.url());
                    } catch (Exception e) {
                        System.out.println("      ⚠️  Sezon listesi alınamadı, geçiliyor: " + e.getMessage());
                        continue;
                    }

                    if (seasons.isEmpty()) {
                        System.out.println("      ℹ️  Sezon bulunamadı.");
                        continue;
                    }

                    // Kaç sezon çekileceğini belirle
                    int actualLimit = Math.min(seasonLimit, seasons.size());
                    List<Season> targetSeasons = seasons.subList(0, actualLimit);

                    System.out.printf("      📅 %d sezon çekilecek (mevcut: %d)%n", actualLimit, seasons.size());

                    List<MatchData> leagueMatches = new ArrayList<>();

                    for (int si = 0; si < targetSeasons.size(); si++) {
                        Season season = targetSeasons.get(si);
                        System.out.printf("      ⏳ [%d/%d] Sezon: %s%n", si + 1, targetSeasons.size(), season.name());
                        System.out.flush();

                        try {
                            String seasonUrl = season.url() + "results/";
                            String seasonHtml = client.getHtmlWithMatches(seasonUrl);
                            List<MatchData> seasonMatches = FlashscoreParser.parseMatchDataFromResultsHtml(
                                    seasonHtml, season.id(), league.slug(), country.code());
                            System.out.printf("         ⚽ %d maç bulundu%n", seasonMatches.size());
                            leagueMatches.addAll(seasonMatches);
                        } catch (Exception e) {
                            System.out.println("         ⚠️  Sezon çekilemedi: " + e.getMessage());
                        }
                    }

                    if (leagueMatches.isEmpty()) {
                        System.out.println("      ℹ️  Bu lig için maç bulunamadı.");
                        continue;
                    }

                    // ── 4. Oranları ve detayları çek (sanal thread'lerle) ────────
                    System.out.printf("      💰 %d maç için detay/oran çekiliyor...%n", leagueMatches.size());
                    System.out.flush();

                    var executor = Executors.newVirtualThreadPerTaskExecutor();
                    AtomicInteger processed = new AtomicInteger(0);
                    var futures = new ArrayList<java.util.concurrent.Future<?>>();

                    for (MatchData md : leagueMatches) {
                        futures.add(executor.submit(() -> {
                            MatchDetailScraper.scrapeMatch(md);
                            int done = processed.incrementAndGet();
                            if (done % 100 == 0 || done == leagueMatches.size()) {
                                long oddsCount = leagueMatches.stream()
                                        .filter(m -> !m.oddsMap.isEmpty()).count();
                                System.out.printf("         ✅ %d/%d | Oranlı: %d%n",
                                        done, leagueMatches.size(), oddsCount);
                                System.out.flush();
                            }
                        }));
                    }

                    for (var f : futures) f.get();
                    executor.shutdown();
                    executor.awaitTermination(10, TimeUnit.MINUTES);

                    globalMatches.addAll(leagueMatches);
                    System.out.printf("      ✅ Lig tamamlandı. Toplam biriken maç: %d%n", globalMatches.size());
                }
            }

            // ── 5. Tek Excel'e yaz ───────────────────────────────────────────────
            System.out.println("\n╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                     ✅ TAMAMLANDI                          ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.printf("📊 Toplam lig işlendi : %d%n", totalLeaguesProcessed);
            System.out.printf("⚽ Toplam maç çekildi : %d%n", globalMatches.size());
            long withOdds = globalMatches.stream().filter(m -> !m.oddsMap.isEmpty()).count();
            System.out.printf("💰 Oranlı maç        : %d%n", withOdds);

            if (!globalMatches.isEmpty()) {
                String filename = "all_leagues_" + seasonLimit + "season_" + timestamp() + ".xlsx";
                System.out.printf("%n📊 Excel'e aktarılıyor → %s%n", filename);
                System.out.flush();
                ExcelReportService.generateReport(globalMatches, filename);
                System.out.println("🎉 Kaydedildi: " + filename);
            } else {
                System.out.println("❌ Hiç maç bulunamadı, Excel oluşturulmadı.");
            }

        } catch (Exception e) {
            System.err.println("❌ HATA: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void scrapeLeagueInteractive(Scanner scanner) {
        System.out.print(" Ülke kodu (örn: england): ");
        System.out.flush();
        String country = scanner.nextLine().trim().toLowerCase();

        System.out.print("Lig slug (örn: premier-league): ");
        System.out.flush();
        String league = scanner.nextLine().trim().toLowerCase();

        List<MatchData> allMatches = new ArrayList<>();

        try (FlashscoreHttpClient client = new FlashscoreHttpClient()) {

            System.out.println("\n⏳ Sezon listesi (Arşiv) çekiliyor...");
            System.out.flush();

            String leagueUrl = FlashscoreConfig.DOMAIN + "/football/" + country + "/" + league + "/";
            // LİGİN DOĞRUDAN ARŞİV SAYFASINA GİDİYORUZ
            String archiveUrl = leagueUrl + "archive/";

            // Arşiv sayfasını çekiyoruz
            String archiveHtml = client.getHtmlWithSeasonDropdown(archiveUrl);
            List<Season> seasons = FlashscoreParser.parseSeasons(archiveHtml, league, leagueUrl);

            if (seasons.isEmpty()) {
                System.out.println("❌ Sezon bulunamadı! Ülke kodu veya lig slug'ı kontrol edin.");
                return;
            }

            System.out.println("\n📋 Bulunan sezonlar:");
            for (int i = 0; i < seasons.size(); i++) {
                System.out.printf("   [%2d] %s%n", i + 1, seasons.get(i).name());
            }
            System.out.println();
            System.out.flush();

            int maxBack = seasons.size();
            int howMany;
            while (true) {
                System.out.print("📅 Kaç sezon çekilsin? (1 = sadece güncel, maks " + maxBack + "): ");
                System.out.flush();
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) input = "1";
                try {
                    howMany = Integer.parseInt(input);
                    if (howMany >= 1 && howMany <= maxBack) break;
                    System.out.println("   ⚠️  Lütfen 1 ile " + maxBack + " arasında bir sayı girin.");
                    System.out.flush();
                } catch (NumberFormatException e) {
                    System.out.println("   ⚠️  Geçersiz giriş, lütfen bir sayı yazın.");
                    System.out.flush();
                }
            }

            List<Season> targetSeasons = seasons.subList(0, howMany);

            System.out.println("\n🎯 Çekilecek sezonlar:");
            targetSeasons.forEach(s -> System.out.println("   • " + s.name()));
            System.out.println();
            System.out.flush();

            for (int si = 0; si < targetSeasons.size(); si++) {
                Season season = targetSeasons.get(si);
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.printf("📅 [%d/%d] Sezon: %s%n", si + 1, targetSeasons.size(), season.name());

                String seasonUrl  = season.url() + "results/";
                System.out.println("🔗 URL: " + seasonUrl);
                System.out.flush();

                String seasonHtml = client.getHtmlWithMatches(seasonUrl);
                System.out.println("📄 HTML: " + seasonHtml.length() + " karakter");

                List<MatchData> seasonMatches = FlashscoreParser.parseMatchDataFromResultsHtml(
                        seasonHtml, season.id(), league, country);
                System.out.println("⚽ Bu sezonda " + seasonMatches.size() + " maç bulundu.");
                System.out.flush();

                seasonMatches.stream().limit(3).forEach(m ->
                        System.out.println("   [" + m.matchId + "] " + m.homeTeam + " vs " + m.awayTeam + " | FT: " + m.ftScore)
                );

                allMatches.addAll(seasonMatches);
            }

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("📦 Toplam maç: " + allMatches.size());

            if (allMatches.isEmpty()) {
                System.out.println("❌ Hiç maç bulunamadı.");
                return;
            }

            System.out.println("\n💰 Oranlar ve HT/FT skorları çekiliyor...");
            System.out.flush();

            var executor = Executors.newVirtualThreadPerTaskExecutor();
            AtomicInteger processed = new AtomicInteger(0);
            var futures = new ArrayList<java.util.concurrent.Future<?>>();

            for (MatchData md : allMatches) {
                futures.add(executor.submit(() -> {
                    MatchDetailScraper.scrapeMatch(md);
                    int done = processed.incrementAndGet();
                    if (done % 50 == 0 || done == allMatches.size()) {
                        long oddsCount = allMatches.stream().filter(m -> !m.oddsMap.isEmpty()).count();
                        System.out.printf("   ✅ İşlenen: %d/%d | Oranlı: %d%n",
                                done, allMatches.size(), oddsCount);
                        System.out.flush();
                    }
                }));
            }

            for (var f : futures) f.get();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);

            String seasonRange = targetSeasons.size() == 1
                    ? targetSeasons.get(0).name().replace("/", "-")
                    : targetSeasons.get(targetSeasons.size() - 1).name().replace("/", "-")
                    + "_to_"
                    + targetSeasons.get(0).name().replace("/", "-");

            String filename = country + "_" + league + "_" + seasonRange + "_" + timestamp() + ".xlsx";

            System.out.println("\n📊 Excel'e aktarılıyor → " + filename);
            System.out.flush();
            ExcelReportService.generateReport(allMatches, filename);
            System.out.println("🎉 Tamamlandı! Dosya: " + filename);

        } catch (Exception e) {
            System.err.println("❌ HATA: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}