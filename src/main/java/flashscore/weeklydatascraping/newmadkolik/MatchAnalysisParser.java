package flashscore.weeklydatascraping.newmadkolik;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Proqramın əsas giriş nöqtəsi. Bütün köməkçi klaslar bu klasın içində statik olaraq yerləşdirilib.
 */
public class MatchAnalysisParser {

    public static void main(String[] args) {
        // Matçların siyahısını çəkəcəyimiz URL
        String fixtureUrl = "https://live21.nowgoal25.com/football/fixture";

        // Chrome brauzerinin seçimlərini konfiqurasiya et
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1200");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--log-level=3");
        options.addArguments("--silent");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        // --- ID'ləri dinamik olaraq vebdən al ---
        MatchIdFetcher idFetcher = new MatchIdFetcher(fixtureUrl, options);
        List<Integer> matchIds = idFetcher.fetchMatchIds();

        // Əgər heç bir ID tapılmayıbsa, proqramı dayandır
        if (matchIds.isEmpty()) {
            System.out.println(AnsiColor.RED + "Təhlil ediləcək heç bir matç ID-si tapılmadı. Proqram dayandırılır." + AnsiColor.RESET);
            return;
        }

        // --- Analiz prosesini başlat ---
        int numberOfThreads = 4; // Bunu CPU nüvələrinizin sayına uyğun tənzimləyə bilərsiniz
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        System.out.printf("%n" + AnsiColor.PURPLE + "%d matç üçün analiz prosesi %d axın ilə başladılır...%n" + AnsiColor.RESET, matchIds.size(), numberOfThreads);
        System.out.println(AnsiColor.PURPLE + "========================================================================\n" + AnsiColor.RESET);

        // Hər bir matç ID-si üçün bir analiz tapşırığı yaradıb executor-a göndər
        for (Integer id : matchIds) {
            Runnable task = new AnalysisTask(id, options);
            executor.submit(task);
        }

