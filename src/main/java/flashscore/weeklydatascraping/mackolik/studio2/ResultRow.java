package flashscore.weeklydatascraping.mackolik.studio2;

import java.util.List;
public class ResultRow {
    MatchData comebackMatch;
    List<MatchData> previousMatches;
    List<MatchData> nextMatches;
    public ResultRow(MatchData comebackMatch, List<MatchData> previousMatches, List<MatchData> nextMatches) {
        this.comebackMatch = comebackMatch;
        this.previousMatches = previousMatches;
        this.nextMatches = nextMatches;
    }
}