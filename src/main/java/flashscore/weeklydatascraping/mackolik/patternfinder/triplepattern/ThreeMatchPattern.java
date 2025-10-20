package flashscore.weeklydatascraping.mackolik.patternfinder.triplepattern;

// Model for three consecutive match pattern
class ThreeMatchPattern {
    public String score1;
    public String score2;
    public String score3;
    public String homeTeam1;
    public String awayTeam1;
    public String homeTeam2;
    public String awayTeam2;
    public String homeTeam3;
    public String awayTeam3;
    public String teamName;
    public String nextHomeTeam;
    public String nextAwayTeam;

    public ThreeMatchPattern(String score1, String score2, String score3,
                             String homeTeam1, String awayTeam1,
                             String homeTeam2, String awayTeam2,
                             String homeTeam3, String awayTeam3,
                             String teamName,
                             String nextHomeTeam, String nextAwayTeam) {
        this.score1 = score1;
        this.score2 = score2;
        this.score3 = score3;
        this.homeTeam1 = homeTeam1;
        this.awayTeam1 = awayTeam1;
        this.homeTeam2 = homeTeam2;
        this.awayTeam2 = awayTeam2;
        this.homeTeam3 = homeTeam3;
        this.awayTeam3 = awayTeam3;
        this.teamName = teamName;
        this.nextHomeTeam = nextHomeTeam;
        this.nextAwayTeam = nextAwayTeam;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Maç 1: %s vs %s -> %s\n", homeTeam1, awayTeam1, score1));
        sb.append(String.format("Maç 2: %s vs %s -> %s\n", homeTeam2, awayTeam2, score2));
        sb.append(String.format("Maç 3: %s vs %s -> %s", homeTeam3, awayTeam3, score3));
        if (nextHomeTeam != null && nextAwayTeam != null) {
            sb.append("\n").append("Sonraki Maç: ").append(nextHomeTeam).append(" vs ").append(nextAwayTeam).append(" -> Henüz oynanmadı");
        }
        return sb.toString();
    }
}

// Result model for found triple match patterns
class TripleMatchResult {
    String homeTeam1;
    String awayTeam1;
    String score1;
    String htScore1;

    String homeTeam2;
    String awayTeam2;
    String score2;
    String htScore2;

    String homeTeam3;
    String awayTeam3;
    String score3;
    String htScore3;

    String previousMatchScore;
    String previousHTScore;
    String nextMatchScore;
    String nextHTScore;
    String season;

    ThreeMatchPattern originalPattern;

    public TripleMatchResult(String homeTeam1, String awayTeam1, String score1,
                             String homeTeam2, String awayTeam2, String score2,
                             String homeTeam3, String awayTeam3, String score3,
                             String season, ThreeMatchPattern originalPattern) {
        this.homeTeam1 = homeTeam1;
        this.awayTeam1 = awayTeam1;
        this.score1 = score1;
        this.homeTeam2 = homeTeam2;
        this.awayTeam2 = awayTeam2;
        this.score2 = score2;
        this.homeTeam3 = homeTeam3;
        this.awayTeam3 = awayTeam3;
        this.score3 = score3;
        this.season = season;
        this.originalPattern = originalPattern;
    }

    @Override
    public String toString() {
        String prevMatch = previousMatchScore != null ?
                previousMatchScore + " (HT: " + (previousHTScore != null ? previousHTScore : "N/A") + ")" :
                "Bilgi Yok";

        String nextMatch = nextMatchScore != null ?
                nextMatchScore + " (HT: " + (nextHTScore != null ? nextHTScore : "N/A") + ")" :
                "Bilgi Yok";

        String match1 = homeTeam1 + " " + score1 + " " + awayTeam1 +
                        " (HT: " + (htScore1 != null ? htScore1 : "N/A") + ")";

        String match2 = homeTeam2 + " " + score2 + " " + awayTeam2 +
                        " (HT: " + (htScore2 != null ? htScore2 : "N/A") + ")";

        String match3 = homeTeam3 + " " + score3 + " " + awayTeam3 +
                        " (HT: " + (htScore3 != null ? htScore3 : "N/A") + ")";

        return String.format("[%s Sezonu]\nÖnceki Maç -> %s\n\nPattern Maçları:\n1) %s\n2) %s\n3) %s\n\nSonraki Maç -> %s",
                season,
                prevMatch,
                match1,
                match2,
                match3,
                nextMatch);
    }
}