package analyzer.fs.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record Match(
    String id,
    String homeTeam,
    String awayTeam,
    Integer homeScore,
    Integer awayScore,
    Long timestamp,
    String seasonId,
    String leagueId,
    String countryCode
) {
    public String getFormattedDate() {
        if (timestamp == null) return "N/A";
        return Instant.ofEpochSecond(timestamp)
            .atZone(ZoneId.of("Europe/London"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    public String getScore() {
        if (homeScore == null || awayScore == null) return "-";
        return homeScore + " - " + awayScore;
    }

    public String getWinner() {
        if (homeScore == null || awayScore == null) return "PENDING";
        if (homeScore > awayScore) return "HOME";
        if (awayScore > homeScore) return "AWAY";
        return "DRAW";
    }

    @Override
    public String toString() {
        return homeTeam + " " + getScore() + " " + awayTeam;
    }
}
