package analyzer.fs.model;

public record Odds(
    String matchId,
    String bookmaker,
    Double homeOdd,
    Double drawOdd,
    Double awayOdd,
    Long timestamp
) {
    public String getHomeOddStr() {
        return homeOdd != null ? String.format("%.2f", homeOdd) : "-";
    }
    public String getDrawOddStr() {
        return drawOdd != null ? String.format("%.2f", drawOdd) : "-";
    }
    public String getAwayOddStr() {
        return awayOdd != null ? String.format("%.2f", awayOdd) : "-";
    }
}
