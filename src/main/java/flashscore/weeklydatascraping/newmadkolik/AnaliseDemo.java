package flashscore.weeklydatascraping.newmadkolik;

import java.util.*;

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

    public TeamStats(String teamName, int rank, int homeRank, int totalMatches,
                     int wins, int draws, int losses, int goalsFor, int goalsAgainst,
                     String matchType) {
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

class HeadToHeadStats {
    int totalMatches;
    int homeWins;
    int draws;
    int awayWins;
    double homeGoalsAvg;
    double awayGoalsAvg;
    int overGames;
    int underGames;

    public HeadToHeadStats(int totalMatches, int homeWins, int draws, int awayWins,
                           double homeGoalsAvg, double awayGoalsAvg,
                           int overGames, int underGames) {
        this.totalMatches = totalMatches;
        this.homeWins = homeWins;
        this.draws = draws;
        this.awayWins = awayWins;
        this.homeGoalsAvg = homeGoalsAvg;
        this.awayGoalsAvg = awayGoalsAvg;
        this.overGames = overGames;
        this.underGames = underGames;
    }

    public double homeWinRate() {
        return totalMatches > 0 ? 100.0 * homeWins / totalMatches : 0.0;
    }

    public double drawRate() {
        return totalMatches > 0 ? 100.0 * draws / totalMatches : 0.0;
    }

    public double awayWinRate() {
        return totalMatches > 0 ? 100.0 * awayWins / totalMatches : 0.0;
    }

    public double overRate() {
        int total = overGames + underGames;
        return total > 0 ? 100.0 * overGames / total : 0.0;
    }
}

class LeagueStats {
    double avgHomeGoals;
    double avgAwayGoals;

    public LeagueStats(double avgHomeGoals, double avgAwayGoals) {
        this.avgHomeGoals = avgHomeGoals;
        this.avgAwayGoals = avgAwayGoals;
    }
}

class MatchAnalysis {
    TeamStats homeTeam;
    TeamStats awayTeam;
    HeadToHeadStats h2h;
    LeagueStats league;
    Map<String, Double> outcomes = new LinkedHashMap<>();
    Map<String, Double> overUnder = new LinkedHashMap<>();
    Map<String, Double> htft = new LinkedHashMap<>();
    double expectedHomeGoals;
    double expectedAwayGoals;

    public MatchAnalysis(TeamStats homeTeam, TeamStats awayTeam,
                         HeadToHeadStats h2h, LeagueStats league) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.h2h = h2h;
        this.league = league;
        calculate();
    }

    private void calculate() {
        double homeAttack = safeDiv(homeTeam.avgGoalsFor(), league.avgHomeGoals);
        double homeDefense = safeDiv(homeTeam.avgGoalsAgainst(), league.avgAwayGoals);
        double awayAttack = safeDiv(awayTeam.avgGoalsFor(), league.avgAwayGoals);
        double awayDefense = safeDiv(awayTeam.avgGoalsAgainst(), league.avgHomeGoals);

        expectedHomeGoals = league.avgHomeGoals * homeAttack * awayDefense;
        expectedAwayGoals = league.avgAwayGoals * awayAttack * homeDefense;

        if (h2h != null && h2h.totalMatches > 0) {
            expectedHomeGoals = (expectedHomeGoals * 0.7) + (h2h.homeGoalsAvg * 0.3);
            expectedAwayGoals = (expectedAwayGoals * 0.7) + (h2h.awayGoalsAvg * 0.3);
        }

        double[][] scoreMatrix = createScoreMatrix(expectedHomeGoals, expectedAwayGoals, 8);
        calculateOutcomes(scoreMatrix);
        calculateOverUnder(scoreMatrix);

        double lambdaHomeHalf = expectedHomeGoals / 2.0;
        double lambdaAwayHalf = expectedAwayGoals / 2.0;
        int maxGoalsHalf = 5;
        double[][] halfProb = createScoreMatrix(lambdaHomeHalf, lambdaAwayHalf, maxGoalsHalf);

        for (String key : Arrays.asList("Ev/Ev", "Ev/Bərabərə", "Ev/Deplasman",
                "Bərabərə/Ev", "Bərabərə/Bərabərə", "Bərabərə/Deplasman",
                "Deplasman/Ev", "Deplasman/Bərabərə", "Deplasman/Deplasman")) {
            htft.put(key, 0.0);
        }

        for (int h1 = 0; h1 <= maxGoalsHalf; h1++) {
            for (int a1 = 0; a1 <= maxGoalsHalf; a1++) {
                for (int h2 = 0; h2 <= maxGoalsHalf; h2++) {
                    for (int a2 = 0; a2 <= maxGoalsHalf; a2++) {
                        double prob = halfProb[h1][a1] * halfProb[h2][a2];
                        int fh = h1 + h2;
                        int fa = a1 + a2;
                        String htRes = getResult(h1, a1);
                        String ftRes = getResult(fh, fa);
                        String key = htRes + "/" + ftRes;
                        if (htft.containsKey(key)) {
                            htft.put(key, htft.get(key) + prob * 100);
                        }
                    }
                }
            }
        }
    }

    private String getResult(int g1, int g2) {
        if (g1 > g2) return "Ev";
        if (g1 < g2) return "Deplasman";
        return "Bərabərə";
    }

    private double safeDiv(double a, double b) {
        return (b != 0) ? a / b : 1.0;
    }

    private double poisson(int k, double lambda) {
        if (k < 0 || lambda <= 0) return 0.0;
        double result = Math.exp(-lambda);
        for (int i = 1; i <= k; i++) {
            result *= lambda / i;
        }
        return result;
    }

    private double[][] createScoreMatrix(double lambdaHome, double lambdaAway, int maxGoals) {
        double[][] matrix = new double[maxGoals + 1][maxGoals + 1];
        for (int i = 0; i <= maxGoals; i++) {
            for (int j = 0; j <= maxGoals; j++) {
                matrix[i][j] = poisson(i, lambdaHome) * poisson(j, lambdaAway);
            }
        }
        return matrix;
    }

    private void calculateOutcomes(double[][] matrix) {
        double homeWin = 0, draw = 0, awayWin = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                if (i > j) homeWin += matrix[i][j];
                else if (i == j) draw += matrix[i][j];
                else awayWin += matrix[i][j];
            }
        }
        outcomes.put("Ev Sahibi Qazanar", homeWin * 100);
        outcomes.put("Bərabərə", draw * 100);
        outcomes.put("Deplasman Qazanar", awayWin * 100);
    }

    private void calculateOverUnder(double[][] matrix) {
        double[] overProbs = new double[10];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                int total = i + j;
                for (int t = 0; t < 10; t++) {
                    if (total > t) overProbs[t] += matrix[i][j];
                }
            }
        }
        overUnder.put("Üst 0.5", overProbs[0] * 100);
        overUnder.put("Üst 1.5", overProbs[1] * 100);
        overUnder.put("Üst 2.5", overProbs[2] * 100);
        overUnder.put("Üst 3.5", overProbs[3] * 100);
        overUnder.put("Alt 2.5", (1 - overProbs[2]) * 100);
        overUnder.put("Alt 3.5", (1 - overProbs[3]) * 100);
    }

    public void printAnalysis() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║            MAÇ ANALİZİ VƏ TAHMİNİ (POISSON MODEL)               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");

        System.out.println("\n┌─────────────────────────── EV SAHİBİ ───────────────────────────┐");
        System.out.println("│ 🏠 " + homeTeam.teamName);
        System.out.println("│ Ümumi Sıra: " + homeTeam.rank + " | Ev Sırası: " + homeTeam.homeRank);
        System.out.printf("│ Statistika (%s): %dG-%dB-%dM | Qol: %d-%d (Avg: %.2f-%.2f)\n",
                homeTeam.matchType, homeTeam.wins, homeTeam.draws, homeTeam.losses,
                homeTeam.goalsFor, homeTeam.goalsAgainst,
                homeTeam.avgGoalsFor(), homeTeam.avgGoalsAgainst());
        System.out.printf("│ Win: %.1f%% | Draw: %.1f%% | Loss: %.1f%%\n",
                homeTeam.winRate, homeTeam.drawRate, homeTeam.lossRate);
        System.out.println("└──────────────────────────────────────────────────────────────────┘");

        System.out.println("\n┌─────────────────────────── DEPLASMAN ────────────────────────────┐");
        System.out.println("│ ✈️  " + awayTeam.teamName);
        System.out.println("│ Ümumi Sıra: " + awayTeam.rank);
        System.out.printf("│ Statistika (%s): %dG-%dB-%dM | Qol: %d-%d (Avg: %.2f-%.2f)\n",
                awayTeam.matchType, awayTeam.wins, awayTeam.draws, awayTeam.losses,
                awayTeam.goalsFor, awayTeam.goalsAgainst,
                awayTeam.avgGoalsFor(), awayTeam.avgGoalsAgainst());
        System.out.printf("│ Win: %.1f%% | Draw: %.1f%% | Loss: %.1f%%\n",
                awayTeam.winRate, awayTeam.drawRate, awayTeam.lossRate);
        System.out.println("└──────────────────────────────────────────────────────────────────┘");

        if (h2h != null && h2h.totalMatches > 0) {
            System.out.println("\n┌────────────────────── HEAD TO HEAD (H2H) ────────────────────────┐");
            System.out.println("│ Son " + h2h.totalMatches + " qarşılaşma:");
            System.out.printf("│ %s Qələbə: %d (%.1f%%) | Bərabərə: %d (%.1f%%) | %s Qələbə: %d (%.1f%%)\n",
                    homeTeam.teamName, h2h.homeWins, h2h.homeWinRate(),
                    h2h.draws, h2h.drawRate(),
                    awayTeam.teamName, h2h.awayWins, h2h.awayWinRate());
            System.out.printf("│ Ortalama Qollar: %.1f - %.1f\n", h2h.homeGoalsAvg, h2h.awayGoalsAvg);
            System.out.printf("│ Üst 2.5: %.1f%% | Alt 2.5: %.1f%%\n",
                    h2h.overRate(), 100 - h2h.overRate());
            System.out.println("└──────────────────────────────────────────────────────────────────┘");
        }

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🎯 GÖZLƏNİLƏN QOL SAYI (POISSON):");
        System.out.printf("   %s: %.2f qol\n", homeTeam.teamName, expectedHomeGoals);
        System.out.printf("   %s: %.2f qol\n", awayTeam.teamName, expectedAwayGoals);
        System.out.printf("   ⚽ Cəmi: %.2f qol\n", expectedHomeGoals + expectedAwayGoals);

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("📊 MAÇ NƏTİCƏSİ EHTİMALLARI:");
        outcomes.forEach((k, v) -> System.out.printf("   %-30s: %5.1f%%\n", k, v));

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("⚽ ÜST/ALT EHTİMALLARI:");
        overUnder.forEach((k, v) -> System.out.printf("   %-30s: %5.1f%%\n", k, v));

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("⏱️ HT/FT EHTİMALLARI:");
        htft.forEach((k, v) -> System.out.printf("   %-30s: %5.1f%%\n", k, v));

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("💡 TÖVSİYƏLƏR:");

        String bestOutcome = outcomes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Bərabərə");

        System.out.println("   ✓ Ən çox ehtimal: " + bestOutcome +
                           String.format(" (%.1f%%)", outcomes.get(bestOutcome)));

        String bestHTFT = htft.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Bərabərə/Bərabərə");

        System.out.printf("   ✓ Ən çox ehtimal HT/FT: %s (%.1f%%)\n", bestHTFT, htft.get(bestHTFT));

        if (overUnder.get("Üst 2.5") > 55) {
            System.out.printf("   ✓ Üst 2.5 tövsiyə olunur (%.1f%%)\n", overUnder.get("Üst 2.5"));
        }
        if (overUnder.get("Alt 2.5") > 55) {
            System.out.printf("   ✓ Alt 2.5 tövsiyə olunur (%.1f%%)\n", overUnder.get("Alt 2.5"));
        }
        if (overUnder.get("Üst 1.5") > 75) {
            System.out.printf("   ✓ Üst 1.5 çox güclü (%.1f%%)\n", overUnder.get("Üst 1.5"));
        }

        System.out.println("\n══════════════════════════════════════════════════════════════════\n");
    }
}

