package flashscore.weeklydatascraping.mackolik.claude;

import java.util.HashSet;
import java.util.Set;

// Keep MatchPattern as is
class MatchPattern {
    public String score1;
    public String score2;
    public String homeTeam1;
    public String awayTeam1;
    public String homeTeam2;
    public String awayTeam2;
    public String teamName; // Add team name for context in results

    public MatchPattern(String score1, String score2, String homeTeam1, String awayTeam1, String homeTeam2, String awayTeam2, String teamName) {
        this.score1 = score1;
        this.score2 = score2;
        this.homeTeam1 = homeTeam1;
        this.awayTeam1 = awayTeam1;
        this.homeTeam2 = homeTeam2;
        this.awayTeam2 = awayTeam2;
        this.teamName = teamName;
    }

    @Override
    public String toString() {
        return String.format("%s vs %s -> %s\n%s vs %s -> %s",
                homeTeam1, awayTeam1, score1,
                homeTeam2, awayTeam2, score2);
    }

    // Get all team names in the pattern - Keep as is
    public Set<String> getAllTeams() {
        Set<String> teams = new HashSet<>();
        teams.add(homeTeam1);
        teams.add(awayTeam1);
        teams.add(homeTeam2);
        teams.add(awayTeam2);
        return teams;
    }
}

// Refactor MatchResult slightly - remove static WebDriver methods
class MatchResult {
    String homeTeam;
    String awayTeam;
    String score;
    String previousMatchScore;
    String previousHTScore;
    String nextMatchScore;
    String nextHTScore;
    String season;
    String firstMatchHTScore;
    String secondMatchHomeTeam;
    String secondMatchScore;
    String secondMatchAwayTeam;
    String secondMatchHTScore;

    // Store the original pattern for context and filtering
    MatchPattern originalPattern;

    public MatchResult(String homeTeam, String awayTeam, String score, String season, MatchPattern originalPattern) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.score = score;
        this.season = season;
        this.originalPattern = originalPattern; // Store the pattern
    }

    @Override
    public String toString() {
        String prevMatch = previousMatchScore != null ?
                previousMatchScore + " (HT: " + (previousHTScore != null ? previousHTScore : "N/A") + ")" :
                "Bilgi Yok";

        String nextMatch = nextMatchScore != null ?
                nextMatchScore + " (HT: " + (nextHTScore != null ? nextHTScore : "N/A") + ")" :
                "Bilgi Yok";

        String matchPattern1 = homeTeam + " " + score + " " + awayTeam +
                               " (HT: " + (firstMatchHTScore != null ? firstMatchHTScore : "N/A") + ")";

        String matchPattern2 = secondMatchHomeTeam != null ?
                secondMatchHomeTeam + " " + secondMatchScore + " " + secondMatchAwayTeam +
                " (HT: " + (secondMatchHTScore != null ? secondMatchHTScore : "N/A") + ")" :
                "Bilgi Yok";

        // Add season info to the output for clarity
        return String.format("[%s Sezonu]\nönceki maç -> %s\npattern maçları -> %s\n                 %s\nsonraki maç -> %s",
                season,
                prevMatch,
                matchPattern1,
                matchPattern2,
                nextMatch);
    }

    // Keep the filtering logic - depends on originalPattern being set
    public boolean containsOriginalTeamVsOpponent() {
        if (originalPattern == null) return false; // Should not happen if constructor is used correctly

        // Check if first match has a team playing against the same opponent as in the original pattern
        if ((homeTeam.equals(originalPattern.homeTeam1) && awayTeam.equals(originalPattern.awayTeam1)) ||
            (homeTeam.equals(originalPattern.awayTeam1) && awayTeam.equals(originalPattern.homeTeam1)) ||
            (homeTeam.equals(originalPattern.homeTeam2) && awayTeam.equals(originalPattern.awayTeam2)) ||
            (homeTeam.equals(originalPattern.awayTeam2) && awayTeam.equals(originalPattern.homeTeam2))) {
            return true;
        }

        // Check if second match has a team playing against the same opponent as in the original pattern
        if (secondMatchHomeTeam != null && // Ensure second match exists
            ((secondMatchHomeTeam.equals(originalPattern.homeTeam1) && secondMatchAwayTeam.equals(originalPattern.awayTeam1)) ||
             (secondMatchHomeTeam.equals(originalPattern.awayTeam1) && secondMatchAwayTeam.equals(originalPattern.homeTeam1)) ||
             (secondMatchHomeTeam.equals(originalPattern.homeTeam2) && secondMatchAwayTeam.equals(originalPattern.awayTeam2)) ||
             (secondMatchHomeTeam.equals(originalPattern.awayTeam2) && secondMatchAwayTeam.equals(originalPattern.homeTeam2)))) {
            return true;
        }


        return false;
    }
}