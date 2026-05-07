package analyzer.mackolik.triplepattern.deepseek;

import java.util.List;


public class OpponentSequenceMatch {
    public final String season;
    public final String targetHome;
    public final String targetAway;
    public final String ftScore;
    public final String htScore;
    public final List<String> matchedOpponentSequence;
    public final String previousMatchSummary;
    public final String nextMatchSummary;

    public OpponentSequenceMatch(String season,
                                 String targetHome,
                                 String targetAway,
                                 String ftScore,
                                 String htScore,
                                 List<String> matchedOpponentSequence,
                                 String previousMatchSummary,
                                 String nextMatchSummary) {
        this.season = season;
        this.targetHome = targetHome;
        this.targetAway = targetAway;
        this.ftScore = ftScore;
        this.htScore = htScore;
        this.matchedOpponentSequence = matchedOpponentSequence;
        this.previousMatchSummary = previousMatchSummary;
        this.nextMatchSummary = nextMatchSummary;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s vs %s : %s (HT: %s)\n", season, targetHome, targetAway, ftScore, htScore));
        sb.append("Rakip Dizisi: ");
        sb.append(String.join(" → ", matchedOpponentSequence));
        sb.append("\nÖnceki Maç: ").append(previousMatchSummary != null ? previousMatchSummary : "Bilgi Yok");
        sb.append("\nSonraki Maç: ").append(nextMatchSummary != null ? nextMatchSummary : "Bilgi Yok");
        return sb.toString();
    }
}