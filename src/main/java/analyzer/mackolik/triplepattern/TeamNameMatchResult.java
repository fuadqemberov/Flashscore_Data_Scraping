package analyzer.mackolik.triplepattern;

import java.util.List;

/**
 * A historical occurrence where the opponent sequence matched the current pattern,
 * AND the target match ended with HT/FT = 1/2 or 2/1.
 */
public class TeamNameMatchResult {

    public final int    teamId;
    public final String teamName;
    public final String season;

    /** Which combination type matched (e.g. "PREV3", "PREV3+NEXT1", etc.) */
    public final String combinationType;

    /** The matched opponents in the historical season (same ordering as pattern) */
    public final List<String> matchedPrevOpponents;
    public final List<String> matchedNextOpponents;

    /** The historical target match */
    public final String historicalHomeTeam;
    public final String historicalAwayTeam;
    public final String historicalFTScore;   // e.g. "2-1"
    public final String historicalHTScore;   // e.g. "1-0"

    /** HT/FT result string: "1/2" or "2/1" */
    public final String htFtResult;

    /** Current season's upcoming match */
    public final String currentTargetHome;
    public final String currentTargetAway;

    public TeamNameMatchResult(int teamId, String teamName, String season,
                               String combinationType,
                               List<String> matchedPrevOpponents,
                               List<String> matchedNextOpponents,
                               String historicalHomeTeam, String historicalAwayTeam,
                               String historicalFTScore, String historicalHTScore,
                               String htFtResult,
                               String currentTargetHome, String currentTargetAway) {
        this.teamId                = teamId;
        this.teamName              = teamName;
        this.season                = season;
        this.combinationType       = combinationType;
        this.matchedPrevOpponents  = matchedPrevOpponents;
        this.matchedNextOpponents  = matchedNextOpponents;
        this.historicalHomeTeam    = historicalHomeTeam;
        this.historicalAwayTeam    = historicalAwayTeam;
        this.historicalFTScore     = historicalFTScore;
        this.historicalHTScore     = historicalHTScore;
        this.htFtResult            = htFtResult;
        this.currentTargetHome     = currentTargetHome;
        this.currentTargetAway     = currentTargetAway;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s | Sezon: %s | Kombinasyon: %s ===\n", teamName, season, combinationType));
        sb.append(String.format("  Mevcut Maç    : %s vs %s\n", currentTargetHome, currentTargetAway));
        sb.append(String.format("  Geçmiş Hedef  : %s %s %s  (İY: %s)  HT/FT: [%s] ★\n",
                historicalHomeTeam, historicalFTScore, historicalAwayTeam,
                historicalHTScore != null ? historicalHTScore : "N/A",
                htFtResult));
        sb.append(String.format("  Eşleşen Önceki: %s\n", matchedPrevOpponents));
        sb.append(String.format("  Eşleşen Sonraki: %s\n", matchedNextOpponents));
        return sb.toString();
    }
}