class MatchAnalysisParser {

    public static TeamStats parseTeamStats(String rawData, String matchType) {
        Scanner sc = new Scanner(rawData);
        String teamName = "Unknown";
        int rank = 0, homeRank = 0;
        int matches = 0, wins = 0, draws = 0, losses = 0;
        int goalsFor = 0, goalsAgainst = 0;

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();

            if (line.matches("\\[.*\\].*")) {
                String[] parts = line.split("\\]");
                if (parts.length > 1) {
                    teamName = parts[1].trim();
                }
            }

            if (line.startsWith("Total") && line.contains(matchType)) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 9) {
                    try {
                        rank = Integer.parseInt(tokens[8]);
                    } catch (NumberFormatException e) {}
                }
            }

            if (line.startsWith(matchType)) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 7) {
                    try {
                        matches = Integer.parseInt(tokens[1]);
                        wins = Integer.parseInt(tokens[2]);
                        draws = Integer.parseInt(tokens[3]);
                        losses = Integer.parseInt(tokens[4]);
                        goalsFor = Integer.parseInt(tokens[5]);
                        goalsAgainst = Integer.parseInt(tokens[6]);
                        if (tokens.length >= 9) {
                            homeRank = Integer.parseInt(tokens[8]);
                        } else {
                            homeRank = rank;
                        }
                    } catch (NumberFormatException e) {}
                }
            }
        }
        sc.close();

        return new TeamStats(teamName, rank, homeRank, matches, wins, draws,
                losses, goalsFor, goalsAgainst, matchType);
    }

    public static HeadToHeadStats parseHeadToHead(String rawData) {
        Scanner sc = new Scanner(rawData);
        int totalMatches = 0, homeWins = 0, draws = 0, awayWins = 0;
        double homeGoals = 0, awayGoals = 0;
        int overGames = 0, underGames = 0;

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();

            if (line.startsWith("Last ")) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 2) {
                    try {
                        totalMatches = Integer.parseInt(tokens[1]);
                    } catch (NumberFormatException e) {}
                }
            }

            if (line.matches("Win \\d+.*Draw \\d+.*Loss \\d+.*")) {
                String[] parts = line.split("\\)");
                for (String part : parts) {
                    if (part.contains("Win")) {
                        homeWins = extractNumber(part);
                    } else if (part.contains("Draw")) {
                        draws = extractNumber(part);
                    } else if (part.contains("Loss")) {
                        awayWins = extractNumber(part);
                    }
                }
            }

            if (line.matches(".*goals.*Goals Scored/Conceded.*goals")) {
                String[] parts = line.split("Goals");
                if (parts.length >= 3) {
                    homeGoals = extractDouble(parts[0]);
                    awayGoals = extractDouble(parts[2]);
                } else if (parts.length == 2) {
                    String cleaned = line.replaceAll("Goals Scored/Conceded per Game", " ");
                    String[] nums = cleaned.split("\\s+");
                    for (String num : nums) {
                        if (num.matches("\\d+\\.\\d+")) {
                            if (homeGoals == 0) homeGoals = Double.parseDouble(num);
                            else if (awayGoals == 0) awayGoals = Double.parseDouble(num);
                        }
                    }
                }
            }

            if (line.contains("Over/Under Odds")) {
                sc.nextLine();
                String nextLine = sc.nextLine();
                String[] tokens = nextLine.split("\\s+");
                if (tokens.length >= 2) {
                    try {
                        overGames = Integer.parseInt(tokens[0].replace("%", ""));
                        if (tokens.length >= 6) {
                            underGames = Integer.parseInt(tokens[5].replace("%", ""));
                        }
                    } catch (NumberFormatException e) {}
                }
            }
        }
        sc.close();

        return new HeadToHeadStats(totalMatches, homeWins, draws, awayWins,
                homeGoals, awayGoals, overGames, underGames);
    }

    private static int extractNumber(String str) {
        try {
            String num = str.replaceAll("[^0-9]", "");
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        } catch (Exception e) {
            return 0;
        }
    }

    private static double extractDouble(String str) {
        try {
            String num = str.replaceAll("[^0-9.]", "");
            return num.isEmpty() ? 0.0 : Double.parseDouble(num);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static void main(String[] args) {
        String fullData = """
Standings
[ITA D2-2]   Palermo
FT	Matches	Win	Draw	Loss	Scored	Conceded	Pts	Rank	Rate
Total	5	3	2	0	7	2	11	2	60.0%
Home	3	2	1	0	4	1	7	2	66.7%
Away	2	1	1	0	3	1	4	6	50.0%
Last 6	5	3	2	0	7	2	11		60.0%
HT	Matches	Win	Draw	Loss	Scored	Conceded	Pts	Rank	Rate
Total	5	2	2	1	2	1	8	7	40.0%
Home	3	1	2	0	1	0	5	5	33.3%
Away	2	1	0	1	1	1	3	9	50.0%
Last 6	5	2	2	1	2	1	8		40.0%
[ITA D2-7]   Venezia
FT	Matches	Win	Draw	Loss	Scored	Conceded	Pts	Rank	Rate
Total	5	2	2	1	7	5	8	7	40.0%
Home	3	2	0	1	5	3	6	5	66.7%
Away	2	0	2	0	2	2	2	11	0.0%
Last 6	5	2	2	1	7	5	8		40.0%
HT	Matches	Win	Draw	Loss	Scored	Conceded	Pts	Rank	Rate
Total	5	3	2	0	4	1	11	2	60.0%
Home	3	2	1	0	3	1	7	1	66.7%
Away	2	1	1	0	1	0	4	6	50.0%
Last 6	5	3	2	0	4	1	11		60.0%
Head to Head Statistics
Palermo   Home    Same League    HT   
Last 14
Win 3 (21%)Draw 5 (36%)Loss 6 (43%)
1.1 goalsGoals Scored/Conceded per Game1.3 goals
Asian Handicap Odds (12 games)
25%Home
8%Draw
67%Away
Over/Under Odds (12 games)
42%Over
0%Draw
58%Under
League/Cup	Date	Home	Score	Away	Corner	
Bet365
   
First

Bet365
   
First
O/U
HW	D	AW	W/L	H	AH	A	AH
ITA D2	24-05-2024	Venezia	2-1(2-0)	Palermo	4-5(4-3)	1.95	3.40	3.80	L	1.00	0.5	0.85	L	O
ITA D2	20-05-2024	Palermo	0-1(0-0)	Venezia	9-4(6-1)	2.50	3.25	2.80	L	0.83	0	1.03	L	U
ITA D2	15-03-2024	Palermo	0-3(0-2)	Venezia	6-3(4-2)	2.15	3.30	2.90	L	0.98	0/0.5	0.88	L	O
ITA D2	26-09-2023	Venezia	1-3(1-1)	Palermo	8-1(3-0)	2.30	3.25	3.10	W	1.03	0/0.5	0.83	W	O
ITA D2	15-04-2023	Venezia	3-2(1-1)	Palermo	6-3(1-3)	2.25	3.20	3.30	L	0.93	0/0.5	0.93	L	O
ITA D2	27-11-2022	Palermo	0-1(0-0)	Venezia	4-5(2-2)	2.30	3.20	2.88	L	1.05	0/0.5	0.80	L	U
ITA D2	12-03-2019	Venezia	1-1(1-0)	Palermo	1-3(1-1)	3.25	3.00	2.37	D	1.15	0	0.72	D	U
ITA D2	26-10-2018	Palermo	1-1(0-0)	Venezia	5-6(1-3)	1.75	3.40	5.00	D	1.02	0.5/1	0.82	L	U
ITA D2	10-06-2018	Palermo	1-0(1-0)	Venezia	9-4(6-0)	2.05	3.20	3.80	W	1.05	0.5	0.75	W	U
ITA D2	06-06-2018	Venezia	1-1(0-0)	Palermo	8-5(3-2)	2.40	2.90	3.00	D	1.00	0/0.5	0.80	W	U
ITA D2	27-04-2018	Venezia	3-0(2-0)	Palermo	4-6(2-3)	2.50	3.00	3.00	L	1.12	0/0.5	0.75	L	O
ITA D2	02-12-2017	Palermo	0-0(0-0)	Venezia	11-3(7-1)	1.75	3.40	5.00	D	0.80	0.5	1.05	L	U
ITA D2	18-03-2004	Palermo	4-0(2-0)	Venezia	-				W					
ITA D2	18-10-2003	Venezia	1-1(0-0)	Palermo	-				D					
Previous Scores Statistics
Palermo   Home    Same League    HT   
Last 14
Win 7 (50%)Draw 4 (29%)Loss 3 (21%)
1.6 goalsGoals Scored/Conceded per Game0.9 goals
Asian Handicap Odds (14 games)
50%Home
7%Draw
43%Away
Over/Under Odds (14 games)
43%Over
0%Draw
57%Under
League/Cup	Date	Home	Score	Away	Corner	
Bet365
   
First

Bet365
   
First
O/U
HW	D	AW	W/L	H	AH	A	AH
ITA D2	27-09-2025	Cesena	1-1(1-0)	Palermo	2-1(2-1)	2.80	3.00	2.70	D	0.95	0	0.85	D	U
ITA Cup	23-09-2025	Udinese	2-1(2-0)	Palermo	3-9(1-5)	1.70	3.50	5.25	L	0.98	0.5/1	0.83	L	O
ITA D2	19-09-2025	Palermo	2-0(0-0)	Bari	6-1(3-0)	1.73	3.50	5.00	W	0.95	0.5/1	0.85	W	U
ITA D2	14-09-2025	SudTirol	0-2(0-1)	Palermo	7-3(4-3)	3.10	3.00	2.35	W	0.78	0/-0.5	1.03	W	U
ITA D2	30-08-2025	Palermo	0-0(0-0)	Frosinone	6-3(3-3)	1.91	3.60	4.00	D	0.90	0.5	0.90	L	U
ITA D2	23-08-2025	Palermo	2-1(1-0)	A.C. Reggiana 1919	9-6(5-3)	1.80	3.50	4.20	W	0.78	0.5	1.03	W	O
ITA Cup	16-08-2025	Cremonese	0-0(0-0)	Palermo	4-3(0-2)	1.91	3.40	4.00	D	0.93	0.5	0.88	W	U
INT CF	09-08-2025	Palermo	0-3(0-1)	Manchester City	4-3(2-1)	6.50	5.25	1.30	L	0.88	-1.5	0.93	L	O
INT CF	30-07-2025	Palermo	3-2(2-0)	FC Annecy	2-3(2-1)	1.95	3.40	3.30	W	1.00	0.5	0.80	W	O
INT CF	27-07-2025	Palermo	1-0(1-0)	A.S.D. Bra	4-6(0-2)	1.07	8.00	19.00	W	0.98	3	0.83	L	U
INT CF	24-07-2025	Palermo	4-0(3-0)	Sondrio	18-1(9-0)	1.08	10.00	21.00	W	0.90	2.5	0.90	W	O
INT CF	20-07-2025	Palermo	5-1(2-0)	FC Paradiso	4-2(4-0)	1.17	6.50	12.00	W	0.85	2	0.95	W	O
ITA D2	17-05-2025	Juve Stabia	1-0(0-0)	Palermo	6-7(4-1)	2.60	3.20	2.75	L	0.83	0	0.98	L	U
ITA D2	13-05-2025	Palermo	1-1(0-1)	Carrarese	10-3(3-2)	1.50	3.90	6.00	D	0.85	1	0.95	L	U
Venezia   Away    Same League    HT   
Last 14
Win 6 (43%)Draw 6 (43%)Loss 2 (14%)
2.3 goalsGoals Scored/Conceded per Game1 goals
Asian Handicap Odds (12 games)
50%Home
8%Draw
42%Away
Over/Under Odds (12 games)
50%Over
17%Draw
33%Under
League/Cup	Date	Home	Score	Away	Corner	
Bet365
   
First

Bet365
   
First
O/U
HW	D	AW	W/L	H	AH	A	AH
ITA D2	27-09-2025	Venezia	2-0(1-0)	Spezia	7-2(3-0)	2.60	3.20	2.62	W	0.80	0	1.00	W	U
ITA Cup	24-09-2025	Verona	0-0(0-0)	Venezia	5-6(2-5)	1.90	3.30	4.20	D	0.90	0.5	0.90	W	U
ITA D2	20-09-2025	Venezia	1-2(0-0)	Cesena	2-3(1-1)	1.95	3.30	3.80	L	0.98	0.5	0.83	L	O
ITA D2	13-09-2025	Pescara	2-2(0-1)	Venezia	6-5(5-3)	3.80	3.30	1.91	D	0.85	-0.5	0.95	L	O
INT CF	05-09-2025	Rapid Wien	1-1(1-1)	Venezia	3-2(0-2)	2.10	3.40	2.88	D	0.95	0/0.5	0.85	W	U
ITA D2	30-08-2025	Juve Stabia	0-0(0-0)	Venezia	6-1(3-0)	2.50	3.10	2.90	D	0.75	0	1.05	D	U
ITA D2	24-08-2025	Venezia	2-1(2-1)	Bari	6-4(2-0)	2.10	3.25	3.50	W	0.80	0/0.5	1.00	W	O
ITA Cup	16-08-2025	Venezia	4-0(2-0)	Mantova	10-4(5-0)	1.57	4.00	5.75	W	0.98	1	0.83	W	O
INT CF	09-08-2025	Tubize	2-2(1-2)	Venezia	-				D					
INT CF	06-08-2025	Lille	3-0(2-0)	Venezia	4-4(0-3)	1.67	3.80	4.00	L	0.90	0.5/1	0.90	L	D
INT CF	02-08-2025	Beerschot Wilrijk	1-1(0-1)	Venezia	7-1(2-0)	4.50	4.00	1.57	D	0.85	-1	0.95	L	D
INT CF	27-07-2025	Venezia	5-0(4-0)	Sassari Torres	8-3(5-1)	1.20	6.00	9.00	W	0.95	2	0.85	W	O
INT CF	23-07-2025	Venezia	4-2(2-0)	AC Dolomiti Bellunesi	9-5(6-0)	1.08	9.00	26.00	W	0.95	2.5/3	0.85	L	O
INT CF	19-07-2025	Venezia	8-0(2-0)	Real Vicenza	-				W					
        """;

        LeagueStats league = new LeagueStats(1.3, 1.1);

        TeamStats palermo = parseTeamStats(fullData, "Home");
        TeamStats venezia = parseTeamStats(fullData, "Away");
        HeadToHeadStats h2h = parseHeadToHead(fullData);

        MatchAnalysis analysis = new MatchAnalysis(palermo, venezia, h2h, league);
        analysis.printAnalysis();
    }
}