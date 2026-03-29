package flashscore.weeklydatascraping.mackolik.season_streak;

public class Match {

    private String date;
    private String homeTeam;
    private String awayTeam;
    private String score;
    private String halfTimeScore;
    private String result;       // G, M, B
    private String overUnder;    // U, A  (Ü yerine U - encoding safe)
    private String bothScore;    // KG, -
    private String side;         // home, away
    private String season;

    private int day;
    private int month;
    private int year;

    public Match(String date, String homeTeam, String awayTeam,
                 String score, String halfTimeScore, String result,
                 String overUnder, String bothScore, String side, String season) {
        this.date          = date.trim();
        this.homeTeam      = homeTeam.trim();
        this.awayTeam      = awayTeam.trim();
        this.score         = score.trim();
        this.halfTimeScore = halfTimeScore.trim();
        this.result        = result.trim();
        this.overUnder     = overUnder.trim();
        this.bothScore     = bothScore.trim();
        this.side          = side;
        this.season        = season;
        parseDate(date.trim());
    }

    // Format: "16.08.2025"  ya da  "1.09.2025"
    private void parseDate(String d) {
        try {
            String[] p = d.split("\\.");
            this.day   = Integer.parseInt(p[0].trim());
            this.month = Integer.parseInt(p[1].trim());
            this.year  = Integer.parseInt(p[2].trim());
        } catch (Exception e) {
            this.day = 0; this.month = 0; this.year = 0;
        }
    }

    // Pencere yoxlamasi: ayin ilk heftesi (1-7) ve son heftesi (24-31)
    public boolean isEarlyMonth() { return day >= 1  && day <= 7;  }
    public boolean isLateMonth()  { return day >= 24 && day <= 31; }
    public boolean isInWindow()   { return isEarlyMonth() || isLateMonth(); }

    // Netice
    public boolean isWin()       { return "G".equals(result); }
    public boolean isLoss()      { return "M".equals(result); }
    public boolean isDraw()      { return "B".equals(result); }
    public boolean isOver()      { return overUnder != null && (overUnder.contains("U") || overUnder.contains("\u00dc")); }
    public boolean isUnder()     { return overUnder != null && overUnder.contains("A") && !overUnder.contains("U"); }
    public boolean isBothScore() { return "KG".equals(bothScore); }
    public boolean isHome()      { return "home".equals(side); }
    public boolean isAway()      { return "away".equals(side); }

    // Getters
    public String getDate()          { return date; }
    public String getHomeTeam()      { return homeTeam; }
    public String getAwayTeam()      { return awayTeam; }
    public String getScore()         { return score; }
    public String getHalfTimeScore() { return halfTimeScore; }
    public String getResult()        { return result; }
    public String getOverUnder()     { return overUnder; }
    public String getBothScore()     { return bothScore; }
    public String getSide()          { return side; }
    public String getSeason()        { return season; }
    public int    getDay()           { return day; }
    public int    getMonth()         { return month; }
    public int    getYear()          { return year; }

    public String getWindowLabel() {
        if (isEarlyMonth()) return "1-7";
        if (isLateMonth())  return "24-31";
        return "mid";
    }

    @Override
    public String toString() {
        return String.format("[%s|gun%2d|%s] %-18s %s %-18s  HT:%-5s FT:%-3s OU:%-3s KG:%-3s",
                season, day, getWindowLabel(),
                homeTeam, score, awayTeam,
                halfTimeScore, result, overUnder, bothScore);
    }
}