package flashscore.weeklydatascraping.newmadkolik;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Komandanın statistik göstəricilərini saxlamaq üçün data klası.
 */
class TeamStats {
    String teamName;
    int rank;
    int homeRank;
    int totalMatches;
    int wins;
    int draws;
    int losses;
    int goalsFor;
    int goalsAgainst;
    double winRate;
    double drawRate;
    double lossRate;
    String matchType;

    public TeamStats(String teamName, int rank, int homeRank, int totalMatches, int wins, int draws, int losses, int goalsFor, int goalsAgainst, String matchType) {
        this.teamName = teamName;
        this.rank = rank;
        this.homeRank = homeRank;
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
class LeagueStats {
    double avgHomeGoals;
    double avgAwayGoals;

    public LeagueStats(double avgHomeGoals, double avgAwayGoals) {
        this.avgHomeGoals = avgHomeGoals;
        this.avgAwayGoals = avgAwayGoals;
    }
}

/**
 * Matç analizini aparan və proqnozları hesablayan əsas klas.
 */
class MatchAnalysis {
    TeamStats homeTeam;
    TeamStats awayTeam;
    LeagueStats league;
    Map<String, Double> bttsAlgos = new LinkedHashMap<>();
    double expectedHomeGoals;
    double expectedAwayGoals;

    public MatchAnalysis(TeamStats homeTeam, TeamStats awayTeam, LeagueStats league) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.league = league;
        calculate();
    }

    private void calculate() {
        // Komandaların hücum və müdafiə güclərinin hesablanması (Liqa ortalamasına nəzərən)
        double homeAttackStrength = safeDiv(homeTeam.avgGoalsFor(), league.avgHomeGoals);
        double homeDefenseStrength = safeDiv(homeTeam.avgGoalsAgainst(), league.avgAwayGoals);
        double awayAttackStrength = safeDiv(awayTeam.avgGoalsFor(), league.avgAwayGoals);
        double awayDefenseStrength = safeDiv(awayTeam.avgGoalsAgainst(), league.avgHomeGoals);

        // Əsas gözlənilən qol sayı (Fundamental Poisson Modeli üçün)
        this.expectedHomeGoals = league.avgHomeGoals * homeAttackStrength * awayDefenseStrength;
        this.expectedAwayGoals = league.avgAwayGoals * awayAttackStrength * homeDefenseStrength;

        bttsAlgos.clear();

        // --- 5 MÜRƏKKƏB HESABLAMA ---

        // Alqoritm 1: Fundamental Poisson Modeli
        double btts1 = calculateBTTS(this.expectedHomeGoals, this.expectedAwayGoals);
        bttsAlgos.put("Algo 1: Fundamental Poisson Modeli", btts1);

        // Alqoritm 2: Birbaşa Ortalamalar Modeli
        double hg2 = (homeTeam.avgGoalsFor() + awayTeam.avgGoalsAgainst()) / 2.0;
        double ag2 = (awayTeam.avgGoalsFor() + homeTeam.avgGoalsAgainst()) / 2.0;
        double btts2 = calculateBTTS(hg2, ag2);
        bttsAlgos.put("Algo 2: Birbaşa Ortalamalar Modeli", btts2);

        // Alqoritm 3: Həndəsi Orta Hibrid Model
        double hg3 = Math.sqrt(this.expectedHomeGoals * hg2);
        double ag3 = Math.sqrt(this.expectedAwayGoals * ag2);
        double btts3 = calculateBTTS(hg3, ag3);
        bttsAlgos.put("Algo 3: Həndəsi Orta Hibrid Model", btts3);

        // Alqoritm 4: Ümumi Qol Paylanması Modeli
        double totalAttack = (homeAttackStrength + awayAttackStrength) / 2.0;
        double totalDefense = (homeDefenseStrength + awayDefenseStrength) / 2.0;
        double expectedTotalGoals = (league.avgHomeGoals + league.avgAwayGoals) * totalAttack * totalDefense;
        double homeStrengthRatio = safeDiv(homeAttackStrength, homeAttackStrength + awayAttackStrength);
        double hg4 = expectedTotalGoals * homeStrengthRatio;
        double ag4 = expectedTotalGoals * (1 - homeStrengthRatio);
        double btts4 = calculateBTTS(hg4, ag4);
        bttsAlgos.put("Algo 4: Ümumi Qol Paylanması Modeli", btts4);

        // Alqoritm 5: Ortalamaya Reqressiya Modeli
        double hg5 = (this.expectedHomeGoals * 0.7) + (league.avgHomeGoals * 0.3);
        double ag5 = (this.expectedAwayGoals * 0.7) + (league.avgAwayGoals * 0.3);
        double btts5 = calculateBTTS(hg5, ag5);
        bttsAlgos.put("Algo 5: Ortalamaya Reqressiya Modeli", btts5);
    }

    private double calculateBTTS(double lambdaH, double lambdaA) {
        if (lambdaH <= 0 || lambdaA <= 0) return 0.0;
        double probHomeScores = 1 - Math.exp(-lambdaH);
        double probAwayScores = 1 - Math.exp(-lambdaA);
        return probHomeScores * probAwayScores * 100;
    }

    private double safeDiv(double a, double b) {
        return (b != 0) ? a / b : 1.0;
    }

    public void printAnalysis() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║ MATÇ ANALİZİ VƏ PROQNOZU (POISSON ƏSASLI MODELLƏR) ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");

        System.out.println("\n┌─────────────────────────── EV SAHİBİ ───────────────────────────┐");
        System.out.println("│ 🏠 " + homeTeam.teamName);
        System.out.printf("│ Statistika (%s): %d Q - %d H - %d M | Qollar: %d-%d (Ort: %.2f-%.2f)\n", homeTeam.matchType, homeTeam.wins, homeTeam.draws, homeTeam.losses, homeTeam.goalsFor, homeTeam.goalsAgainst, homeTeam.avgGoalsFor(), homeTeam.avgGoalsAgainst());
        System.out.printf("│ Qələbə: %.1f%% | Heç-heçə: %.1f%% | Məğlubiyyət: %.1f%%\n", homeTeam.winRate, homeTeam.drawRate, homeTeam.lossRate);
        System.out.println("└──────────────────────────────────────────────────────────────────┘");

        System.out.println("\n┌─────────────────────────── QONAQ ────────────────────────────┐");
        System.out.println("│ ✈️ " + awayTeam.teamName);
        System.out.printf("│ Statistika (%s): %d Q - %d H - %d M | Qollar: %d-%d (Ort: %.2f-%.2f)\n", awayTeam.matchType, awayTeam.wins, awayTeam.draws, awayTeam.losses, awayTeam.goalsFor, awayTeam.goalsAgainst, awayTeam.avgGoalsFor(), awayTeam.avgGoalsAgainst());
        System.out.printf("│ Qələbə: %.1f%% | Heç-heçə: %.1f%% | Məğlubiyyət: %.1f%%\n", awayTeam.winRate, awayTeam.drawRate, awayTeam.lossRate);
        System.out.println("└──────────────────────────────────────────────────────────────────┘");

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🎯 GÖZLƏNİLƏN QOL SAYI (ƏSAS POISSON MODELİ):");
        System.out.printf(" %s: %.2f qol\n", homeTeam.teamName, expectedHomeGoals);
        System.out.printf(" %s: %.2f qol\n", awayTeam.teamName, expectedAwayGoals);
        System.out.printf(" ⚽ Cəmi: %.2f qol\n", expectedHomeGoals + expectedAwayGoals);

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("⚽ QARŞILIQLI QOL (BTTS) ANALİZİ:");
        bttsAlgos.forEach((algoName, bttsRate) -> {
            System.out.printf("\n -> %s:\n", algoName);
            System.out.printf("    Qarşılıqlı Qol Var (Hə): %.1f%%\n", bttsRate);
            System.out.printf("    Qarşılıqlı Qol Yox (Yox): %.1f%%\n", 100 - bttsRate);
        });
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("\n══════════════════════════════════════════════════════════════════\n");
    }
}

