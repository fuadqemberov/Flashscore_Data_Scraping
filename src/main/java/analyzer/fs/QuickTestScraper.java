package analyzer.fs;

import analyzer.fs.model.Season;
import analyzer.fs.scraper.FlashscoreHttpClient;
import analyzer.fs.util.FlashscoreConfig;
import analyzer.fs.util.FlashscoreParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class QuickTestScraper {

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║      ⚡ FLASHSCORE MULTI-SEASON SCRAPER      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        String countryCode = readLine(scanner, "🌍 Ülke kodu  (varsayılan: england)       : ", "england");
        String leagueSlug  = readLine(scanner, "🏆 Lig slug   (varsayılan: premier-league): ", "premier-league");

        System.out.println("\n⏳ Sezon listesi (Arşiv) çekiliyor...");
        System.out.flush();

        List<MatchData> allMatches = new ArrayList<>();

        try (FlashscoreHttpClient client = new FlashscoreHttpClient()) {

            String leagueUrl  = FlashscoreConfig.DOMAIN + "/football/" + countryCode + "/" + leagueSlug + "/";
            String archiveUrl = leagueUrl + "archive/";

            String archiveHtml = client.getHtmlWithSeasonDropdown(archiveUrl);
            List<Season> seasons = FlashscoreParser.parseSeasons(archiveHtml, leagueSlug, leagueUrl);

            System.out.println("\n📋 Bulunan sezonlar:");
            for (int i = 0; i < seasons.size(); i++) {
                System.out.printf("   [%2d] %s%n", i + 1, seasons.get(i).name());
            }
            System.out.println();
            System.out.flush();

            int howMany;
            while (true) {
                String input = readLine(scanner, "📅 Kaç sezon çekilsin? (1 = sadece güncel, maks " + seasons.size() + "): ", "1");
                try {
                    howMany = Integer.parseInt(input.trim());
                    if (howMany >= 1 && howMany <= seasons.size()) break;
                } catch (Exception ignored) {}
            }

            List<Season> targetSeasons = seasons.subList(0, howMany);

            for (int si = 0; si < targetSeasons.size(); si++) {
                Season season = targetSeasons.get(si);
                System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.printf("📅 [%d/%d] Sezon: %s%n", si + 1, targetSeasons.size(), season.name());

                String seasonUrl  = season.url() + "results/";
                String seasonHtml = client.getHtml(seasonUrl);

                List<MatchData> seasonMatches = FlashscoreParser.parseMatchDataFromResultsHtml(
                        seasonHtml, season.id(), leagueSlug, countryCode);
                System.out.println("⚽ Bu sezonda " + seasonMatches.size() + " maç bulundu.");

                allMatches.addAll(seasonMatches);
            }

            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("📦 Toplam maç: " + allMatches.size());

            if (allMatches.isEmpty()) return;

            System.out.println("\n💰 Oranlar ve HT/FT skorları çekiliyor...");
            System.out.flush();
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            AtomicInteger processed = new AtomicInteger(0);

            // RATE LIMIT KORUMASI: Aynı anda en fazla 15 istek gitsin (Ban yememek için)
            Semaphore apiLimiter = new Semaphore(15);
            var futures = new ArrayList<java.util.concurrent.Future<?>>();

            for (MatchData md : allMatches) {
                futures.add(executor.submit(() -> {
                    try {
                        apiLimiter.acquire();
                        MatchDetailScraper.scrapeMatch(md);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        apiLimiter.release();
                        int done = processed.incrementAndGet();
                        if (done % 20 == 0 || done == allMatches.size()) {
                            long oddsCount = allMatches.stream().filter(m -> !m.oddsMap.isEmpty()).count();
                            System.out.printf("   ✅ İşlenen: %d/%d | Oranı Bulunan: %d%n", done, allMatches.size(), oddsCount);
                        }
                    }
                }));
            }

            for (var f : futures) f.get();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);

            String filename = countryCode + "_" + leagueSlug + "_export.xlsx";
            ExcelReportService.generateReport(allMatches, filename);
            System.out.println("\n🎉 Tamamlandı! Dosya: " + filename);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String readLine(Scanner scanner, String prompt, String defaultValue) {
        System.out.print(prompt);
        System.out.flush();
        String line = scanner.nextLine().trim();
        return line.isEmpty() ? defaultValue : line;
    }
}