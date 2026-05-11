package analyzer.flashscore;

import java.util.*;
import java.util.stream.Collectors;

public class ScraperConstants {

    public static final int MAX_CONCURRENT_DRIVERS = 10;

    public static final String BASE_URL = "https://www.flashscore.co.uk/football/";
    public static final String MATCH_URL_PREFIX = "https://www.flashscore.co.uk/match/football/";

    public static final String ODDS_API_URL =
            "https://global.ds.lsapp.eu/odds/pq_graphql?_hash=oce&eventId=%s&projectId=5&geoIpCode=AZ&geoIpSubdivisionCode=AZBA";

    public static final int BET365_ID = 16;

    public static final String[][] ODDS_TABS = {
            {"1x2", "1X2"}, {"Over/Under", "Over/Under"}, {"Both teams", "Both teams to score"},
            {"Double chance", "Double chance"}, {"Correct score", "Correct score"}
    };
    public static final String[] HALF_TABS = {"Full Time", "1st Half", "2nd Half"};
    public static final List<Double> OU_THRESHOLDS = Arrays.asList(0.5, 1.5, 2.5, 3.5, 4.5, 5.5);
    public static final List<String> CORRECT_SCORES = Arrays.asList(
            "1:0", "0:1", "1:1", "2:0", "0:2", "2:1", "1:2", "2:2", "3:0", "0:3",
            "3:1", "1:3", "3:2", "2:3", "3:3",
            "4:0", "0:4", "4:1", "1:4", "4:2", "2:4", "4:3", "3:4", "4:4"
    );

    // ---------- YENİ SÜTUN TANIMLARI (CSV sırasına uygun) ----------
    public static class ColumnDef {
        public final String groupLabel;   // Excel 1. satırdaki grup adı
        public final String subLabel;     // Excel 2. satırdaki alt başlık (ör: FT_1_A)
        public final String dataKey;      // oddsMap'teki anahtar (aynı kalacak)

        public ColumnDef(String groupLabel, String subLabel, String dataKey) {
            this.groupLabel = groupLabel;
            this.subLabel = subLabel;
            this.dataKey = dataKey;
        }
    }

