package analyzer.mackolik.triplepattern.deepseek;



public class ThreeMatchPattern {
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