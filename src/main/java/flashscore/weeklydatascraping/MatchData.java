package flashscore.weeklydatascraping;

public class MatchData {
    String date;
    String homeTeam;
    String awayTeam;
    String ftScore;
    String htScore;
    String comebackType = "YOK"; // "1/2", "2/1", veya "YOK"

    // Verileri tek bir string olarak formatlamak için yardımcı metod
    @Override
    public String toString() {
        return String.format("Tarih: %s, Maç: %s vs %s, Skor: %s, İY: %s",
                date, homeTeam, awayTeam, ftScore, htScore);
    }
}