    public static final List<ColumnDef> COLUMN_DEFS = List.of(
            // --------------------- MAC SONU / FULL TIME (3 sütun) ---------------------
            new ColumnDef("MAC SONU / FULL TIME", "FT_1_A", "1x2|Full Time|Home"),
            new ColumnDef("MAC SONU / FULL TIME", "FT_X_A", "1x2|Full Time|Draw"),
            new ColumnDef("MAC SONU / FULL TIME", "FT_2_A", "1x2|Full Time|Away"),
            // --------------------- ILK YARI / 1ST HALF (3 sütun) ---------------------
            new ColumnDef("ILK YARI / 1ST HALF", "1ST_1_A", "1x2|1st Half|Home"),
            new ColumnDef("ILK YARI / 1ST HALF", "1ST_X_A", "1x2|1st Half|Draw"),
            new ColumnDef("ILK YARI / 1ST HALF", "1ST_2_A", "1x2|1st Half|Away"),
            // --------------------- IKINCI YARI / 2ND HALF (3 sütun) ---------------------
            new ColumnDef("IKINCI YARI / 2ND HALF", "2ND_1_A", "1x2|2nd Half|Home"),
            new ColumnDef("IKINCI YARI / 2ND HALF", "2ND_X_A", "1x2|2nd Half|Draw"),
            new ColumnDef("IKINCI YARI / 2ND HALF", "2ND_2_A", "1x2|2nd Half|Away"),
            // --------------------- MAC SONU KG VAR/YOK (2 sütun) ---------------------
            new ColumnDef("MAC SONU KG VAR/YOK", "BTS_FT_YES_A", "Both teams|Full Time|Yes"),
            new ColumnDef("MAC SONU KG VAR/YOK", "BTS_FT_NO_A", "Both teams|Full Time|No"),
            // --------------------- ILK YARI KG VAR/YOK (2 sütun) ---------------------
            new ColumnDef("ILK YARI KG VAR/YOK", "BTS_1ST_YES_A", "Both teams|1st Half|Yes"),
            new ColumnDef("ILK YARI KG VAR/YOK", "BTS_1ST_NO_A", "Both teams|1st Half|No"),
            // --------------------- IKINCI YARI KG VAR/YOK (2 sütun) ---------------------
            new ColumnDef("IKINCI YARI KG VAR/YOK", "BTS_2ND_YES_A", "Both teams|2nd Half|Yes"),
            new ColumnDef("IKINCI YARI KG VAR/YOK", "BTS_2ND_NO_A", "Both teams|2nd Half|No"),
            // --------------------- MAC SONU CIFTE SANS / FULL TIME DOUBLE CHANCE (3) ---------------------
            new ColumnDef("MAC SONU CIFTE SANS / FULL TIME DC", "DBC_FT_1X_A", "Double chance|Full Time|1X"),
            new ColumnDef("MAC SONU CIFTE SANS / FULL TIME DC", "DBC_FT_12_A", "Double chance|Full Time|12"),
            new ColumnDef("MAC SONU CIFTE SANS / FULL TIME DC", "DBC_FT_X2_A", "Double chance|Full Time|X2"),
            // --------------------- ILK YARI CIFTE SANS / 1ST DOUBLE CHANCE (3) ---------------------
            new ColumnDef("ILK YARI CIFTE SANS / 1ST DC", "DBC_1ST_1X_A", "Double chance|1st Half|1X"),
            new ColumnDef("ILK YARI CIFTE SANS / 1ST DC", "DBC_1ST_12_A", "Double chance|1st Half|12"),
            new ColumnDef("ILK YARI CIFTE SANS / 1ST DC", "DBC_1ST_X2_A", "Double chance|1st Half|X2"),
            // --------------------- MAC SONU A/U (0.5 - 5.5) (12 sütun) ---------------------
            new ColumnDef("MAC SONU A/U 0,5", "FT_0,5_OVER_A", "Over/Under|Full Time|O 0.5"),
            new ColumnDef("MAC SONU A/U 0,5", "FT_0,5_UNDER_A", "Over/Under|Full Time|U 0.5"),
            new ColumnDef("MAC SONU A/U 1,5", "FT_1,5_OVER_A", "Over/Under|Full Time|O 1.5"),
            new ColumnDef("MAC SONU A/U 1,5", "FT_1,5_UNDER_A", "Over/Under|Full Time|U 1.5"),
            new ColumnDef("MAC SONU A/U 2,5", "FT_2,5_OVER_A", "Over/Under|Full Time|O 2.5"),
            new ColumnDef("MAC SONU A/U 2,5", "FT_2,5_UNDER_A", "Over/Under|Full Time|U 2.5"),
            new ColumnDef("MAC SONU A/U 3,5", "FT_3,5_OVER_A", "Over/Under|Full Time|O 3.5"),
            new ColumnDef("MAC SONU A/U 3,5", "FT_3,5_UNDER_A", "Over/Under|Full Time|U 3.5"),
            new ColumnDef("MAC SONU A/U 4,5", "FT_4,5_OVER_A", "Over/Under|Full Time|O 4.5"),
            new ColumnDef("MAC SONU A/U 4,5", "FT_4,5_UNDER_A", "Over/Under|Full Time|U 4.5"),
            new ColumnDef("MAC SONU A/U 5,5", "FT_5,5_OVER_A", "Over/Under|Full Time|O 5.5"),
            new ColumnDef("MAC SONU A/U 5,5", "FT_5,5_UNDER_A", "Over/Under|Full Time|U 5.5"),
            // --------------------- ILK YARI A/U (0.5 - 2.5) (6 sütun) ---------------------
            new ColumnDef("ILK YARI A/U 0,5", "1ST_0,5_OVER_A", "Over/Under|1st Half|O 0.5"),
            new ColumnDef("ILK YARI A/U 0,5", "1ST_0,5_UNDER_A", "Over/Under|1st Half|U 0.5"),
            new ColumnDef("ILK YARI A/U 1,5", "1ST_1,5_OVER_A", "Over/Under|1st Half|O 1.5"),
            new ColumnDef("ILK YARI A/U 1,5", "1ST_1,5_UNDER_A", "Over/Under|1st Half|U 1.5"),
            new ColumnDef("ILK YARI A/U 2,5", "1ST_2,5_OVER_A", "Over/Under|1st Half|O 2.5"),
            new ColumnDef("ILK YARI A/U 2,5", "1ST_2,5_UNDER_A", "Over/Under|1st Half|U 2.5"),
            // --------------------- IKINCI YARI A/U (0.5 - 2.5) (6 sütun) ---------------------
            new ColumnDef("IKINCI YARI A/U 0,5", "2ND_0,5_OVER_A", "Over/Under|2nd Half|O 0.5"),
            new ColumnDef("IKINCI YARI A/U 0,5", "2ND_0,5_UNDER_A", "Over/Under|2nd Half|U 0.5"),
            new ColumnDef("IKINCI YARI A/U 1,5", "2ND_1,5_OVER_A", "Over/Under|2nd Half|O 1.5"),
            new ColumnDef("IKINCI YARI A/U 1,5", "2ND_1,5_UNDER_A", "Over/Under|2nd Half|U 1.5"),
            new ColumnDef("IKINCI YARI A/U 2,5", "2ND_2,5_OVER_A", "Over/Under|2nd Half|O 2.5"),
            new ColumnDef("IKINCI YARI A/U 2,5", "2ND_2,5_UNDER_A", "Over/Under|2nd Half|U 2.5"),
            // --------------------- ILK YARI / MAÇ SONU (9 sütun) ---------------------
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "1/1_A", "HTFT|1/1"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "1/X_A", "HTFT|1/X"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "1/2_A", "HTFT|1/2"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "X/1_A", "HTFT|X/1"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "X/X_A", "HTFT|X/X"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "X/2_A", "HTFT|X/2"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "2/1_A", "HTFT|2/1"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "2/X_A", "HTFT|2/X"),
            new ColumnDef("ILK YARI / MAÇ SONU / HT-FT", "2/2_A", "HTFT|2/2"),
            // --------------------- ILK YARI SKOR (15 sütun) ---------------------
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_1:0_A", "Correct score|1st Half|1:0"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_2:0_A", "Correct score|1st Half|2:0"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_2:1_A", "Correct score|1st Half|2:1"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_3:0_A", "Correct score|1st Half|3:0"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_3:1_A", "Correct score|1st Half|3:1"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_3:2_A", "Correct score|1st Half|3:2"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_0:0_A", "Correct score|1st Half|0:0"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_1:1_A", "Correct score|1st Half|1:1"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_2:2_A", "Correct score|1st Half|2:2"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_0:1_A", "Correct score|1st Half|0:1"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_0:2_A", "Correct score|1st Half|0:2"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_1:2_A", "Correct score|1st Half|1:2"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_0:3_A", "Correct score|1st Half|0:3"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_1:3_A", "Correct score|1st Half|1:3"),
            new ColumnDef("ILK YARI SKOR / 1ST SCORE", "1ST_2:3_A", "Correct score|1st Half|2:3"),
            // --------------------- MAC SONU SKOR (30 sütun) ---------------------
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_1:0_A", "Correct score|Full Time|1:0"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_2:0_A", "Correct score|Full Time|2:0"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_2:1_A", "Correct score|Full Time|2:1"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_3:0_A", "Correct score|Full Time|3:0"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_3:1_A", "Correct score|Full Time|3:1"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_3:2_A", "Correct score|Full Time|3:2"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_4:0_A", "Correct score|Full Time|4:0"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_4:1_A", "Correct score|Full Time|4:1"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_4:2_A", "Correct score|Full Time|4:2"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_4:3_A", "Correct score|Full Time|4:3"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_5:0_A", "Correct score|Full Time|5:0"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_5:1_A", "Correct score|Full Time|5:1"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_5:2_A", "Correct score|Full Time|5:2"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_0:0_A", "Correct score|Full Time|0:0"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_1:1_A", "Correct score|Full Time|1:1"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_2:2_A", "Correct score|Full Time|2:2"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_3:3_A", "Correct score|Full Time|3:3"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_4:4_A", "Correct score|Full Time|4:4"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_0:1_A", "Correct score|Full Time|0:1"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_0:2_A", "Correct score|Full Time|0:2"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_1:2_A", "Correct score|Full Time|1:2"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_0:3_A", "Correct score|Full Time|0:3"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_1:3_A", "Correct score|Full Time|1:3"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_2:3_A", "Correct score|Full Time|2:3"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_0:4_A", "Correct score|Full Time|0:4"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_1:4_A", "Correct score|Full Time|1:4"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_2:4_A", "Correct score|Full Time|2:4"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_3:4_A", "Correct score|Full Time|3:4"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_0:5_A", "Correct score|Full Time|0:5"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_1:5_A", "Correct score|Full Time|1:5"),
            new ColumnDef("MAC SONU SKOR / FT SCORE", "FT_2:5_A", "Correct score|Full Time|2:5")
    );

    public static final List<String> STATIC_COLUMN_KEYS = COLUMN_DEFS.stream()
            .map(cd -> cd.dataKey)
            .collect(Collectors.toList());
}