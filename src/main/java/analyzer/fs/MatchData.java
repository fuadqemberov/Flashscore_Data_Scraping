package analyzer.fs;

import java.util.LinkedHashMap;
import java.util.Map;

public class MatchData {
    public String matchId;
    public String date;
    public String homeTeam;
    public String awayTeam;
    public String ftScore = "-";
    public String htScore = "-";
    public Map<String, String> oddsMap = new LinkedHashMap<>();

    public MatchData(String matchId, String date, String homeTeam, String awayTeam) {
        this.matchId = matchId;
        this.date = date;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
    }
}
