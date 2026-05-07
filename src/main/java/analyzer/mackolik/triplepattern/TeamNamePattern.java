package analyzer.mackolik.triplepattern;

import java.util.List;

/**
 * Holds the opponent team names (in order) surrounding an unstarted match.
 * Direction (home/away) is IGNORED – only the sequence matters.
 *
 * Layout (indices into combined list):
 *   prev[-3], prev[-2], prev[-1] | TARGET (unstarted) | next[+1], next[+2], next[+3]
 */
public class TeamNamePattern {

    /** Team ID of the team being analyzed */
    public final int teamId;
    public final String teamName;

    /** Opponents in the 3 matches BEFORE the unstarted match (oldest → newest) */
    public final List<String> prevOpponents;   // size <= 3

    /** Opponents in the 3 matches AFTER the unstarted match (nearest → furthest) */
    public final List<String> nextOpponents;   // size <= 3

    /** The unstarted match itself */
    public final String targetHomeTeam;
    public final String targetAwayTeam;

    public TeamNamePattern(int teamId, String teamName,
                           List<String> prevOpponents,
                           List<String> nextOpponents,
                           String targetHomeTeam, String targetAwayTeam) {
        this.teamId        = teamId;
        this.teamName      = teamName;
        this.prevOpponents = prevOpponents;
        this.nextOpponents = nextOpponents;
        this.targetHomeTeam = targetHomeTeam;
        this.targetAwayTeam = targetAwayTeam;
    }

    @Override
    public String toString() {
        return String.format("TeamNamePattern[team=%s(%d), prev=%s, target=%s vs %s, next=%s]",
                teamName, teamId, prevOpponents, targetHomeTeam, targetAwayTeam, nextOpponents);
    }
}