package flashscore.weeklydatascraping.mackolik.aistudio;

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