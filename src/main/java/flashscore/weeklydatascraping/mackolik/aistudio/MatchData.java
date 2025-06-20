package flashscore.weeklydatascraping.mackolik.aistudio;

class MatchData {
    String season;
    String date;
    String homeTeam;
    String awayTeam;
    String ftScore;
    String htScore;
    String comebackType;

    @Override
    public String toString() {
        return String.format("[%s] %s: %s %s %s (İY: %s) [%s]", season, date, homeTeam, ftScore, awayTeam, htScore, comebackType);
    }
}
