package analyzer.mackolik.triplepattern.deepseek;


import java.util.List;

/**
 * Mevcut sezonda oynanmamış bir maçın etrafındaki rakip dizisini tutar.
 */
public class CurrentOpponentSequence {
    public final String teamName;
    public final String upcomingHomeTeam;
    public final String upcomingAwayTeam;
    public final List<String> lastOpponents;   // kronolojik (en eski → en yeni)
    public final List<String> nextOpponents;   // upcoming maçtan sonraki rakipler

    public CurrentOpponentSequence(String teamName,
                                   String upcomingHomeTeam,
                                   String upcomingAwayTeam,
                                   List<String> lastOpponents,
                                   List<String> nextOpponents) {
        this.teamName = teamName;
        this.upcomingHomeTeam = upcomingHomeTeam;
        this.upcomingAwayTeam = upcomingAwayTeam;
        this.lastOpponents = lastOpponents;
        this.nextOpponents = nextOpponents;
    }
}