/**
 * Selenium ilə vebsaytdan məlumatları çəkən və təhlil edən (parsing) klas.
 * Proqramın əsas giriş nöqtəsi (main method) buradadır.
 */
public class MatchAnalysisParser {

    public static Integer Id = 2810852;
    // DÜZƏLDİLMİŞ REGEX İFADƏLƏRİ: Pattern.DOTALL parametri ilə yeni sətirlər nəzərə alınır.
    private static final Pattern winDrawLossPattern = Pattern.compile(
            "Win\\s+(\\d+).*?Draw\\s+(\\d+).*?Loss\\s+(\\d+)", Pattern.DOTALL);

    private static final Pattern goalsPattern = Pattern.compile(
            "(\\d+\\.\\d+)\\s*goals.*?Goals Scored/Conceded per Game.*?(\\d+\\.\\d+)\\s*goals", Pattern.DOTALL);

    public static TeamStats parseTeamStatsFromElement(WebElement tableElement, String teamName, String matchType) {
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
            System.out.println("XƏTA: " + teamName + " üçün Win/Draw/Loss məlumatı tapılmadı!");
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
            System.out.println("XƏTA: " + teamName + " üçün Qol ortalamaları məlumatı tapılmadı!");
        }

        return new TeamStats(teamName, 0, 0, matches, wins, draws, losses, goalsFor, goalsAgainst, matchType);
    }

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1200");
        options.addArguments("--ignore-certificate-errors");

        WebDriver driver = new ChromeDriver(options);
        String url = "https://live21.nowgoal25.com/match/h2h-"+Id;

        try {
            System.out.println("Vebsayta qoşulunur: " + url);
            driver.get(url);
            System.out.println("Səhifənin yüklənməsi gözlənilir...");
            Thread.sleep(3000);

            System.out.println("Məlumatlar çəkilir...");
            WebElement porletP6 = driver.findElement(By.id("porletP6"));
            String homeTeamName = porletP6.findElement(By.cssSelector("#table_v1 .team-home a")).getText().trim();
            String awayTeamName = porletP6.findElement(By.cssSelector("#table_v2 .team-guest a")).getText().trim();

            System.out.println("Ev Sahibi Takım: " + homeTeamName);
            System.out.println("Deplasman Takımı: " + awayTeamName);

            WebElement homeTable = porletP6.findElement(By.id("table_v1"));
            WebElement awayTable = porletP6.findElement(By.id("table_v2"));

            // Liqa ortalamaları (lazım gələrsə bu hissəni də dinamik edə bilərsiniz)
            LeagueStats league = new LeagueStats(1.25, 1.10);
            TeamStats homeTeam = parseTeamStatsFromElement(homeTable, homeTeamName, "Home");
            TeamStats awayTeam = parseTeamStatsFromElement(awayTable, awayTeamName, "Away");

            System.out.println("Məlumatlar təhlil edilir və proqnozlar hazırlanır...");
            MatchAnalysis analysis = new MatchAnalysis(homeTeam, awayTeam, league);
            analysis.printAnalysis();

        } catch (Exception e) {
            System.err.println("Analiz zamanı bir xəta baş verdi:");
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("WebDriver sessiyası bağlandı.");
            }
        }
    }
}