package analyzer.mackolik.patternfinder;

import java.util.HashSet;
import java.util.Set;

class MatchPattern {
    public String score1;
    public String score2;
    public String homeTeam1;
    public String awayTeam1;
    public String homeTeam2;
    public String awayTeam2;
    public String teamName;
    public String nextHomeTeam;
    public String nextAwayTeam;
    public String middleHomeTeam; // ara maç ev sahibi
    public String middleAwayTeam; // ara maç deplasman

    public MatchPattern(String score1, String score2, String homeTeam1, String awayTeam1,
                        String homeTeam2, String awayTeam2, String teamName,
                        String nextHomeTeam, String nextAwayTeam) {
        this.score1 = score1;
        this.score2 = score2;
        this.homeTeam1 = homeTeam1;
        this.awayTeam1 = awayTeam1;
        this.homeTeam2 = homeTeam2;
        this.awayTeam2 = awayTeam2;
        this.teamName = teamName;
        this.nextHomeTeam = nextHomeTeam;
        this.nextAwayTeam = nextAwayTeam;
        this.middleHomeTeam = nextHomeTeam;
        this.middleAwayTeam = nextAwayTeam;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s vs %s -> %s\n", homeTeam1, awayTeam1, score1));
        if (middleHomeTeam != null)
            sb.append(String.format("%s vs %s -> ??? (ARA MAC - tahmin)\n", middleHomeTeam, middleAwayTeam));
        if (homeTeam2 != null)
            sb.append(String.format("%s vs %s -> Henuz oynanmadi", homeTeam2, awayTeam2));
        return sb.toString();
    }

    public Set<String> getAllTeams() {
        Set<String> teams = new HashSet<>();
        teams.add(homeTeam1); teams.add(awayTeam1);
        if (homeTeam2 != null) teams.add(homeTeam2);
        if (awayTeam2 != null) teams.add(awayTeam2);
        return teams;
    }
}

class MatchResult {
    String homeTeam, awayTeam, score;
    String middleHomeTeam, middleAwayTeam, middleScore, middleHTScore;
    String previousMatchScore, previousHTScore;
    String nextMatchScore, nextHTScore;
    String season, firstMatchHTScore;
    String secondMatchHomeTeam, secondMatchScore, secondMatchAwayTeam, secondMatchHTScore;
    MatchPattern originalPattern;

    public MatchResult(String homeTeam, String awayTeam, String score, String season, MatchPattern originalPattern) {
        this.homeTeam = homeTeam; this.awayTeam = awayTeam;
        this.score = score; this.season = season;
        this.originalPattern = originalPattern;
    }

    @Override
    public String toString() {
        String prev   = previousMatchScore != null ? previousMatchScore + " (HT: " + nvl(previousHTScore) + ")" : "Bilgi Yok";
        String next   = nextMatchScore != null ? nextMatchScore + " (HT: " + nvl(nextHTScore) + ")" : "Bilgi Yok";
        String m1     = homeTeam + " " + score + " " + awayTeam + " (HT: " + nvl(firstMatchHTScore) + ")";
        String mid    = (middleHomeTeam != null && middleScore != null)
                ? middleHomeTeam + " " + middleScore + " " + middleAwayTeam + " (HT: " + nvl(middleHTScore) + ")"
                : "Bilgi Yok";
        String m2     = secondMatchHomeTeam != null
                ? secondMatchHomeTeam + " " + secondMatchScore + " " + secondMatchAwayTeam + " (HT: " + nvl(secondMatchHTScore) + ")"
                : "Bilgi Yok";

        return String.format(
                "[%s Sezonu]\nonceki mac    -> %s\n1. pat. mac   -> %s\nARA MAC (2/1) -> %s\n2. pat. mac   -> %s\nsonraki mac   -> %s",
                season, prev, m1, mid, m2, next);
    }

    private String nvl(String s) { return s != null ? s : "N/A"; }

    public boolean containsOriginalTeamVsOpponent() { return true; }
}