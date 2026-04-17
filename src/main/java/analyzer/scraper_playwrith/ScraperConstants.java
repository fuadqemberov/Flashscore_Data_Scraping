package analyzer.scraper_playwrith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScraperConstants {
    public static final int MAX_CONCURRENT_DRIVERS = 20;
    public static final String BASE_URL = "https://www.flashscore.co.uk/football/";
    public static final String MATCH_URL_PREFIX = "https://www.flashscore.co.uk/match/football/";

    public static final String[][] ODDS_TABS = {
            {"1x2", "1X2"}, {"Over/Under", "Over/Under"}, {"Both teams", "Both teams to score"},
            {"Double chance", "Double chance"}, {"Correct score", "Correct score"}
    };
    public static final String[] HALF_TABS = {"Full Time", "1st Half", "2nd Half"};
    public static final List<Double> OU_THRESHOLDS = Arrays.asList(0.5, 1.5, 2.5, 3.5, 4.5);
    public static final List<String> CORRECT_SCORES = Arrays.asList(
            "1:0", "0:1", "1:1", "2:0", "0:2", "2:1", "1:2", "2:2", "3:0", "0:3", "3:1", "1:3", "3:2", "2:3", "3:3",
            "4:0", "0:4", "4:1", "1:4", "4:2", "2:4", "4:3", "3:4", "4:4"
    );

    public static final Set<String> MAIN_TAB_TEXTS = new HashSet<>(Arrays.asList(
            "MATCH", "ODDS", "H2H", "STANDINGS", "TABLE", "DRAW", "STATS", "NEWS"));
    public static final List<String> STATIC_COLUMN_KEYS = new ArrayList<>();

    static {
        for (String[] tabDef : ODDS_TABS) {
            String tabKey = tabDef[0];
            for (String period : HALF_TABS) {
                if (tabKey.equals("Correct score") && period.equals("2nd Half")) continue;
                switch (tabKey) {
                    case "1x2":
                        Arrays.asList("Home", "Draw", "Away").forEach(l -> STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + l));
                        break;
                    case "Over/Under":
                        OU_THRESHOLDS.forEach(th -> {
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|O " + th);
                            STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|U " + th);
                        });
                        break;
                    case "Both teams":
                        Arrays.asList("Yes", "No").forEach(l -> STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + l));
                        break;
                    case "Double chance":
                        Arrays.asList("1X", "12", "X2").forEach(l -> STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + l));
                        break;
                    case "Correct score":
                        CORRECT_SCORES.forEach(s -> STATIC_COLUMN_KEYS.add(tabKey + "|" + period + "|" + s));
                        break;
                }
            }
        }
    }
}