        // Bütün tapşırıqların bitməsini gözlə
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                System.err.println("Bəzi tapşırıqlar təyin olunan vaxtda bitmədi, dayandırılır...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Gözləmə zamanı xəta baş verdi.");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println(AnsiColor.GREEN + AnsiColor.BOLD + "\nBütün analizlər tamamlandı." + AnsiColor.RESET);
    }

    // =========================================================================================
    // DAXİLİ KLASLAR (NESTED CLASSES)
    // Bütün köməkçi klaslar burada yerləşir.
    // =========================================================================================

    /**
     * Konsola rəngli çıxış vermək üçün ANSI escape kodları.
     */
    public interface AnsiColor {
        String RESET = "\u001B[0m";
        String BOLD = "\u001B[1m";
        String RED = "\u001B[31m";
        String GREEN = "\u001B[32m";
        String YELLOW = "\u001B[33m";
        String PURPLE = "\u001B[35m";
        String CYAN = "\u001B[36m";
    }

    /**
     * Komandanın statistik göstəricilərini saxlamaq üçün data klası.
     */
    static class TeamStats {
        String teamName;
        int totalMatches, wins, draws, losses, goalsFor, goalsAgainst;
        double winRate, drawRate, lossRate;
        String matchType;

        public TeamStats(String teamName, int totalMatches, int wins, int draws, int losses, int goalsFor, int goalsAgainst, String matchType) {
            this.teamName = teamName;
            this.totalMatches = totalMatches;
            this.wins = wins;
            this.draws = draws;
            this.losses = losses;
            this.goalsFor = goalsFor;
            this.goalsAgainst = goalsAgainst;
            this.matchType = matchType;
            if (totalMatches > 0) {
                this.winRate = 100.0 * wins / totalMatches;
                this.drawRate = 100.0 * draws / totalMatches;
                this.lossRate = 100.0 * losses / totalMatches;
            }
        }

        public double avgGoalsFor() {
            return totalMatches > 0 ? (double) goalsFor / totalMatches : 0.0;
        }

        public double avgGoalsAgainst() {
            return totalMatches > 0 ? (double) goalsAgainst / totalMatches : 0.0;
        }
    }

    /**
     * Liqanın ortalama qol göstəricilərini saxlamaq üçün data klası.
     */
    static class LeagueStats {
        double avgHomeGoals;
        double avgAwayGoals;

        public LeagueStats(double avgHomeGoals, double avgAwayGoals) {
            this.avgHomeGoals = avgHomeGoals;
            this.avgAwayGoals = avgAwayGoals;
        }
    }

    /**
     * Matç analizini aparan və proqnozları hesablayan klas.
     */
    static class MatchAnalysis {
        TeamStats homeTeam;
        TeamStats awayTeam;
        LeagueStats league;
        Map<String, Double> bttsAlgos = new LinkedHashMap<>();
        double expectedHomeGoals;
        double expectedAwayGoals;
        private final Integer matchId;

        public MatchAnalysis(TeamStats homeTeam, TeamStats awayTeam, LeagueStats league, Integer matchId) {
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.league = league;
            this.matchId = matchId;
            calculate();
        }

        private void calculate() {
            double homeAttackStrength = safeDiv(homeTeam.avgGoalsFor(), league.avgHomeGoals);
            double homeDefenseStrength = safeDiv(homeTeam.avgGoalsAgainst(), league.avgAwayGoals);
            double awayAttackStrength = safeDiv(awayTeam.avgGoalsFor(), league.avgAwayGoals);
            double awayDefenseStrength = safeDiv(awayTeam.avgGoalsAgainst(), league.avgHomeGoals);

            this.expectedHomeGoals = league.avgHomeGoals * homeAttackStrength * awayDefenseStrength;
            this.expectedAwayGoals = league.avgAwayGoals * awayAttackStrength * homeDefenseStrength;

            bttsAlgos.clear();
            bttsAlgos.put("Algo 1: Fundamental Poisson Modeli", calculateBTTS(this.expectedHomeGoals, this.expectedAwayGoals));

            double hg2 = (homeTeam.avgGoalsFor() + awayTeam.avgGoalsAgainst()) / 2.0;
            double ag2 = (awayTeam.avgGoalsFor() + homeTeam.avgGoalsAgainst()) / 2.0;
            bttsAlgos.put("Algo 2: Birbaşa Ortalamalar Modeli", calculateBTTS(hg2, ag2));

            double hg3 = Math.sqrt(this.expectedHomeGoals * hg2);
            double ag3 = Math.sqrt(this.expectedAwayGoals * ag2);
            bttsAlgos.put("Algo 3: Həndəsi Orta Hibrid Model", calculateBTTS(hg3, ag3));

            double totalAttack = (homeAttackStrength + awayAttackStrength) / 2.0;
            double totalDefense = (homeDefenseStrength + awayDefenseStrength) / 2.0;
            double expectedTotalGoals = (league.avgHomeGoals + league.avgAwayGoals) * totalAttack * totalDefense;
            double homeStrengthRatio = safeDiv(homeAttackStrength, homeAttackStrength + awayAttackStrength);
            double hg4 = expectedTotalGoals * homeStrengthRatio;
            double ag4 = expectedTotalGoals * (1 - homeStrengthRatio);
            bttsAlgos.put("Algo 4: Ümumi Qol Paylanması Modeli", calculateBTTS(hg4, ag4));

            double hg5 = (this.expectedHomeGoals * 0.7) + (league.avgHomeGoals * 0.3);
            double ag5 = (this.expectedAwayGoals * 0.7) + (league.avgAwayGoals * 0.3);
            bttsAlgos.put("Algo 5: Ortalamaya Reqressiya Modeli", calculateBTTS(hg5, ag5));
        }

        private double calculateBTTS(double lambdaH, double lambdaA) {
            if (lambdaH <= 0 || lambdaA <= 0) return 0.0;
            return (1 - Math.exp(-lambdaH)) * (1 - Math.exp(-lambdaA)) * 100;
        }

        private double safeDiv(double a, double b) {
            return (b != 0) ? a / b : 1.0;
        }

        public synchronized void printAnalysis() {
            String B = AnsiColor.BOLD, R = AnsiColor.RESET, Y = AnsiColor.YELLOW, C = AnsiColor.CYAN, G = AnsiColor.GREEN;
            System.out.println(Y + B + "═══════════════════════ " + C + "MATÇ ANALİZİ (ID: " + matchId + ")" + Y + " ═══════════════════════" + R);
            System.out.printf("%n" + B + "⚽ %s " + C + "vs" + R + B + " %s%n" + R, homeTeam.teamName, awayTeam.teamName);
            System.out.println(Y + "------------------------------------------------------------------------" + R);
            System.out.println(B + "📊 KOMANDA STATİSTİKALARI:" + R);
            System.out.printf("   %-28s: %2d O | %2d Q | %2d H | %2d M | Qollar: %2d-%-2d | Ort: %.2f-%.2f%n", "🏠 " + homeTeam.teamName + " (" + homeTeam.matchType + ")", homeTeam.totalMatches, homeTeam.wins, homeTeam.draws, homeTeam.losses, homeTeam.goalsFor, homeTeam.goalsAgainst, homeTeam.avgGoalsFor(), homeTeam.avgGoalsAgainst());
            System.out.printf("   %-28s: %2d O | %2d Q | %2d H | %2d M | Qollar: %2d-%-2d | Ort: %.2f-%.2f%n", "✈️ " + awayTeam.teamName + " (" + awayTeam.matchType + ")", awayTeam.totalMatches, awayTeam.wins, awayTeam.draws, awayTeam.losses, awayTeam.goalsFor, awayTeam.goalsAgainst, awayTeam.avgGoalsFor(), awayTeam.avgGoalsAgainst());
            System.out.println(Y + "------------------------------------------------------------------------" + R);
            System.out.println(B + "🎯 GÖZLƏNİLƏN QOL SAYI (Poisson Modeli):" + R);
            System.out.printf("   %-25s: " + G + "%.2f qol" + R + "%n", homeTeam.teamName, expectedHomeGoals);
            System.out.printf("   %-25s: " + G + "%.2f qol" + R + "%n", awayTeam.teamName, expectedAwayGoals);
            System.out.printf("   %-25s: " + G + B + "%.2f qol" + R + "%n", "CƏMİ", expectedHomeGoals + expectedAwayGoals);
            System.out.println(Y + "------------------------------------------------------------------------" + R);
            System.out.println(B + "🔄 QARŞILIQLI QOL (BTTS) PROQNOZLARI:" + R);
            System.out.println(C + "+-----------------------------------------+-----------------------+");
            System.out.printf("| %-39s | %-21s |%n", "Alqoritm Adı", "BTTS (Hə) Ehtimalı");
            System.out.println("+-----------------------------------------+-----------------------+");
            double totalBttsRate = bttsAlgos.values().stream().mapToDouble(Double::doubleValue).sum();
            bttsAlgos.forEach((algoName, bttsRate) -> System.out.printf("| %-39s | " + G + "%17.1f %%" + R + "   |%n", algoName.split(":")[1].trim(), bttsRate));
            System.out.println("+-----------------------------------------+-----------------------+");
            double averageBttsRate = totalBttsRate / bttsAlgos.size();
            System.out.printf("| %-39s | " + Y + B + "%17.1f %%" + R + "   |%n", "ORTALAMA EHTİMAL", averageBttsRate);
            System.out.println(C + "+-----------------------------------------+-----------------------+" + R);
            System.out.println(Y + B + "════════════════════════════════════════════════════════════════════════\n\n" + R);
        }
    }

    /**
     * Hər bir matçın analizini ayrıca bir axında (thread) həyata keçirən klas.
     */
    static class AnalysisTask implements Runnable {
        private final Integer matchId;
        private final ChromeOptions chromeOptions;

        private static final Pattern winDrawLossPattern = Pattern.compile("Win\\s+(\\d+).*?Draw\\s+(\\d+).*?Loss\\s+(\\d+)", Pattern.DOTALL);
        private static final Pattern goalsPattern = Pattern.compile("(\\d+\\.\\d+)\\s*goals.*?Goals Scored/Conceded per Game.*?(\\d+\\.\\d+)\\s*goals", Pattern.DOTALL);

        public AnalysisTask(Integer matchId, ChromeOptions options) {
            this.matchId = matchId;
            this.chromeOptions = options;
        }

        @Override
        public void run() {
            WebDriver driver = new ChromeDriver(chromeOptions);
            String url = "https://live21.nowgoal25.com/match/h2h-" + matchId;
            try {
                System.out.println("[" + Thread.currentThread().getName() + "] Matç ID " + matchId + " üçün analizə başlayır...");
                driver.get(url);
                Thread.sleep(3000); // Səhifənin tam yüklənməsi üçün gözləmə

                WebElement porletP6 = driver.findElement(By.id("porletP6"));
                String homeTeamName = porletP6.findElement(By.cssSelector("#table_v1 .team-home a")).getText().trim();
                String awayTeamName = porletP6.findElement(By.cssSelector("#table_v2 .team-guest a")).getText().trim();

                WebElement homeTable = porletP6.findElement(By.id("table_v1"));
                WebElement awayTable = porletP6.findElement(By.id("table_v2"));

                LeagueStats league = new LeagueStats(1.25, 1.10);

                TeamStats homeTeam = parseTeamStatsFromElement(homeTable, homeTeamName, "Home");
                TeamStats awayTeam = parseTeamStatsFromElement(awayTable, awayTeamName, "Away");

                MatchAnalysis analysis = new MatchAnalysis(homeTeam, awayTeam, league, matchId);
                analysis.printAnalysis();

            } catch (Exception e) {
                System.err.println("[" + Thread.currentThread().getName() + "] Matç ID " + matchId + " üçün analiz zamanı xəta: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            } finally {
                driver.quit();
            }
        }

        private TeamStats parseTeamStatsFromElement(WebElement tableElement, String teamName, String matchType) {
            String rawData = tableElement.getText();
            int matches = 0, wins = 0, draws = 0, losses = 0;
            int goalsFor = 0, goalsAgainst = 0;

            Matcher m = winDrawLossPattern.matcher(rawData);
            if (m.find()) {
                wins = Integer.parseInt(m.group(1));
                draws = Integer.parseInt(m.group(2));
                losses = Integer.parseInt(m.group(3));
                matches = wins + draws + losses;
            } else {
                System.err.println("XƏTA: " + teamName + " (" + matchType + ") üçün Win/Draw/Loss tapılmadı!");
            }

            Matcher avgM = goalsPattern.matcher(rawData);
            if (avgM.find()) {
                double avgFor = Double.parseDouble(avgM.group(1));
                double avgAgainst = Double.parseDouble(avgM.group(2));
                if (matches > 0) {
                    goalsFor = (int) Math.round(avgFor * matches);
                    goalsAgainst = (int) Math.round(avgAgainst * matches);
                }
            } else {
                System.err.println("XƏTA: " + teamName + " (" + matchType + ") üçün Qol ortalamaları tapılmadı!");
            }

            return new TeamStats(teamName, matches, wins, draws, losses, goalsFor, goalsAgainst, matchType);
        }
    }

    /**
     * Veb səhifəsindən analiz ediləcək matç ID-lərini avtomatik olaraq çəkən klas.
     */
    static class MatchIdFetcher {
        private final String url;
        private final ChromeOptions chromeOptions;

        public MatchIdFetcher(String url, ChromeOptions options) {
            this.url = url;
            this.chromeOptions = options;
        }

        public List<Integer> fetchMatchIds() {
            System.out.println(AnsiColor.CYAN + "Matç ID-ləri çəkilir: " + url + AnsiColor.RESET);
            WebDriver driver = new ChromeDriver(chromeOptions);
            List<Integer> matchIds = new ArrayList<>();
            Pattern idPattern = Pattern.compile("tr(?:1|2|3)_(\\d+)"); // tr1_, tr2_, tr3_ formatlarını dəstəkləyir

            try {
                driver.get(url);
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                WebElement tbody = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("tbody")));

                List<WebElement> matchRows = tbody.findElements(By.xpath(".//tr[starts-with(@id, 'tr1_')]"));
                System.out.println(AnsiColor.YELLOW + matchRows.size() + " ədəd matç sətri tapıldı." + AnsiColor.RESET);

                for (WebElement row : matchRows) {
                    String rowId = row.getAttribute("id");
                    Matcher matcher = idPattern.matcher(rowId);
                    if (matcher.find()) {
                        try {
                            int matchId = Integer.parseInt(matcher.group(1));
                            matchIds.add(matchId);
                        } catch (NumberFormatException e) {
                            System.err.println("ID rəqəmə çevrilə bilmədi: " + matcher.group(1));
                        }
                    }
                }

                List<Integer> distinctMatchIds = matchIds.stream().distinct().collect(Collectors.toList());
                System.out.println(AnsiColor.GREEN + "Analiz üçün " + distinctMatchIds.size() + " unikal matç ID-si tapıldı." + AnsiColor.RESET);
                return distinctMatchIds;

            } catch (Exception e) {
                System.err.println("Matç ID-lərini çəkərkən xəta baş verdi: " + e.getMessage());
                e.printStackTrace();
                return new ArrayList<>();
            } finally {
                if (driver != null) {
                    driver.quit();
                    System.out.println(AnsiColor.CYAN + "ID çəkmə üçün WebDriver sessiyası bağlandı." + AnsiColor.RESET);
                }
            }
        }
    }
}