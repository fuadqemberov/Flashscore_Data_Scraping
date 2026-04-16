package analyzer.scraper_playwrith;

import java.util.HashMap;
import java.util.Map;

public class MatchData {
    public String matchId, homeTeam, awayTeam, date;
    public String country = "-", league = "-", matchDateTime = "-", ftScore = "-", htScore = "-";
    public Map<String, String> oddsMap = new HashMap<>();

    public MatchData(String matchId, String homeTeam, String awayTeam, String date) {
        this.matchId = matchId;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.date = date;
    }
}